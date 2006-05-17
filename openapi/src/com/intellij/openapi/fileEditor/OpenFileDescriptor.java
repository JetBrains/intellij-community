/*
 * Copyright 2000-2005 JetBrains s.r.o.
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

import com.intellij.ide.FileEditorProvider;
import com.intellij.ide.SelectInContext;
import com.intellij.ide.SelectInManager;
import com.intellij.ide.SelectInTarget;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.Navigatable;
import com.intellij.psi.PsiManager;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class OpenFileDescriptor implements Navigatable {
  @NotNull private final VirtualFile myFile;
  private final int myOffset;
  private final int myLine;
  private final int myColumn;
  private final RangeMarker myRangeMarker;

  private final Project myProject;

  public OpenFileDescriptor(Project project, @NotNull VirtualFile file, int offset) {
    this(project, file, -1, -1, offset);
  }

  public OpenFileDescriptor(Project project, @NotNull VirtualFile file, int line, int col) {
    this(project, file, line, col, -1);
  }

  public OpenFileDescriptor(Project project, @NotNull VirtualFile file) {
    this(project, file, -1, -1, -1);
  }

  private OpenFileDescriptor(Project project, VirtualFile file, int line, int col, int offset) {
    myProject = project;

    myFile = file;
    myLine = line;
    myColumn = col;
    myOffset = offset;
    if (offset >= 0) {
      final Document document = FileDocumentManager.getInstance().getDocument(file);
      myRangeMarker = document != null && offset <= document.getTextLength() ? document.createRangeMarker(offset, offset) : null;
    }
    else {
      myRangeMarker = null;
    }
  }


  public VirtualFile getFile() {
    return myFile;
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
    if (myProject == null) {
      throw new IllegalStateException("Navigation is not possible with null project");
    }

    FileEditor fileEditor = openFileAskingType(myProject, requestFocus);
    if (fileEditor == null) {
      final SelectInTarget projectSelector = SelectInManager.getInstance(myProject).getTarget(SelectInManager.PROJECT);
      if (projectSelector != null) {
        projectSelector.selectIn(new SelectInContext() {
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
            return myFile.isValid() ? PsiManager.getInstance(myProject).findFile(myFile) : null;
          }

          @Nullable
          public FileEditorProvider getFileEditorProvider() {
            return null;
          }
        }, true);
      }
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
    return myProject != null;
  }

  public boolean canNavigateToSource() {
    return myProject != null;
  }

  public Project getProject() {
    return myProject;
  }
}