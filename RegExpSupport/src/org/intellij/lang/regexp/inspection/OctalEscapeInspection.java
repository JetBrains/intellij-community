// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.lang.regexp.inspection;

import com.intellij.codeInspection.*;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.PsiUpdateModCommandQuickFix;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import org.intellij.lang.regexp.RegExpBundle;
import org.intellij.lang.regexp.psi.RegExpChar;
import org.intellij.lang.regexp.psi.RegExpElementVisitor;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

/**
 * @author Bas Leijdekkers
 */
public class OctalEscapeInspection extends LocalInspectionTool {

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    return new OctalEscapeVisitor(holder);
  }

  private static class OctalEscapeVisitor extends RegExpElementVisitor {

    private final ProblemsHolder myHolder;

    OctalEscapeVisitor(ProblemsHolder holder) {
      myHolder = holder;
    }

    @Override
    public void visitRegExpChar(RegExpChar ch) {
      if (ch.getType() != RegExpChar.Type.OCT) {
        return;
      }
      myHolder.registerProblem(ch, RegExpBundle.message("inspection.warning.octal.escape.code.ref.code.in.regexp"),
                               new ReplaceWithHexEscapeFix(buildReplacementText(ch)));
    }
  }

  static String buildReplacementText(RegExpChar aChar) {
    final int value = aChar.getValue();
    final String hex = Integer.toHexString(value);
    return (hex.length() == 1 ? "\\x0" : "\\x") + hex;
  }

  private static class ReplaceWithHexEscapeFix extends PsiUpdateModCommandQuickFix {
    private final String myHex;

    ReplaceWithHexEscapeFix(String hex) {
      myHex = hex;
    }

    @Nls
    @NotNull
    @Override
    public String getName() {
      return CommonQuickFixBundle.message("fix.replace.with.x", myHex);
    }

    @Nls
    @NotNull
    @Override
    public String getFamilyName() {
      return RegExpBundle.message("inspection.quick.fix.replace.with.hexadecimal.escape");
    }

    @Override
    protected void applyFix(@NotNull Project project, @NotNull PsiElement element, @NotNull ModPsiUpdater updater) {
      if (!(element instanceof RegExpChar regExpChar)) {
        return;
      }
      RegExpReplacementUtil.replaceInContext(element, buildReplacementText(regExpChar));
    }
  }
}