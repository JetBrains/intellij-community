package com.intellij.codeInspection.duplicateThrows;

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.codeInspection.DeleteThrowsFix;
import com.intellij.codeInspection.InspectionsBundle;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.codeInspection.ex.BaseLocalInspectionTool;
import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;

public class DuplicateThrowsInspection extends BaseLocalInspectionTool {
  @NotNull
  public String getDisplayName() {
    return InspectionsBundle.message("inspection.duplicate.throws.display.name");
  }

  @NotNull
  public String getGroupDisplayName() {
    return GroupNames.DECLARATION_REDUNDANCY;
  }

  @NotNull
  public String getShortName() {
    return "DuplicateThrows";
  }


  @NotNull
  public PsiElementVisitor buildVisitor(@NotNull final ProblemsHolder holder, boolean isOnTheFly) {
    return new PsiElementVisitor() {
      @Override public void visitReferenceExpression(PsiReferenceExpression expression) {
      }

      @Override public void visitMethod(PsiMethod method) {
        PsiReferenceList throwsList = method.getThrowsList();
        PsiJavaCodeReferenceElement[] refs = throwsList.getReferenceElements();
        PsiClassType[] types = throwsList.getReferencedTypes();
        outer:
        for (int i = 0; i < types.length; i++) {
          PsiClassType type = types[i];
          for (int j = i+1; j < types.length; j++) {
            PsiClassType otherType = types[j];
            String problem = null;
            PsiJavaCodeReferenceElement ref = refs[i];
            if (type.equals(otherType)) {
              problem = InspectionsBundle.message("inspection.duplicate.throws.problem");
            }
            else if (otherType.isAssignableFrom(type)) {
              problem = InspectionsBundle.message("inspection.duplicate.throws.more.general.problem", otherType.getCanonicalText());
            }
            else if (type.isAssignableFrom(otherType)) {
              problem = InspectionsBundle.message("inspection.duplicate.throws.more.general.problem", type.getCanonicalText());
              ref = refs[j];
              type = otherType;
            }
            if (problem != null) {
              holder.registerProblem(ref, problem, ProblemHighlightType.LIKE_UNUSED_SYMBOL, new DeleteThrowsFix(method, type));
              //break outer;
            }
          }
        }
      }
    };
  }
}
