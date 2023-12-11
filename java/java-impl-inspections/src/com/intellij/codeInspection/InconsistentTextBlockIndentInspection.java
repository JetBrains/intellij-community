// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection;

import com.intellij.application.options.CodeStyle;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightingFeature;
import com.intellij.codeInspection.util.IntentionFamilyName;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.java.JavaBundle;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.PsiUpdateModCommandQuickFix;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.JavaElementVisitor;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiLiteralExpression;
import com.intellij.psi.util.PsiLiteralUtil;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.util.SmartList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;

import static com.intellij.util.ObjectUtils.tryCast;

public final class InconsistentTextBlockIndentInspection extends AbstractBaseJavaLocalInspectionTool {
  @Override
  public @NotNull PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    if (!HighlightingFeature.TEXT_BLOCKS.isAvailable(holder.getFile())) return PsiElementVisitor.EMPTY_VISITOR;
    return new JavaElementVisitor() {
      @Override
      public void visitLiteralExpression(@NotNull PsiLiteralExpression expression) {
        String[] lines = PsiLiteralUtil.getTextBlockLines(expression);
        if (lines == null) return;
        int tabSize = CodeStyle.getSettings(expression.getProject()).getTabSize(JavaFileType.INSTANCE);
        if (tabSize == 1) return;
        int start = expression.getText().indexOf('\n');
        if (start == -1) return;
        start++;
        MixedIndentModel indentModel = MixedIndentModel.create(lines);
        if (indentModel == null) return;
        int indexToReport = indentModel.myInconsistencyIdx;
        List<LocalQuickFix> fixes = new SmartList<>(new MakeIndentConsistentFix(IndentType.SPACES),
                                                    new MakeIndentConsistentFix(IndentType.TABS),
                                                    new MakeIndentConsistentFix(tabSize, IndentType.SPACES));
        if (indentModel.canReplaceWithTabs(tabSize)) {
          fixes.add(new MakeIndentConsistentFix(tabSize, IndentType.TABS));
        }
        holder.registerProblem(expression,
                               new TextRange(start + indexToReport, start + indexToReport + 1),
                               JavaBundle.message("inspection.inconsistent.text.block.indent.message"),
                               fixes.toArray(LocalQuickFix.EMPTY_ARRAY));
      }
    };
  }

  private enum IndentType {
    SPACES,
    TABS
  }

  private static class MakeIndentConsistentFix extends PsiUpdateModCommandQuickFix {

    private final int myTabSize;
    private final @NotNull IndentType myDesiredIndentType;

    private MakeIndentConsistentFix(int tabSize, @NotNull IndentType indentType) {
      myTabSize = tabSize;
      myDesiredIndentType = indentType;
    }

    private MakeIndentConsistentFix(@NotNull IndentType indentType) {
      this(1, indentType);
    }

    @Override
    public @IntentionFamilyName @NotNull String getFamilyName() {
      String message;
      if (myDesiredIndentType == IndentType.TABS) {
        message = myTabSize == 1 ?
                  JavaBundle.message("inspection.inconsistent.text.block.indent.spaces.to.tabs.one.to.one.fix") :
                  JavaBundle.message("inspection.inconsistent.text.block.indent.spaces.to.tabs.many.to.one.fix", myTabSize);
      }
      else {
        message = myTabSize == 1 ?
                  JavaBundle.message("inspection.inconsistent.text.block.indent.tabs.to.spaces.one.to.one.fix") :
                  JavaBundle.message("inspection.inconsistent.text.block.indent.tabs.to.spaces.one.to.many.fix", myTabSize);
      }
      return message;
    }

    @Override
    protected void applyFix(@NotNull Project project, @NotNull PsiElement element, @NotNull ModPsiUpdater updater) {
      PsiLiteralExpression literalExpression = tryCast(element, PsiLiteralExpression.class);
      if (literalExpression == null || !literalExpression.isTextBlock()) return;
      if (!CommonRefactoringUtil.checkReadOnlyStatus(project, literalExpression)) return;
      String[] lines = PsiLiteralUtil.getTextBlockLines(literalExpression);
      if (lines == null) return;
      MixedIndentModel indentModel = MixedIndentModel.create(lines);
      if (indentModel == null) return;
      String newTextBlock = indentModel.indentWith(myDesiredIndentType, myTabSize);
      if (newTextBlock == null) return;
      TrailingWhitespacesInTextBlockInspection.replaceTextBlock(literalExpression, "\"\"\"\n" + newTextBlock + "\"\"\"");
    }
  }

  private static class MixedIndentModel {

    private final String[] myLines;
    private final int[] mySpaces;
    private final int[] myTabs;
    private final int myInconsistencyIdx;

    private MixedIndentModel(String[] lines, int[] spaces, int[] tabs, int inconsistencyIdx) {
      myLines = lines;
      mySpaces = spaces;
      myTabs = tabs;
      myInconsistencyIdx = inconsistencyIdx;
    }

    private boolean canReplaceWithTabs(int tabSize) {
      return Arrays.stream(mySpaces).allMatch(nSpaces -> nSpaces == -1 || nSpaces % tabSize == 0);
    }

    private @Nullable String indentWith(@NotNull IndentType indentType, int tabSize) {
      StringBuilder indented = new StringBuilder();
      for (int i = 0; i < myLines.length; i++) {
        if (i != 0) indented.append('\n');
        String line = myLines[i];
        int nSpaces = mySpaces[i];
        if (nSpaces == -1) {
          indented.append(line);
          continue;
        }
        int nTabs = myTabs[i];
        String indent = createIndent(nSpaces, nTabs, indentType, tabSize);
        if (indent == null) return null;
        indented.append(indent);
        indented.append(line, nSpaces + nTabs, line.length());
      }
      return indented.toString();
    }

    private static @Nullable String createIndent(int nSpaces, int nTabs, @NotNull IndentType indentType, int tabSize) {
      if (indentType == IndentType.SPACES) return " ".repeat(nSpaces + nTabs * tabSize);
      if (nSpaces % tabSize != 0) return null;
      return "\t".repeat(nTabs + nSpaces / tabSize);
    }

    private static @Nullable MixedIndentModel create(String[] lines) {
      int indent = PsiLiteralUtil.getTextBlockIndent(lines, true, false);
      if (indent <= 0) return null;
      int[] spaces = new int[lines.length];
      int[] tabs = new int[lines.length];
      Character expectedIndentChar = null;
      int inconsistencyIdx = -1;
      int pos = 0;
      for (int i = 0; i < lines.length; i++) {
        if (i != 0) pos++;
        String line = lines[i];
        boolean isContentPart = !line.isBlank() || i == lines.length - 1;
        if (!isContentPart) {
          spaces[i] = -1;
          tabs[i] = -1;
          pos += line.length();
          continue;
        }
        if (expectedIndentChar == null) expectedIndentChar = line.charAt(0);
        for (int j = 0; j < line.length(); j++) {
          char c = line.charAt(j);
          if (c != ' ' && c != '\t') break;
          if (j < indent) inconsistencyIdx = getInconsistencyIndex(inconsistencyIdx, c, pos, j, expectedIndentChar);
          if (c == ' ') {
            spaces[i]++;
          }
          else {
            tabs[i]++;
          }
        }
        pos += line.length();
      }
      return inconsistencyIdx == -1 ? null : new MixedIndentModel(lines, spaces, tabs, inconsistencyIdx);
    }

    private static int getInconsistencyIndex(int inconsistencyIdx, char c, int pos, int idx, @NotNull Character expectedIndentChar) {
      if (inconsistencyIdx != -1) return inconsistencyIdx;
      if (expectedIndentChar == c) return -1;
      return pos + idx;
    }
  }
}
