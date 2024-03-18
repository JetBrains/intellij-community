// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.duplicateThrows;

import com.intellij.codeInsight.daemon.impl.quickfix.MethodThrowsFix;
import com.intellij.codeInspection.AbstractBaseJavaLocalInspectionTool;
import com.intellij.codeInspection.CleanupLocalInspectionTool;
import com.intellij.codeInspection.InspectionsBundle;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.codeInspection.options.OptPane;
import com.intellij.java.analysis.JavaAnalysisBundle;
import com.intellij.psi.*;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.javadoc.PsiDocTag;
import com.intellij.psi.javadoc.PsiDocTagValue;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;

import static com.intellij.codeInspection.options.OptPane.checkbox;
import static com.intellij.codeInspection.options.OptPane.pane;

public final class DuplicateThrowsInspection extends AbstractBaseJavaLocalInspectionTool implements CleanupLocalInspectionTool {
  @SuppressWarnings("PublicField")
  public boolean ignoreSubclassing;

  @Override
  @NotNull
  public String getGroupDisplayName() {
    return InspectionsBundle.message("group.names.declaration.redundancy");
  }

  @Override
  @NotNull
  public String getShortName() {
    return "DuplicateThrows";
  }

  @Override
  public @NotNull OptPane getOptionsPane() {
    return pane(
      checkbox("ignoreSubclassing", JavaAnalysisBundle.message("inspection.duplicate.throws.ignore.subclassing.option")));
  }

  @Override
  @NotNull
  public PsiElementVisitor buildVisitor(@NotNull final ProblemsHolder holder, boolean isOnTheFly) {
    return new JavaElementVisitor() {

      @Override public void visitMethod(@NotNull PsiMethod method) {
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
              problem = JavaAnalysisBundle.message("inspection.duplicate.throws.problem");
            }
            else if (!ignoreSubclassing) {
              if (otherType.isAssignableFrom(type)) {
                problem = JavaAnalysisBundle.message("inspection.duplicate.throws.more.general.problem", otherType.getCanonicalText());
              }
              else if (type.isAssignableFrom(otherType)) {
                problem = JavaAnalysisBundle.message("inspection.duplicate.throws.more.general.problem", type.getCanonicalText());
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
              holder.problem(ref, problem).fix(new MethodThrowsFix.RemoveFirst(method, type, false)).register();
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
