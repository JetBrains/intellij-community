// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.tools.util.base;

import com.intellij.diff.requests.DiffRequest;
import com.intellij.diff.util.*;
import com.intellij.diff.util.DiffUserDataKeysEx.ScrollToPolicy;
import com.intellij.openapi.diff.DiffNavigationContext;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import com.intellij.ui.ComponentUtil;
import com.intellij.util.concurrency.annotations.RequiresEdt;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.List;
import java.util.Set;

@ApiStatus.Internal
public final class InitialScrollPositionSupport {
  public abstract static class InitialScrollHelperBase {
    protected boolean myShouldScroll = true;

    @Nullable protected ScrollToPolicy myScrollToChange;
    @Nullable protected EditorsVisiblePositions myEditorsPosition;
    protected LogicalPosition @Nullable [] myCaretPosition;

    public void processContext(@NotNull DiffRequest request) {
      myScrollToChange = request.getUserData(DiffUserDataKeysEx.SCROLL_TO_CHANGE);
      myEditorsPosition = request.getUserData(EditorsVisiblePositions.KEY);
      myCaretPosition = request.getUserData(DiffUserDataKeysEx.EDITORS_CARET_POSITION);
    }

    public void updateContext(@NotNull DiffRequest request) {
      LogicalPosition[] carets = getCaretPositions();
      EditorsVisiblePositions visiblePositions = getVisiblePositions();

      request.putUserData(DiffUserDataKeysEx.SCROLL_TO_CHANGE, null);
      request.putUserData(EditorsVisiblePositions.KEY, visiblePositions);
      request.putUserData(DiffUserDataKeysEx.EDITORS_CARET_POSITION, carets);
    }

    protected abstract LogicalPosition @Nullable [] getCaretPositions();

    @ApiStatus.Internal
    @Nullable
    protected abstract EditorsVisiblePositions getVisiblePositions();
  }

  private static abstract class SideInitialScrollHelper extends InitialScrollHelperBase {
    @Override
    protected LogicalPosition @Nullable [] getCaretPositions() {
      return doGetCaretPositions(getEditors());
    }

    @ApiStatus.Internal
    @Nullable
    @Override
    protected EditorsVisiblePositions getVisiblePositions() {
      return doGetVisiblePositions(getEditors());
    }

    @RequiresEdt
    protected boolean doScrollToPosition() {
      List<? extends Editor> editors = getEditors();
      if (myCaretPosition == null || myCaretPosition.length != editors.size()) return false;

      doMoveCaretsToPositions(myCaretPosition, editors);

      try {
        disableSyncScroll(true);

        if (myEditorsPosition != null && myEditorsPosition.isSame(myCaretPosition)) {
          doScrollToVisiblePositions(myEditorsPosition, editors);
        }
        else {
          doScrollToCaret(editors);
        }
      }
      finally {
        disableSyncScroll(false);
      }
      return true;
    }

    @NotNull
    protected abstract List<? extends Editor> getEditors();

    protected abstract void disableSyncScroll(boolean value);
  }

  public static abstract class TwosideInitialScrollHelper extends SideInitialScrollHelper {
    @Nullable protected Pair<Side, Integer> myScrollToLine;
    @Nullable protected DiffNavigationContext myNavigationContext;

    @Override
    public void processContext(@NotNull DiffRequest request) {
      super.processContext(request);
      myScrollToLine = request.getUserData(DiffUserDataKeys.SCROLL_TO_LINE);
      myNavigationContext = request.getUserData(DiffUserDataKeysEx.NAVIGATION_CONTEXT);
    }

    @Override
    public void updateContext(@NotNull DiffRequest request) {
      super.updateContext(request);
      request.putUserData(DiffUserDataKeys.SCROLL_TO_LINE, null);
      request.putUserData(DiffUserDataKeysEx.NAVIGATION_CONTEXT, null);
    }

    @RequiresEdt
    public void onSlowRediff() {
      if (wasScrolled(getEditors())) myShouldScroll = false;
      if (myScrollToChange != null) return;

      ensureEditorSizeIsUpToDate(getEditors());
      if (myShouldScroll) myShouldScroll = !doScrollToLine(true);
      if (myNavigationContext != null) return;
      if (myShouldScroll) myShouldScroll = !doScrollToPosition();
    }

    @RequiresEdt
    public void onRediff() {
      if (wasScrolled(getEditors())) myShouldScroll = false;

      ensureEditorSizeIsUpToDate(getEditors());
      if (myShouldScroll) myShouldScroll = !doScrollToChange();
      if (myShouldScroll) myShouldScroll = !doScrollToLine(false);
      if (myShouldScroll) myShouldScroll = !doScrollToContext();
      if (myShouldScroll) myShouldScroll = !doScrollToPosition();
      if (myShouldScroll) doScrollToFirstChange();
      myShouldScroll = false;
    }

    @RequiresEdt
    protected abstract boolean doScrollToChange();

    @RequiresEdt
    protected abstract boolean doScrollToFirstChange();

    @RequiresEdt
    protected abstract boolean doScrollToContext();

    @RequiresEdt
    protected abstract boolean doScrollToLine(boolean onSlowRediff);
  }

  public static abstract class ThreesideInitialScrollHelper extends SideInitialScrollHelper {
    @Nullable protected Pair<ThreeSide, Integer> myScrollToLine;

    @Override
    public void processContext(@NotNull DiffRequest request) {
      super.processContext(request);
      myScrollToLine = request.getUserData(DiffUserDataKeys.SCROLL_TO_LINE_THREESIDE);
    }

    @Override
    public void updateContext(@NotNull DiffRequest request) {
      super.updateContext(request);
      request.putUserData(DiffUserDataKeys.SCROLL_TO_LINE_THREESIDE, null);
    }

    public void onSlowRediff() {
      if (wasScrolled(getEditors())) myShouldScroll = false;
      if (myScrollToChange != null) return;

      ensureEditorSizeIsUpToDate(getEditors());
      if (myShouldScroll) myShouldScroll = !doScrollToLine();
      if (myShouldScroll) myShouldScroll = !doScrollToPosition();
    }

    public void onRediff() {
      if (wasScrolled(getEditors())) myShouldScroll = false;

      ensureEditorSizeIsUpToDate(getEditors());
      if (myShouldScroll) myShouldScroll = !doScrollToChange();
      if (myShouldScroll) myShouldScroll = !doScrollToLine();
      if (myShouldScroll) myShouldScroll = !doScrollToPosition();
      if (myShouldScroll) doScrollToFirstChange();
      myShouldScroll = false;
    }

    @RequiresEdt
    protected abstract boolean doScrollToChange();

    @RequiresEdt
    protected abstract boolean doScrollToFirstChange();

    @RequiresEdt
    protected abstract boolean doScrollToLine();
  }

  public static Point @NotNull [] doGetScrollingPositions(@NotNull List<? extends Editor> editors) {
    Point[] carets = new Point[editors.size()];
    for (int i = 0; i < editors.size(); i++) {
      carets[i] = DiffUtil.getScrollingPosition(editors.get(i));
    }
    return carets;
  }

  public static LogicalPosition @NotNull [] doGetCaretPositions(@NotNull List<? extends Editor> editors) {
    LogicalPosition[] carets = new LogicalPosition[editors.size()];
    for (int i = 0; i < editors.size(); i++) {
      carets[i] = DiffUtil.getCaretPosition(editors.get(i));
    }
    return carets;
  }

  @Nullable
  public static EditorsVisiblePositions doGetVisiblePositions(@NotNull List<? extends Editor> editors) {
    LogicalPosition[] carets = doGetCaretPositions(editors);
    Point[] points = doGetScrollingPositions(editors);
    return new EditorsVisiblePositions(carets, points);
  }

  public static void doMoveCaretsToPositions(LogicalPosition @NotNull [] positions, @NotNull List<? extends Editor> editors) {
    for (int i = 0; i < editors.size(); i++) {
      Editor editor = editors.get(i);
      if (editor != null) editor.getCaretModel().moveToLogicalPosition(positions[i]);
    }
  }

  public static void doScrollToVisiblePositions(@NotNull EditorsVisiblePositions visiblePositions,
                                                @NotNull List<? extends Editor> editors) {
    for (int i = 0; i < editors.size(); i++) {
      Editor editor = editors.get(i);
      if (editor != null) DiffUtil.scrollToPoint(editor, visiblePositions.myPoints[i], false);
    }
  }

  public static void doScrollToCaret(@NotNull List<? extends Editor> editors) {
    for (int i = 0; i < editors.size(); i++) {
      Editor editor = editors.get(i);
      if (editor != null) DiffUtil.scrollToCaret(editor, false);
    }
  }

  public static boolean wasScrolled(@NotNull List<? extends Editor> editors) {
    for (Editor editor : editors) {
      if (editor == null) continue;
      if (editor.getCaretModel().getOffset() != 0) return true;
      if (editor.getScrollingModel().getVerticalScrollOffset() != 0) return true;
      if (editor.getScrollingModel().getHorizontalScrollOffset() != 0) return true;
    }
    return false;
  }

  public static void ensureEditorSizeIsUpToDate(@NotNull List<? extends Editor> editors) {
    Set<Window> windows = ContainerUtil.map2SetNotNull(editors, editor -> ComponentUtil.getWindow(editor.getComponent()));
    for (Window window : windows) {
      window.validate();
    }
  }

  public static class EditorsVisiblePositions {
    public static final Key<EditorsVisiblePositions> KEY = Key.create("Diff.EditorsVisiblePositions");

    public final LogicalPosition @NotNull [] myCaretPosition;
    public final Point @NotNull [] myPoints;

    public EditorsVisiblePositions(@NotNull LogicalPosition caretPosition, @NotNull Point points) {
      this(new LogicalPosition[]{caretPosition}, new Point[]{points});
    }

    public EditorsVisiblePositions(LogicalPosition @NotNull [] caretPosition, Point @NotNull [] points) {
      myCaretPosition = caretPosition;
      myPoints = points;
    }

    public boolean isSame(LogicalPosition @Nullable ... caretPosition) {
      // TODO: allow small fluctuations ?
      if (caretPosition == null) return true;
      if (myCaretPosition.length != caretPosition.length) return false;
      for (int i = 0; i < caretPosition.length; i++) {
        if (!caretPosition[i].equals(myCaretPosition[i])) return false;
      }
      return true;
    }
  }
}
