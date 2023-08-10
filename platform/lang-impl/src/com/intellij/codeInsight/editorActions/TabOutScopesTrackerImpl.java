// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.editorActions;

import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.injected.editor.DocumentWindow;
import com.intellij.injected.editor.EditorWindow;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Caret;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.editor.impl.EditorImpl;
import com.intellij.openapi.util.Key;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Iterator;
import java.util.List;

public class TabOutScopesTrackerImpl implements TabOutScopesTracker {
  private static final Key<Integer> CARET_SHIFT = Key.create("tab.out.caret.shift");

  @Override
  public void registerScopeRange(@NotNull Editor editor, int rangeStart, int rangeEnd, int tabOutOffset) {
    ApplicationManager.getApplication().assertWriteAccessAllowed();

    if (editor.isDisposed()) throw new IllegalArgumentException(editor + " is already disposed");
    if (rangeStart > rangeEnd) {
      final String message = String.format("regionEnd (%d) should be larger than regionStart (%d)", rangeEnd, rangeStart);
      throw new IllegalArgumentException(message);
    }
    if (tabOutOffset <= rangeEnd) {
      final String message = String.format("tabOutOffset (%d) should be larger than rangeEnd (%d)", tabOutOffset, rangeEnd);
      throw new IllegalArgumentException(message);
    }

    if (!CodeInsightSettings.getInstance().TAB_EXITS_BRACKETS_AND_QUOTES) return;

    if (editor instanceof EditorWindow) {
      DocumentWindow documentWindow = ((EditorWindow)editor).getDocument();
      rangeStart = documentWindow.injectedToHost(rangeStart);
      rangeEnd = documentWindow.injectedToHost(rangeEnd);
      tabOutOffset = documentWindow.injectedToHost(tabOutOffset);
      editor = ((EditorWindow)editor).getDelegate();
    }
    if (!(editor instanceof EditorImpl)) return;

    Tracker tracker = Tracker.forEditor((EditorImpl)editor, true);
    tracker.registerScope(rangeStart, rangeEnd, tabOutOffset - rangeEnd);
  }

  @Override
  public boolean hasScopeEndingAt(@NotNull Editor editor, int offset) {
    return checkOrRemoveScopeEndingAt(editor, offset, false) > 0;
  }

  @Override
  public int getScopeEndingAt(@NotNull Editor editor, int offset) {
    int caretShift = checkOrRemoveScopeEndingAt(editor, offset, false);
    return caretShift > 0 ? offset + caretShift : -1;
  }

  @Override
  public int removeScopeEndingAt(@NotNull Editor editor, int offset) {
    int caretShift = checkOrRemoveScopeEndingAt(editor, offset, true);
    return caretShift > 0 ? offset + caretShift : -1;
  }

  private static int checkOrRemoveScopeEndingAt(@NotNull Editor editor, int offset, boolean removeScope) {
    ApplicationManager.getApplication().assertReadAccessAllowed();

    if (!CodeInsightSettings.getInstance().TAB_EXITS_BRACKETS_AND_QUOTES) return 0;

    if (editor instanceof EditorWindow) {
      DocumentWindow documentWindow = ((EditorWindow)editor).getDocument();
      offset = documentWindow.injectedToHost(offset);
      editor = ((EditorWindow)editor).getDelegate();
    }
    if (!(editor instanceof EditorImpl)) return 0;

    Tracker tracker = Tracker.forEditor((EditorImpl)editor, false);
    if (tracker == null) return 0;

    return tracker.getCaretShiftForScopeEndingAt(offset, removeScope);
  }

  private static final class Tracker implements DocumentListener {
    private static final Key<Tracker> TRACKER = Key.create("tab.out.scope.tracker");
    private static final Key<List<RangeMarker>> TRACKED_SCOPES = Key.create("tab.out.scopes");

    private final Editor myEditor;

    private static Tracker forEditor(@NotNull EditorImpl editor, boolean createIfAbsent) {
      Tracker tracker = editor.getUserData(TRACKER);
      if (tracker == null && createIfAbsent) {
        editor.putUserData(TRACKER, tracker = new Tracker(editor));
      }
      return tracker;
    }

    private Tracker(@NotNull EditorImpl editor) {
      myEditor = editor;
      Disposable editorDisposable = editor.getDisposable();
      myEditor.getDocument().addDocumentListener(this, editorDisposable);
    }

    private List<RangeMarker> getCurrentScopes(boolean create) {
      Caret currentCaret = myEditor.getCaretModel().getCurrentCaret();
      List<RangeMarker> result = currentCaret.getUserData(TRACKED_SCOPES);
      if (result == null && create) {
        result = currentCaret.putUserDataIfAbsent(TRACKED_SCOPES, ContainerUtil.createLockFreeCopyOnWriteList());
      }
      return result;
    }

    private void registerScope(final int offsetStart, final int offsetEnd, final int caretShift) {
      RangeMarker marker = myEditor.getDocument().createRangeMarker(offsetStart, offsetEnd);
      marker.setGreedyToLeft(true);
      marker.setGreedyToRight(true);
      if (caretShift > 1) marker.putUserData(CARET_SHIFT, caretShift);
      getCurrentScopes(true).add(marker);
    }

    private int getCaretShiftForScopeEndingAt(int offset, boolean remove) {
      List<RangeMarker> scopes = getCurrentScopes(false);
      if (scopes == null) return 0;
      for (Iterator<RangeMarker> it = scopes.iterator(); it.hasNext(); ) {
        RangeMarker scope = it.next();
        if (offset == scope.getEndOffset()) {
          if (remove) it.remove();
          Integer caretShift = scope.getUserData(CARET_SHIFT);
          return caretShift == null ? 1 : caretShift;
        }
      }
      return 0;
    }

    @Override
    public void beforeDocumentChange(@NotNull DocumentEvent event) {
      List<RangeMarker> scopes = getCurrentScopes(false);
      if (scopes == null) return;
      int caretOffset = myEditor.getCaretModel().getOffset();
      int changeStart = event.getOffset();
      int changeEnd = event.getOffset() + event.getOldLength();
      for (Iterator<RangeMarker> it = scopes.iterator(); it.hasNext(); ) {
        RangeMarker scope = it.next();
        // We don't reset scope if the change is completely inside our scope, or if caret is inside, but the change is outside
        if ((changeStart < scope.getStartOffset() || changeEnd > scope.getEndOffset()) &&
            (caretOffset < scope.getStartOffset() || caretOffset > scope.getEndOffset() ||
             (changeEnd >= scope.getStartOffset() && changeStart <= scope.getEndOffset()))) {
          it.remove();
        }
      }
    }

    @Override
    public void bulkUpdateStarting(@NotNull Document document) {
      for (Caret caret : myEditor.getCaretModel().getAllCarets()) {
        caret.putUserData(TRACKED_SCOPES, null);
      }
    }
  }
}
