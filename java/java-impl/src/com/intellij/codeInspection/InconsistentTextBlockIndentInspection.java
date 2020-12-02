// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection;

import com.google.common.base.Strings;
import com.intellij.application.options.CodeStyle;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightingFeature;
import com.intellij.codeInspection.util.IntentionFamilyName;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.java.JavaBundle;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.util.PsiLiteralUtil;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.util.SmartList;
import com.intellij.util.text.MergingCharSequence;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Objects;

import static com.intellij.util.ObjectUtils.tryCast;

public class InconsistentTextBlockIndentInspection extends AbstractBaseJavaLocalInspectionTool {
  @Override
  public @NotNull PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    if (!HighlightingFeature.TEXT_BLOCKS.isAvailable(holder.getFile())) return PsiElementVisitor.EMPTY_VISITOR;
    return new JavaElementVisitor() {
      @Override
      public void visitLiteralExpression(PsiLiteralExpression expression) {
        String[] lines = PsiLiteralUtil.getTextBlockLines(expression);
        if (lines == null) return;
        MixedIndentModel indentModel = MixedIndentModel.create(lines);
        if (indentModel == null) return;
        int tabSize = CodeStyle.getSettings(expression.getProject()).getTabSize(JavaFileType.INSTANCE);
        int desiredIndent = indentModel.findDesiredIndent(tabSize);
        int indexToReport = indentModel.findFirstInconsistentCharIdx(desiredIndent, tabSize);
        if (indexToReport == -1) return;
        List<LocalQuickFix> fixes = new SmartList<>(new MakeIndentConsistentFix(IndentType.SPACES),
                                                    new MakeIndentConsistentFix(IndentType.TABS));
        indentModel.findAvailableIndentTypes(desiredIndent, tabSize)
          .forEach(type -> fixes.add(new MakeIndentConsistentFix(tabSize, type)));
        int start = expression.getText().indexOf('\n');
        if (start == -1) return;
        start++;
        holder.registerProblem(expression,
                               new TextRange(start + indexToReport, start + indexToReport + 1),
                               JavaBundle.message("inspection.inconsistent.text.block.indent.message"),
                               fixes.toArray(LocalQuickFix.EMPTY_ARRAY));
      }
    };
  }

  private enum IndentType {
    SPACES,
    TABS;

    private static @Nullable IndentType of(char c) {
      if (c == ' ') return SPACES;
      if (c == '\t') return TABS;
      return null;
    }
  }

  private static class MakeIndentConsistentFix implements LocalQuickFix {

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
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      PsiLiteralExpression literalExpression = tryCast(descriptor.getPsiElement(), PsiLiteralExpression.class);
      if (literalExpression == null || !literalExpression.isTextBlock()) return;
      if (!CommonRefactoringUtil.checkReadOnlyStatus(project, literalExpression)) return;
      String[] lines = PsiLiteralUtil.getTextBlockLines(literalExpression);
      if (lines == null) return;
      MixedIndentModel indentModel = MixedIndentModel.create(lines);
      if (indentModel == null) return;
      int desiredIndent = indentModel.findDesiredIndent(myTabSize);
      if (desiredIndent == -1) return;
      StringBuilder newTextBlock = new StringBuilder();
      newTextBlock.append("\"\"\"\n");
      String indentText = myDesiredIndentType == IndentType.SPACES ?
                          Strings.repeat(" ", desiredIndent) : Strings.repeat("\t", desiredIndent / myTabSize);
      for (int i = 0; i < lines.length; i++) {
        String line = lines[i];
        if (i != 0) newTextBlock.append('\n');
        if (!isContentPart(lines, i, line)) {
          newTextBlock.append(line);
          continue;
        }
        int lineIndent = MixedIndentModel.findLineIndent(line, desiredIndent, myTabSize, myDesiredIndentType);
        if (lineIndent == -1) return;
        MergingCharSequence newLine = StringUtil.replaceSubSequence(line, 0, lineIndent, indentText);
        newTextBlock.append(newLine);
      }
      newTextBlock.append("\"\"\"");

      PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(project);
      PsiExpression replacement = elementFactory.createExpressionFromText(newTextBlock.toString(), literalExpression);
      CodeStyleManager manager = CodeStyleManager.getInstance(project);
      manager.performActionWithFormatterDisabled((Runnable)() -> WriteAction.run(() -> literalExpression.replace(replacement)));
    }

    @Override
    public boolean startInWriteAction() {
      return false;
    }
  }

  private static class MixedIndentModel {
    private final String @NotNull [] myLines;

    private MixedIndentModel(String @NotNull [] lines) {
      myLines = lines;
    }

    private int findDesiredIndent(int tabSize) {
      int desiredIndent = Integer.MAX_VALUE;
      for (int i = 0; i < myLines.length; i++) {
        String line = myLines[i];
        if (!isContentPart(myLines, i, line)) continue;
        int lineIndent = 0;
        for (int j = 0; j < line.length(); j++) {
          char c = line.charAt(j);
          if (c == ' ') lineIndent++;
          else if (c == '\t') lineIndent += tabSize;
          else break;
        }
        if (lineIndent < desiredIndent) desiredIndent = lineIndent;
      }
      return desiredIndent;
    }

    private int findFirstInconsistentCharIdx(int desiredIndent, int tabSize) {
      int pos = 0;
      IndentType indentType = null;
      for (int i = 0; i < myLines.length; i++) {
        if (i != 0) pos++;
        String line = myLines[i];
        if (!isContentPart(myLines, i, line)) {
          if (!line.isEmpty()) pos += line.length();
          continue;
        }
        int indentToSee = desiredIndent;
        for (int j = 0; j < line.length(); j++) {
          if (indentToSee <= 0) break;
          char c = line.charAt(j);
          if (c == ' ') indentToSee--;
          else if (c == '\t') indentToSee -= tabSize;
          else return -1;
          IndentType curIndentType = Objects.requireNonNull(IndentType.of(c));
          if (indentType == null) {
            indentType = curIndentType;
          }
          else if (indentType != curIndentType) {
            return pos + j;
          }
        }
        pos += line.length();
      }
      return -1;
    }

    private @NotNull List<IndentType> findAvailableIndentTypes(int desiredIndent, int tabSize) {
      List<IndentType> indentTypes = new SmartList<>(IndentType.SPACES, IndentType.TABS);
      for (int i = 0; i < myLines.length; i++) {
        String line = myLines[i];
        if (!isContentPart(myLines, i, line)) continue;
        int indentToSee = desiredIndent;
        int nSpaces = 0;
        for (int j = 0; j < line.length(); j++) {
          char c = line.charAt(j);
          if (c == ' ') {
            indentToSee--;
            nSpaces++;
          }
          else if (c == '\t') {
            if (nSpaces % tabSize != 0) {
              indentTypes.remove(IndentType.TABS);
            }
            indentToSee -= tabSize;
            nSpaces = 0;
          }
          if (indentToSee <= 0) break;
        }
        if (nSpaces % tabSize != 0) indentTypes.remove(IndentType.TABS);
        if (indentToSee < 0) indentTypes.remove(IndentType.SPACES);
      }
      return indentTypes;
    }

    private static int findLineIndent(@NotNull String line, int desiredIndent, int tabSize, @NotNull IndentType desiredIndentType) {
      int i;
      int nSpaces = 0;
      for (i = 0; i < line.length(); i++) {
        char c = line.charAt(i);
        if (c == ' ') {
          nSpaces++;
          desiredIndent--;
        }
        else if (c == '\t') {
          if (desiredIndentType == IndentType.TABS && nSpaces % tabSize != 0) {
            return -1;
          }
          nSpaces = 0;
          desiredIndent -= tabSize;
        }
        if (desiredIndent <= 0) break;
      }
      if (desiredIndentType == IndentType.TABS && nSpaces % tabSize != 0) return -1;
      return desiredIndent == 0 ? i + 1 : -1;
    }

    private static @Nullable MixedIndentModel create(String @NotNull [] lines) {
      int indent = PsiLiteralUtil.getTextBlockIndent(lines, true, false);
      if (indent <= 0) return null;
      IndentType indentType = null;
      for (int i = 0; i < lines.length; i++) {
        String line = lines[i];
        if (!isContentPart(lines, i, line)) continue;
        for (int j = 0; j < indent; j++) {
          IndentType curIndentType = IndentType.of(line.charAt(j));
          if (indentType == null) {
            indentType = curIndentType;
            continue;
          }
          if (curIndentType != indentType) {
            return new MixedIndentModel(lines);
          }
        }
      }
      return null;
    }
  }

  private static boolean isContentPart(String @NotNull [] lines, int i, String line) {
    return !line.isBlank() || i == lines.length - 1;
  }
}
