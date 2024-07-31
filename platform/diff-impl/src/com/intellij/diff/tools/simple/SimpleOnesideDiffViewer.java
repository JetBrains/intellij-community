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
import com.intellij.diff.actions.AllLinesIterator;
import com.intellij.diff.contents.DocumentContent;
import com.intellij.diff.requests.ContentDiffRequest;
import com.intellij.diff.requests.DiffRequest;
import com.intellij.diff.tools.util.DiffDataKeys;
import com.intellij.diff.tools.util.FoldingModelSupport;
import com.intellij.diff.tools.util.base.HighlightPolicy;
import com.intellij.diff.tools.util.base.TextDiffViewerUtil;
import com.intellij.diff.tools.util.side.OnesideTextDiffViewer;
import com.intellij.diff.tools.util.text.TextDiffProvider;
import com.intellij.diff.util.DiffDrawUtil;
import com.intellij.diff.util.DiffUtil;
import com.intellij.diff.util.LineRange;
import com.intellij.diff.util.TextDiffType;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.DataSink;
import com.intellij.openapi.actionSystem.Separator;
import com.intellij.openapi.diff.DiffNavigationContext;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.util.concurrency.annotations.RequiresEdt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

import static com.intellij.diff.util.DiffUtil.getLineCount;

public class SimpleOnesideDiffViewer extends OnesideTextDiffViewer {
  @NotNull private final MyInitialScrollHelper myInitialScrollHelper = new MyInitialScrollHelper();

  @NotNull private final TextDiffProvider myTextDiffProvider;

  @NotNull private final List<RangeHighlighter> myHighlighters = new ArrayList<>();

  @NotNull private final MyMockFoldingModel myFoldingModel;

  public SimpleOnesideDiffViewer(@NotNull DiffContext context, @NotNull DiffRequest request) {
    super(context, (ContentDiffRequest)request);

    myFoldingModel = new MyMockFoldingModel(getProject(), getEditor(), this);

    myTextDiffProvider = DiffUtil.createTextDiffProvider(getProject(), getRequest(), getTextSettings(), this::rediff, this);
  }

  @NotNull
  @Override
  protected List<AnAction> createToolbarActions() {
    List<AnAction> group = new ArrayList<>(myTextDiffProvider.getToolbarActions());
    group.add(new MyToggleExpandByDefaultAction());
    group.add(new MyReadOnlyLockAction());
    group.add(myEditorSettingsAction);

    group.add(Separator.getInstance());
    group.addAll(super.createToolbarActions());

    return group;
  }

  @NotNull
  @Override
  protected List<AnAction> createPopupActions() {
    List<AnAction> group = new ArrayList<>(myTextDiffProvider.getPopupActions());
    group.add(Separator.getInstance());
    group.addAll(super.createPopupActions());

    return group;
  }

  @Override
  @RequiresEdt
  protected void processContextHints() {
    super.processContextHints();
    myInitialScrollHelper.processContext(myRequest);
  }

  @Override
  @RequiresEdt
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
    return () -> {
      clearDiffPresentation();

      boolean shouldHighlight = getTextSettings().getHighlightPolicy() != HighlightPolicy.DO_NOT_HIGHLIGHT;
      if (shouldHighlight) {
        final DocumentContent content = getContent();
        final Document document = content.getDocument();

        TextDiffType type = getSide().select(TextDiffType.DELETED, TextDiffType.INSERTED);

        myHighlighters.addAll(DiffDrawUtil.createHighlighter(getEditor(), 0, getLineCount(document), type, false));
      }

      myInitialScrollHelper.onRediff();
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

    AllLinesIterator allLinesIterator = new AllLinesIterator(getEditor().getDocument());
    int line = context.contextMatchCheck(allLinesIterator);
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

  private class MyToggleExpandByDefaultAction extends TextDiffViewerUtil.ToggleExpandByDefaultAction {
    MyToggleExpandByDefaultAction() {
      super(getTextSettings(), myFoldingModel);
    }
  }

  private class MyReadOnlyLockAction extends TextDiffViewerUtil.EditorReadOnlyLockAction {
    MyReadOnlyLockAction() {
      super(getContext(), getEditableEditors());
    }
  }

  //
  // Helpers
  //

  @Override
  public void uiDataSnapshot(@NotNull DataSink sink) {
    super.uiDataSnapshot(sink);
    sink.set(DiffDataKeys.CURRENT_CHANGE_RANGE, new LineRange(0, getLineCount(getEditor().getDocument())));
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

    @Override
    protected LogicalPosition @Nullable [] getCaretPositions() {
      int index = getSide().getIndex();
      int otherIndex = getSide().other().getIndex();

      LogicalPosition[] carets = new LogicalPosition[2];
      carets[index] = getEditor().getCaretModel().getLogicalPosition();
      carets[otherIndex] = new LogicalPosition(0, 0);
      return carets;
    }
  }

  private static class MyMockFoldingModel extends FoldingModelSupport {
    MyMockFoldingModel(@Nullable Project project, @NotNull EditorEx editor, @NotNull Disposable disposable) {
      super(project, new EditorEx[]{editor}, disposable);
    }
  }
}
