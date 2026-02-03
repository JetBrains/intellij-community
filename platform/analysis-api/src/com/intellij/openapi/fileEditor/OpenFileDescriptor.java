// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileEditor;

import com.intellij.codeInsight.multiverse.CodeInsightContext;
import com.intellij.codeInsight.multiverse.CodeInsightContexts;
import com.intellij.codeInsight.multiverse.EditorContextManager;
import com.intellij.codeInsight.multiverse.SingleEditorContext;
import com.intellij.openapi.actionSystem.DataKey;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * Allows opening file in editor, optionally at specific line/column position.
 */
public class OpenFileDescriptor implements FileEditorNavigatable, Comparable<OpenFileDescriptor> {
  /**
   * Tells descriptor to navigate in specific editor rather than file editor in the main IDE window.
   * For example, if you want to navigate in an editor embedded into modal dialog, you should provide this data.
   */
  public static final DataKey<Editor> NAVIGATE_IN_EDITOR = DataKey.create("NAVIGATE_IN_EDITOR");

  private final Project myProject;
  private final VirtualFile myFile;
  private final int myLogicalLine;
  private final int myLogicalColumn;
  private final int myOffset;
  private final RangeMarker myRangeMarker;
  private final @NotNull CodeInsightContext myContext;

  private boolean myUseCurrentWindow;
  private boolean myUsePreviewTab;
  private ScrollType myScrollType = ScrollType.CENTER;

  @ApiStatus.Experimental
  public OpenFileDescriptor(@NotNull Project project, @NotNull VirtualFile file, @NotNull CodeInsightContext context, int offset) {
    this(project, file, context, -1, -1, offset, false);
  }

  public OpenFileDescriptor(@NotNull Project project, @NotNull VirtualFile file, int offset) {
    this(project, file, CodeInsightContexts.anyContext(), - 1, -1, offset, false);
  }

  public OpenFileDescriptor(@NotNull Project project, @NotNull VirtualFile file, int logicalLine, int logicalColumn) {
    this(project, file, CodeInsightContexts.anyContext(), logicalLine, logicalColumn, -1, false);
  }

  public OpenFileDescriptor(@NotNull Project project, @NotNull VirtualFile file, int logicalLine, int logicalColumn, boolean persistent) {
    this(project, file, CodeInsightContexts.anyContext(), logicalLine, logicalColumn, -1, persistent);
  }

  public OpenFileDescriptor(@NotNull Project project, @NotNull VirtualFile file) {
    this(project, file, CodeInsightContexts.anyContext(), -1, -1, -1, false);
  }

  private OpenFileDescriptor(
    @NotNull Project project,
    @NotNull VirtualFile file,
    @NotNull CodeInsightContext context,
    int logicalLine,
    int logicalColumn,
    int offset,
    boolean persistent
  ) {
    myProject = project;
    myFile = file;
    myContext = context;
    myLogicalLine = logicalLine;
    myLogicalColumn = logicalColumn;
    myOffset = offset;
    if (offset >= 0) {
      myRangeMarker = LazyRangeMarkerFactory.getInstance(project).createRangeMarker(file, offset);
    }
    else if (logicalLine >= 0 ){
      myRangeMarker = LazyRangeMarkerFactory.getInstance(project).createRangeMarker(file, logicalLine, Math.max(0, logicalColumn), persistent);
    }
    else {
      myRangeMarker = null;
    }
  }

  @Override
  public @NotNull VirtualFile getFile() {
    return myFile;
  }

  @ApiStatus.Internal
  public @NotNull CodeInsightContext getContext() {
    return myContext;
  }

  public RangeMarker getRangeMarker() {
    return myRangeMarker;
  }

  public int getOffset() {
    return myRangeMarker != null && myRangeMarker.isValid() ? myRangeMarker.getStartOffset() : myOffset;
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

  @ApiStatus.Internal
  public static void navigateInEditor(@NotNull OpenFileDescriptor descriptor, @NotNull Editor e) {
    int offset = descriptor.getOffset();
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

    if (CodeInsightContexts.isSharedSourceSupportEnabled(descriptor.getProject())) {
      CodeInsightContext context = descriptor.getContext();
      if (context != CodeInsightContexts.anyContext()) {
        EditorContextManager.getInstance(descriptor.getProject()).setEditorContext(e, new SingleEditorContext(context));
      }
    }
  }

  @ApiStatus.Internal
  public static void unfoldCurrentLine(@NotNull Editor editor) {
    FoldRegion[] allRegions = editor.getFoldingModel().getAllFoldRegions();
    TextRange range = getRangeToUnfoldOnNavigation(editor);
    editor.getFoldingModel().runBatchFoldingOperation(() -> {
      for (FoldRegion region : allRegions) {
        if (!region.isExpanded() && range.intersects(region)) {
          region.setExpanded(true);
        }
      }
    });
  }

  public static @NotNull TextRange getRangeToUnfoldOnNavigation(@NotNull Editor editor) {
    int offset = editor.getCaretModel().getOffset();
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
    return FileNavigator.getInstance().canNavigate(this);
  }

  @Override
  public boolean canNavigateToSource() {
    return FileNavigator.getInstance().canNavigateToSource(this);
  }

  public @NotNull Project getProject() {
    return myProject;
  }

  public @NotNull OpenFileDescriptor setUseCurrentWindow(boolean search) {
    myUseCurrentWindow = search;
    return this;
  }

  @Override
  public boolean isUseCurrentWindow() {
    return myUseCurrentWindow;
  }

  public @NotNull OpenFileDescriptor setUsePreviewTab(boolean usePreviewTab) {
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
    if (myRangeMarker != null) {
      myRangeMarker.dispose();
    }
  }

  @Override
  public int compareTo(@NotNull OpenFileDescriptor o) {
    int i = myProject.getName().compareTo(o.myProject.getName());
    if (i != 0) return i;
    i = myFile.getName().compareTo(o.myFile.getName());
    if (i != 0) return i;
    if (myRangeMarker != null) {
      if (o.myRangeMarker == null) return 1;
      i = myRangeMarker.getStartOffset() - o.myRangeMarker.getStartOffset();
      if (i != 0) return i;
      return myRangeMarker.getEndOffset() - o.myRangeMarker.getEndOffset();
    }
    return o.myRangeMarker == null ? 0 : -1;
  }
}
