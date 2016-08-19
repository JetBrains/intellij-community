/*
 * Copyright 2000-2013 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.codeInspection.varScopeCanBeNarrowed;

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.codeInspection.*;
import com.intellij.psi.*;
import com.intellij.psi.controlFlow.*;
import com.intellij.psi.search.searches.SuperMethodsSearch;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class ParameterCanBeLocalInspectionBase extends BaseJavaBatchLocalInspectionTool {
  @NonNls public static final String SHORT_NAME = "ParameterCanBeLocal";

  @NotNull
  private static List<PsiParameter> filterFinal(PsiParameter[] parameters) {
    final List<PsiParameter> result = new ArrayList<>(parameters.length);
    for (PsiParameter parameter : parameters) {
      if (!parameter.hasModifierProperty(PsiModifier.FINAL)) {
        result.add(parameter);
      }
    }
    return result;
  }

  private static Collection<PsiParameter> getWriteBeforeRead(@NotNull Collection<PsiParameter> parameters,
                                                             @NotNull PsiCodeBlock body) {
    final ControlFlow controlFlow = getControlFlow(body);
    if (controlFlow == null) return Collections.emptyList();

    final Set<PsiParameter> result = filterParameters(controlFlow, parameters);
    result.retainAll(ControlFlowUtil.getWrittenVariables(controlFlow, 0, controlFlow.getSize(), false));
    for (final PsiReferenceExpression readBeforeWrite : ControlFlowUtil.getReadBeforeWrite(controlFlow)) {
      final PsiElement resolved = readBeforeWrite.resolve();
      if (resolved instanceof PsiParameter) {
        result.remove(resolved);
      }
    }

    return result;
  }

  private static Set<PsiParameter> filterParameters(@NotNull ControlFlow controlFlow, @NotNull Collection<PsiParameter> parameters) {
    final Set<PsiVariable> usedVars = new HashSet<>(ControlFlowUtil.getUsedVariables(controlFlow, 0, controlFlow.getSize()));

    final Set<PsiParameter> result = new HashSet<>();
    for (PsiParameter parameter : parameters) {
      if (usedVars.contains(parameter)) {
        result.add(parameter);
      }
    }
    return result;
  }

  private static boolean isOverrides(PsiMethod method) {
    return SuperMethodsSearch.search(method, null, true, false).findFirst() != null;
  }

  @Nullable
  private static ControlFlow getControlFlow(final PsiElement context) {
    try {
      return ControlFlowFactory.getInstance(context.getProject())
        .getControlFlow(context, LocalsOrMyInstanceFieldsControlFlowPolicy.getInstance());
    }
    catch (AnalysisCanceledException e) {
      return null;
    }
  }

  @Override
  @NotNull
  public String getGroupDisplayName() {
    return GroupNames.CLASS_LAYOUT_GROUP_NAME;
  }

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionsBundle.message("inspection.parameter.can.be.local.display.name");
  }

  @Override
  @NotNull
  public String getShortName() {
    return SHORT_NAME;
  }

  @Override
  public ProblemDescriptor[] checkMethod(@NotNull PsiMethod method, @NotNull InspectionManager manager, boolean isOnTheFly) {
    final Collection<PsiParameter> parameters = filterFinal(method.getParameterList().getParameters());
    final PsiCodeBlock body = method.getBody();
    if (body == null || parameters.isEmpty() || isOverrides(method)) {
      return ProblemDescriptor.EMPTY_ARRAY;
    }

    final List<ProblemDescriptor> result = new ArrayList<>();
    for (PsiParameter parameter : getWriteBeforeRead(parameters, body)) {
      final PsiIdentifier identifier = parameter.getNameIdentifier();
      if (identifier != null && identifier.isPhysical()) {
        result.add(createProblem(manager, identifier, isOnTheFly));
      }
    }
    return result.toArray(new ProblemDescriptor[result.size()]);
  }

  @NotNull
  private ProblemDescriptor createProblem(@NotNull InspectionManager manager,
                                          @NotNull PsiIdentifier identifier,
                                          boolean isOnTheFly) {
    return manager.createProblemDescriptor(
      identifier,
      InspectionsBundle.message("inspection.parameter.can.be.local.problem.descriptor"),
      true,
      ProblemHighlightType.LIKE_UNUSED_SYMBOL,
      isOnTheFly,
      createFix()
    );
  }

  protected LocalQuickFix createFix() {
    return null;
  }
}
