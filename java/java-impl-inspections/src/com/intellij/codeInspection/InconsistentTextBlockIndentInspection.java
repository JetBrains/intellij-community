// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection;

import com.intellij.application.options.CodeStyle;
import com.intellij.codeInspection.util.IntentionFamilyName;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.java.JavaBundle;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.PsiUpdateModCommandQuickFix;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.pom.java.JavaFeature;
import com.intellij.psi.JavaElementVisitor;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiLiteralExpression;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.util.PsiLiteralUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

public final class InconsistentTextBlockIndentInspection extends AbstractBaseJavaLocalInspectionTool {
  @Override
  public @NotNull Set<@NotNull JavaFeature> requiredFeatures() {
    return Set.of(JavaFeature.TEXT_BLOCKS);
  }

  @Override
  public @NotNull PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    return new JavaElementVisitor() {
      @Override
      public void visitLiteralExpression(@NotNull PsiLiteralExpression expression) {
        String[] lines = PsiLiteralUtil.getTextBlockLines(expression);
        if (lines == null || lines.length == 1) return;
        int indentLength = PsiLiteralUtil.getTextBlockIndent(expression);
        int indexToReport = -1;
        int offset = 0;
        String indent = null;
        for (String line : lines) {
          if (line.length() >= indentLength) {
            if (indent == null) {
              indent = line.substring(0, indentLength);
            }
            else {
              int mismatched = mismatch(indent, line.substring(0, indentLength));
              if (mismatched >= 0) {
                indexToReport = offset + mismatched;
                break;
              }
            }
          }
          offset += line.length() + 1; // length plus newline
        }
        if (indexToReport < 0) return;
        var styleSettings = CodeStyle.getSettings(expression.getProject());
        int tabSize = styleSettings.getTabSize(JavaFileType.INSTANCE);
        if (tabSize == 1) return;
        int start = expression.getText().indexOf('\n');
        if (start == -1) return;
        start++;
        boolean useTabCharacter = styleSettings.getIndentOptions(JavaFileType.INSTANCE).USE_TAB_CHARACTER;
        holder.registerProblem(expression,
                               new TextRange(start + indexToReport, start + indexToReport + 1),
                               JavaBundle.message("inspection.inconsistent.text.block.indent.message"),
                               new MakeIndentConsistentFix(useTabCharacter));
      }
    };
  }

  /**
   * Finds and returns the index of the first char mismatch between two Strings, otherwise return -1 if no mismatch is found.
   * The index will be in the range of 0 (inclusive) up to the length (inclusive) of the smaller String
   *
   * @param a the first String to be tested for a mismatch
   * @param b the second String to be tested for a mismatch
   * @return the index of the first char mismatch between the two String, otherwise {@code -1}.
   */
  private static int mismatch(@NotNull String a, @NotNull String b) {
    int length = Math.min(a.length(), b.length());
    for (int i = 0; i < length; i++) {
      if (a.charAt(i) != b.charAt(i)) return i;
    }
    return -1;
  }

  private static class MakeIndentConsistentFix extends PsiUpdateModCommandQuickFix {

    private final boolean myUseTabs;

    private MakeIndentConsistentFix(boolean useTabs) {
      myUseTabs = useTabs;
    }

    @Override
    public @IntentionFamilyName @NotNull String getFamilyName() {
      return myUseTabs
             ? JavaBundle.message("inspection.inconsistent.text.block.indent.tabs")
             : JavaBundle.message("inspection.inconsistent.text.block.indent.spaces");
    }

    @Override
    protected void applyFix(@NotNull Project project, @NotNull PsiElement element, @NotNull ModPsiUpdater updater) {
      CodeStyleManager.getInstance(project).reformat(element);
    }
  }
}
