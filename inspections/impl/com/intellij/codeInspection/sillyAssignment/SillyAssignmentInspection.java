/*
 * Copyright 2000-2005 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.codeInspection.sillyAssignment;

import com.intellij.codeInsight.daemon.JavaErrorMessages;
import com.intellij.codeInspection.*;
import com.intellij.openapi.util.Comparing;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * User: anna
 * Date: 15-Nov-2005
 */
public class SillyAssignmentInspection extends LocalInspectionTool {
  public String getGroupDisplayName() {
    return "";
  }

  public String getDisplayName() {
    return JavaErrorMessages.message("assignment.to.itself");
  }

  @NonNls
  public String getShortName() {
    return "SillyAssignment";
  }

  public boolean isEnabledByDefault() {
    return true;
  }

  @Nullable
  public ProblemDescriptor[] checkFile(PsiFile file, final InspectionManager manager, boolean isOnTheFly) {
    final Set<ProblemDescriptor> problems = new HashSet<ProblemDescriptor>();
    file.accept(new PsiRecursiveElementVisitor() {
      public void visitElement(PsiElement element) {
        super.visitElement(element);
      }

      public void visitAssignmentExpression(PsiAssignmentExpression expression) {
        final ProblemDescriptor[] problemDescriptors = checkSillyAssignment(expression, manager);
        if (problemDescriptors != null){
          problems.addAll(Arrays.asList(problemDescriptors));
        }
      }

      public void visitReferenceExpression(PsiReferenceExpression expression) {
        visitElement(expression);
      }
    });
    return problems.isEmpty() ? null : problems.toArray(new ProblemDescriptor[problems.size()]);
  }


  @Nullable
  private static ProblemDescriptor[] checkSillyAssignment(PsiAssignmentExpression assignment, InspectionManager inspectionManager) {
    if (assignment.getOperationSign().getTokenType() != JavaTokenType.EQ) return null;
    PsiExpression lExpression = assignment.getLExpression();
    PsiExpression rExpression = assignment.getRExpression();
    if (rExpression == null) return null;
    lExpression = PsiUtil.deparenthesizeExpression(lExpression);
    rExpression = PsiUtil.deparenthesizeExpression(rExpression);
    if (!(lExpression instanceof PsiReferenceExpression) || !(rExpression instanceof PsiReferenceExpression)) return null;
    PsiReferenceExpression lRef = (PsiReferenceExpression)lExpression;
    PsiReferenceExpression rRef = (PsiReferenceExpression)rExpression;
    PsiManager manager = assignment.getManager();
    if (!sameInstanceReferences(lRef, rRef, manager)) return null;
    return new ProblemDescriptor[]{inspectionManager.createProblemDescriptor(assignment, JavaErrorMessages.message("assignment.to.itself"), (LocalQuickFix [])null, ProblemHighlightType.LIKE_UNUSED_SYMBOL)};
  }

  /**
   * @return true if both expressions resolve to the same variable/class or field in the same instance of the class
   */
  private static boolean sameInstanceReferences(PsiReferenceExpression lRef, PsiReferenceExpression rRef, PsiManager manager) {
    PsiElement lResolved = lRef.resolve();
    PsiElement rResolved = rRef.resolve();
    if (!manager.areElementsEquivalent(lResolved, rResolved)) return false;

    PsiExpression lQualifier = lRef.getQualifierExpression();
    PsiExpression rQualifier = rRef.getQualifierExpression();
    if (lQualifier instanceof PsiReferenceExpression && rQualifier instanceof PsiReferenceExpression) {
      return sameInstanceReferences((PsiReferenceExpression)lQualifier, (PsiReferenceExpression)rQualifier, manager);
    }
    if (Comparing.equal(lQualifier, rQualifier)) return true;
    boolean lThis = lQualifier == null || lQualifier instanceof PsiThisExpression;
    boolean rThis = rQualifier == null || rQualifier instanceof PsiThisExpression;
    return lThis && rThis;
  }

}
