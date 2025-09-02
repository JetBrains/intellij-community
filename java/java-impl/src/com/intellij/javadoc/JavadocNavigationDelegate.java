// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.javadoc;

import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.util.text.CharArrayUtil;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Holds javadoc-specific navigation logic.
 */
public final class JavadocNavigationDelegate extends EditorNavigationDelegateAdapter {
  /**
   * Improves navigation in case of incomplete javadoc parameter descriptions.
   * <p/>
   * Example:
   * <pre>
   *   /**
   *    * @param i[caret]
   *    * @param secondArgument
   *    *&#47;
   *    abstract void test(int i, int secondArgument);
   * </pre>
   * <p/>
   * We expect the caret to be placed in position of parameter description start then (code style is condifured to
   * <b>align</b> parameter descriptions):
   * <pre>
   *   /**
   *    * @param i                 [caret]
   *    * @param secondArgument
   *    *&#47;
   *    abstract void test(int i, int secondArgument);
   * </pre>
   * <p/>
   * or this one for non-aligned descriptions:
   * <pre>
   *   /**
   *    * @param i    [caret]
   *    * @param secondArgument
   *    *&#47;
   *    abstract void test(int i, int secondArgument);
   * </pre>
   * 
   * @param editor      current editor
   * @return            processing result
   */
  @Override
  public @NotNull Result navigateToLineEnd(@NotNull Editor editor, @NotNull DataContext dataContext) {
    if (!CodeInsightSettings.getInstance().SMART_END_ACTION) {
      return Result.CONTINUE;
    }

    final Project project = CommonDataKeys.PROJECT.getData(dataContext);
    if (project == null) {
      return Result.CONTINUE;
    }

    final Document document = editor.getDocument();
    PsiFile psiFile = CommonDataKeys.PSI_FILE.getData(dataContext);
    if (psiFile == null) {
      psiFile = PsiDocumentManager.getInstance(project).getPsiFile(document);
    }
    if (psiFile == null) {
      return Result.CONTINUE;
    }

    return navigateToLineEnd(editor, psiFile);
  }
  
  public static Result navigateToLineEnd(@NotNull Editor editor, @NotNull PsiFile psiFile) {
    final Document document = psiFile.getFileDocument();
    final CaretModel caretModel = editor.getCaretModel();
    final int offset = caretModel.getOffset();

    final CharSequence text = document.getCharsSequence();
    int line = caretModel.getLogicalPosition().line;
    final int endLineOffset = document.getLineEndOffset(line);
    final LogicalPosition endLineLogicalPosition = editor.offsetToLogicalPosition(endLineOffset);

    // Stop processing if there are non-white space symbols after the current caret position.
    final int lastNonWsSymbolOffset = CharArrayUtil.shiftBackward(text, endLineOffset, " \t");
    if (lastNonWsSymbolOffset > offset || caretModel.getLogicalPosition().column > endLineLogicalPosition.column) {
      return Result.CONTINUE;
    }

    final Pair<JavadocHelper.JavadocParameterInfo,List<JavadocHelper.JavadocParameterInfo>> pair = JavadocHelper.parse(psiFile, editor, offset);
    if (pair.first == null || pair.first.parameterDescriptionStartPosition != null) {
      return Result.CONTINUE;
    }

    final LogicalPosition position = JavadocHelper.calculateDescriptionStartPosition(psiFile, pair.second, pair.first);
    JavadocHelper.navigate(position, editor, psiFile.getProject());
    return Result.STOP;
  }
}
