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
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.PsiElement;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.ui.ReplacePromptDialog;
import com.intellij.util.Consumer;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author Dennis.Ushakov
 */
public class ExtractMethodHelper {
  public static void processDuplicates(@NotNull final PsiElement callElement,
                                       @NotNull final PsiElement generatedMethod,
                                       @NotNull final List<PsiElement> scope,
                                       @NotNull final SimpleDuplicatesFinder finder,
                                       @NotNull final Editor editor,
                                       @NotNull final Consumer<Pair<SimpleMatch, PsiElement>> replacer) {
    finder.setReplacement(callElement);
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      replaceDuplicates(callElement, editor, replacer, finder.findDuplicates(scope, generatedMethod));
      return;
    }
    final Project project = callElement.getProject();
    ProgressManager.getInstance().run(new Task.Backgroundable(project, "Searching for duplicates...", true) {
      public void run(@NotNull ProgressIndicator indicator) {
        if (myProject == null || myProject.isDisposed()) return;
        final List<SimpleMatch> duplicates = ApplicationManager.getApplication().runReadAction(new Computable<List<SimpleMatch>>() {
          @Override
          public List<SimpleMatch> compute() {
            return finder.findDuplicates(scope, generatedMethod);
          }
        });

        ApplicationManager.getApplication().invokeLater(new Runnable() {
          @Override
          public void run() {
            replaceDuplicates(callElement, editor, replacer, duplicates);
          }
        });
      }
    });
  }

  private static void replaceDuplicates(PsiElement callElement,
                                        Editor editor,
                                        Consumer<Pair<SimpleMatch, PsiElement>> replacer,
                                        List<SimpleMatch> duplicates) {
    if (duplicates.size() > 0) {
      final String message = RefactoringBundle
        .message("0.has.detected.1.code.fragments.in.this.file.that.can.be.replaced.with.a.call.to.extracted.method",
                 ApplicationNamesInfo.getInstance().getProductName(), duplicates.size());
      final boolean isUnittest = ApplicationManager.getApplication().isUnitTestMode();
      final Project project = callElement.getProject();
      final int exitCode = !isUnittest ? Messages.showYesNoDialog(project, message,
                                                                  RefactoringBundle.message("refactoring.extract.method.dialog.title"),
                                                                  Messages.getInformationIcon()) :
                           Messages.YES;
      if (exitCode == Messages.YES) {
        boolean replaceAll = false;
        final Map<SimpleMatch, RangeHighlighter> highlighterMap = new HashMap<SimpleMatch, RangeHighlighter>();
        for (SimpleMatch match : duplicates) {
          if (!match.getStartElement().isValid() || !match.getEndElement().isValid()) continue;
          final Pair<SimpleMatch, PsiElement> replacement = Pair.create(match, callElement);
          if (!replaceAll) {
            highlightInEditor(project, match, editor, highlighterMap);

            int promptResult = FindManager.PromptResult.ALL;
            //noinspection ConstantConditions
            if (!isUnittest) {
              ReplacePromptDialog promptDialog =
                new ReplacePromptDialog(false, RefactoringBundle.message("replace.fragment"), project);
              promptDialog.show();
              promptResult = promptDialog.getExitCode();
            }
            if (promptResult == FindManager.PromptResult.SKIP) {
              final HighlightManager highlightManager = HighlightManager.getInstance(project);
              final RangeHighlighter highlighter = highlighterMap.get(match);
              if (highlighter != null) highlightManager.removeSegmentHighlighter(editor, highlighter);
              continue;
            }
            if (promptResult == FindManager.PromptResult.CANCEL) break;

            if (promptResult == FindManager.PromptResult.OK) {
              replaceDuplicate(project, replacer, replacement);
            }
            else if (promptResult == FindManager.PromptResult.ALL) {
              replaceDuplicate(project, replacer, replacement);
              replaceAll = true;
            }
          }
          else {
            replaceDuplicate(project, replacer, replacement);
          }
        }
      }
    }
  }

  private static void replaceDuplicate(final Project project, final Consumer<Pair<SimpleMatch, PsiElement>> replacer,
                                       final Pair<SimpleMatch, PsiElement> replacement) {
    CommandProcessor.getInstance().executeCommand(project, new Runnable() {
      @Override
      public void run() {
        ApplicationManager.getApplication().runWriteAction(new Runnable() {
          @Override
          public void run() {
            replacer.consume(replacement);
          }
        });
      }
    }, "Replace duplicate", null);
  }


  private static void highlightInEditor(@NotNull final Project project, @NotNull final SimpleMatch match,
                                 @NotNull final Editor editor, Map<SimpleMatch, RangeHighlighter> highlighterMap) {
    final List<RangeHighlighter> highlighters = new ArrayList<RangeHighlighter>();
    final HighlightManager highlightManager = HighlightManager.getInstance(project);
    final EditorColorsManager colorsManager = EditorColorsManager.getInstance();
    final TextAttributes attributes = colorsManager.getGlobalScheme().getAttributes(EditorColors.SEARCH_RESULT_ATTRIBUTES);
    final int startOffset = match.getStartElement().getTextRange().getStartOffset();
    final int endOffset = match.getEndElement().getTextRange().getEndOffset();
    highlightManager.addRangeHighlight(editor, startOffset, endOffset, attributes, true, highlighters);
    highlighterMap.put(match, highlighters.get(0));
    final LogicalPosition logicalPosition = editor.offsetToLogicalPosition(startOffset);
    editor.getScrollingModel().scrollTo(logicalPosition, ScrollType.MAKE_VISIBLE);
  }
}
