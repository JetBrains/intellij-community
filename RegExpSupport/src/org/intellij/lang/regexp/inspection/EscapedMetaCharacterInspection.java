// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.lang.regexp.inspection;

import com.intellij.codeInspection.CommonQuickFixBundle;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.lang.ASTNode;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.PsiUpdateModCommandQuickFix;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import org.intellij.lang.regexp.RegExpBundle;
import org.intellij.lang.regexp.RegExpTT;
import org.intellij.lang.regexp.psi.RegExpChar;
import org.intellij.lang.regexp.psi.RegExpElementVisitor;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

/**
 * @author Bas Leijdekkers
 */
public class EscapedMetaCharacterInspection extends LocalInspectionTool {

  @Override
  public @NotNull PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    return new EscapedMetaCharacterVisitor(holder);
  }

  private static class EscapedMetaCharacterVisitor extends RegExpElementVisitor {

    private final ProblemsHolder myHolder;

    EscapedMetaCharacterVisitor(ProblemsHolder holder) {
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
      myHolder.registerProblem(ch, RegExpBundle.message("inspection.warning.escaped.meta.character.0", c), new EscapedMetaCharacterFix(c));
    }
  }

  private static class EscapedMetaCharacterFix extends PsiUpdateModCommandQuickFix {

    private final char myC;

    EscapedMetaCharacterFix(char c) {
      myC = c;
    }

    @Override
    public @Nls @NotNull String getName() {
      return CommonQuickFixBundle.message("fix.replace.with.x", "[" + myC + ']');
    }

    @Override
    public @Nls @NotNull String getFamilyName() {
      return RegExpBundle.message("inspection.quick.fix.replace.with.character.inside.class");
    }

    @Override
    protected void applyFix(@NotNull Project project, @NotNull PsiElement element, @NotNull ModPsiUpdater updater) {
      if (!(element instanceof RegExpChar)) {
        return;
      }
      RegExpReplacementUtil.replaceInContext(element, "[" + myC + ']');
    }
  }
}
