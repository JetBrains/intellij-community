// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.util.duplicates;

import com.intellij.CommonBundle;
import com.intellij.codeInsight.folding.CodeFoldingManager;
import com.intellij.codeInsight.highlighting.HighlightManager;
import com.intellij.find.FindManager;
import com.intellij.java.refactoring.JavaRefactoringBundle;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.FoldRegion;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.markup.RangeHighlighter;
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
public final class DuplicatesImpl {
  private static final Logger LOG = Logger.getInstance(DuplicatesImpl.class);

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

  public static void invoke(final Project project, final MatchProvider provider, boolean showDialog) {
    final List<Match> duplicates = provider.getDuplicates();
    int idx = 0;
    final Ref<Boolean> showAll = new Ref<>();
    if (!showDialog) showAll.set(true);
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
    final ArrayList<RangeHighlighter> highlighters = previewMatch(project, editor, match.getTextRange());
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
            if (Messages.showOkCancelDialog(project, JavaRefactoringBundle.message("process.duplicates.change.signature.promt"),
                                            JavaRefactoringBundle.message("change.method.signature.action.name"), CommonBundle.getContinueButtonText(), CommonBundle.getCancelButtonText(), Messages.getWarningIcon()) !=
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

    WriteCommandAction.writeCommandAction(project).withName(MethodDuplicatesHandler.getRefactoringName())
                      .withGroupId(MethodDuplicatesHandler.getRefactoringName()).run(() -> {
      try {
        provider.processMatch(match);
      }
      catch (IncorrectOperationException e) {
        LOG.error(e);
      }
    });

    return false;
  }

  public static ArrayList<RangeHighlighter> previewMatch(Project project, Editor editor, TextRange range) {
    final ArrayList<RangeHighlighter> highlighters = new ArrayList<>();
    highlightMatch(project, editor, range, highlighters);
    final LogicalPosition logicalPosition = editor.offsetToLogicalPosition(range.getStartOffset());
    expandAllRegionsCoveringRange(project, editor, range);
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

  public static void highlightMatch(final Project project, Editor editor, final TextRange range, final ArrayList<? super RangeHighlighter> highlighters) {
    HighlightManager.getInstance(project).addRangeHighlight(editor, range.getStartOffset(), range.getEndOffset(),
                                                            EditorColors.SEARCH_RESULT_ATTRIBUTES, true, highlighters);
  }

  public static void processDuplicates(@NotNull MatchProvider provider, @NotNull Project project, @NotNull Editor editor) {
    Boolean hasDuplicates = provider.hasDuplicates();
    if (hasDuplicates == null || hasDuplicates.booleanValue()) {
      List<Match> duplicates = provider.getDuplicates();
      ArrayList<RangeHighlighter> highlighters = null;
      if (duplicates.size() == 1) {
        highlighters = previewMatch(project, editor, duplicates.get(0).getTextRange());
      }
      final int answer = ApplicationManager.getApplication().isUnitTestMode() || hasDuplicates == null ? Messages.YES : Messages.showYesNoDialog(project,
                                                                                                                                                 RefactoringBundle.message("0.has.detected.1.code.fragments.in.this.file.that.can.be.replaced.with.a.call.to.extracted.method",
        ApplicationNamesInfo.getInstance().getProductName(), duplicates.size()),
                                                                                                                                                 JavaRefactoringBundle
                                                                                                                                                   .message(
                                                                                                                                                     "process.duplicates.title"), Messages.getQuestionIcon());
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
