/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.controlFlow.*;
import com.intellij.psi.search.searches.SuperMethodsSearch;
import com.intellij.refactoring.changeSignature.ChangeSignatureProcessor;
import com.intellij.refactoring.changeSignature.JavaChangeInfo;
import com.intellij.refactoring.changeSignature.JavaChangeInfoImpl;
import com.intellij.refactoring.changeSignature.ParameterInfoImpl;
import com.intellij.refactoring.util.CanonicalTypes;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.NotNullFunction;
import com.intellij.util.VisibilityUtil;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.ig.psiutils.MethodUtils;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * @author Danila Ponomarenko
 */
public class ParameterCanBeLocalInspection extends AbstractBaseJavaLocalInspectionTool {
  @NonNls public static final String SHORT_NAME = "ParameterCanBeLocal";

  protected ConvertParameterToLocalQuickFix createFix() {
    return new ConvertParameterToLocalQuickFix();
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
    if (body == null || parameters.isEmpty() || isOverrides(method) || MethodUtils.isOverridden(method)) {
      return ProblemDescriptor.EMPTY_ARRAY;
    }

    final List<ProblemDescriptor> result = new ArrayList<>();
    for (PsiParameter parameter : getWriteBeforeRead(parameters, body)) {
      final PsiIdentifier identifier = parameter.getNameIdentifier();
      if (identifier != null && identifier.isPhysical()) {
        result.add(createProblem(manager, identifier, isOnTheFly));
      }
    }
    return result.toArray(ProblemDescriptor.EMPTY_ARRAY);
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

  private static Collection<PsiParameter> getWriteBeforeRead(@NotNull Collection<? extends PsiParameter> parameters,
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

  private static Set<PsiParameter> filterParameters(@NotNull ControlFlow controlFlow, @NotNull Collection<? extends PsiParameter> parameters) {
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

  public static class ConvertParameterToLocalQuickFix extends BaseConvertToLocalQuickFix<PsiParameter> {
    @Override
    protected PsiParameter getVariable(@NotNull ProblemDescriptor descriptor) {
      return (PsiParameter)descriptor.getPsiElement().getParent();
    }

    @Override
    protected PsiElement applyChanges(@NotNull final Project project,
                                      @NotNull final String localName,
                                      @Nullable final PsiExpression initializer,
                                      @NotNull final PsiParameter parameter,
                                      @NotNull final Collection<? extends PsiReference> references,
                                      boolean delete, 
                                      @NotNull final NotNullFunction<? super PsiDeclarationStatement, ? extends PsiElement> action) {
      final PsiElement scope = parameter.getDeclarationScope();
      if (scope instanceof PsiMethod) {
        final PsiMethod method = (PsiMethod)scope;
        final PsiParameter[] parameters = method.getParameterList().getParameters();

        final List<ParameterInfoImpl> info = new ArrayList<>();
        for (int i = 0; i < parameters.length; i++) {
          PsiParameter psiParameter = parameters[i];
          if (psiParameter == parameter) continue;
          info.add(new ParameterInfoImpl(i, psiParameter.getName(), psiParameter.getType()));
        }
        final ParameterInfoImpl[] newParams = info.toArray(new ParameterInfoImpl[0]);
        final String visibilityModifier = VisibilityUtil.getVisibilityModifier(method.getModifierList());
        final PsiType returnType = method.getReturnType();
        final JavaChangeInfo changeInfo = new JavaChangeInfoImpl(visibilityModifier, method, method.getName(),
                                                                 returnType != null ? CanonicalTypes.createTypeWrapper(returnType) : null,
                                                                 newParams, null, false, ContainerUtil.newHashSet(), ContainerUtil.newHashSet());
        class ParameterToLocalProcessor extends ChangeSignatureProcessor {
          private PsiElement newDeclaration;

          ParameterToLocalProcessor(Project project, JavaChangeInfo changeInfo) {
            super(project, changeInfo);
          }

          @Override
          protected void performRefactoring(@NotNull UsageInfo[] usages) {
            final PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(project);
            newDeclaration = moveDeclaration(elementFactory, localName, parameter, initializer, action, references);
            super.performRefactoring(usages);
          }
        }

        final ParameterToLocalProcessor processor = new ParameterToLocalProcessor(project, changeInfo);
        processor.run();

        return processor.newDeclaration;
      }
      return null;
    }

    @Override
    public boolean startInWriteAction() {
      return false;
    }

    @NotNull
    @Override
    protected String suggestLocalName(@NotNull Project project, @NotNull PsiParameter parameter, @NotNull PsiCodeBlock scope) {
      return parameter.getName();
    }
  }
}
