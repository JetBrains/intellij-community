// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.varScopeCanBeNarrowed;

import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo;
import com.intellij.codeInsight.intention.preview.IntentionPreviewUtils;
import com.intellij.codeInspection.*;
import com.intellij.java.JavaBundle;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.controlFlow.*;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.search.searches.SuperMethodsSearch;
import com.intellij.refactoring.JavaRefactoringFactory;
import com.intellij.refactoring.changeSignature.ParameterInfoImpl;
import com.intellij.util.VisibilityUtil;
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

  @Override
  @NotNull
  public String getGroupDisplayName() {
    return InspectionsBundle.message("group.names.class.structure");
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

    Collection<PsiParameter> writtenBeforeReadParameters = getWriteBeforeRead(parameters, body);
    if (writtenBeforeReadParameters.isEmpty() || MethodUtils.isOverridden(method)) {
      return ProblemDescriptor.EMPTY_ARRAY;
    }
    final List<ProblemDescriptor> result = new ArrayList<>();
    for (PsiParameter parameter : writtenBeforeReadParameters) {
      final PsiIdentifier identifier = parameter.getNameIdentifier();
      if (identifier != null && identifier.isPhysical()) {
        result.add(createProblem(manager, identifier, isOnTheFly));
      }
    }
    return result.toArray(ProblemDescriptor.EMPTY_ARRAY);
  }

  @NotNull
  private static ProblemDescriptor createProblem(@NotNull InspectionManager manager,
                                                 @NotNull PsiIdentifier identifier,
                                                 boolean isOnTheFly) {
    return manager.createProblemDescriptor(
      identifier,
      JavaBundle.message("inspection.parameter.can.be.local.problem.descriptor"),
      true,
      ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
      isOnTheFly,
      new ConvertParameterToLocalQuickFix()
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
    if (result.isEmpty()) return Collections.emptyList();
    result.retainAll(ControlFlowUtil.getWrittenVariables(controlFlow, 0, controlFlow.getSize(), false));
    if (result.isEmpty()) return Collections.emptyList();
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

  private static final class ConvertParameterToLocalQuickFix extends BaseConvertToLocalQuickFix<PsiParameter> {
    @Override
    protected PsiParameter getVariable(@NotNull ProblemDescriptor descriptor) {
      return (PsiParameter)descriptor.getPsiElement().getParent();
    }

    @NotNull
    @Override
    protected String suggestLocalName(@NotNull Project project, @NotNull PsiParameter parameter, @NotNull PsiCodeBlock scope) {
      return parameter.getName();
    }

    @Override
    public @NotNull IntentionPreviewInfo generatePreview(@NotNull Project project, @NotNull ProblemDescriptor previewDescriptor) {
      applyFix(project, previewDescriptor);
      return IntentionPreviewInfo.DIFF;
    }

    @Override
    @NotNull
    protected List<PsiElement> moveDeclaration(@NotNull Project project, @NotNull PsiParameter variable) {
      final Collection<PsiReference> references = ReferencesSearch.search(variable).findAll();
      if (references.isEmpty()) return Collections.emptyList();
      final PsiElement scope = variable.getDeclarationScope();
      if (!(scope instanceof PsiMethod)) return Collections.emptyList();
      final PsiMethod method = (PsiMethod)scope;
      if (!IntentionPreviewUtils.prepareElementForWrite(method)) return Collections.emptyList();
      final PsiParameter[] parameters = method.getParameterList().getParameters();
      final List<ParameterInfoImpl> info = new ArrayList<>();
      for (int i = 0; i < parameters.length; i++) {
        PsiParameter psiParameter = parameters[i];
        if (psiParameter == variable) continue;
        info.add(ParameterInfoImpl.create(i).withName(psiParameter.getName()).withType(psiParameter.getType()));
      }
      final ParameterInfoImpl[] newParams = info.toArray(new ParameterInfoImpl[0]);
      final String visibilityModifier = VisibilityUtil.getVisibilityModifier(method.getModifierList());
      PsiElement moved = IntentionPreviewUtils.writeAndCompute(() -> copyVariableToMethodBody(variable, references));
      if (moved == null) return Collections.emptyList();
      SmartPsiElementPointer<PsiElement> newDeclaration = SmartPointerManager.createPointer(moved);
      if (IntentionPreviewUtils.isPreviewElement(variable)) {
        variable.delete();
      } else {
        var processor = JavaRefactoringFactory.getInstance(project).createChangeSignatureProcessor(
          method, false, visibilityModifier, method.getName(), method.getReturnType(), newParams,
          null, null, null, null
        );
        processor.run();
      }
      return Collections.singletonList(Objects.requireNonNull(newDeclaration.getElement()));
    }
  }
}
