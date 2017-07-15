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
import org.intellij.lang.regexp.RegExpLanguageHosts;
import org.intellij.lang.regexp.RegExpTT;
import org.intellij.lang.regexp.psi.RegExpChar;
import org.intellij.lang.regexp.psi.RegExpElementVisitor;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

/**
 * @author Bas Leijdekkers
 */
public class RedundantEscapeInspection extends LocalInspectionTool {

  @Nls
  @NotNull
  @Override
  public String getDisplayName() {
    return "Redundant character escape";
  }

  @NotNull
  @Override
  public RegExpElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    return new RedundantEscapeVisitor(holder);
  }

  private static class RedundantEscapeVisitor extends RegExpElementVisitor {

    private final ProblemsHolder myHolder;

    public RedundantEscapeVisitor(ProblemsHolder holder) {
      myHolder = holder;
    }

    @Override
    public void visitRegExpChar(RegExpChar ch) {
      final String text = ch.getUnescapedText();
      if (!text.startsWith("\\") || !RegExpLanguageHosts.getInstance().isRedundantEscape(ch, text)) {
        return;
      }
      final ASTNode astNode = ch.getNode().getFirstChildNode();
      if (astNode == null || astNode.getElementType() != RegExpTT.REDUNDANT_ESCAPE) {
        return;
      }
      myHolder.registerProblem(ch, "Redundant character escape <code>" + ch.getText() + "</code> in RegExp",
                               new RemoveRedundantEscapeFix());
    }
  }

  private static class RemoveRedundantEscapeFix implements LocalQuickFix {

    @Nls
    @NotNull
    @Override
    public String getFamilyName() {
      return "Remove redundant escape";
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      final PsiElement element = descriptor.getPsiElement();
      if (!(element instanceof RegExpChar)) {
        return;
      }
      final RegExpChar aChar = (RegExpChar)element;
      final ASTNode node = aChar.getNode().getFirstChildNode();
      final ASTNode parent = node.getTreeParent();
      parent.addLeaf(RegExpTT.CHARACTER, replacement(aChar), node);
      parent.removeChild(node);
    }

    @NotNull
    private static String replacement(RegExpChar aChar) {
      final int codePoint = aChar.getValue();
      final String s = Character.isSupplementaryCodePoint(codePoint)
                       ? Character.toString(Character.highSurrogate(codePoint)) + Character.toString(Character.lowSurrogate(codePoint))
                       : Character.toString((char)codePoint);
      return RegExpReplacementUtil.escapeForContext(s, aChar);
    }
  }
}
