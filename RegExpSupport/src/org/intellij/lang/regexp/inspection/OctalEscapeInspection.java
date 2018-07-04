// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.lang.regexp.inspection;

import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import org.intellij.lang.regexp.psi.RegExpChar;
import org.intellij.lang.regexp.psi.RegExpElementVisitor;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

/**
 * @author Bas Leijdekkers
 */
public class OctalEscapeInspection extends LocalInspectionTool {

  @Nls
  @NotNull
  @Override
  public String getDisplayName() {
    return "Octal escape";
  }

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    return new OctalEscapeVisitor(holder);
  }

  private static class OctalEscapeVisitor extends RegExpElementVisitor {

    private final ProblemsHolder myHolder;

    public OctalEscapeVisitor(ProblemsHolder holder) {
      myHolder = holder;
    }

    @Override
    public void visitRegExpChar(RegExpChar ch) {
      if (ch.getType() != RegExpChar.Type.OCT) {
        return;
      }
      myHolder.registerProblem(ch, "Octal escape <code>#ref</code> in RegExp", new ReplaceWithHexEscapeFix(buildReplacementText(ch)));
    }
  }

  static String buildReplacementText(RegExpChar aChar) {
    final int value = aChar.getValue();
    final String hex = Integer.toHexString(value);
    return (hex.length() == 1 ? "\\x0" : "\\x") + hex;
  }

  private static class ReplaceWithHexEscapeFix implements LocalQuickFix {
    private final String myHex;

    public ReplaceWithHexEscapeFix(String hex) {
      myHex = hex;
    }

    @Nls
    @NotNull
    @Override
    public String getName() {
      return "Replace with '" + myHex + '\'';
    }

    @Nls
    @NotNull
    @Override
    public String getFamilyName() {
      return "Replace with hexadecimal escape";
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      final PsiElement element = descriptor.getPsiElement();
      if (!(element instanceof RegExpChar)) {
        return;
      }
      RegExpReplacementUtil.replaceInContext(element, buildReplacementText((RegExpChar)element));
    }
  }
}