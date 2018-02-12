// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.duplicateThrows;

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.codeInsight.daemon.impl.quickfix.MethodThrowsFix;
import com.intellij.codeInspection.*;
import com.intellij.codeInspection.ui.SingleCheckboxOptionsPanel;
import com.intellij.psi.*;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.javadoc.PsiDocTag;
import com.intellij.psi.javadoc.PsiDocTagValue;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class DuplicateThrowsInspection extends AbstractBaseJavaLocalInspectionTool implements CleanupLocalInspectionTool {
  @SuppressWarnings("PublicField")
  public boolean ignoreSubclassing;

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionsBundle.message("inspection.duplicate.throws.display.name");
  }

  @Override
  @NotNull
  public String getGroupDisplayName() {
    return GroupNames.DECLARATION_REDUNDANCY;
  }

  @Override
  @NotNull
  public String getShortName() {
    return "DuplicateThrows";
  }

  @Nullable
  @Override
  public JComponent createOptionsPanel() {
    return new SingleCheckboxOptionsPanel(
      InspectionsBundle.message("inspection.duplicate.throws.ignore.subclassing.option"), this, "ignoreSubclassing");
  }

  @Override
  @NotNull
  public PsiElementVisitor buildVisitor(@NotNull final ProblemsHolder holder, boolean isOnTheFly) {
    return new JavaElementVisitor() {

      @Override public void visitMethod(PsiMethod method) {
        PsiReferenceList throwsList = method.getThrowsList();
        PsiJavaCodeReferenceElement[] refs = throwsList.getReferenceElements();
        PsiClassType[] types = throwsList.getReferencedTypes();
        for (int i = 0; i < types.length; i++) {
          for (int j = i+1; j < types.length; j++) {
            PsiClassType type = types[i];
            PsiClassType otherType = types[j];
            String problem = null;
            PsiJavaCodeReferenceElement ref = refs[i];
            if (type.equals(otherType)) {
              problem = InspectionsBundle.message("inspection.duplicate.throws.problem");
            }
            else if (!ignoreSubclassing) {
              if (otherType.isAssignableFrom(type)) {
                problem = InspectionsBundle.message("inspection.duplicate.throws.more.general.problem", otherType.getCanonicalText());
              }
              else if (type.isAssignableFrom(otherType)) {
                problem = InspectionsBundle.message("inspection.duplicate.throws.more.general.problem", type.getCanonicalText());
                ref = refs[j];
                type = otherType;
              }
              if (problem != null) {
                PsiDocComment comment = method.getDocComment();
                if (comment != null) {
                  PsiDocTag[] docTags = comment.findTagsByName("throws");
                  if (docTags.length >= 2 && refersTo(docTags, type) && refersTo(docTags, otherType)) {
                    // Both exceptions are present in JavaDoc: ignore
                    return;
                  }
                }
              }
            }
            if (problem != null) {
              holder.registerProblem(ref, problem, ProblemHighlightType.LIKE_UNUSED_SYMBOL, new MethodThrowsFix(method, type, false, false));
            }
          }
        }
      }
    };
  }

  private static boolean refersTo(PsiDocTag[] tags, PsiClassType exceptionType) {
    for (PsiDocTag tag : tags) {
      PsiDocTagValue element = tag.getValueElement();
      if (element == null) continue;
      PsiJavaCodeReferenceElement ref = PsiTreeUtil.findChildOfType(element, PsiJavaCodeReferenceElement.class);
      if (ref != null && ref.resolve() == exceptionType.resolve()) return true;
    }
    return false;
  }

  @Override
  public boolean isEnabledByDefault() {
    return true;
  }
}
