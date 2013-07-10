/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.refactoring.extractMethod;

import com.intellij.codeInsight.highlighting.HighlightManager;
import com.intellij.find.FindManager;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.PsiElement;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.ui.ReplacePromptDialog;
import com.intellij.util.Consumer;
import com.intellij.util.Function;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author Dennis.Ushakov
 */
public class ExtractMethodHelper {
  public static void processDuplicates(@NotNull final PsiElement callElement,
                                        @NotNull final PsiElement generatedMethod,
                                        @NotNull final List<PsiElement> scope,
                                        @NotNull final SimpleDuplicatesFinder finder,
                                        @NotNull final Editor editor,
                                        @NotNull final Function<Pair<PsiElement, PsiElement>, List<PsiElement>> collector,
                                        @NotNull final Consumer<Pair<List<PsiElement>, PsiElement>> replacer) {
    final List<Pair<PsiElement, PsiElement>> duplicates = finder.findDuplicates(scope, generatedMethod);

    if (duplicates.size() > 0) {
      final String message = RefactoringBundle
        .message("0.has.detected.1.code.fragments.in.this.file.that.can.be.replaced.with.a.call.to.extracted.method",
                 ApplicationNamesInfo.getInstance().getProductName(), duplicates.size());
      final boolean isUnittest = ApplicationManager.getApplication().isUnitTestMode();
      final int exitCode = !isUnittest ? Messages.showYesNoDialog(callElement.getProject(), message,
                                                                  RefactoringBundle.message("refactoring.extract.method.dialog.title"),
                                                                  Messages.getInformationIcon()) :
                           DialogWrapper.OK_EXIT_CODE;
      if (exitCode == DialogWrapper.OK_EXIT_CODE) {
        boolean replaceAll = false;
        for (Pair<PsiElement, PsiElement> match : duplicates) {
          final List<PsiElement> elementsRange = collector.fun(match);
          final Pair<List<PsiElement>, PsiElement> replacement = Pair.create(elementsRange, callElement);
          if (!replaceAll) {
            highlightInEditor(callElement.getProject(), match, editor);

            int promptResult = FindManager.PromptResult.ALL;
            if (!isUnittest) {
              ReplacePromptDialog promptDialog =
                new ReplacePromptDialog(false, RefactoringBundle.message("replace.fragment"), callElement.getProject());
              promptDialog.show();
              promptResult = promptDialog.getExitCode();
            }
            if (promptResult == FindManager.PromptResult.SKIP) continue;
            if (promptResult == FindManager.PromptResult.CANCEL) break;

            if (promptResult == FindManager.PromptResult.OK) {
              replacer.consume(replacement);
            }
            else if (promptResult == FindManager.PromptResult.ALL) {
              replacer.consume(replacement);
              replaceAll = true;
            }
          }
          else {
            replacer.consume(replacement);
          }
        }
      }
    }
  }

  private static void highlightInEditor(@NotNull final Project project, @NotNull final Pair<PsiElement, PsiElement> pair,
                                        @NotNull final Editor editor) {
    final HighlightManager highlightManager = HighlightManager.getInstance(project);
    final EditorColorsManager colorsManager = EditorColorsManager.getInstance();
    final TextAttributes attributes = colorsManager.getGlobalScheme().getAttributes(EditorColors.SEARCH_RESULT_ATTRIBUTES);
    final int startOffset = pair.getFirst().getTextRange().getStartOffset();
    final int endOffset = pair.getSecond().getTextRange().getEndOffset();
    highlightManager.addRangeHighlight(editor, startOffset, endOffset, attributes, true, null);
    final LogicalPosition logicalPosition = editor.offsetToLogicalPosition(startOffset);
    editor.getScrollingModel().scrollTo(logicalPosition, ScrollType.MAKE_VISIBLE);
  }
}
