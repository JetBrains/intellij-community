// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.varScopeCanBeNarrowed;

import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo;
import com.intellij.codeInsight.intention.preview.IntentionPreviewUtils;
import com.intellij.codeInspection.*;
import com.intellij.java.JavaBundle;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.controlFlow.*;
import com.intellij.psi.search.searches.SuperMethodsSearch;
import com.intellij.refactoring.JavaRefactoringFactory;
import com.intellij.refactoring.changeSignature.ParameterInfoImpl;
import com.intellij.util.IJSwingUtilities;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.VisibilityUtil;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.ig.psiutils.MethodUtils;
import com.siyeh.ig.psiutils.VariableAccessUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.stream.Collectors;

public final class ParameterCanBeLocalInspection extends AbstractBaseJavaLocalInspectionTool {

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

  private static @NotNull ProblemDescriptor createProblem(@NotNull InspectionManager manager,
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

  private static @NotNull List<PsiParameter> filterFinal(PsiParameter[] parameters) {
    return ContainerUtil.filter(parameters, parameter -> !parameter.hasModifierProperty(PsiModifier.FINAL));
  }

  private static Collection<PsiParameter> getWriteBeforeRead(@NotNull Collection<? extends PsiParameter> parameters,
                                                             @NotNull PsiCodeBlock body) {
    final ControlFlow controlFlow = getControlFlow(body);
    if (controlFlow == null) return Collections.emptyList();

    final Set<PsiParameter> result = filterParameters(controlFlow, parameters);
    if (result.isEmpty()) return Collections.emptyList();
    result.retainAll(ControlFlowUtil.getWrittenVariables(controlFlow, 0, controlFlow.getSize(), false));
    if (result.isEmpty()) return Collections.emptyList();
    for (PsiReferenceExpression readBeforeWrite : ControlFlowUtil.getReadBeforeWrite(controlFlow)) {
      if (readBeforeWrite.resolve() instanceof PsiParameter param) {
        result.remove(param);
      }
    }

    return result;
  }

  private static Set<PsiParameter> filterParameters(@NotNull ControlFlow controlFlow,
                                                    @NotNull Collection<? extends PsiParameter> parameters) {
    final Set<PsiVariable> usedVars = new HashSet<>(ControlFlowUtil.getUsedVariables(controlFlow, 0, controlFlow.getSize()));
    return parameters.stream().filter(usedVars::contains).collect(Collectors.toSet());
  }

  private static boolean isOverrides(PsiMethod method) {
    return SuperMethodsSearch.search(method, null, true, false).findFirst() != null;
  }

  private static @Nullable ControlFlow getControlFlow(final PsiElement context) {
    try {
      return ControlFlowFactory.getInstance(context.getProject())
        .getControlFlow(context, LocalsOrMyInstanceFieldsControlFlowPolicy.getInstance());
    }
    catch (AnalysisCanceledException e) {
      return null;
    }
  }

  private static final class ConvertParameterToLocalQuickFix implements LocalQuickFix {
    private static final Logger LOG = Logger.getInstance(ConvertParameterToLocalQuickFix.class);

    @Override
    public @NotNull IntentionPreviewInfo generatePreview(@NotNull Project project, @NotNull ProblemDescriptor previewDescriptor) {
      applyFix(project, previewDescriptor);
      return IntentionPreviewInfo.DIFF;
    }

    private static @NotNull List<PsiElement> moveDeclaration(@NotNull Project project, @NotNull PsiParameter variable) {
      final List<PsiReferenceExpression> references = VariableAccessUtils.getVariableReferences(variable);
      if (references.isEmpty()) return Collections.emptyList();
      final PsiElement scope = variable.getDeclarationScope();
      if (!(scope instanceof PsiMethod method)) return Collections.emptyList();
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
      PsiElement moved = IntentionPreviewUtils.writeAndCompute(
        () -> ConvertToLocalUtils.copyVariableToMethodBody(variable, references, block -> variable.getName()));
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

    @Override
    public @NotNull String getFamilyName() {
      return JavaBundle.message("inspection.convert.to.local.quickfix");
    }

    @Override
    public boolean startInWriteAction() {
      return false;
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      final PsiParameter variable = (PsiParameter)descriptor.getPsiElement().getParent();
      if (variable == null || !variable.isValid()) return; //weird. should not get here when field becomes invalid
      if (!IntentionPreviewUtils.prepareElementForWrite(descriptor.getPsiElement())) return;
      final PsiFile myFile = variable.getContainingFile();
      try {
        final List<PsiElement> newDeclarations = moveDeclaration(project, variable);
        if (newDeclarations.isEmpty()) return;
        positionCaretToDeclaration(project, myFile, newDeclarations.get(newDeclarations.size() - 1));
        newDeclarations.forEach(declaration -> IntentionPreviewUtils.write(() -> ConvertToLocalUtils.inlineRedundant(declaration)));
      }
      catch (IncorrectOperationException e) {
        LOG.error(e);
      }
    }

    private static void positionCaretToDeclaration(@NotNull Project project, @NotNull PsiFile psiFile, @NotNull PsiElement declaration) {
      if (!psiFile.isPhysical()) return;
      final Editor editor = FileEditorManager.getInstance(project).getSelectedTextEditor();
      if (editor != null && (IJSwingUtilities.hasFocus(editor.getComponent()) || ApplicationManager.getApplication().isUnitTestMode())) {
        final PsiFile openedFile = PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument());
        if (openedFile == psiFile) {
          editor.getCaretModel().moveToOffset(declaration.getTextOffset());
          editor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
        }
      }
    }
  }
}
