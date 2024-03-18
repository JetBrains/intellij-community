// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.javadoc;

import com.intellij.codeInspection.CleanupLocalInspectionTool;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.modcommand.PsiUpdateModCommandQuickFix;
import com.intellij.codeInspection.options.OptPane;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.javadoc.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.MethodSignature;
import com.intellij.psi.util.MethodSignatureUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import static com.intellij.codeInspection.options.OptPane.checkbox;
import static com.intellij.codeInspection.options.OptPane.pane;

public final class UnnecessaryJavaDocLinkInspection extends BaseInspection implements CleanupLocalInspectionTool {

  private static final int THIS_METHOD = 1;
  private static final int THIS_CLASS = 2;
  private static final int SUPER_METHOD = 3;

  @SuppressWarnings("PublicField")
  public boolean ignoreInlineLinkToSuper = false;

  @NotNull
  @Override
  protected String buildErrorString(Object... infos) {
    final int n = ((Integer)infos[1]).intValue();
    if (n == THIS_METHOD) {
      return InspectionGadgetsBundle.message(
        "unnecessary.javadoc.link.this.method.problem.descriptor");
    }
    if (n == THIS_CLASS) {
      return InspectionGadgetsBundle.message(
        "unnecessary.javadoc.link.this.class.problem.descriptor");
    }
    return InspectionGadgetsBundle.message(
      "unnecessary.javadoc.link.super.method.problem.descriptor");
  }

  @Override
  public @NotNull OptPane getOptionsPane() {
    return pane(
      checkbox("ignoreInlineLinkToSuper", InspectionGadgetsBundle.message(
        "unnecessary.javadoc.link.option")));
  }

  @Override
  protected LocalQuickFix buildFix(Object... infos) {
    return new UnnecessaryJavaDocLinkFix((String)infos[0]);
  }

  private static class UnnecessaryJavaDocLinkFix
    extends PsiUpdateModCommandQuickFix {

    private final String tagName;

    UnnecessaryJavaDocLinkFix(String tagName) {
      this.tagName = tagName;
    }

    @Override
    @NotNull
    public String getName() {
      return InspectionGadgetsBundle.message(
        "unnecessary.javadoc.link.quickfix", tagName);
    }

    @NotNull
    @Override
    public String getFamilyName() {
      return InspectionGadgetsBundle.message("unnecessary.java.doc.link.fix.family.name");
    }

    @Override
    protected void applyFix(@NotNull Project project, @NotNull PsiElement element, @NotNull ModPsiUpdater updater) {
      final PsiElement parent = element.getParent();
      if (!(parent instanceof PsiDocTag docTag)) {
        return;
      }
      final PsiDocComment docComment = docTag.getContainingComment();
      if (docComment != null) {
        if (shouldDeleteEntireComment(docComment)) {
          docComment.delete();
          return;
        }
      }
      docTag.delete();
    }

    private static boolean shouldDeleteEntireComment(
      PsiDocComment docComment) {
      final PsiDocToken[] docTokens = PsiTreeUtil.getChildrenOfType(
        docComment, PsiDocToken.class);
      if (docTokens == null) {
        return false;
      }
      for (PsiDocToken docToken : docTokens) {
        final IElementType tokenType = docToken.getTokenType();
        if (!JavaDocTokenType.DOC_COMMENT_DATA.equals(tokenType)) {
          continue;
        }
        if (!StringUtil.isEmptyOrSpaces(docToken.getText())) {
          return false;
        }
      }
      return true;
    }
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new UnnecessaryJavaDocLinkVisitor();
  }

  private class UnnecessaryJavaDocLinkVisitor
    extends BaseInspectionVisitor {

    @Override
    public void visitDocTag(@NotNull PsiDocTag tag) {
      super.visitDocTag(tag);
      @NonNls final String name = tag.getName();
      if ("link".equals(name) || "linkplain".equals(name)) {
        if (!(tag instanceof PsiInlineDocTag)) {
          return;
        }
      }
      else if ("see".equals(name)) {
        if (tag instanceof PsiInlineDocTag) {
          return;
        }
      }
      final PsiReference reference = extractReference(tag);
      if (reference == null) {
        return;
      }
      final PsiElement target = reference.resolve();
      if (target == null) {
        return;
      }
      final PsiMethod containingMethod =
        PsiTreeUtil.getParentOfType(tag, PsiMethod.class);
      if (containingMethod == null) {
        return;
      }
      if (target.equals(containingMethod)) {
        registerError(tag.getNameElement(), '@' + name,
                      Integer.valueOf(THIS_METHOD));
        return;
      }
      final PsiClass containingClass =
        PsiTreeUtil.getParentOfType(tag, PsiClass.class);
      if (target.equals(containingClass)) {
        registerError(tag.getNameElement(), '@' + name,
                      Integer.valueOf(THIS_CLASS));
        return;
      }
      if (!(target instanceof PsiMethod method)) {
        return;
      }
      if (!isSuperMethod(method, containingMethod)) {
        return;
      }
      if (ignoreInlineLinkToSuper && tag instanceof PsiInlineDocTag) {
        return;
      }
      registerError(tag.getNameElement(), '@' + name,
                    Integer.valueOf(SUPER_METHOD));
    }

    private static PsiReference extractReference(PsiDocTag tag) {
      final PsiDocTagValue valueElement = tag.getValueElement();
      if (valueElement != null) {
        return valueElement.getReference();
      }
      // hack around the fact that a reference to a class is apparently
      // not a PsiDocTagValue
      final PsiElement[] dataElements = tag.getDataElements();
      if (dataElements.length == 0) {
        return null;
      }
      PsiElement salientElement = null;
      for (PsiElement dataElement : dataElements) {
        if (!(dataElement instanceof PsiWhiteSpace)) {
          salientElement = dataElement;
          break;
        }
      }
      if (salientElement == null) {
        return null;
      }
      final PsiElement child = salientElement.getFirstChild();
      if (!(child instanceof PsiReference)) {
        return null;
      }
      return (PsiReference)child;
    }

    public boolean isSuperMethod(PsiMethod superMethodCandidate,
                                 PsiMethod derivedMethod) {
      final PsiClass superClassCandidate =
        superMethodCandidate.getContainingClass();
      final PsiClass derivedClass = derivedMethod.getContainingClass();
      if (derivedClass == null || superClassCandidate == null) {
        return false;
      }
      if (!derivedClass.isInheritor(superClassCandidate, false)) {
        return false;
      }
      final PsiSubstitutor superSubstitutor =
        TypeConversionUtil.getSuperClassSubstitutor(
          superClassCandidate, derivedClass,
          PsiSubstitutor.EMPTY);
      final MethodSignature superSignature =
        superMethodCandidate.getSignature(superSubstitutor);
      final MethodSignature derivedSignature =
        derivedMethod.getSignature(PsiSubstitutor.EMPTY);
      return MethodSignatureUtil.isSubsignature(superSignature,
                                                derivedSignature);
    }
  }
}