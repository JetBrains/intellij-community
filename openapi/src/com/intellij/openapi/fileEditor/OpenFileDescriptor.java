/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.fileEditor;

import com.intellij.ide.FileEditorProvider;
import com.intellij.ide.SelectInContext;
import com.intellij.ide.SelectInManager;
import com.intellij.ide.SelectInTarget;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.Navigatable;
import com.intellij.psi.PsiManager;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class OpenFileDescriptor implements Navigatable {
  private final VirtualFile myFile;
  private final int myOffset;
  private final int myLine;
  private final int myColumn;
  private final RangeMarker myRangeMarker;

  private final Project myProject;

  /**
   * @throws java.lang.IllegalArgumentException if <code>file</code> is <code>null</code>
   */
  public OpenFileDescriptor(Project project, VirtualFile file, int offset) {
    this(project, file, -1, -1, offset);
  }

  /**
   * @throws java.lang.IllegalArgumentException if <code>file</code> is <code>null</code>
   */
  public OpenFileDescriptor(Project project, VirtualFile file, int line, int col) {
    this(project, file, line, col, -1);
  }

  /**
   * @throws java.lang.IllegalArgumentException if <code>file</code> is <code>null</code>
   */
  public OpenFileDescriptor(Project project, VirtualFile file) {
    this(project, file, -1, -1, -1);
  }

  private OpenFileDescriptor(Project project, VirtualFile file, int line, int col, int offset) {
    myProject = project;
    if (file == null){
      throw new IllegalArgumentException("file cannot be null");
    }
    myFile = file;
    myLine = line;
    myColumn = col;
    myOffset = offset;
    if (offset >= 0) {
      final Document document = FileDocumentManager.getInstance().getDocument(file);
      myRangeMarker = (document != null && offset <= document.getTextLength())? document.createRangeMarker(offset, offset) : null;
    }
    else {
      myRangeMarker = null;
    }
  }


  public VirtualFile getFile() {
    return myFile;
  }

  public int getOffset() {
    return (myRangeMarker != null && myRangeMarker.isValid())? myRangeMarker.getStartOffset() : myOffset;
  }


  public int getLine() {
    return myLine;
  }

  public int getColumn() {
    return myColumn;
  }

  public void navigate(boolean requestFocus) {
    FileEditor fileEditor = openFileAskingType(myProject, requestFocus);
    if (fileEditor == null) {
      final SelectInTarget projectSelector = SelectInManager.getInstance(myProject).getTarget("Project");
      if (projectSelector != null) {
        projectSelector.selectIn(new SelectInContext() {
          public Project getProject() {
            return myProject;
          }

          public VirtualFile getVirtualFile() {
            return myFile;
          }

          @Nullable
          public Object getSelectorInFile() {
            return PsiManager.getInstance(myProject).findFile(myFile);
          }

          @Nullable
          public FileEditorProvider getFileEditorProvider() {
            return null;
          }
        }, true);
      }

      return;
    }

    if (fileEditor instanceof TextEditor) {
      Editor editor = ((TextEditor)fileEditor).getEditor();
      Document document = editor.getDocument();
      LogicalPosition position;
      int offset = getOffset();
      if (offset < 0) {
        position = new LogicalPosition(Math.min(document.getLineCount() - 1, getLine()), getColumn());
      }
      else {
        position = editor.offsetToLogicalPosition(Math.min(document.getTextLength(), offset));
      }
      editor.getCaretModel().moveToLogicalPosition(position);
      editor.getScrollingModel().scrollToCaret(ScrollType.CENTER);
    }
  }

  @Nullable
  private FileEditor openFileAskingType(Project project, boolean focusEditor) {
    FileType type = FileTypeManager.getInstance().getKnownFileTypeOrAssociate(myFile);
    if (type == null || myFile == null || !myFile.isValid()) return null;

    final List<FileEditor> fileEditors = FileEditorManager.getInstance(project).openEditor(this, focusEditor);
    if (fileEditors.size() == 0) return null;
    return fileEditors.get(0);
  }

  public boolean canNavigate() {
    return true;
  }

  public boolean canNavigateToSource() {
    return true;
  }

  public Project getProject() {
    return myProject;
  }
}