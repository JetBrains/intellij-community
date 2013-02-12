/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.ide.favoritesTreeView;

import com.intellij.icons.AllIcons;
import com.intellij.ide.projectView.PresentationData;
import com.intellij.ide.projectView.ViewSettings;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import java.io.File;

/**
 * Created with IntelliJ IDEA.
 * User: Irina.Chernushina
 * Date: 6/8/12
 * Time: 12:23 PM
 */
public class FileGroupingProjectNode extends ProjectViewNodeWithChildrenList<File> {
  private VirtualFile myVirtualFile;

  public FileGroupingProjectNode(Project project, File file, ViewSettings viewSettings) {
    super(project, file, viewSettings);
    final LocalFileSystem lfs = LocalFileSystem.getInstance();
    myVirtualFile = lfs.findFileByIoFile(file);
    if (myVirtualFile == null) {
      myVirtualFile = lfs.refreshAndFindFileByIoFile(file);
    }
  }

  @Override
  public boolean contains(@NotNull VirtualFile file) {
    return file.equals(myVirtualFile);
  }

  @Override
  protected void update(PresentationData presentation) {
    if (myVirtualFile != null && myVirtualFile.isDirectory()) {
      presentation.setIcon(AllIcons.Nodes.TreeClosed);
    }
    else if (myVirtualFile != null) {
      presentation.setIcon(myVirtualFile.getFileType().getIcon());
    }
    else {
      presentation.setIcon(AllIcons.FileTypes.Unknown);
    }
    presentation.setPresentableText(getValue().getName());
  }

  @Override
  public VirtualFile getVirtualFile() {
    return myVirtualFile;
  }

  @Override
  public void navigate(boolean requestFocus) {
    if (myVirtualFile != null) {
      new OpenFileDescriptor(myProject, myVirtualFile).navigate(requestFocus);
    }
  }

  // todo possibly we need file
  @Override
  public boolean canNavigate() {
    return myVirtualFile != null && myVirtualFile.isValid();
  }

  @Override
  public boolean canNavigateToSource() {
    return myVirtualFile != null && myVirtualFile.isValid();
  }
}
