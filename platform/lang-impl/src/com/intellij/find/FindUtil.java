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

package com.intellij.find;

import com.intellij.codeInsight.hint.HintManager;
import com.intellij.codeInsight.hint.HintManagerImpl;
import com.intellij.codeInsight.hint.HintUtil;
import com.intellij.find.findUsages.PsiElement2UsageTargetAdapter;
import com.intellij.find.impl.FindInProjectUtil;
import com.intellij.find.impl.livePreview.LivePreview;
import com.intellij.find.replaceInProject.ReplaceInProjectManager;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.actionSystem.EditorActionManager;
import com.intellij.openapi.editor.actions.IncrementalFindAction;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.event.CaretEvent;
import com.intellij.openapi.editor.event.CaretListener;
import com.intellij.openapi.editor.ex.DocumentEx;
import com.intellij.openapi.editor.ex.RangeHighlighterEx;
import com.intellij.openapi.editor.markup.HighlighterLayer;
import com.intellij.openapi.editor.markup.HighlighterTargetArea;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.fileEditor.ex.IdeDocumentHistory;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.ui.LightweightHint;
import com.intellij.usageView.UsageInfo;
import com.intellij.usages.*;
import com.intellij.usages.impl.UsageViewImpl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class FindUtil {
  private static final Key<Direction> KEY = Key.create("FindUtil.KEY");

  private FindUtil() {
  }

  @Nullable
  static VirtualFile getVirtualFile(@NotNull Editor myEditor) {
    Project project = myEditor.getProject();
    PsiFile file = project != null ? PsiDocumentManager.getInstance(project).getPsiFile(myEditor.getDocument()) : null;
    return file != null ? file.getVirtualFile() : null;
  }

  public static void initStringToFindWithSelection(FindModel findModel, Editor editor) {
    if (editor != null) {
      String s = editor.getSelectionModel().getSelectedText();
      FindModel.initStringToFindNoMultiline(findModel, s);
    }
  }

  private static boolean isMultilineSelection(Editor editor) {
    SelectionModel selectionModel = editor != null ? editor.getSelectionModel() : null;
    if (selectionModel != null) {
      String selectedText = selectionModel.getSelectedText();
      if (selectedText != null) {
        if (selectedText.indexOf("\n") != -1) {
          return true;
        }
      }
    }
    return false;
  }

  private static boolean isWholeLineSelection(Editor editor) {
    SelectionModel selectionModel = editor != null ? editor.getSelectionModel() : null;
    if (selectionModel != null) {
      String selectedText = selectionModel.getSelectedText();
      final Document document = editor.getDocument();
      final int line = document.getLineNumber(selectionModel.getSelectionStart());
      final String lineText = document.getText(new TextRange(document.getLineStartOffset(line), document.getLineEndOffset(line)));
      if (lineText.trim().equals(selectedText)) {
        return true;
      }
    }
    return false;
  }

  public static void configureFindModel(boolean replace, @Nullable Editor editor, FindModel model, boolean firstSearch) {
    boolean isGlobal = true;
    String stringToFind = null;
    final SelectionModel selectionModel = editor != null ? editor.getSelectionModel() : null;
    String selectedText = selectionModel != null ? selectionModel.getSelectedText() : null;
    if (!StringUtil.isEmpty(selectedText)) {
      if (replace && (isMultilineSelection(editor) || isWholeLineSelection(editor))) {
        isGlobal = false;
        stringToFind = model.getStringToFind();
      } else if (isMultilineSelection(editor)) {
        model.setMultiline(true);
      }
      if (stringToFind == null) {
        stringToFind = selectedText;
      }
    }
    else {
      if (firstSearch) {
        stringToFind = "";
      }
      else {
        stringToFind = model.getStringToFind();
      }
    }
    model.setReplaceState(replace);
    model.setStringToFind(stringToFind);
    model.setGlobal(isGlobal);
    model.setPromptOnReplace(false);
  }

  private enum Direction {
    UP, DOWN
  }

  public static void findWordAtCaret(Project project, Editor editor) {
    int caretOffset = editor.getCaretModel().getOffset();
    Document document = editor.getDocument();
    CharSequence text = document.getCharsSequence();
    int start = 0;
    int end = document.getTextLength();
    if (!editor.getSelectionModel().hasSelection()) {
      for (int i = caretOffset - 1; i >= 0; i--) {
        char c = text.charAt(i);
        if (!Character.isJavaIdentifierPart(c)) {
          start = i + 1;
          break;
        }
      }
      for (int i = caretOffset; i < document.getTextLength(); i++) {
        char c = text.charAt(i);
        if (!Character.isJavaIdentifierPart(c)) {
          end = i;
          break;
        }
      }
    }
    else {
      start = editor.getSelectionModel().getSelectionStart();
      end = editor.getSelectionModel().getSelectionEnd();
    }
    if (start >= end) {
      return;
    }
    FindManager findManager = FindManager.getInstance(project);
    String s = text.subSequence(start, end).toString();
    FindSettings.getInstance().addStringToFind(s);
    findManager.getFindInFileModel().setStringToFind(s);
    findManager.setFindWasPerformed();
    findManager.clearFindingNextUsageInFile();
    FindModel model = new FindModel();
    model.setStringToFind(s);
    model.setCaseSensitive(true);
    model.setWholeWordsOnly(!editor.getSelectionModel().hasSelection());

    final JComponent header = editor.getHeaderComponent();
    if (header instanceof EditorSearchComponent) {
      final EditorSearchComponent searchComponent = (EditorSearchComponent)header;
      searchComponent.setTextInField(model.getStringToFind());
    }

    findManager.setFindNextModel(model);
    doSearch(project, editor, caretOffset, true, model, true);
  }

  public static void find(@NotNull final Project project, @NotNull final Editor editor) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    final FindManager findManager = FindManager.getInstance(project);
    String s = editor.getSelectionModel().getSelectedText();

    final FindModel model = (FindModel)findManager.getFindInFileModel().clone();
    if (StringUtil.isEmpty(s)) {
      model.setGlobal(true);
    }
    else {
      if (s.indexOf('\n') >= 0) {
        model.setGlobal(false);
      }
      else {
        model.setStringToFind(s);
        model.setGlobal(true);
      }
    }

    model.setReplaceState(false);
    model.setFindAllEnabled(PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument()) != null);

    findManager.showFindDialog(model, new Runnable() {
      @Override
      public void run() {
        if (model.isFindAll()) {
          findManager.setFindNextModel(model);
          findAllAndShow(project, editor, model);
          return;
        }

        if (!model.isGlobal() && editor.getSelectionModel().hasSelection()) {
          int offset = model.isForward()
                       ? editor.getSelectionModel().getSelectionStart()
                       : editor.getSelectionModel().getSelectionEnd();
          ScrollType scrollType = model.isForward() ? ScrollType.CENTER_DOWN : ScrollType.CENTER_UP;
          moveCaretAndDontChangeSelection(editor, offset, scrollType);
        }

        int offset;
        if (model.isGlobal()) {
          if (model.isFromCursor()) {
            offset = editor.getCaretModel().getOffset();
          }
          else {
            offset = model.isForward() ? 0 : editor.getDocument().getTextLength();
          }
        }
        else {
          // in selection

          if (!editor.getSelectionModel().hasSelection()) {
            // TODO[anton] actually, this should never happen - Find dialog should not allow such combination
            findManager.setFindNextModel(null);
            return;
          }

          offset = model.isForward() ? editor.getSelectionModel().getSelectionStart() : editor.getSelectionModel().getSelectionEnd();
        }

        findManager.setFindNextModel(null);
        findManager.getFindInFileModel().copyFrom(model);
        doSearch(project, editor, offset, true, model, true);
      }
    });
  }

  @Nullable
  public static List<Usage> findAll(@NotNull Project project, @NotNull Editor editor, @NotNull FindModel findModel) {
    final Document document = editor.getDocument();
    final PsiFile psiFile = PsiDocumentManager.getInstance(project).getPsiFile(document);
    if (psiFile == null) return null;

    CharSequence text = document.getCharsSequence();
    int textLength = document.getTextLength();
    final List<Usage> usages = new ArrayList<Usage>();
    FindManager findManager = FindManager.getInstance(project);
    findModel.setForward(true); // when find all there is no diff in direction

    int offset = 0;
    VirtualFile virtualFile = getVirtualFile(editor);

    while (offset < textLength) {
      FindResult result = findManager.findString(text, offset, findModel, virtualFile);
      if (!result.isStringFound()) break;

      usages.add(new UsageInfo2UsageAdapter(new UsageInfo(psiFile, result.getStartOffset(), result.getEndOffset())));

      final int prevOffset = offset;
      offset = result.getEndOffset();

      if (prevOffset == offset) {
        // for regular expr the size of the match could be zero -> could be infinite loop in finding usages!
        ++offset;
      }
    }
    return usages;
  }

  public static void findAllAndShow(final Project project, final Editor editor, final FindModel findModel) {
    List<Usage> usages = findAll(project, editor, findModel);
    if (usages == null) return;
    final UsageTarget[] usageTargets = {new FindInProjectUtil.StringUsageTarget(findModel.getStringToFind())};
    final UsageViewPresentation usageViewPresentation = FindInProjectUtil.setupViewPresentation(false, findModel);
    UsageViewManager.getInstance(project).showUsages(usageTargets, usages.toArray(new Usage[usages.size()]), usageViewPresentation);
  }

  public static void searchBack(Project project, FileEditor fileEditor, @Nullable DataContext dataContext) {
    if (!(fileEditor instanceof TextEditor)) return;
    TextEditor textEditor = (TextEditor)fileEditor;
    Editor editor = textEditor.getEditor();

    searchBack(project, editor, dataContext);
  }

  public static void searchBack(final Project project, final Editor editor, @Nullable DataContext context) {
    FindManager findManager = FindManager.getInstance(project);
    if (!findManager.findWasPerformed()) {
      new IncrementalFindAction().getHandler().execute(editor, context);
      return;
    }

    FindModel model = findManager.getFindNextModel(editor);
    if (model == null) {
      model = findManager.getFindInFileModel();
    }
    model = (FindModel)model.clone();
    model.setForward(!model.isForward());
    if (!model.isGlobal() && !editor.getSelectionModel().hasSelection()) {
      model.setGlobal(true);
    }

    int offset;
    if (Direction.UP.equals(editor.getUserData(KEY)) && !model.isForward()) {
      offset = editor.getDocument().getTextLength();
    }
    else if (Direction.DOWN.equals(editor.getUserData(KEY)) && model.isForward()) {
      offset = 0;
    }
    else {
      editor.putUserData(KEY, null);
      offset = editor.getCaretModel().getOffset();
      if (!model.isForward() && offset > 0) {
        offset--;
      }
    }
    searchAgain(project, editor, offset, model);
  }

  public static boolean searchAgain(Project project, FileEditor fileEditor, @Nullable DataContext context) {
    if (!(fileEditor instanceof TextEditor)) return false;
    TextEditor textEditor = (TextEditor)fileEditor;
    Editor editor = textEditor.getEditor();

    return searchAgain(project, editor, context);
  }

  public static boolean searchAgain(final Project project, final Editor editor, @Nullable DataContext context) {
    FindManager findManager = FindManager.getInstance(project);
    if (!findManager.findWasPerformed()) {
      new IncrementalFindAction().getHandler().execute(editor, context);
      return false;
    }

    FindModel model = findManager.getFindNextModel(editor);
    if (model == null) {
      model = findManager.getFindInFileModel();
    }
    model = (FindModel)model.clone();

    int offset;
    if (Direction.DOWN.equals(editor.getUserData(KEY)) && model.isForward()) {
      offset = 0;
    }
    else if (Direction.UP.equals(editor.getUserData(KEY)) && !model.isForward()) {
      offset = editor.getDocument().getTextLength();
    }
    else {
      editor.putUserData(KEY, null);
      offset = editor.getCaretModel().getOffset();
      if (!model.isForward() && offset > 0) {
        offset--;
      }
    }
    return searchAgain(project, editor, offset, model);
  }

  private static boolean searchAgain(Project project, Editor editor, int offset, FindModel model) {
    if (!model.isGlobal() && !editor.getSelectionModel().hasSelection()) {
      model.setGlobal(true);
    }
    model.setFromCursor(false);
    if (model.isReplaceState()) {
      model.setPromptOnReplace(true);
      model.setReplaceAll(false);
      replace(project, editor, offset, model);
      return true;
    }
    else {
      doSearch(project, editor, offset, true, model, true);
      return false;
    }
  }

  public static void replace(final Project project, final Editor editor) {
    final FindManager findManager = FindManager.getInstance(project);
    final FindModel model = (FindModel)findManager.getFindInFileModel().clone();
    final String s = editor.getSelectionModel().getSelectedText();
    if (!StringUtil.isEmpty(s)) {
      if (s.indexOf('\n') >= 0) {
        model.setGlobal(false);
      }
      else {
        model.setStringToFind(s);
        model.setGlobal(true);
      }
    }
    else {
      model.setGlobal(true);
    }
    model.setReplaceState(true);

    findManager.showFindDialog(model, new Runnable() {
      @Override
      public void run() {
        if (!model.isGlobal() && editor.getSelectionModel().hasSelection()) {
          int offset = model.isForward()
                       ? editor.getSelectionModel().getSelectionStart()
                       : editor.getSelectionModel().getSelectionEnd();
          ScrollType scrollType = model.isForward() ? ScrollType.CENTER_DOWN : ScrollType.CENTER_UP;
          moveCaretAndDontChangeSelection(editor, offset, scrollType);
        }
        int offset;
        if (model.isGlobal()) {
          if (model.isFromCursor()) {
            offset = editor.getCaretModel().getOffset();
            if (!model.isForward()) {
              offset++;
            }
          }
          else {
            offset = model.isForward() ? 0 : editor.getDocument().getTextLength();
          }
        }
        else {
          // in selection

          if (!editor.getSelectionModel().hasSelection()) {
            // TODO[anton] actually, this should never happen - Find dialog should not allow such combination
            findManager.setFindNextModel(null);
            return;
          }

          offset = model.isForward() ? editor.getSelectionModel().getSelectionStart() : editor.getSelectionModel().getSelectionEnd();
        }

        if (s != null && editor.getSelectionModel().hasSelection() && s.equals(model.getStringToFind())) {
          if (model.isFromCursor() && model.isForward()) {
            offset = Math.min(editor.getSelectionModel().getSelectionStart(), offset);
          }
          else if (model.isFromCursor() && !model.isForward()) {
            offset = Math.max(editor.getSelectionModel().getSelectionEnd(), offset);
          }
        }
        findManager.setFindNextModel(null);
        findManager.getFindInFileModel().copyFrom(model);
        replace(project, editor, offset, model);
      }
    });
  }

  public static boolean replace(Project project, Editor editor, int offset, FindModel model) {
    return replace(project, editor, offset, model, new ReplaceDelegate() {
      @Override
      public boolean shouldReplace(TextRange range, String replace) {
        return true;
      }
    });
  }

  public static boolean replace(Project project, Editor editor, int offset, FindModel model, ReplaceDelegate delegate) {
    Document document = editor.getDocument();

    if (!FileDocumentManager.getInstance().requestWriting(document, project)) {
      return false;
    }

    document.startGuardedBlockChecking();
    boolean toPrompt = model.isPromptOnReplace();

    if (!toPrompt) {
      ((DocumentEx)document).setInBulkUpdate(true);
    }
    try {
      toPrompt = doReplace(project, editor, model, document, offset, toPrompt, delegate);
    }
    catch (ReadOnlyFragmentModificationException e) {
      EditorActionManager.getInstance().getReadonlyFragmentModificationHandler(document).handle(e);
    }
    finally {
      if (!toPrompt) {
        ((DocumentEx)document).setInBulkUpdate(false);
      }
      document.stopGuardedBlockChecking();
    }

    return true;
  }

  private static boolean doReplace(Project project, final Editor editor, final FindModel aModel, final Document document, int caretOffset,
                                   boolean toPrompt, ReplaceDelegate delegate) {
    FindManager findManager = FindManager.getInstance(project);
    final FindModel model = (FindModel)aModel.clone();
    int occurrences = 0;

    List<Pair<TextRange, String>> rangesToChange = new ArrayList<Pair<TextRange, String>>();

    boolean replaced = false;
    boolean reallyReplaced = false;

    int offset = caretOffset;
    while (offset >= 0 && offset < editor.getDocument().getTextLength()) {
      caretOffset = offset;
      FindResult result = doSearch(project, editor, offset, !replaced, model, toPrompt);
      if (result == null) {
        break;
      }
      int startResultOffset = result.getStartOffset();
      model.setFromCursor(true);

      int startOffset = result.getStartOffset();
      int endOffset = result.getEndOffset();
      String foundString = document.getCharsSequence().subSequence(startOffset, endOffset).toString();
      String toReplace;
      try {
        CharSequence textToMatch = document.getCharsSequence().subSequence(offset, document.getTextLength());
        toReplace = findManager.getStringToReplace(foundString, model, startOffset - offset, textToMatch);
      }
      catch (FindManager.MalformedReplacementStringException e) {
        if (!ApplicationManager.getApplication().isUnitTestMode()) {
          Messages.showErrorDialog(project, e.getMessage(), FindBundle.message("find.replace.invalid.replacement.string.title"));
        }
        break;
      }

      if (toPrompt) {
        int promptResult = findManager.showPromptDialog(model, FindBundle.message("find.replace.dialog.title"));
        if (promptResult == FindManager.PromptResult.SKIP) {
          offset = model.isForward() ? result.getEndOffset() : startResultOffset;
          continue;
        }
        if (promptResult == FindManager.PromptResult.CANCEL) {
          break;
        }
        if (promptResult == FindManager.PromptResult.ALL) {
          toPrompt = false;
          ((DocumentEx)document).setInBulkUpdate(true);
        }
      }
      int newOffset;
      if (delegate == null || delegate.shouldReplace(result, toReplace)) {
        boolean reallyReplace = toPrompt;
        if (reallyReplace) {
          //[SCR 7258]
          if (!reallyReplaced) {
            editor.getCaretModel().moveToOffset(0);
            reallyReplaced = true;
          }
        }
        TextRange textRange = doReplace(project, document, model, result, toReplace, reallyReplace, rangesToChange);
        replaced = true;
        newOffset = model.isForward() ? textRange.getEndOffset() : textRange.getStartOffset();
        occurrences++;
      }
      else {
        newOffset = model.isForward() ? result.getEndOffset() : result.getStartOffset();
      }

      if (newOffset == offset) {
        newOffset += model.isForward() ? 1 : -1;
      }
      offset = newOffset;
    }

    if (replaced) {
      if (!toPrompt) {
        CharSequence text = document.getCharsSequence();
        final StringBuilder newText = new StringBuilder(document.getTextLength());
        Collections.sort(rangesToChange, new Comparator<Pair<TextRange, String>>() {
          @Override
          public int compare(Pair<TextRange, String> o1, Pair<TextRange, String> o2) {
            return o1.getFirst().getStartOffset() - o2.getFirst().getStartOffset();
          }
        });
        int offsetBefore = 0;
        for (Pair<TextRange, String> pair : rangesToChange) {
          TextRange range = pair.getFirst();
          String replace = pair.getSecond();
          newText.append(text, offsetBefore, range.getStartOffset()); //before change
          if (delegate == null || delegate.shouldReplace(range, replace)) {
            newText.append(replace);
          }
          else {
            newText.append(text.subSequence(range.getStartOffset(), range.getEndOffset()));
          }
          offsetBefore = range.getEndOffset();
          if (offsetBefore < caretOffset) {
            caretOffset += replace.length() - range.getLength();
          }
        }
        newText.append(text, offsetBefore, text.length()); //tail
        if (caretOffset > newText.length()) {
          caretOffset = newText.length();
        }
        final int finalCaretOffset = caretOffset;
        CommandProcessor.getInstance().executeCommand(project, new Runnable() {
          @Override
          public void run() {
            ApplicationManager.getApplication().runWriteAction(new Runnable() {
              @Override
              public void run() {
                document.setText(newText);
                editor.getCaretModel().moveToOffset(finalCaretOffset);
                if (model.isGlobal()) {
                  editor.getSelectionModel().removeSelection();
                }
              }
            });
          }
        }, null, document);
      }
      else {
        if (reallyReplaced) {
          if (caretOffset > document.getTextLength()) {
            caretOffset = document.getTextLength();
          }
          editor.getCaretModel().moveToOffset(caretOffset);
        }
      }
    }

    ReplaceInProjectManager.reportNumberReplacedOccurrences(project, occurrences);
    return replaced;
  }


  private static boolean selectionMayContainRange(SelectionModel selection, TextRange range) {
    int[] starts = selection.getBlockSelectionStarts();
    int[] ends = selection.getBlockSelectionEnds();
    if (starts.length == 0) {
      return false;
    }
    return new TextRange(starts[0], ends[starts.length - 1]).contains(range);
  }

  private static boolean selectionStrictlyContainsRange(SelectionModel selection, TextRange range) {
    int[] starts = selection.getBlockSelectionStarts();
    int[] ends = selection.getBlockSelectionEnds();
    for (int i = 0; i < starts.length; ++i) {
      if (new TextRange(starts[i], ends[i]).contains(range)) {  //todo
        return true;
      }
    }
    return false;
  }

  @Nullable
  private static FindResult doSearch(@NotNull Project project,
                                     @NotNull final Editor editor,
                                     int offset,
                                     boolean toWarn,
                                     @NotNull FindModel model,
                                     boolean adjustEditor) {
    FindManager findManager = FindManager.getInstance(project);
    Document document = editor.getDocument();

    final FindResult result = findManager.findString(document.getCharsSequence(), offset, model, getVirtualFile(editor));

    boolean isFound = result.isStringFound();
    final SelectionModel selection = editor.getSelectionModel();
    if (isFound && !model.isGlobal()) {
      if (!selectionMayContainRange(selection, result)) {
        isFound = false;
      }
      else if (!selectionStrictlyContainsRange(selection, result)) {
        final int[] starts = selection.getBlockSelectionStarts();
        for (int newOffset : starts) {
          if (newOffset > result.getStartOffset()) {
            return doSearch(project, editor, newOffset, toWarn, model, adjustEditor);
          }
        }
      }
    }
    if (!isFound) {
      if (toWarn) {
        processNotFound(editor, model.getStringToFind(), model, project);
      }
      return null;
    }

    if (adjustEditor) {
      final CaretModel caretModel = editor.getCaretModel();
      final ScrollingModel scrollingModel = editor.getScrollingModel();
      int oldCaretOffset = caretModel.getOffset();
      boolean forward = oldCaretOffset < result.getStartOffset();
      final ScrollType scrollType = forward ? ScrollType.CENTER_DOWN : ScrollType.CENTER_UP;

      if (model.isGlobal()) {
        caretModel.moveToOffset(result.getEndOffset());
        selection.removeSelection();
        scrollingModel.scrollToCaret(scrollType);
        scrollingModel.runActionOnScrollingFinished(
          new Runnable() {
            @Override
            public void run() {
              scrollingModel.scrollTo(editor.offsetToLogicalPosition(result.getStartOffset()), scrollType);
              scrollingModel.scrollTo(editor.offsetToLogicalPosition(result.getEndOffset()), scrollType);
            }
          }
        );
      }
      else {
        moveCaretAndDontChangeSelection(editor, result.getStartOffset(), scrollType);
        moveCaretAndDontChangeSelection(editor, result.getEndOffset(), scrollType);
      }
      IdeDocumentHistory.getInstance(project).includeCurrentCommandAsNavigation();

      EditorColorsManager manager = EditorColorsManager.getInstance();
      TextAttributes selectionAttributes = manager.getGlobalScheme().getAttributes(EditorColors.SEARCH_RESULT_ATTRIBUTES);

      if (!model.isGlobal()) {
        final RangeHighlighterEx segmentHighlighter = (RangeHighlighterEx)editor.getMarkupModel().addRangeHighlighter(
          result.getStartOffset(),
          result.getEndOffset(),
          HighlighterLayer.SELECTION + 1,
          selectionAttributes, HighlighterTargetArea.EXACT_RANGE);
        MyListener listener = new MyListener(editor, segmentHighlighter);
        caretModel.addCaretListener(listener);
      }
      else {
        selection.setSelection(result.getStartOffset(), result.getEndOffset());
      }
    }

    return result;
  }

  private static class MyListener implements CaretListener {
    private final Editor myEditor;
    private final RangeHighlighter mySegmentHighlighter;

    private MyListener(@NotNull Editor editor, @NotNull RangeHighlighter segmentHighlighter) {
      myEditor = editor;
      mySegmentHighlighter = segmentHighlighter;
    }

    @Override
    public void caretPositionChanged(CaretEvent e) {
      removeAll();
    }

    private void removeAll() {
      myEditor.getCaretModel().removeCaretListener(this);
      mySegmentHighlighter.dispose();
    }
  }

  public static void processNotFound(final Editor editor, String stringToFind, FindModel model, Project project) {
    String message = FindBundle.message("find.search.string.not.found.message", stringToFind);

    short position = HintManager.UNDER;
    if (model.isGlobal()) {
      final FindModel newModel = (FindModel)model.clone();
      FindManager findManager = FindManager.getInstance(project);
      Document document = editor.getDocument();
      FindResult result = findManager.findString(document.getCharsSequence(),
                                                 newModel.isForward() ? 0 : document.getTextLength(), model, getVirtualFile(editor));
      if (!result.isStringFound()) {
        result = null;
      }

      FindModel modelForNextSearch = findManager.getFindNextModel(editor);
      if (modelForNextSearch == null) {
        modelForNextSearch = findManager.getFindInFileModel();
      }

      if (result != null) {
        if (newModel.isForward()) {
          AnAction action = ActionManager.getInstance().getAction(
            modelForNextSearch.isForward() ? IdeActions.ACTION_FIND_NEXT : IdeActions.ACTION_FIND_PREVIOUS);
          String shortcutsText = KeymapUtil.getFirstKeyboardShortcutText(action);
          if (shortcutsText.length() > 0) {
            message = FindBundle.message("find.search.again.from.top.hotkey.message", message, shortcutsText);
          }
          else {
            message = FindBundle.message("find.search.again.from.top.action.message", message);
          }
          editor.putUserData(KEY, Direction.DOWN);
        }
        else {
          AnAction action = ActionManager.getInstance().getAction(
            modelForNextSearch.isForward() ? IdeActions.ACTION_FIND_PREVIOUS : IdeActions.ACTION_FIND_NEXT);
          String shortcutsText = KeymapUtil.getFirstKeyboardShortcutText(action);
          if (shortcutsText.length() > 0) {
            message = FindBundle.message("find.search.again.from.bottom.hotkey.message", message, shortcutsText);
          }
          else {
            message = FindBundle.message("find.search.again.from.bottom.action.message", message);
          }
          editor.putUserData(KEY, Direction.UP);
          position = HintManager.ABOVE;
        }
      }
      CaretListener listener = new CaretListener() {
        @Override
        public void caretPositionChanged(CaretEvent e) {
          editor.putUserData(KEY, null);
          editor.getCaretModel().removeCaretListener(this);
        }
      };
      editor.getCaretModel().addCaretListener(listener);
    }
    JComponent component = HintUtil.createInformationLabel(JDOMUtil.escapeText(message, false, false));

    if (ApplicationManager.getApplication().isUnitTestMode()) {
      LivePreview.processNotFound();
    } else {
      final LightweightHint hint = new LightweightHint(component);
      HintManagerImpl.getInstanceImpl().showEditorHint(hint, editor, position,
                                                       HintManager.HIDE_BY_ANY_KEY |
                                                       HintManager.HIDE_BY_TEXT_CHANGE |
                                                       HintManager.HIDE_BY_SCROLLING,
                                                       0, false);
    }
  }

  public static TextRange doReplace(final Project project,
                                    final Document document,
                                    final FindModel model,
                                    FindResult result,
                                    @NotNull String stringToReplace,
                                    boolean reallyReplace,
                                    List<Pair<TextRange, String>> rangesToChange) {
    final int startOffset = result.getStartOffset();
    final int endOffset = result.getEndOffset();

    int newOffset;
    if (reallyReplace) {
      newOffset = doReplace(project, document, startOffset, endOffset, stringToReplace);
    }
    else {
      final String converted = StringUtil.convertLineSeparators(stringToReplace);
      TextRange textRange = new TextRange(startOffset, endOffset);
      rangesToChange.add(Pair.create(textRange, converted));

      newOffset = endOffset;
    }

    int start = startOffset;
    int end = newOffset;
    if (model.isRegularExpressions()) {
      String toFind = model.getStringToFind();
      if (model.isForward()) {
        if (StringUtil.endsWithChar(toFind, '$')) {
          int i = 0;
          int length = toFind.length();
          while (i + 2 <= length && toFind.charAt(length - i - 2) == '\\') i++;
          if (i % 2 == 0) end++; //This $ is a special symbol in regexp syntax
        }
        else if (StringUtil.startsWithChar(toFind, '^')) {
          while (end < document.getTextLength() && document.getCharsSequence().charAt(end) != '\n') end++;
        }
      }
      else {
        if (StringUtil.startsWithChar(toFind, '^')) {
          start--;
        }
        else if (StringUtil.endsWithChar(toFind, '$')) {
          while (start >= 0 && document.getCharsSequence().charAt(start) != '\n') start--;
        }
      }
    }
    return new TextRange(start, end);
  }

  public static int doReplace(Project project,
                              final Document document,
                              final int startOffset,
                              final int endOffset,
                              final String stringToReplace) {
    final String converted = StringUtil.convertLineSeparators(stringToReplace);
    CommandProcessor.getInstance().executeCommand(project, new Runnable() {
      @Override
      public void run() {
        ApplicationManager.getApplication().runWriteAction(new Runnable() {
          @Override
          public void run() {
            //[ven] I doubt converting is a good solution to SCR 21224
            document.replaceString(startOffset, endOffset, converted);
          }
        });
      }
    }, null, null);
    return startOffset + converted.length();
  }

  private static void moveCaretAndDontChangeSelection(final Editor editor, int offset, ScrollType scrollType) {
    LogicalPosition pos = editor.offsetToLogicalPosition(offset);
    editor.getCaretModel().moveToLogicalPosition(pos);
    editor.getScrollingModel().scrollToCaret(scrollType);
  }

  public interface ReplaceDelegate {
    boolean shouldReplace(TextRange range, String replace);
  }

  @Nullable
  public static UsageView showInUsageView(PsiElement sourceElement, @NotNull final PsiElement[] targets, String title, Project project) {
    if (targets.length == 0) return null;
    final UsageViewPresentation presentation = new UsageViewPresentation();
    presentation.setCodeUsagesString(title);
    presentation.setTabName(title);
    presentation.setTabText(title);
    final UsageTarget[] usageTargets =
      sourceElement == null ? UsageTarget.EMPTY_ARRAY : new UsageTarget[]{new PsiElement2UsageTargetAdapter(sourceElement)};

    final UsageInfoToUsageConverter.TargetElementsDescriptor targetElementsDescriptor =
      sourceElement != null ? new UsageInfoToUsageConverter.TargetElementsDescriptor(sourceElement)
                            : new UsageInfoToUsageConverter.TargetElementsDescriptor(PsiElement.EMPTY_ARRAY);
    final Usage[] usages = {UsageInfoToUsageConverter.convert(targetElementsDescriptor, new UsageInfo(targets[0]))};
    final UsageView view =
      UsageViewManager.getInstance(project).showUsages(usageTargets, usages, presentation);
    ProgressManager.getInstance().run(new Task.Backgroundable(project, "Updating Usage View ...") {
      @Override
      public void run(@NotNull ProgressIndicator indicator) {

        for (int i = 1; i < targets.length; i++) {
          if (((UsageViewImpl)view).isDisposed()) break;
          final PsiElement target = targets[i];
          ApplicationManager.getApplication().runReadAction(new Runnable() {
            @Override
            public void run() {
              final Usage usage = UsageInfoToUsageConverter.convert(targetElementsDescriptor, new UsageInfo(target));
              view.appendUsage(usage);
            }
          });
        }
      }
    });
    return view;
  }
}
