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
package com.intellij.openapi.fileEditor;

import com.intellij.ide.*;
import com.intellij.ide.FileEditorProvider;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.DataKey;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.INativeFileType;
import com.intellij.openapi.fileTypes.NativeFileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.pom.Navigatable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class OpenFileDescriptor implements Navigatable {
  /**
   * Tells descriptor to navigate in specific editor rather than file editor
   * in main IDEA window.
   * For example if you want to navigate in editor embedded into modal dialog,
   * you should provide this data.
   */
  public static final DataKey<Editor> NAVIGATE_IN_EDITOR = DataKey.create("NAVIGATE_IN_EDITOR");

  @NotNull
  private final VirtualFile myFile;
  private final int         myOffset;
  private final int         myLine;
  private final int         myColumn;
  private final RangeMarker myRangeMarker;
  private final Project     myProject;

  private boolean myUseCurrentWindow = false;

  public OpenFileDescriptor(Project project, @NotNull VirtualFile file, int offset) {
    this(project, file, -1, -1, offset, false);
  }

  public OpenFileDescriptor(Project project, @NotNull VirtualFile file, int line, int col) {
    this(project, file, line, col, -1, false);
  }

  public OpenFileDescriptor(Project project, @NotNull VirtualFile file, int line, int col, boolean persistent) {
    this(project, file, line, col, -1, persistent);
  }

  public OpenFileDescriptor(Project project, @NotNull VirtualFile file) {
    this(project, file, -1, -1, -1, false);
  }

  private OpenFileDescriptor(final Project project, @NotNull final VirtualFile file, int line, int col, int offset, boolean persistent) {
    myProject = project;

    myFile = file;
    myLine = line;
    myColumn = col;
    myOffset = offset;
    if (offset >= 0) {
      myRangeMarker = LazyRangeMarkerFactory.getInstance(project).createRangeMarker(file, offset);
    }
    else if (line >= 0 ){
      myRangeMarker = LazyRangeMarkerFactory.getInstance(project).createRangeMarker(file, line, Math.max(0, col), persistent);
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
    return myLine;
  }

  public int getColumn() {
    return myColumn;
  }

  public void navigate(boolean requestFocus) {
    if (!canNavigate()) {
      throw new IllegalStateException("Navigation is not possible with null project");
    }

    if (!myFile.isDirectory() && navigateInEditor(myProject, requestFocus)) return;

    navigateInProjectView();
  }

  private boolean navigateInEditor(Project project, boolean focusEditor) {
    FileType type = FileTypeManager.getInstance().getKnownFileTypeOrAssociate(myFile);
    if (type == null || !myFile.isValid()) return false;

    if (type instanceof INativeFileType) {
      return NativeFileType.openAssociatedApplication(myFile);
    }

    return navigateInRequestedEditor() || navigateInAnyFileEditor(project, focusEditor);

  }

  private boolean navigateInRequestedEditor() {
    DataContext ctx = DataManager.getInstance().getDataContext();
    Editor e = NAVIGATE_IN_EDITOR.getData(ctx);
    if (e == null) return false;
    if (FileDocumentManager.getInstance().getFile(e.getDocument()) != myFile) return false;
    
    navigateIn(e);
    return true;
  }

  private boolean navigateInAnyFileEditor(Project project, boolean focusEditor) {
    List<FileEditor> editors = FileEditorManager.getInstance(project).openEditor(this, focusEditor);
    return !editors.isEmpty();
  }

  private void navigateInProjectView() {
    SelectInContext context = new SelectInContext() {
      @NotNull
      public Project getProject() {
        return myProject;
      }

      @NotNull
      public VirtualFile getVirtualFile() {
        return myFile;
      }

      @Nullable
      public Object getSelectorInFile() {
        return null;
      }

      @Nullable
      public FileEditorProvider getFileEditorProvider() {
        return null;
      }
    };

    for (SelectInTarget target : SelectInManager.getInstance(myProject).getTargets()) {
      if (target.canSelect(context)) {
        target.selectIn(context, true);
        return;
      }
    }
  }

  public void navigateIn(Editor e) {
    if (getOffset() >= 0) {
      e.getCaretModel().moveToOffset(Math.min(getOffset(), e.getDocument().getTextLength()));
    }
    else if (getLine() != -1) {
      LogicalPosition pos = new LogicalPosition(getLine(), getColumn());
      e.getCaretModel().moveToLogicalPosition(pos);
    }
    else {
      return;
    }

    e.getSelectionModel().removeSelection();
    scrollToCaret(e);
  }

  private static void scrollToCaret(final Editor e) {
    final Project project = e.getProject();
    if (project != null) {
      IdeFocusManager.getInstance(project).doWhenFocusSettlesDown(new Runnable() {
        public void run() {
          e.getScrollingModel().scrollToCaret(ScrollType.CENTER);
        }
      });
    } else {
      e.getScrollingModel().scrollToCaret(ScrollType.CENTER);
    }
  }

  public boolean canNavigate() {
    return myProject != null;
  }

  public boolean canNavigateToSource() {
    return myProject != null;
  }

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
}
