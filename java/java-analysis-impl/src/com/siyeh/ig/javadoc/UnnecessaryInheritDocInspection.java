// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.javadoc;

import com.intellij.codeInspection.CleanupLocalInspectionTool;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.modcommand.PsiUpdateModCommandQuickFix;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.javadoc.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.MethodUtils;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public final class UnnecessaryInheritDocInspection extends BaseInspection implements CleanupLocalInspectionTool {

  @NotNull
  @Override
  protected String buildErrorString(Object... infos) {
    return switch ((WarningType)infos[0]) {
      case MODULE -> InspectionGadgetsBundle.message("unnecessary.inherit.doc.module.invalid.problem.descriptor");
      case CLASS -> InspectionGadgetsBundle.message("unnecessary.inherit.doc.class.invalid.problem.descriptor");
      case FIELD -> InspectionGadgetsBundle.message("unnecessary.inherit.doc.field.invalid.problem.descriptor");
      case CONSTRUCTOR -> InspectionGadgetsBundle.message("unnecessary.inherit.doc.constructor.invalid.problem.descriptor");
      case NO_SUPER -> InspectionGadgetsBundle.message("unnecessary.inherit.doc.constructor.no.super.problem.descriptor");
      case EMPTY -> InspectionGadgetsBundle.message("unnecessary.inherit.doc.problem.descriptor");
    };
  }

  enum WarningType {
    MODULE, CLASS, FIELD, CONSTRUCTOR, EMPTY, NO_SUPER
  }

  @Override
  protected LocalQuickFix buildFix(Object... infos) {
    return new UnnecessaryInheritDocFix();
  }

  private static class UnnecessaryInheritDocFix extends PsiUpdateModCommandQuickFix {

    @Override
    @NotNull
    public String getFamilyName() {
      return InspectionGadgetsBundle.message(
        "unnecessary.inherit.doc.quickfix");
    }

    @Override
    protected void applyFix(@NotNull Project project, @NotNull PsiElement element, @NotNull ModPsiUpdater updater) {
      if (!(element instanceof PsiDocTag docTag)) {
        return;
      }
      final PsiElement parent = docTag.getParent();
      if (parent instanceof PsiDocComment docComment) {
        final PsiDocTag[] docTags = docComment.getTags();
        if (docTags.length > 0) {
          element.delete();
          return;
        }
        final PsiDocToken[] docTokens = PsiTreeUtil.getChildrenOfType(parent, PsiDocToken.class);
        if (docTokens != null) {
          for (PsiDocToken docToken : docTokens) {
            final IElementType tokenType = docToken.getTokenType();
            if (JavaDocTokenType.DOC_COMMENT_DATA.equals(tokenType) && !StringUtil.isEmptyOrSpaces(docToken.getText())) {
              element.delete();
              return;
            }
          }
        }
      }
      parent.delete();
    }
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new UnnecessaryInheritDocVisitor();
  }

  private static class UnnecessaryInheritDocVisitor extends BaseInspectionVisitor {

    @Override
    public void visitInlineDocTag(@NotNull PsiInlineDocTag tag) {
      @NonNls final String name = tag.getName();
      if (!"inheritDoc".equals(name)) {
        return;
      }
      final PsiDocComment docComment = tag.getContainingComment();
      if (docComment == null) {
        return;
      }
      final PsiJavaDocumentedElement owner = docComment.getOwner();
      if (owner instanceof PsiJavaModule) {
        registerError(tag, WarningType.MODULE);
        return;
      }
      if (owner instanceof PsiField) {
        registerError(tag, WarningType.FIELD);
        return;
      }
      else if (owner instanceof PsiClass) {
        registerError(tag, WarningType.CLASS);
        return;
      }
      else if (owner instanceof PsiMethod method) {
        if (method.isConstructor()) {
          registerError(tag, WarningType.CONSTRUCTOR);
          return;
        }
        if (!MethodUtils.hasSuper(method)) {
          registerError(tag, WarningType.NO_SUPER);
          return;
        }
      }
      else {
        return;
      }
      final PsiElement parent = tag.getParent();
      if (parent instanceof PsiDocTag docTag) {
        @NonNls final String docTagName = docTag.getName();
        if ((docTagName.equals("throws") || docTagName.equals("exception")) &&
            !isCheckExceptionAndPresentInThrowsList((PsiMethod)owner, docTag)) {
          return;
        }
      }
      final PsiDocToken[] docTokens = PsiTreeUtil.getChildrenOfType(parent, PsiDocToken.class);
      if (docTokens == null) {
        return;
      }
      for (PsiDocToken docToken : docTokens) {
        final IElementType tokenType = docToken.getTokenType();
        if (!JavaDocTokenType.DOC_COMMENT_DATA.equals(tokenType)) {
          continue;
        }
        if (!StringUtil.isEmptyOrSpaces(docToken.getText())) {
          return;
        }
      }
      registerError(tag, WarningType.EMPTY);
    }

    private static boolean isCheckExceptionAndPresentInThrowsList(PsiMethod method, PsiDocTag docTag) {
      final PsiDocTagValue valueElement = docTag.getValueElement();
      final PsiJavaCodeReferenceElement referenceElement =
        PsiTreeUtil.findChildOfType(valueElement, PsiJavaCodeReferenceElement.class);
      if (referenceElement != null) {
        final PsiElement target = referenceElement.resolve();
        if (!(target instanceof PsiClass aClass)) {
          return false;
        }
        if (!InheritanceUtil.isInheritor(aClass, CommonClassNames.JAVA_LANG_EXCEPTION) ||
          InheritanceUtil.isInheritor(aClass, CommonClassNames.JAVA_LANG_RUNTIME_EXCEPTION)) {
          return false;
        }
        final PsiReferenceList throwsList = method.getThrowsList();
        final PsiJavaCodeReferenceElement[] elements = throwsList.getReferenceElements();
        boolean found = false;
        for (PsiJavaCodeReferenceElement element : elements) {
          if (target.equals(element.resolve())) {
            found = true;
          }
        }
        if (!found){
          return false;
        }
      }
      return true;
    }
  }
}
