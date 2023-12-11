// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testIntegration.createTest;

import com.intellij.codeInsight.CodeInsightUtil;
import com.intellij.codeInsight.FileModificationService;
import com.intellij.codeInsight.TestFrameworks;
import com.intellij.codeInsight.hint.HintManager;
import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction;
import com.intellij.ide.util.PsiClassListCellRenderer;
import com.intellij.java.JavaBundle;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testIntegration.TestFinderHelper;
import com.intellij.testIntegration.TestFramework;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;

public final class GenerateMissedTestsAction extends PsiElementBaseIntentionAction {

  @Override
  @NotNull
  public String getText() {
    return JavaBundle.message("intention.text.generate.missed.test.methods");
  }

  @Override
  @NotNull
  public String getFamilyName() {
    return getText();
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, @NotNull PsiElement element) {
    if (!TestFramework.EXTENSION_NAME.hasAnyExtensions()) return false;

    final PsiElement parent = element.getParent();
    if (!(parent instanceof PsiMethod)) return false;

    if (!((PsiMethod)parent).hasModifierProperty(PsiModifier.PUBLIC) ||
        ((PsiMethod)parent).hasModifierProperty(PsiModifier.ABSTRACT)) {
      return false;
    }

    PsiClass aClass = PsiTreeUtil.getParentOfType(element, PsiClass.class);
    return aClass != null && TestFrameworks.detectFramework(aClass) == null;
  }

  @Override
  public void invoke(final @NotNull Project project, final Editor editor, @NotNull PsiElement element) throws IncorrectOperationException {
    final PsiClass srcClass = PsiTreeUtil.getParentOfType(element, PsiClass.class);

    if (srcClass == null) return;

    final Collection<PsiElement> testClasses = ContainerUtil.filter(TestFinderHelper.findTestsForClass(srcClass), e -> e.getLanguage() == JavaLanguage.INSTANCE);

    if (testClasses.isEmpty()) {
      HintManager.getInstance().showErrorHint(editor, JavaBundle.message("generate.missed.tests.action.error.no.tests.found"));
      return;
    }

    if (testClasses.size() == 1) {
      generateMissedTests((PsiClass)ContainerUtil.getFirstItem(testClasses), srcClass, editor);
      return;
    }

    JBPopupFactory.getInstance()
      .createPopupChooserBuilder(new ArrayList<>(testClasses))
      .setRenderer(new PsiClassListCellRenderer())
      .setItemChosenCallback((selectedClass) -> generateMissedTests((PsiClass)selectedClass, srcClass, editor))
      .setTitle(JavaBundle.message("popup.title.choose.test"))
      .createPopup()
      .showInBestPositionFor(editor);
  }

  private static void generateMissedTests(final PsiClass testClass, final PsiClass srcClass, Editor srcEditor) {
    if (testClass != null) {
      final TestFramework framework = TestFrameworks.detectFramework(testClass);
      if (framework != null) {
        final Project project = testClass.getProject();
        final Editor editor = CodeInsightUtil.positionCursorAtLBrace(project, testClass.getContainingFile(), testClass);
        if (!FileModificationService.getInstance().preparePsiElementsForWrite(testClass)) return;
        final MissedTestsDialog dialog = new MissedTestsDialog(project, srcClass, testClass, framework);
        if (dialog.showAndGet()) {
          WriteCommandAction.runWriteCommandAction(project, () -> JavaTestGenerator.addTestMethods(editor, testClass, srcClass, framework, dialog.getSelectedMethods(), false, false));
        }
      }
      else {
        String message = JavaBundle.message("generate.missed.tests.action.failed.to.detect.framework", testClass.getQualifiedName());
        HintManager.getInstance().showErrorHint(srcEditor, message);
      }
    }
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }
}