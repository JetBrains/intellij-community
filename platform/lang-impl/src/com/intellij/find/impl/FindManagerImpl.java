// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.find.impl;

import com.intellij.codeInsight.highlighting.HighlightManager;
import com.intellij.codeInsight.highlighting.HighlightManagerImpl;
import com.intellij.codeInsight.hint.HintManager;
import com.intellij.codeInsight.hint.HintManagerImpl;
import com.intellij.codeInsight.hint.HintUtil;
import com.intellij.find.EditorSearchSession;
import com.intellij.find.FindBundle;
import com.intellij.find.FindInProjectSettings;
import com.intellij.find.FindModel;
import com.intellij.find.findUsages.FindUsagesManager;
import com.intellij.find.impl.livePreview.SearchResults;
import com.intellij.navigation.NavigationItem;
import com.intellij.notification.impl.NotificationsConfigurationImpl;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.ex.FoldingModelEx;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.search.SearchScope;
import com.intellij.ui.LightweightHint;
import com.intellij.ui.ReplacePromptDialog;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public final class FindManagerImpl extends FindManagerBase {
  private static final Key<Boolean> HIGHLIGHTER_WAS_NOT_FOUND_KEY =
    Key.create("com.intellij.find.impl.FindManagerImpl.HighlighterNotFoundKey");

  private final FindUsagesManager myFindUsagesManager;
  private boolean isFindWasPerformed;
  private boolean isSelectNextOccurrenceWasPerformed;
  private Point myReplaceInFilePromptPos = new Point(-1, -1);
  private Point myReplaceInProjectPromptPos = new Point(-1, -1);
  private FindModel myFindNextModel;
  private FindModel myPreviousFindModel;
  private FindUIHelper myHelper;

  public FindManagerImpl(@NotNull Project project) {
    super(project);

    myFindUsagesManager = new FindUsagesManager(myProject);

    NotificationsConfigurationImpl.remove("FindInPath");
    Disposer.register(project, () -> {
      if (myHelper != null) {
        Disposer.dispose(myHelper);
      }
    });
  }

  @Override
  public FindModel createReplaceInFileModel() {
    FindModel model = new FindModel();
    model.copyFrom(getFindInFileModel());
    model.setReplaceState(true);
    model.setPromptOnReplace(false);
    return model;
  }

  @Override
  public int showPromptDialog(@NotNull FindModel model, String title) {
    return showPromptDialogImpl(model, title, null);
  }

  @PromptResultValue
  private int showPromptDialogImpl(@NotNull FindModel model,
                                   @NlsContexts.DialogTitle String title,
                                   @Nullable MalformedReplacementStringException exception) {
    ReplacePromptDialog replacePromptDialog = new ReplacePromptDialog(model.isMultipleFiles(), title, myProject, exception) {
      @Override
      public @Nullable Point getInitialLocation() {
        if (model.isMultipleFiles() && myReplaceInProjectPromptPos.x >= 0 && myReplaceInProjectPromptPos.y >= 0) {
          return myReplaceInProjectPromptPos;
        }
        if (!model.isMultipleFiles() && myReplaceInFilePromptPos.x >= 0 && myReplaceInFilePromptPos.y >= 0) {
          return myReplaceInFilePromptPos;
        }
        return null;
      }
    };

    replacePromptDialog.show();

    if (model.isMultipleFiles()) {
      myReplaceInProjectPromptPos = replacePromptDialog.getLocation();
    }
    else{
      myReplaceInFilePromptPos = replacePromptDialog.getLocation();
    }
    //noinspection MagicConstant
    return replacePromptDialog.getExitCode();
  }

  void changeGlobalSettings(FindModel findModel) {
    String stringToFind = findModel.getStringToFind();
    FindInProjectSettings findInProjectSettings = FindInProjectSettings.getInstance(myProject);
    findInProjectSettings.addStringToFind(stringToFind);
    if (!findModel.isMultipleFiles()) {
      setFindWasPerformed();
    }
    if (findModel.isReplaceState()) {
      findInProjectSettings.addStringToReplace(findModel.getStringToReplace());
    }
    if (findModel.isMultipleFiles() && !findModel.isProjectScope() && findModel.getDirectoryName() != null) {
      findInProjectSettings.addDirectory(findModel.getDirectoryName());
      myFindInProjectModel.setWithSubdirectories(findModel.isWithSubdirectories());
    }
  }

  @Override
  public void showFindDialog(@NotNull FindModel model, @NotNull Runnable okHandler) {
    if (myHelper == null || Disposer.isDisposed(myHelper)) {
      myHelper = new FindUIHelper(myProject, model, okHandler);
      Disposer.register(myHelper, () -> myHelper = null);
    }
    else {
      myHelper.setModel(model);
      myHelper.setOkHandler(okHandler);
    }
    myHelper.showUI();
  }

  @Override
  public void closeFindDialog() {
    if (myHelper != null) {
      myHelper.closeUI();
    }
  }

  @Override
  public boolean findWasPerformed() {
    return isFindWasPerformed;
  }

  @Override
  public void setFindWasPerformed() {
    isFindWasPerformed = true;
    isSelectNextOccurrenceWasPerformed = false;
  }

  @Override
  public boolean selectNextOccurrenceWasPerformed() {
    return isSelectNextOccurrenceWasPerformed;
  }

  @Override
  public void setSelectNextOccurrenceWasPerformed() {
    isSelectNextOccurrenceWasPerformed = true;
    isFindWasPerformed = false;
  }

  @Override
  public FindModel getFindNextModel() {
    return myFindNextModel;
  }

  @Override
  public FindModel getFindNextModel(@NotNull Editor editor) {
    if (myFindNextModel == null) return null;

    EditorSearchSession search = EditorSearchSession.get(editor);
    if (search != null && !isSelectNextOccurrenceWasPerformed) {
      String textInField = search.getTextInField();
      if (!Objects.equals(textInField, myFindInFileModel.getStringToFind()) && !textInField.isEmpty()) {
        FindModel patched = new FindModel();
        patched.copyFrom(myFindNextModel);
        patched.setStringToFind(textInField);
        return patched;
      }
    }

    return myFindNextModel;
  }

  @Override
  public void setFindNextModel(FindModel findNextModel) {
    myFindNextModel = findNextModel;
    myProject.getMessageBus().syncPublisher(FIND_MODEL_TOPIC).findNextModelChanged();
  }

  @Override
  public int showMalformedReplacementPrompt(@NotNull FindModel model, String title, MalformedReplacementStringException exception) {
    return showPromptDialogImpl(model, title, exception);
  }

  @Override
  public FindModel getPreviousFindModel() {
    return myPreviousFindModel;
  }

  @Override
  public void setPreviousFindModel(FindModel previousFindModel) {
    myPreviousFindModel = previousFindModel;
  }

  @Override
  public boolean canFindUsages(@NotNull PsiElement element) {
    return element.isValid() && myFindUsagesManager.canFindUsages(element);
  }

  @Override
  public void findUsages(@NotNull PsiElement element) {
    findUsages(element, false);
  }

  @Override
  public void findUsagesInScope(@NotNull PsiElement element, @NotNull SearchScope searchScope) {
    myFindUsagesManager.findUsages(element, null, null, false, searchScope);
  }

  @Override
  public void findUsages(@NotNull PsiElement element, boolean showDialog) {
    myFindUsagesManager.findUsages(element, null, null, showDialog, null);
  }

  @Override
  public void showSettingsAndFindUsages(NavigationItem @NotNull [] targets) {
    FindUsagesManager.showSettingsAndFindUsages(targets);
  }

  @Override
  public void clearFindingNextUsageInFile() {
    myFindUsagesManager.clearFindingNextUsageInFile();
  }

  @Override
  public void findUsagesInEditor(@NotNull PsiElement element, @NotNull FileEditor fileEditor) {
    if (fileEditor instanceof TextEditor textEditor) {
      Document document = textEditor.getEditor().getDocument();
      PsiFile psiFile = PsiDocumentManager.getInstance(myProject).getPsiFile(document);

      myFindUsagesManager.findUsages(element, psiFile, fileEditor, false, null);
    }
  }

  private static boolean tryToFindNextUsageViaEditorSearchComponent(Editor editor, SearchResults.Direction forwardOrBackward) {
    EditorSearchSession search = EditorSearchSession.get(editor);
    if (search == null || !search.hasMatches()) return false;
    if (!search.isSearchInProgress()) {
      if (forwardOrBackward == SearchResults.Direction.UP) {
        search.searchBackward();
      }
      else {
        search.searchForward();
      }
    }
    return true;
  }

  @Override
  public boolean findNextUsageInEditor(@NotNull Editor editor) {
    return findNextUsageInFile(editor, SearchResults.Direction.DOWN);
  }

  @Override
  public boolean findPreviousUsageInEditor(@NotNull Editor editor) {
    return findNextUsageInFile(editor, SearchResults.Direction.UP);
  }

  private boolean findNextUsageInFile(@NotNull Editor editor, @NotNull SearchResults.Direction direction) {
    editor.getCaretModel().removeSecondaryCarets();
    if (tryToFindNextUsageViaEditorSearchComponent(editor, direction)) {
      return true;
    }

    RangeHighlighter[] highlighters = ((HighlightManagerImpl)HighlightManager.getInstance(myProject)).getHighlighters(editor);
    if (highlighters.length > 0) {
      return highlightNextHighlighter(highlighters, editor, editor.getCaretModel().getOffset(), direction == SearchResults.Direction.DOWN, false);
    }

    if (direction == SearchResults.Direction.DOWN) {
      return myFindUsagesManager.findNextUsageInFile(editor);
    }
    return myFindUsagesManager.findPreviousUsageInFile(editor);
  }

  private static boolean highlightNextHighlighter(RangeHighlighter[] highlighters, Editor editor, int offset,
                                                  boolean isForward, boolean secondPass) {
    RangeHighlighter highlighterToSelect = null;
    Object wasNotFound = editor.getUserData(HIGHLIGHTER_WAS_NOT_FOUND_KEY);
    for (RangeHighlighter highlighter : highlighters) {
      int start = highlighter.getStartOffset();
      int end = highlighter.getEndOffset();
      if (highlighter.isValid() && start < end) {
        if (isForward && (start > offset || start == offset && secondPass)) {
          if (highlighterToSelect == null || highlighterToSelect.getStartOffset() > start) highlighterToSelect = highlighter;
        }
        if (!isForward && (end < offset || end == offset && secondPass)) {
          if (highlighterToSelect == null || highlighterToSelect.getEndOffset() < end) highlighterToSelect = highlighter;
        }
      }
    }
    if (highlighterToSelect != null) {
      expandFoldRegionsIfNecessary(editor, highlighterToSelect.getStartOffset(), highlighterToSelect.getEndOffset());
      editor.getSelectionModel().setSelection(highlighterToSelect.getStartOffset(), highlighterToSelect.getEndOffset());
      editor.getCaretModel().moveToOffset(highlighterToSelect.getStartOffset());
      ScrollType scrollType;
      if (secondPass) {
        scrollType = isForward ? ScrollType.CENTER_UP : ScrollType.CENTER_DOWN;
      }
      else {
        scrollType = isForward ? ScrollType.CENTER_DOWN : ScrollType.CENTER_UP;
      }
      editor.getScrollingModel().scrollToCaret(scrollType);
      editor.putUserData(HIGHLIGHTER_WAS_NOT_FOUND_KEY, null);
      EditorSearchSession.logSelectionUpdate();
      return true;
    }

    if (wasNotFound == null) {
      editor.putUserData(HIGHLIGHTER_WAS_NOT_FOUND_KEY, Boolean.TRUE);
      String message = FindBundle.message("find.highlight.no.more.highlights.found");
      if (isForward) {
        AnAction action=ActionManager.getInstance().getAction(IdeActions.ACTION_FIND_NEXT);
        String shortcutsText=KeymapUtil.getFirstKeyboardShortcutText(action);
        if (shortcutsText.isEmpty()) {
          message = FindBundle.message("find.search.again.from.top.action.message", message);
        }
        else {
          message = FindBundle.message("find.search.again.from.top.hotkey.message", message, shortcutsText);
        }
      }
      else {
        AnAction action=ActionManager.getInstance().getAction(IdeActions.ACTION_FIND_PREVIOUS);
        String shortcutsText=KeymapUtil.getFirstKeyboardShortcutText(action);
        if (shortcutsText.isEmpty()) {
          message = FindBundle.message("find.search.again.from.bottom.action.message", message);
        }
        else {
          message = FindBundle.message("find.search.again.from.bottom.hotkey.message", message, shortcutsText);
        }
      }
      JComponent component = HintUtil.createInformationLabel(message);
      final LightweightHint hint = new LightweightHint(component);
      int flags = HintManager.HIDE_BY_ANY_KEY | HintManager.HIDE_BY_TEXT_CHANGE | HintManager.HIDE_BY_SCROLLING;
      HintManagerImpl.getInstanceImpl().showEditorHint(hint, editor, HintManager.UNDER, flags, 0, false);
      return true;
    }
    if (!secondPass) {
      offset = isForward ? 0 : editor.getDocument().getTextLength();
      return highlightNextHighlighter(highlighters, editor, offset, isForward, true);
    }

    return false;
  }

  private static void expandFoldRegionsIfNecessary(@NotNull Editor editor, int startOffset, int endOffset) {
    final FoldingModel foldingModel = editor.getFoldingModel();
    final FoldRegion[] regions;
    if (foldingModel instanceof FoldingModelEx ex) {
      regions = ex.fetchTopLevel();
    }
    else {
      regions = foldingModel.getAllFoldRegions();
    }
    if (regions == null) {
      return;
    }
    int i = Arrays.binarySearch(regions, null, (o1, o2) -> {
      // Find the first region that ends after the given start offset
      if (o1 == null) {
        return startOffset - o2.getEndOffset();
      }
      return o1.getEndOffset() - startOffset;
    });
    if (i < 0) {
      i = -i - 1;
    }
    else {
      i++; // Don't expand fold region that ends at the start offset.
    }
    if (i >= regions.length) {
      return;
    }
    final List<FoldRegion> toExpand = new ArrayList<>();
    for (; i < regions.length; i++) {
      final FoldRegion region = regions[i];
      if (region.getStartOffset() >= endOffset) {
        break;
      }
      if (!region.isExpanded()) {
        toExpand.add(region);
      }
    }
    if (toExpand.isEmpty()) {
      return;
    }
    foldingModel.runBatchFoldingOperation(() -> {
      for (FoldRegion region : toExpand) {
        region.setExpanded(true);
      }
    });
  }

  public @NotNull FindUsagesManager getFindUsagesManager() {
    return myFindUsagesManager;
  }
}
