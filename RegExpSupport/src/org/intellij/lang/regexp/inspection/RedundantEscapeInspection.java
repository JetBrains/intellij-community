// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
      RegExpReplacementUtil.replaceInContext(element, replacement((RegExpChar)element));
    }

    @NotNull
    private static String replacement(RegExpChar aChar) {
      final int codePoint = aChar.getValue();
      return Character.isSupplementaryCodePoint(codePoint)
             ? Character.toString(Character.highSurrogate(codePoint)) + Character.toString(Character.lowSurrogate(codePoint))
             : Character.toString((char)codePoint);
    }
  }
}
