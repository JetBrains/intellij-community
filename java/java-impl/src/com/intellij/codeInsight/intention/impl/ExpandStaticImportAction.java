// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.intention.impl;

import com.intellij.codeInsight.intention.BaseElementAtCaretIntentionAction;
import com.intellij.java.JavaBundle;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.PopupStep;
import com.intellij.openapi.ui.popup.util.BaseListPopupStep;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

import java.util.List;

import static com.intellij.psi.util.ImportsUtil.*;

public class ExpandStaticImportAction extends BaseElementAtCaretIntentionAction {
  private static final String REPLACE_THIS_OCCURRENCE = "Replace this occurrence and keep the import";
  private static final String REPLACE_ALL_AND_DELETE_IMPORT = "Replace all and delete the import";

  @Override
  @NotNull
  public String getFamilyName() {
    return JavaBundle.message("intention.family.expand.static.import");
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, @NotNull PsiElement element) {
    if (!PsiUtil.isLanguageLevel5OrHigher(element)) return false;
    final PsiElement parent = element.getParent();
    if (!(element instanceof PsiIdentifier) || !(parent instanceof PsiJavaCodeReferenceElement referenceElement)) {
      return false;
    }
    final PsiElement resolveScope = getImportStaticStatement(referenceElement);
    if (resolveScope instanceof PsiImportStaticStatement) {
      final PsiClass targetClass = ((PsiImportStaticStatement)resolveScope).resolveTargetClass();
      if (targetClass == null) return false;
      setText(JavaBundle.message("intention.text.replace.static.import.with.qualified.access.to.0", targetClass.getName()));
      return true;
    }
    return false;
  }

  private static PsiElement getImportStaticStatement(PsiJavaCodeReferenceElement referenceElement) {
    PsiElement parent = referenceElement.getParent();
    return parent instanceof PsiImportStaticStatement ? parent
                                                      : referenceElement.advancedResolve(true).getCurrentFileResolveScope();
  }

  public void invoke(final Project project, final PsiFile file, final Editor editor, PsiElement element) {
    final PsiJavaCodeReferenceElement refExpr = (PsiJavaCodeReferenceElement)element.getParent();
    final PsiImportStaticStatement staticImport = (PsiImportStaticStatement) getImportStaticStatement(refExpr);
    final List<PsiJavaCodeReferenceElement> expressionToExpand = collectReferencesThrough(file, refExpr, staticImport);

    if (expressionToExpand.isEmpty()) {
      expand(refExpr, staticImport);
      staticImport.delete();
    }
    else {
      if (ApplicationManager.getApplication().isUnitTestMode() || refExpr.getParent() instanceof PsiImportStaticStatement ||
          !file.isPhysical()) {
        replaceAllAndDeleteImport(expressionToExpand, refExpr, staticImport);
      }
      else {
        final BaseListPopupStep<String> step =
          new BaseListPopupStep<>(JavaBundle.message("multiple.usages.of.static.import.found"), REPLACE_THIS_OCCURRENCE,
                                  REPLACE_ALL_AND_DELETE_IMPORT) {
            @Override
            public PopupStep onChosen(final String selectedValue, boolean finalChoice) {
              WriteCommandAction.writeCommandAction(project).withName(ExpandStaticImportAction.this.getText()).run(() -> {
                if (selectedValue.equals(REPLACE_THIS_OCCURRENCE)) {
                  expand(refExpr, staticImport);
                }
                else {
                  replaceAllAndDeleteImport(expressionToExpand, refExpr, staticImport);
                }
              });
              return FINAL_CHOICE;
            }
          };
        JBPopupFactory.getInstance().createListPopup(step).showInBestPositionFor(editor);
      }
    }
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, @NotNull PsiElement element) throws IncorrectOperationException {
    invoke(project, element.getContainingFile(), editor, element);
  }
}
