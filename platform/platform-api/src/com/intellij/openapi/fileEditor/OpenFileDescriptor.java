// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.fileEditor;

import com.intellij.ide.*;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.DataKey;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.INativeFileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.pom.Navigatable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.List;

/**
 * Allows opening file in editor, optionally at specific line/column position.
 */
public class OpenFileDescriptor implements Navigatable, Comparable<OpenFileDescriptor> {
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
  private final RangeMarker myRangeMarker;

  private boolean myUseCurrentWindow;
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
      myRangeMarker = LazyRangeMarkerFactory.getInstance(project).createRangeMarker(file, offset);
    }
    else if (logicalLine >= 0 ){
      myRangeMarker = LazyRangeMarkerFactory.getInstance(project).createRangeMarker(file, logicalLine, Math.max(0, logicalColumn), persistent);
    }
    else {
      myRangeMarker = null;
    }
  }

  @NotNull
  public VirtualFile getFile() {
    return myFile;
  }

  @Nullable
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
    if (!canNavigate()) {
      throw new IllegalStateException("target not valid");
    }

    if (!myFile.isDirectory()) {
      if (navigateInEditorOrNativeApp(myProject, requestFocus)) return;
    }

    if (navigateInProjectView(requestFocus)) return;

    String message = IdeBundle.message("error.files.of.this.type.cannot.be.opened", ApplicationNamesInfo.getInstance().getProductName());
    Messages.showErrorDialog(myProject, message, IdeBundle.message("title.cannot.open.file"));
  }

  private boolean navigateInEditorOrNativeApp(@NotNull Project project, boolean requestFocus) {
    FileType type = FileTypeManager.getInstance().getKnownFileTypeOrAssociate(myFile,project);
    if (type == null || !myFile.isValid()) return false;

    if (type instanceof INativeFileType) {
      return ((INativeFileType) type).openFileInAssociatedApplication(project, myFile);
    }

    return navigateInEditor(project, requestFocus);
  }

  public boolean navigateInEditor(@NotNull Project project, boolean requestFocus) {
    return navigateInRequestedEditor() || navigateInAnyFileEditor(project, requestFocus);
  }

  private boolean navigateInRequestedEditor() {
    @SuppressWarnings("deprecation") DataContext ctx = DataManager.getInstance().getDataContext();
    Editor e = NAVIGATE_IN_EDITOR.getData(ctx);
    if (e == null) return false;
    if (e.isDisposed()) {
      Logger.getInstance(OpenFileDescriptor.class).error("Disposed editor returned for NAVIGATE_IN_EDITOR from " + ctx);
      return false;
    }
    if (!Comparing.equal(FileDocumentManager.getInstance().getFile(e.getDocument()), myFile)) return false;

    navigateIn(e);
    return true;
  }

  protected boolean navigateInAnyFileEditor(@NotNull Project project, boolean focusEditor) {
    FileEditorManager fileEditorManager = FileEditorManager.getInstance(project);
    List<FileEditor> editors = fileEditorManager.openEditor(this, focusEditor);
    for (FileEditor editor : editors) {
      if (editor instanceof TextEditor) {
        Editor e = ((TextEditor)editor).getEditor();
        fileEditorManager.runWhenLoaded(e, () -> {
          unfoldCurrentLine(e);
          if (focusEditor) {
            IdeFocusManager.getInstance(project).requestFocus(e.getContentComponent(), true);
            Window ancestor = SwingUtilities.getWindowAncestor(e.getContentComponent());
            if (ancestor != null) {
              ancestor.toFront();
            }
          }
        });
      }
    }
    return !editors.isEmpty();
  }

  private boolean navigateInProjectView(boolean requestFocus) {
    SelectInContext context = new FileSelectInContext(myProject, myFile, null);
    for (SelectInTarget target : SelectInManager.getInstance(myProject).getTargetList()) {
      if (context.selectIn(target, requestFocus)) {
        return true;
      }
    }
    return false;
  }

  public void navigateIn(@NotNull Editor e) {
    final int offset = getOffset();
    CaretModel caretModel = e.getCaretModel();
    boolean caretMoved = false;
    if (myLogicalLine >= 0) {
      LogicalPosition pos = new LogicalPosition(myLogicalLine, Math.max(myLogicalColumn, 0));
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
      FileEditorManager.getInstance(myProject).runWhenLoaded(e, () -> {
        scrollToCaret(e);
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
    return myFile.isValid();
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

  public boolean isUseCurrentWindow() {
    return myUseCurrentWindow;
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