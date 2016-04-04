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
package com.intellij.diff.tools.util.base;

import com.intellij.diff.requests.DiffRequest;
import com.intellij.diff.util.*;
import com.intellij.diff.util.DiffUserDataKeysEx.ScrollToPolicy;
import com.intellij.openapi.diff.DiffNavigationContext;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import org.jetbrains.annotations.CalledInAwt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.List;

public class InitialScrollPositionSupport {
  public abstract static class InitialScrollHelperBase {
    protected boolean myShouldScroll = true;

    @Nullable protected ScrollToPolicy myScrollToChange;
    @Nullable protected EditorsVisiblePositions myEditorsPosition;
    @Nullable protected LogicalPosition[] myCaretPosition;

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

    @Nullable
    protected abstract LogicalPosition[] getCaretPositions();

    @Nullable
    protected abstract EditorsVisiblePositions getVisiblePositions();
  }

  private static abstract class SideInitialScrollHelper extends InitialScrollHelperBase {
    @Nullable
    @Override
    protected LogicalPosition[] getCaretPositions() {
      return doGetCaretPositions(getEditors());
    }

    @Nullable
    @Override
    protected EditorsVisiblePositions getVisiblePositions() {
      return doGetVisiblePositions(getEditors());
    }

    @CalledInAwt
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

    @CalledInAwt
    public void onSlowRediff() {
      if (wasScrolled(getEditors())) myShouldScroll = false;
      if (myScrollToChange != null) return;
      if (myShouldScroll) myShouldScroll = !doScrollToLine();
      if (myNavigationContext != null) return;
      if (myShouldScroll) myShouldScroll = !doScrollToPosition();
    }

    @CalledInAwt
    public void onRediff() {
      if (wasScrolled(getEditors())) myShouldScroll = false;
      if (myShouldScroll) myShouldScroll = !doScrollToChange();
      if (myShouldScroll) myShouldScroll = !doScrollToLine();
      if (myShouldScroll) myShouldScroll = !doScrollToContext();
      if (myShouldScroll) myShouldScroll = !doScrollToPosition();
      if (myShouldScroll) doScrollToFirstChange();
      myShouldScroll = false;
    }

    @CalledInAwt
    protected abstract boolean doScrollToChange();

    @CalledInAwt
    protected abstract boolean doScrollToFirstChange();

    @CalledInAwt
    protected abstract boolean doScrollToContext();

    @CalledInAwt
    protected abstract boolean doScrollToLine();
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
      if (myShouldScroll) myShouldScroll = !doScrollToLine();
      if (myShouldScroll) myShouldScroll = !doScrollToPosition();
    }

    public void onRediff() {
      if (wasScrolled(getEditors())) myShouldScroll = false;
      if (myShouldScroll) myShouldScroll = !doScrollToChange();
      if (myShouldScroll) myShouldScroll = !doScrollToLine();
      if (myShouldScroll) myShouldScroll = !doScrollToPosition();
      if (myShouldScroll) doScrollToFirstChange();
      myShouldScroll = false;
    }

    @CalledInAwt
    protected abstract boolean doScrollToChange();

    @CalledInAwt
    protected abstract boolean doScrollToFirstChange();

    @CalledInAwt
    protected abstract boolean doScrollToLine();
  }

  @NotNull
  public static Point[] doGetScrollingPositions(@NotNull List<? extends Editor> editors) {
    Point[] carets = new Point[editors.size()];
    for (int i = 0; i < editors.size(); i++) {
      carets[i] = DiffUtil.getScrollingPosition(editors.get(i));
    }
    return carets;
  }

  @NotNull
  public static LogicalPosition[] doGetCaretPositions(@NotNull List<? extends Editor> editors) {
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

  public static void doMoveCaretsToPositions(@NotNull LogicalPosition[] positions, @NotNull List<? extends Editor> editors) {
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

  public static class EditorsVisiblePositions {
    public static final Key<EditorsVisiblePositions> KEY = Key.create("Diff.EditorsVisiblePositions");

    @NotNull public final LogicalPosition[] myCaretPosition;
    @NotNull public final Point[] myPoints;

    public EditorsVisiblePositions(@NotNull LogicalPosition caretPosition, @NotNull Point points) {
      this(new LogicalPosition[]{caretPosition}, new Point[]{points});
    }

    public EditorsVisiblePositions(@NotNull LogicalPosition[] caretPosition, @NotNull Point[] points) {
      myCaretPosition = caretPosition;
      myPoints = points;
    }

    public boolean isSame(@Nullable LogicalPosition... caretPosition) {
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
