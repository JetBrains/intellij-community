// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.fileEditor;

import com.intellij.openapi.actionSystem.DataKey;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NotNullLazyValue;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Supplier;

import static com.intellij.openapi.util.NotNullLazyValue.atomicLazy;

/**
 * Allows opening file in editor, optionally at specific line/column position.
 */
public class OpenFileDescriptor implements FileEditorNavigatable, Comparable<OpenFileDescriptor> {
  /**
   * Tells descriptor to navigate in specific editor rather than file editor in main IDE window.
   * For example if you want to navigate in editor embedded into modal dialog, you should provide this data.
   */
  public static final DataKey<Editor> NAVIGATE_IN_EDITOR = DataKey.create("NAVIGATE_IN_EDITOR");

  private final Project myProject;
  private final VirtualFile myFile;
  private final int myLogicalLine;
  private final int myLogicalColumn;
  private final int myOffset;
  private final @Nullable RangeMarkerSupplier myRangeMarkerSupplier;

  private boolean myUseCurrentWindow;
  private boolean myUsePreviewTab;
  private ScrollType myScrollType = ScrollType.CENTER;

  public OpenFileDescriptor(@NotNull Project project, @NotNull VirtualFile file, int offset) {
    this(project, file, -1, -1, offset, false);
  }

  public OpenFileDescriptor(@NotNull Project project, @NotNull VirtualFile file, int logicalLine, int logicalColumn) {
    this(project, file, logicalLine, logicalColumn, -1, false);
  }

  public OpenFileDescriptor(@NotNull Project project, @NotNull VirtualFile file, int logicalLine, int logicalColumn, boolean persistent) {
    this(project, file, logicalLine, logicalColumn, -1, persistent);
  }

  public OpenFileDescriptor(@NotNull Project project, @NotNull VirtualFile file) {
    this(project, file, -1, -1, -1, false);
  }

  private OpenFileDescriptor(@NotNull Project project, @NotNull VirtualFile file, int logicalLine, int logicalColumn, int offset, boolean persistent) {
    myProject = project;
    myFile = file;
    myLogicalLine = logicalLine;
    myLogicalColumn = logicalColumn;
    myOffset = offset;
    if (offset >= 0) {
      myRangeMarkerSupplier = new RangeMarkerSupplier(
        atomicLazy(() -> LazyRangeMarkerFactory.getInstance(project).createRangeMarker(file, offset))
      );
    }
    else if (logicalLine >= 0) {
      myRangeMarkerSupplier = new RangeMarkerSupplier(
        atomicLazy(
          () -> LazyRangeMarkerFactory.getInstance(project).createRangeMarker(file, logicalLine, Math.max(0, logicalColumn), persistent))
      );
    }
    else {
      myRangeMarkerSupplier = null;
    }
  }

  @Override
  @NotNull
  public VirtualFile getFile() {
    return myFile;
  }

  @Nullable
  public RangeMarker getRangeMarker() {
    return getOrComputeRangeMarker();
  }

  private @Nullable RangeMarker getOrComputeRangeMarker() {
    return myRangeMarkerSupplier == null ? null : myRangeMarkerSupplier.get();
  }

  public int getOffset() {
    RangeMarker rangeMarker = getOrComputeRangeMarker();
    return rangeMarker != null && rangeMarker.isValid() ? rangeMarker.getStartOffset() : myOffset;
  }

  public int getLine() {
    return myLogicalLine;
  }

  public int getColumn() {
    return myLogicalColumn;
  }

  @Override
  public void navigate(boolean requestFocus) {
    FileNavigator.getInstance().navigate(this, requestFocus);
  }

  public boolean navigateInEditor(@NotNull Project project, boolean requestFocus) {
    return FileNavigator.getInstance().navigateInEditor(this, requestFocus);
  }


  public void navigateIn(@NotNull Editor e) {
    navigateInEditor(this, e);
  }

  protected static void navigateInEditor(@NotNull OpenFileDescriptor descriptor, @NotNull Editor e) {
    final int offset = descriptor.getOffset();
    CaretModel caretModel = e.getCaretModel();
    boolean caretMoved = false;
    if (descriptor.getLine() >= 0) {
      LogicalPosition pos = new LogicalPosition(descriptor.getLine(), Math.max(descriptor.getColumn(), 0));
      if (offset < 0 || offset == e.logicalPositionToOffset(pos)) {
        caretModel.removeSecondaryCarets();
        caretModel.moveToLogicalPosition(pos);
        caretMoved = true;
      }
    }
    if (!caretMoved && offset >= 0) {
      caretModel.removeSecondaryCarets();
      caretModel.moveToOffset(Math.min(offset, e.getDocument().getTextLength()));
      caretMoved = true;
    }

    if (caretMoved) {
      e.getSelectionModel().removeSelection();
      FileEditorManager.getInstance(descriptor.getProject()).runWhenLoaded(e, () -> {
        descriptor.scrollToCaret(e);
        unfoldCurrentLine(e);
      });
    }
  }

  protected static void unfoldCurrentLine(@NotNull final Editor editor) {
    final FoldRegion[] allRegions = editor.getFoldingModel().getAllFoldRegions();
    final TextRange range = getRangeToUnfoldOnNavigation(editor);
    editor.getFoldingModel().runBatchFoldingOperation(() -> {
      for (FoldRegion region : allRegions) {
        if (!region.isExpanded() && range.intersects(TextRange.create(region))) {
          region.setExpanded(true);
        }
      }
    });
  }

  @NotNull
  public static TextRange getRangeToUnfoldOnNavigation(@NotNull Editor editor) {
    final int offset = editor.getCaretModel().getOffset();
    int line = editor.getDocument().getLineNumber(offset);
    int start = editor.getDocument().getLineStartOffset(line);
    int end = editor.getDocument().getLineEndOffset(line);
    return new TextRange(start, end);
  }

  private void scrollToCaret(@NotNull Editor e) {
    e.getScrollingModel().scrollToCaret(myScrollType);
  }

  @Override
  public boolean canNavigate() {
    return FileNavigator.getInstance().canNavigate(myFile);
  }

  @Override
  public boolean canNavigateToSource() {
    return canNavigate();
  }

  @NotNull
  public Project getProject() {
    return myProject;
  }

  public OpenFileDescriptor setUseCurrentWindow(boolean search) {
    myUseCurrentWindow = search;
    return this;
  }

  @Override
  public boolean isUseCurrentWindow() {
    return myUseCurrentWindow;
  }

  public OpenFileDescriptor setUsePreviewTab(boolean usePreviewTab) {
    myUsePreviewTab = usePreviewTab;
    return this;
  }

  @Override
  public boolean isUsePreviewTab() {
    return myUsePreviewTab;
  }

  public void setScrollType(@NotNull ScrollType scrollType) {
    myScrollType = scrollType;
  }

  public void dispose() {
    if (myRangeMarkerSupplier != null) {
      myRangeMarkerSupplier.dispose();
    }
  }

  @Override
  public int compareTo(@NotNull OpenFileDescriptor o) {
    int i = myProject.getName().compareTo(o.myProject.getName());
    if (i != 0) return i;
    i = myFile.getName().compareTo(o.myFile.getName());
    if (i != 0) return i;
    RangeMarker rangeMarker = getOrComputeRangeMarker();
    RangeMarker otherRangeMarker = o.getOrComputeRangeMarker();
    if (rangeMarker != null) {
      if (otherRangeMarker == null) return 1;
      i = rangeMarker.getStartOffset() - otherRangeMarker.getStartOffset();
      if (i != 0) return i;
      return rangeMarker.getEndOffset() - otherRangeMarker.getEndOffset();
    }
    return otherRangeMarker == null ? 0 : -1;
  }

  private static class RangeMarkerSupplier implements Supplier<RangeMarker> {
    private volatile boolean disposed;
    private final @NotNull NotNullLazyValue<@NotNull RangeMarker> value;

    private RangeMarkerSupplier(@NotNull NotNullLazyValue<@NotNull RangeMarker> value) {
      this.value = value;
    }

    @Override
    public RangeMarker get() {
      return disposed ? null : value.getValue();
    }

    private void dispose() {
      disposed = true;
      if (value.isComputed()) {
        value.getValue().dispose();
      }
    }
  }
}
