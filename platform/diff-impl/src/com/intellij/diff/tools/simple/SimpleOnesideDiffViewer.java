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
package com.intellij.diff.tools.simple;

import com.intellij.diff.DiffContext;
import com.intellij.diff.actions.NavigationContextChecker;
import com.intellij.diff.contents.DocumentContent;
import com.intellij.diff.requests.ContentDiffRequest;
import com.intellij.diff.requests.DiffRequest;
import com.intellij.diff.tools.util.DiffDataKeys;
import com.intellij.diff.tools.util.base.HighlightPolicy;
import com.intellij.diff.tools.util.base.TextDiffViewerUtil;
import com.intellij.diff.tools.util.side.OnesideTextDiffViewer;
import com.intellij.diff.util.DiffDrawUtil;
import com.intellij.diff.util.DiffUtil;
import com.intellij.diff.util.LineRange;
import com.intellij.diff.util.TextDiffType;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.Separator;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.diff.DiffNavigationContext;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.util.Pair;
import org.jetbrains.annotations.CalledInAwt;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import static com.intellij.diff.util.DiffUtil.getLineCount;

public class SimpleOnesideDiffViewer extends OnesideTextDiffViewer {
  public static final Logger LOG = Logger.getInstance(SimpleOnesideDiffViewer.class);

  @NotNull private final MyInitialScrollHelper myInitialScrollHelper = new MyInitialScrollHelper();

  @NotNull private final List<RangeHighlighter> myHighlighters = new ArrayList<RangeHighlighter>();

  public SimpleOnesideDiffViewer(@NotNull DiffContext context, @NotNull DiffRequest request) {
    super(context, (ContentDiffRequest)request);
  }

  @Override
  @CalledInAwt
  protected void onDispose() {
    for (RangeHighlighter highlighter : myHighlighters) {
      highlighter.dispose();
    }
    myHighlighters.clear();
    super.onDispose();
  }

  @NotNull
  @Override
  protected List<AnAction> createToolbarActions() {
    List<AnAction> group = new ArrayList<AnAction>();

    group.add(new MyIgnorePolicySettingAction());
    group.add(new MyHighlightPolicySettingAction());
    group.add(new MyReadOnlyLockAction());
    group.add(myEditorSettingsAction);

    group.add(Separator.getInstance());
    group.addAll(super.createToolbarActions());

    return group;
  }

  @NotNull
  @Override
  protected List<AnAction> createPopupActions() {
    List<AnAction> group = new ArrayList<AnAction>();

    group.add(Separator.getInstance());
    group.add(new MyIgnorePolicySettingAction().getPopupGroup());
    group.add(Separator.getInstance());
    group.add(new MyHighlightPolicySettingAction().getPopupGroup());

    group.add(Separator.getInstance());
    group.addAll(super.createPopupActions());

    return group;
  }

  @Override
  @CalledInAwt
  protected void processContextHints() {
    super.processContextHints();
    myInitialScrollHelper.processContext(myRequest);
  }

  @Override
  @CalledInAwt
  protected void updateContextHints() {
    super.updateContextHints();
    myInitialScrollHelper.updateContext(myRequest);
  }

  //
  // Diff
  //

  @Override
  @NotNull
  protected Runnable performRediff(@NotNull final ProgressIndicator indicator) {
    indicator.checkCanceled();

    return new Runnable() {
      @Override
      public void run() {
        clearDiffPresentation();

        boolean shouldHighlight = getTextSettings().getHighlightPolicy() != HighlightPolicy.DO_NOT_HIGHLIGHT;
        if (shouldHighlight) {
          final DocumentContent content = getContent();
          final Document document = content.getDocument();

          TextDiffType type = getSide().select(TextDiffType.DELETED, TextDiffType.INSERTED);

          myHighlighters.addAll(DiffDrawUtil.createHighlighter(getEditor(), 0, getLineCount(document), type, false));

          int startLine = 0;
          int endLine = getLineCount(document);

          if (startLine != endLine) {
            myHighlighters.addAll(DiffDrawUtil.createLineMarker(getEditor(), startLine, endLine, type, false));
          }
        }

        myInitialScrollHelper.onRediff();
      }
    };
  }


  private void clearDiffPresentation() {
    myPanel.resetNotifications();

    for (RangeHighlighter highlighter : myHighlighters) {
      highlighter.dispose();
    }
    myHighlighters.clear();
  }

  //
  // Impl
  //

  private void doScrollToChange(final boolean animated) {
    DiffUtil.moveCaret(getEditor(), 0);
    DiffUtil.scrollEditor(getEditor(), 0, animated);
  }

  protected boolean doScrollToContext(@NotNull DiffNavigationContext context) {
    if (getSide().isLeft()) return false;

    AllLinesIterator allLinesIterator = new AllLinesIterator();
    NavigationContextChecker checker2 = new NavigationContextChecker(allLinesIterator, context);
    int line = checker2.contextMatchCheck();
    if (line == -1) return false;

    scrollToLine(line);
    return true;
  }

  //
  // Misc
  //

  @SuppressWarnings("MethodOverridesStaticMethodOfSuperclass")
  public static boolean canShowRequest(@NotNull DiffContext context, @NotNull DiffRequest request) {
    return OnesideTextDiffViewer.canShowRequest(context, request);
  }

  //
  // Actions
  //

  private class MyReadOnlyLockAction extends TextDiffViewerUtil.EditorReadOnlyLockAction {
    public MyReadOnlyLockAction() {
      super(getContext(), getEditableEditors());
    }
  }

  //
  // Modification operations
  //

  private class MyHighlightPolicySettingAction extends TextDiffViewerUtil.HighlightPolicySettingAction {
    public MyHighlightPolicySettingAction() {
      super(getTextSettings());
    }

    @Override
    protected void onSettingsChanged() {
      rediff();
    }
  }

  private class MyIgnorePolicySettingAction extends TextDiffViewerUtil.IgnorePolicySettingAction {
    public MyIgnorePolicySettingAction() {
      super(getTextSettings());
    }

    @Override
    protected void onSettingsChanged() {
      rediff();
    }
  }

  //
  // Scroll from annotate
  //

  private class AllLinesIterator implements Iterator<Pair<Integer, CharSequence>> {
    @NotNull private final Document myDocument;
    private int myLine = 0;

    private AllLinesIterator() {
      myDocument = getEditor().getDocument();
    }

    @Override
    public boolean hasNext() {
      return myLine < getLineCount(myDocument);
    }

    @Override
    public Pair<Integer, CharSequence> next() {
      int offset1 = myDocument.getLineStartOffset(myLine);
      int offset2 = myDocument.getLineEndOffset(myLine);

      CharSequence text = myDocument.getImmutableCharSequence().subSequence(offset1, offset2);

      Pair<Integer, CharSequence> pair = new Pair<Integer, CharSequence>(myLine, text);
      myLine++;

      return pair;
    }

    @Override
    public void remove() {
      throw new UnsupportedOperationException();
    }
  }

  //
  // Helpers
  //

  @Nullable
  @Override
  public Object getData(@NonNls String dataId) {
    if (DiffDataKeys.CURRENT_CHANGE_RANGE.is(dataId)) {
      int lineCount = getLineCount(getEditor().getDocument());
      return new LineRange(0, lineCount);
    }
    return super.getData(dataId);
  }

  private class MyInitialScrollHelper extends MyInitialScrollPositionHelper {
    @Override
    protected boolean doScrollToChange() {
      if (myScrollToChange == null) return false;
      SimpleOnesideDiffViewer.this.doScrollToChange(false);
      return true;
    }

    @Override
    protected boolean doScrollToFirstChange() {
      SimpleOnesideDiffViewer.this.doScrollToChange(false);
      return true;
    }

    @Override
    protected boolean doScrollToContext() {
      if (myNavigationContext == null) return false;
      return SimpleOnesideDiffViewer.this.doScrollToContext(myNavigationContext);
    }

    @Override
    protected boolean doScrollToPosition() {
      if (myCaretPosition == null) return false;

      LogicalPosition position = getSide().select(myCaretPosition);
      getEditor().getCaretModel().moveToLogicalPosition(position);

      if (myEditorsPosition != null && myEditorsPosition.isSame(position)) {
        DiffUtil.scrollToPoint(getEditor(), myEditorsPosition.myPoints[0], false);
      }
      else {
        DiffUtil.scrollToCaret(getEditor(), false);
      }
      return true;
    }

    @Nullable
    @Override
    protected LogicalPosition[] getCaretPositions() {
      int index = getSide().getIndex();
      int otherIndex = getSide().other().getIndex();

      LogicalPosition[] carets = new LogicalPosition[2];
      carets[index] = getEditor().getCaretModel().getLogicalPosition();
      carets[otherIndex] = new LogicalPosition(0, 0);
      return carets;
    }
  }
}
