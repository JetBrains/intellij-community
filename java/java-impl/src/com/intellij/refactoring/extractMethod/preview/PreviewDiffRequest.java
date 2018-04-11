// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.extractMethod.preview;

import com.intellij.diff.FrameDiffTool;
import com.intellij.diff.contents.DiffContent;
import com.intellij.diff.requests.SimpleDiffRequest;
import com.intellij.diff.tools.fragmented.UnifiedDiffViewer;
import com.intellij.diff.tools.simple.SimpleDiffViewer;
import com.intellij.diff.tools.util.base.DiffViewerBase;
import com.intellij.diff.tools.util.base.DiffViewerListener;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.CaretModel;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.editor.event.CaretEvent;
import com.intellij.openapi.editor.event.CaretListener;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.util.Couple;
import com.intellij.openapi.util.TextRange;
import com.intellij.util.Consumer;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.function.Function;

/**
 * @author Pavel.Dolgov
 */
class PreviewDiffRequest extends SimpleDiffRequest {
  private static final Logger LOG = Logger.getInstance(PreviewDiffRequest.class);

  private final Map<FragmentNode, Couple<TextRange>> myLinesBounds;
  private final Consumer<FragmentNode> mySelectNode;
  private CaretTracker myCaretTracker; // accessed in EDT

  public PreviewDiffRequest(@NotNull Map<FragmentNode, Couple<TextRange>> linesBounds,
                            @NotNull DiffContent content1,
                            @NotNull DiffContent content2,
                            @NotNull Consumer<FragmentNode> selectNode) {
    super(null, content1, content2, null, null);
    myLinesBounds = linesBounds;
    mySelectNode = selectNode;
  }

  public void setViewer(FrameDiffTool.DiffViewer viewer) {
    LOG.assertTrue(ApplicationManager.getApplication().isDispatchThread(), "EDT only");

    if (viewer instanceof UnifiedDiffViewer) {
      myCaretTracker = new UnifiedCaretTracker((UnifiedDiffViewer)viewer);
    }
    else if (viewer instanceof SimpleDiffViewer) {
      myCaretTracker = new SimpleCaretTracker((SimpleDiffViewer)viewer);
    }
    else {
      myCaretTracker = null;
    }
  }

  public void onNodeSelected(@NotNull FragmentNode node) {
    LOG.assertTrue(ApplicationManager.getApplication().isDispatchThread(), "EDT only");

    Couple<TextRange> bounds = myLinesBounds.get(node);
    if (bounds != null && myCaretTracker != null) {
      myCaretTracker.selectBounds(bounds);
    }
  }

  abstract class CaretTracker<V extends DiffViewerBase> extends DiffViewerListener {
    protected final V myViewer;
    protected boolean myMoveCaret = true;

    protected CaretTracker(V viewer) {
      this.myViewer = viewer;
      viewer.addListener(this);
    }

    protected abstract void selectBounds(Couple<TextRange> bounds);

    protected void setCaretPosition(EditorEx editor, TextRange range) {
      CaretModel caretModel = editor.getCaretModel();
      int offset = caretModel.getOffset();
      if (!range.contains(offset)) {
        caretModel.moveToOffset(range.getStartOffset());
      }
      editor.getScrollingModel().scrollToCaret(ScrollType.CENTER);
    }

    protected class MyCaretListener implements CaretListener {
      private final Function<Couple<TextRange>, TextRange> mySideGetter;

      public MyCaretListener(Function<Couple<TextRange>, TextRange> sideGetter) {
        mySideGetter = sideGetter;
      }

      @Override
      public void caretPositionChanged(CaretEvent e) {
        myMoveCaret = false;
        try {
          int newOffset = e.getEditor().logicalPositionToOffset(e.getNewPosition());
          for (Map.Entry<FragmentNode, Couple<TextRange>> entry : myLinesBounds.entrySet()) {
            TextRange range = mySideGetter.apply(entry.getValue());
            if (range.containsOffset(newOffset)) {
              mySelectNode.consume(entry.getKey());
              break;
            }
          }
        }
        finally {
          myMoveCaret = true;
        }
      }
    }
  }

  class UnifiedCaretTracker extends CaretTracker<UnifiedDiffViewer> {
    private final CaretListener myListener = new MyCaretListener(c -> c.getFirst());

    protected UnifiedCaretTracker(UnifiedDiffViewer viewer) {
      super(viewer);
    }

    @Override
    protected void onInit() {
      EditorEx editor = myViewer.getEditor();
      editor.getCaretModel().addCaretListener(myListener);
    }

    @Override
    protected void onDispose() {
      EditorEx editor = myViewer.getEditor();
      editor.getCaretModel().removeCaretListener(myListener);
    }

    @Override
    protected void selectBounds(Couple<TextRange> bounds) {
      if (myMoveCaret) {
        setCaretPosition(myViewer.getEditor(), bounds.getFirst());
      }
    }
  }

  class SimpleCaretTracker extends CaretTracker<SimpleDiffViewer> {
    private final CaretListener myListener1 = new MyCaretListener(c -> c.getFirst());
    private final CaretListener myListener2 = new MyCaretListener(c -> c.getSecond());

    protected SimpleCaretTracker(SimpleDiffViewer viewer) {
      super(viewer);
    }

    @Override
    protected void onInit() {
      myViewer.getEditor1().getCaretModel().addCaretListener(myListener1);
      myViewer.getEditor2().getCaretModel().addCaretListener(myListener2);
    }

    @Override
    protected void onDispose() {
      myViewer.getEditor1().getCaretModel().removeCaretListener(myListener1);
      myViewer.getEditor2().getCaretModel().removeCaretListener(myListener2);
    }

    @Override
    protected void selectBounds(Couple<TextRange> bounds) {
      if (myMoveCaret) {
        setCaretPosition(myViewer.getEditor1(), bounds.getFirst());
        setCaretPosition(myViewer.getEditor2(), bounds.getSecond());
      }
    }
  }
}
