/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
import com.intellij.find.impl.FindInProjectUtil;
import com.intellij.find.replaceInProject.ReplaceInProjectManager;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.actionSystem.EditorActionManager;
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
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.ui.LightweightHint;
import com.intellij.usageView.UsageInfo;
import com.intellij.usages.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.FocusAdapter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 *
 */
public class FindUtil {
  private static final Key<Direction> KEY = Key.create("FindUtil.KEY");

  private FindUtil() {}

  @Nullable static VirtualFile getVirtualFile(@NotNull Editor myEditor) {
    Project project = myEditor.getProject();
    PsiFile file = project != null ? PsiDocumentManager.getInstance(project).getPsiFile(myEditor.getDocument()):null;
    return file != null ? file.getVirtualFile() : null;
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

  public static void find(final Project project, final Editor editor) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    final FindManager findManager = FindManager.getInstance(project);
    String s = editor.getSelectionModel().getSelectedText();

    final FindModel model = (FindModel)findManager.getFindInFileModel().clone();
    if (s != null) {
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

    model.setReplaceState(false);
    model.setFindAllEnabled(PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument()) != null);

    findManager.showFindDialog(model, new Runnable() {
      public void run() {
        if (model.isFindAll()) {
          findManager.setFindNextModel(model);
          findAll(project, editor, model);
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

  public static void findAll(final Project project, final Editor editor, final FindModel findModel) {
    final Document document = editor.getDocument();
    final PsiFile psiFile = PsiDocumentManager.getInstance(project).getPsiFile(document);
    if (psiFile == null) return;

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
    final UsageTarget[] usageTargets = { new FindInProjectUtil.StringUsageTarget(findModel.getStringToFind()) };
    final UsageViewPresentation usageViewPresentation = FindInProjectUtil.setupViewPresentation(false, findModel);
    UsageViewManager.getInstance(project).showUsages(usageTargets, usages.toArray(new Usage[usages.size()]), usageViewPresentation);
  }

  public static void searchBack(Project project, FileEditor fileEditor) {
    if (!(fileEditor instanceof TextEditor)) return;
    TextEditor textEditor = (TextEditor)fileEditor;
    Editor editor = textEditor.getEditor();

    searchBack(project, editor);
  }

  public static void searchBack(final Project project, final Editor editor) {
    FindManager findManager = FindManager.getInstance(project);
    if (!findManager.findWasPerformed()) {
      find(project, editor);
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

  public static boolean searchAgain(Project project, FileEditor fileEditor) {
    if (!(fileEditor instanceof TextEditor)) return false;
    TextEditor textEditor = (TextEditor)fileEditor;
    Editor editor = textEditor.getEditor();

    return searchAgain(project, editor);
  }

  public static boolean searchAgain(final Project project, final Editor editor) {
    FindManager findManager = FindManager.getInstance(project);
    if (!findManager.findWasPerformed()) {
      find(project, editor);
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
      if (!model.isForward() && offset > 0 ) {
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
    if (s != null) {
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
    Document document = editor.getDocument();

    if (!FileDocumentManager.getInstance().requestWriting(document, project)) {
        return false;
    }

    document.startGuardedBlockChecking();
    boolean toPrompt = model.isPromptOnReplace();

    if (!toPrompt) {
      ((DocumentEx) document).setInBulkUpdate(true);
    }
    try {
      toPrompt = doReplace(project, editor, model, document, offset, toPrompt);
    }
    catch (ReadOnlyFragmentModificationException e) {
      EditorActionManager.getInstance().getReadonlyFragmentModificationHandler(document).handle(e);
    }
    finally {
      if (!toPrompt) {
        ((DocumentEx) document).setInBulkUpdate(false);
      }
      document.stopGuardedBlockChecking();
    }

    return true;
  }

  private static boolean doReplace(Project project, Editor editor, FindModel model, final Document document, int caretOffset, boolean toPrompt) {
    FindManager findManager = FindManager.getInstance(project);
    model = (FindModel)model.clone();
    int occurrences = 0;

    List<Pair<TextRange,String>> rangesToChange = new ArrayList<Pair<TextRange, String>>();

    boolean replaced = false;
    int offset = caretOffset;
    while (offset >= 0 && offset < editor.getDocument().getTextLength()) {
      caretOffset = offset;
      FindResult result = doSearch(project, editor, offset, !replaced, model, toPrompt);
      if (result == null) {
        break;
      }
      int startResultOffset = result.getStartOffset();
      model.setFromCursor(true);
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
          ((DocumentEx) document).setInBulkUpdate(true);
        }
      }

      int startOffset = result.getStartOffset();
      int endOffset = result.getEndOffset();
      String foundString = document.getCharsSequence().subSequence(startOffset, endOffset).toString();
      String toReplace = findManager.getStringToReplace(foundString, model, startOffset, document.getText());
      if (toReplace == null) break;

      boolean reallyReplace = toPrompt;
      TextRange textRange = doReplace(project, document, model, result, toReplace, reallyReplace, rangesToChange);

      int newOffset = model.isForward() ? textRange.getEndOffset() : textRange.getStartOffset();
      if (newOffset == offset) {
        newOffset += model.isForward() ? 1 : -1;
      }
      offset = newOffset;
      occurrences++;

      //[SCR 7258]
      if (!replaced) {
        editor.getCaretModel().moveToOffset(0);
      }

      replaced = true;
    }

    if (replaced) {
      if (!toPrompt) {
        CharSequence text = document.getCharsSequence();
        final StringBuilder newText = new StringBuilder(document.getTextLength());
        Collections.sort(rangesToChange, new Comparator<Pair<TextRange, String>>() {
          public int compare(Pair<TextRange, String> o1, Pair<TextRange, String> o2) {
            return o1.getFirst().getStartOffset() - o2.getFirst().getStartOffset();
          }
        });
        int offsetBefore = 0;
        for (Pair<TextRange, String> pair : rangesToChange) {
          TextRange range = pair.getFirst();
          String replace = pair.getSecond();
          newText.append(text, offsetBefore, range.getStartOffset()); //before change
          newText.append(replace);
          offsetBefore = range.getEndOffset();
          if (offsetBefore < caretOffset) {
            caretOffset += replace.length() - range.getLength();
          }
        }
        newText.append(text, offsetBefore, text.length()); //tail
        CommandProcessor.getInstance().executeCommand(project, new Runnable() {
          public void run() {
            ApplicationManager.getApplication().runWriteAction(new Runnable() {
              public void run() {
                document.setText(newText);
              }
            });
          }
        }, null, document);

        if (caretOffset > document.getTextLength()) caretOffset = document.getTextLength();
      }
      editor.getCaretModel().moveToOffset(caretOffset);
    }

    ReplaceInProjectManager.reportNumberReplacedOccurences(project, occurrences);
    return replaced;
  }

  @Nullable
  private static FindResult doSearch(Project project,
                                     final Editor editor,
                                     int offset,
                                     boolean toWarn,
                                     FindModel model, boolean adjustEditor) {
    FindManager findManager = FindManager.getInstance(project);
    Document document = editor.getDocument();

    final FindResult result = findManager.findString(document.getCharsSequence(), offset, model, getVirtualFile(editor));

    boolean isFound = result.isStringFound();
    if (!model.isGlobal()) {
      if (result.getEndOffset() > editor.getSelectionModel().getSelectionEnd() ||
          result.getStartOffset() < editor.getSelectionModel().getSelectionStart()) {
        isFound = false;
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
        editor.getSelectionModel().removeSelection();
        scrollingModel.scrollToCaret(scrollType);
        scrollingModel.runActionOnScrollingFinished(
          new Runnable() {
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
        editor.getContentComponent().addFocusListener(listener);
        caretModel.addCaretListener(listener);
      }
      else {
        editor.getSelectionModel().setSelection(result.getStartOffset(), result.getEndOffset());
      }
    }

    return result;
  }

  private static class MyListener extends FocusAdapter implements CaretListener {
    private final Editor myEditor;
    private final RangeHighlighter mySegmentHighlighter;

    private MyListener(Editor editor, RangeHighlighter segmentHighlighter) {
      myEditor = editor;
      mySegmentHighlighter = segmentHighlighter;
    }

    public void caretPositionChanged(CaretEvent e) {
      removeAll();
    }

    private void removeAll() {
      myEditor.getMarkupModel().removeHighlighter(mySegmentHighlighter);
      myEditor.getContentComponent().addFocusListener(this);
      myEditor.getCaretModel().removeCaretListener(this);
    }
  }

  private static void processNotFound(final Editor editor, String stringToFind, FindModel model, Project project) {

    String message = FindBundle.message("find.search.string.not.found.message", stringToFind);

    if (model.isGlobal()) {
      final FindModel newModel = (FindModel)model.clone();
      FindManager findManager = FindManager.getInstance(project);
      Document document = editor.getDocument();
      FindResult result;
      if (newModel.isForward()) {
        result = findManager.findString(document.getCharsSequence(), 0, model, getVirtualFile(editor));
      }
      else {
        result = findManager.findString(document.getCharsSequence(), document.getTextLength(), model, getVirtualFile(editor));
      }
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
        }
      }
      CaretListener listener = new CaretListener() {
        public void caretPositionChanged(CaretEvent e) {
          editor.putUserData(KEY, null);
          editor.getCaretModel().removeCaretListener(this);
        }
      };
      editor.getCaretModel().addCaretListener(listener);
    }
    JComponent component = HintUtil.createInformationLabel(message);
    final LightweightHint hint = new LightweightHint(component);
    HintManagerImpl.getInstanceImpl().showEditorHint(hint, editor, HintManager.UNDER,
                               HintManager.HIDE_BY_ANY_KEY | HintManager.HIDE_BY_TEXT_CHANGE | HintManager.HIDE_BY_SCROLLING,
                               0, false);
  }

  private static TextRange doReplace(final Project project, final Document document, final FindModel model, FindResult result, @NotNull String stringToReplace,
                                     boolean reallyReplace,
                                     List<Pair<TextRange, String>> rangesToChange) {
    final int startOffset = result.getStartOffset();
    final int endOffset = result.getEndOffset();

    final String converted = StringUtil.convertLineSeparators(stringToReplace);
    int newOffset;
    if (reallyReplace) {
      CommandProcessor.getInstance().executeCommand(project, new Runnable() {
        public void run() {
          ApplicationManager.getApplication().runWriteAction(new Runnable() {
            public void run() {
              //[ven] I doubt converting is a good solution to SCR 21224
              document.replaceString(startOffset, endOffset, converted);
            }
          });
        }
      }, null, document);
      newOffset = startOffset + converted.length();
    }
    else {
      TextRange textRange = new TextRange(startOffset, endOffset);
      rangesToChange.add(Pair.create(textRange,converted));

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

  private static void moveCaretAndDontChangeSelection(final Editor editor, int offset, ScrollType scrollType) {
    LogicalPosition pos = editor.offsetToLogicalPosition(offset);
    editor.getCaretModel().moveToLogicalPosition(pos);
    editor.getScrollingModel().scrollToCaret(scrollType);
  }
}
