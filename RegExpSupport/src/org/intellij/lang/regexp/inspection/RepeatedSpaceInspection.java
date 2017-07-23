/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package org.intellij.lang.regexp.inspection;

import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiWhiteSpace;
import org.intellij.lang.regexp.psi.*;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

/**
 * @author Bas Leijdekkers
 */
public class RepeatedSpaceInspection extends LocalInspectionTool {

  @Nls
  @NotNull
  @Override
  public String getDisplayName() {
    return "Consecutive spaces";
  }

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    return new RepeatedSpaceVisitor(holder);
  }

  private static class RepeatedSpaceVisitor extends RegExpElementVisitor {

    private final ProblemsHolder myHolder;
    private int myCount = 0;
    private RegExpChar myFirstChar = null;
    private boolean quoted = false;

    public RepeatedSpaceVisitor(ProblemsHolder holder) {
      myHolder = holder;
    }

    @Override
    public void visitRegExpChar(RegExpChar aChar) {
      if (!quoted && !(aChar.getParent() instanceof RegExpClass) && aChar.getType() == RegExpChar.Type.CHAR && aChar.getValue() == ' ') {
        if (myFirstChar == null) {
          myFirstChar = aChar;
        }
        myCount++;
      }
      else {
        super.visitRegExpChar(aChar);
      }
    }

    @Override
    public void visitWhiteSpace(PsiWhiteSpace space) {
      super.visitWhiteSpace(space);
      final String text = space.getText();
      if (text.equals("\\Q")) {
        quoted = true;
      }
      else if (text.equals("\\E")) {
        quoted = false;
      }
      myFirstChar = null;
      myCount = 0;
    }

    @Override
    public void visitRegExpClass(RegExpClass expClass) {
      super.visitRegExpClass(expClass);
      myFirstChar = null;
      myCount = 0;
    }

    @Override
    public void visitRegExpElement(RegExpElement element) {
      super.visitRegExpElement(element);
      if (myFirstChar != null && myCount > 1) {
        final int offset = myFirstChar.getStartOffsetInParent();
        final String message = myCount + " consecutive spaces in RegExp";
        myHolder.registerProblem(myFirstChar.getParent(), new TextRange(offset, offset + myCount), message,
                                 new RepeatedSpaceFix(myCount));
      }
      myFirstChar = null;
      myCount = 0;
    }
  }

  private static class RepeatedSpaceFix implements LocalQuickFix {
    private final int myCount;

    public RepeatedSpaceFix(int count) {
      myCount = count;
    }

    @Nls
    @NotNull
    @Override
    public String getName() {
      return "Replace with ' {" + myCount + "}'";
    }

    @Nls
    @NotNull
    @Override
    public String getFamilyName() {
      return "Replace with space and repeated quantifier";
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      final PsiElement element = descriptor.getPsiElement();
      if (!(element instanceof RegExpBranch)) {
        return;
      }
      final TextRange range = descriptor.getTextRangeInElement();
      final StringBuilder text = new StringBuilder();
      final PsiElement[] children = element.getChildren();
      boolean inserted = false;
      for (PsiElement child : children) {
        if (!range.contains(child.getStartOffsetInParent())) {
          text.append(child.getText());
        }
        else if (!inserted) {
          text.append(" {").append(range.getLength()).append('}');
          inserted = true;
        }
      }
      element.replace(RegExpFactory.createBranchFromText(text, element));
    }
  }
}
