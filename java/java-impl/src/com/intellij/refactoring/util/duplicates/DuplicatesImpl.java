/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.refactoring.util.duplicates;

import com.intellij.CommonBundle;
import com.intellij.codeInsight.folding.CodeFoldingManager;
import com.intellij.codeInsight.highlighting.HighlightManager;
import com.intellij.find.FindManager;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.FoldRegion;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.ui.ReplacePromptDialog;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * @author dsl
 */
public class DuplicatesImpl {
  private static final Logger LOG = Logger.getInstance("#com.intellij.refactoring.util.duplicates.DuplicatesImpl");

  private DuplicatesImpl() {}

  public static void invoke(@NotNull  final Project project, @NotNull Editor editor, @NotNull MatchProvider provider) {
    invoke(project, editor, provider, true);
  }

  public static void invoke(@NotNull final Project project, @NotNull Editor editor, @NotNull MatchProvider provider, boolean skipPromptWhenOne) {
    final List<Match> duplicates = provider.getDuplicates();
    int idx = 0;
    final Ref<Boolean> showAll = new Ref<>();
    final String confirmDuplicatePrompt = getConfirmationPrompt(provider, duplicates);
    for (final Match match : duplicates) {
      if (!match.getMatchStart().isValid() || !match.getMatchEnd().isValid()) continue;
      if (replaceMatch(project, provider, match, editor, ++idx, duplicates.size(), showAll, confirmDuplicatePrompt, skipPromptWhenOne)) return;
    }
  }

  public static void invoke(final Project project, final MatchProvider provider) {
    final List<Match> duplicates = provider.getDuplicates();
    int idx = 0;
    final Ref<Boolean> showAll = new Ref<>();
    final String confirmDuplicatePrompt = getConfirmationPrompt(provider, duplicates);
    for (final Match match : duplicates) {
      final PsiFile file = match.getFile();
      final VirtualFile virtualFile = file.getVirtualFile();
      if (virtualFile == null || !virtualFile.isValid()) return;
      if (!CommonRefactoringUtil.checkReadOnlyStatus(project, file)) return;
      final Editor editor = FileEditorManager.getInstance(project).openTextEditor(new OpenFileDescriptor(project, virtualFile), false);
      LOG.assertTrue(editor != null);
      if (!match.getMatchStart().isValid() || !match.getMatchEnd().isValid()) continue;
      if (replaceMatch(project, provider, match, editor, ++idx, duplicates.size(), showAll, confirmDuplicatePrompt, false)) return;
    }
  }

  @Nullable
  private static String getConfirmationPrompt(MatchProvider provider, List<Match> duplicates) {
    String confirmDuplicatePrompt = null;
    for (Match duplicate : duplicates) {
      confirmDuplicatePrompt = provider.getConfirmDuplicatePrompt(duplicate);
      if (confirmDuplicatePrompt != null) {
        break;
      }
    }
    return confirmDuplicatePrompt;
  }

  private static boolean replaceMatch(final Project project,
                                      final MatchProvider provider,
                                      final Match match,
                                      @NotNull final Editor editor,
                                      final int idx,
                                      final int size,
                                      Ref<Boolean> showAll,
                                      final String confirmDuplicatePrompt,
                                      boolean skipPromptWhenOne) {
    final ArrayList<RangeHighlighter> highlighters = previewMatch(project, match, editor);
    try {
      if (!ApplicationManager.getApplication().isUnitTestMode()) {
        if ((!skipPromptWhenOne || size > 1) && (showAll.get() == null || !showAll.get())) {
          final String prompt = provider.getConfirmDuplicatePrompt(match);
          final ReplacePromptDialog promptDialog = new ReplacePromptDialog(false, provider.getReplaceDuplicatesTitle(idx, size), project) {
            @Override
            protected String getMessage() {
              final String message = super.getMessage();
              return prompt != null ? message + " " + prompt : message;
            }
          };
          promptDialog.show();
          final boolean allChosen = promptDialog.getExitCode() == FindManager.PromptResult.ALL;
          showAll.set(allChosen);
          if (allChosen && confirmDuplicatePrompt != null && prompt == null) {
            if (Messages.showOkCancelDialog(project, "In order to replace all occurrences method signature will be changed. Proceed?", CommonBundle.getWarningTitle(), Messages.getWarningIcon()) !=
                Messages.OK) return true;
          }
          if (promptDialog.getExitCode() == FindManager.PromptResult.SKIP) return false;
          if (promptDialog.getExitCode() == FindManager.PromptResult.CANCEL) return true;
        }
      }
    }
    finally {
      HighlightManager.getInstance(project).removeSegmentHighlighter(editor, highlighters.get(0));
    }

    // call change signature when needed
    provider.prepareSignature(match);

    new WriteCommandAction(project, MethodDuplicatesHandler.REFACTORING_NAME, MethodDuplicatesHandler.REFACTORING_NAME) {
      @Override
      protected void run(@NotNull Result result) throws Throwable {
        try {
          provider.processMatch(match);
        }
        catch (IncorrectOperationException e) {
          LOG.error(e);
        }
      }
    }.execute();

    return false;
  }

  public static ArrayList<RangeHighlighter> previewMatch(Project project, Match match, Editor editor) {
    final ArrayList<RangeHighlighter> highlighters = new ArrayList<>();
    highlightMatch(project, editor, match, highlighters);
    final TextRange textRange = match.getTextRange();
    final LogicalPosition logicalPosition = editor.offsetToLogicalPosition(textRange.getStartOffset());
    expandAllRegionsCoveringRange(project, editor, textRange);
    editor.getScrollingModel().scrollTo(logicalPosition, ScrollType.MAKE_VISIBLE);
    return highlighters;
  }

  private static void expandAllRegionsCoveringRange(final Project project, Editor editor, final TextRange textRange) {
    final FoldRegion[] foldRegions = CodeFoldingManager.getInstance(project).getFoldRegionsAtOffset(editor, textRange.getStartOffset());
    boolean anyCollapsed = false;
    for (final FoldRegion foldRegion : foldRegions) {
      if (!foldRegion.isExpanded()) {
        anyCollapsed = true;
        break;
      }
    }
    if (anyCollapsed) {
      editor.getFoldingModel().runBatchFoldingOperation(() -> {
        for (final FoldRegion foldRegion : foldRegions) {
          if (!foldRegion.isExpanded()) {
            foldRegion.setExpanded(true);
          }
        }
      });
    }
  }

  public static void highlightMatch(final Project project, Editor editor, final Match match, final ArrayList<RangeHighlighter> highlighters) {
    EditorColorsManager colorsManager = EditorColorsManager.getInstance();
    TextAttributes attributes = colorsManager.getGlobalScheme().getAttributes(EditorColors.SEARCH_RESULT_ATTRIBUTES);
    HighlightManager.getInstance(project).addRangeHighlight(editor, match.getTextRange().getStartOffset(), match.getTextRange().getEndOffset(),
                                                            attributes, true, highlighters);
  }

  public static void processDuplicates(@NotNull MatchProvider provider, @NotNull Project project, @NotNull Editor editor) {
    Boolean hasDuplicates = provider.hasDuplicates();
    if (hasDuplicates == null || hasDuplicates.booleanValue()) {
      List<Match> duplicates = provider.getDuplicates();
      ArrayList<RangeHighlighter> highlighters = null;
      if (duplicates.size() == 1) {
        highlighters = previewMatch(project, duplicates.get(0), editor);
      }
      final int answer = ApplicationManager.getApplication().isUnitTestMode() || hasDuplicates == null ? Messages.YES : Messages.showYesNoDialog(project,
        RefactoringBundle.message("0.has.detected.1.code.fragments.in.this.file.that.can.be.replaced.with.a.call.to.extracted.method",
        ApplicationNamesInfo.getInstance().getProductName(), duplicates.size()),
        "Process Duplicates", Messages.getQuestionIcon());
      if (answer == Messages.YES) {
        PsiDocumentManager.getInstance(project).commitAllDocuments();
        invoke(project, editor, provider, hasDuplicates != null);
      }
      else if (highlighters != null) {
        final HighlightManager highlightManager = HighlightManager.getInstance(project);
        for (RangeHighlighter highlighter : highlighters) {
          highlightManager.removeSegmentHighlighter(editor, highlighter);
        }
      }
    }
  }
}
