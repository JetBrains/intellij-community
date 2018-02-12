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
import com.intellij.lang.ASTNode;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import org.intellij.lang.regexp.RegExpTT;
import org.intellij.lang.regexp.psi.RegExpBranch;
import org.intellij.lang.regexp.psi.RegExpChar;
import org.intellij.lang.regexp.psi.RegExpElementVisitor;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

/**
 * @author Bas Leijdekkers
 */
public class EscapedMetaCharacterInspection extends LocalInspectionTool {

  @Nls
  @NotNull
  @Override
  public String getDisplayName() {
    return "Escaped meta character";
  }

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    return new EscapedMetaCharacterVisitor(holder);
  }

  private static class EscapedMetaCharacterVisitor extends RegExpElementVisitor {

    private final ProblemsHolder myHolder;

    public EscapedMetaCharacterVisitor(ProblemsHolder holder) {
      myHolder = holder;
    }

    @Override
    public void visitRegExpChar(RegExpChar ch) {
      if (ch.getType() != RegExpChar.Type.CHAR) {
        return;
      }
      final String text = ch.getUnescapedText();
      if (text.length() != 2 || text.charAt(0) != '\\') {
        return;
      }
      final char c = text.charAt(1);
      if ("{}().*+?|$".indexOf(c) < 0) {
        return;
      }
      final ASTNode node = ch.getNode().getFirstChildNode();
      if (node != null && node.getElementType() == RegExpTT.REDUNDANT_ESCAPE) {
        return;
      }
      myHolder.registerProblem(ch, "Escaped meta character <code>" + c + "</code>", new EscapedMetaCharacterFix(c));
    }
  }

  private static class EscapedMetaCharacterFix implements LocalQuickFix {

    private final char myC;

    EscapedMetaCharacterFix(char c) {
      myC = c;
    }

    @Nls
    @NotNull
    @Override
    public String getName() {
      return "Replace with '[" + myC + "]'";
    }

    @Nls
    @NotNull
    @Override
    public String getFamilyName() {
      return "Replace with character inside class";
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      final PsiElement element = descriptor.getPsiElement();
      if (!(element instanceof RegExpChar)) {
        return;
      }
      final RegExpBranch branch = RegExpFactory.createBranchFromText("[" + myC + ']', element);
      element.replace(branch.getFirstChild());
    }
  }
}
