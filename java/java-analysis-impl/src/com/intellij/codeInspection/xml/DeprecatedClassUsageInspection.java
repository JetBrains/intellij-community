// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.xml;

import com.intellij.codeInspection.LocalInspectionToolSession;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.codeInspection.XmlSuppressableInspectionTool;
import com.intellij.codeInspection.deprecation.DeprecationInspectionBase;
import com.intellij.java.analysis.JavaAnalysisBundle;
import com.intellij.psi.*;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

/**
 * @author Dmitry Avdeev
 */
public final class DeprecatedClassUsageInspection extends XmlSuppressableInspectionTool {

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull final ProblemsHolder holder,
                                        boolean isOnTheFly,
                                        @NotNull LocalInspectionToolSession session) {
    return new XmlElementVisitor() {
      @Override
      public void visitXmlTag(@NotNull XmlTag tag) {
        if (tag.getValue().getTextElements().length > 0) {
          checkReferences(tag, holder);
        }
      }

      @Override
      public void visitXmlAttributeValue(@NotNull XmlAttributeValue value) {
        checkReferences(value, holder);
      }
    };
  }

  private static void checkReferences(PsiElement psiElement, ProblemsHolder holder) {
    PsiReference[] references = psiElement.getReferences();
    PsiReference last = ArrayUtil.getLastElement(references);
    if (last != null && (!(last instanceof ResolvingHint) || ((ResolvingHint)last).canResolveTo(PsiDocCommentOwner.class))) {
      PsiElement resolved = last.resolve();
      if (resolved instanceof PsiModifierListOwner) {
        DeprecationInspectionBase.checkDeprecated((PsiModifierListOwner)resolved, psiElement, last.getRangeInElement(), false, false, true, false,
                                                  holder, false);
      }
    }
  }

  @Override
  public boolean isEnabledByDefault() {
    return true;
  }

  @Nls
  @NotNull
  @Override
  public String getGroupDisplayName() {
    return JavaAnalysisBundle.message("deprecated.class.usage.group.xml");
  }

  @NotNull
  @Override
  public String getShortName() {
    return "DeprecatedClassUsageInspection";
  }
}
