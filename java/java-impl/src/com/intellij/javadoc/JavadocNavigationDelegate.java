/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.javadoc;

import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
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
 * 
 * @author Denis Zhdanov
 * @since 5/26/11 5:22 PM
 */
public class JavadocNavigationDelegate extends EditorNavigationDelegateAdapter {

  private static final JavadocHelper ourHelper = JavadocHelper.getInstance();
  
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
  @NotNull
  @Override
  public Result navigateToLineEnd(@NotNull Editor editor, @NotNull DataContext dataContext) {
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
    final Document document = editor.getDocument();
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

    final Pair<JavadocHelper.JavadocParameterInfo,List<JavadocHelper.JavadocParameterInfo>> pair = ourHelper.parse(psiFile, editor, offset);
    if (pair.first == null || pair.first.parameterDescriptionStartPosition != null) {
      return Result.CONTINUE;
    }

    final LogicalPosition position = ourHelper.calculateDescriptionStartPosition(psiFile, pair.second, pair.first);
    ourHelper.navigate(position, editor, psiFile.getProject());
    return Result.STOP;
  }
}
