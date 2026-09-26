// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.lang.regexp.inspection;

import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.codeInspection.options.OptPane;
import com.intellij.lang.ASTNode;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.PsiUpdateModCommandQuickFix;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import org.intellij.lang.regexp.RegExpBundle;
import org.intellij.lang.regexp.RegExpLanguageHosts;
import org.intellij.lang.regexp.RegExpTT;
import org.intellij.lang.regexp.psi.RegExpChar;
import org.intellij.lang.regexp.psi.RegExpClass;
import org.intellij.lang.regexp.psi.RegExpElementVisitor;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import static com.intellij.codeInspection.options.OptPane.checkbox;
import static com.intellij.codeInspection.options.OptPane.pane;

/**
 * @author Bas Leijdekkers
 */
public class RegExpRedundantEscapeInspection extends LocalInspectionTool {

  public boolean ignoreEscapedMetaCharacters = false;
  public boolean ignoreEscapedForwardSlashes = false;

  @Override
  public @NotNull OptPane getOptionsPane() {
    return pane(
      checkbox("ignoreEscapedMetaCharacters", RegExpBundle.message("inspection.option.ignore.escaped.closing.brackets")),
      checkbox("ignoreEscapedForwardSlashes", RegExpBundle.message("inspection.option.ignore.escaped.forward.slashes"))
    );
  }

  @Override
  public @NotNull RegExpElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    return new RedundantEscapeVisitor(holder);
  }

  private class RedundantEscapeVisitor extends RegExpElementVisitor {

    private final ProblemsHolder myHolder;

    RedundantEscapeVisitor(ProblemsHolder holder) {
      myHolder = holder;
    }

    @Override
    public void visitRegExpChar(RegExpChar ch) {
      final String text = ch.getUnescapedText();
      if (!text.startsWith("\\") || !RegExpLanguageHosts.getInstance().isRedundantEscape(ch, text)) {
        return;
      }
      if (text.equals("\\{") && !(ch.getParent() instanceof RegExpClass)) {
        return;
      }
      if (ignoreEscapedMetaCharacters && (text.equals("\\}") || text.equals("\\]")) && !(ch.getParent() instanceof RegExpClass)) {
        return;
      }
      if (ignoreEscapedForwardSlashes && text.equals("\\/")) {
        return;
      }
      final ASTNode astNode = ch.getNode().getFirstChildNode();
      if (astNode == null || astNode.getElementType() != RegExpTT.REDUNDANT_ESCAPE) {
        return;
      }
      myHolder.registerProblem(ch, RegExpBundle.message("inspection.warning.redundant.character.escape.0.in.regexp", ch.getText()),
                               new RemoveRedundantEscapeFix());
    }
  }

  private static class RemoveRedundantEscapeFix extends PsiUpdateModCommandQuickFix {

    @Override
    public @Nls @NotNull String getFamilyName() {
      return RegExpBundle.message("inspection.quick.fix.remove.redundant.escape");
    }

    @Override
    protected void applyFix(@NotNull Project project, @NotNull PsiElement element, @NotNull ModPsiUpdater updater) {
      if (!(element instanceof RegExpChar regExpChar)) {
        return;
      }
      RegExpReplacementUtil.replaceInContext(element, replacement(regExpChar));
    }

    private static @NotNull String replacement(RegExpChar aChar) {
      final int codePoint = aChar.getValue();
      return Character.isSupplementaryCodePoint(codePoint)
             ? Character.toString(Character.highSurrogate(codePoint)) + Character.lowSurrogate(codePoint)
             : Character.toString((char)codePoint);
    }
  }
}
