/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.ide.diff;

import com.intellij.openapi.diff.DiffManager;
import com.intellij.openapi.diff.DiffRequest;
import com.intellij.openapi.diff.SimpleDiffRequest;
import com.intellij.openapi.diff.impl.DiffPanelImpl;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;

/**
 * @author Konstantin Bulenkov
 */
public class VirtualFileDiffElement extends DiffElement<VirtualFile> {
  private final VirtualFile myFile;
  private Editor myEditor;
  private DiffPanelImpl myDiffPanel;

  public VirtualFileDiffElement(@NotNull VirtualFile file) {
    myFile = file;
  }

  @Override
  public String getPath() {
    return myFile.getPath();
  }

  @Override
  public String getName() {
    return myFile.getName();
  }

  @Override
  public long getSize() {
    return myFile.getLength();
  }

  @Override
  public long getModificationStamp() {
    return myFile.getModificationStamp();
  }

  @Override
  public FileType getFileType() {
    return myFile.getFileType();
  }

  @Override
  public boolean isContainer() {
    return myFile.isDirectory();
  }

  @Override
  public DiffElement getParent() {
    final VirtualFile parent = myFile.getParent();
    return parent == null ? null : new VirtualFileDiffElement(parent);
  }

  @Override
  public VirtualFileDiffElement[] getChildren() {
    final VirtualFile[] children = myFile.getChildren();
    final VirtualFileDiffElement[] elements = new VirtualFileDiffElement[children.length];
    for (int i = 0; i < children.length; i++) {
      elements[i] = new VirtualFileDiffElement(children[i]);
    }
    return elements;
  }

  @Override
  public VirtualFileDiffElement findFileByRelativePath(String path) {
    final VirtualFile file = myFile.findFileByRelativePath(path);
    return file == null ? null : new VirtualFileDiffElement(file);
  }

  @Override
  public byte[] getContent() throws IOException {
    return myFile.contentsToByteArray();
  }

  @Override
  public boolean canCompareWith(DiffElement element) {
    return element instanceof VirtualFileDiffElement;
  }

  @Override
  public JComponent getViewComponent(Project project) {
    disposeViewComponent();
    final Document document = FileDocumentManager.getInstance().getDocument(myFile);
    if (document != null) {
      myEditor = EditorFactory.getInstance().createEditor(document, project, myFile, true);
      myEditor.getSettings().setFoldingOutlineShown(false);
      return myEditor.getComponent();
    }
    return null;
  }

  @Override
  public JComponent getDiffComponent(DiffElement element, Project project, Window parentWindow) {
    disposeDiffComponent();
    if (element instanceof VirtualFileDiffElement) {
      final VirtualFileDiffElement diffElement = (VirtualFileDiffElement)element;
      final DiffRequest request = SimpleDiffRequest.compareFiles(myFile, diffElement.getValue(), project);
      myDiffPanel = (DiffPanelImpl)DiffManager.getInstance().createDiffPanel(parentWindow, project);
      myDiffPanel.setIsRequestFocus(false);
      myDiffPanel.setDiffRequest(request);
      return myDiffPanel.getComponent();
    }

    return null;
  }

  @Override
  public VirtualFile getValue() {
    return myFile;
  }

  @Override
  public void disposeViewComponent() {
    if (myEditor != null) {
      EditorFactory.getInstance().releaseEditor(myEditor);
      myEditor = null;
    }
  }

  @Override
  public void disposeDiffComponent() {
    if (myDiffPanel != null) {
      Disposer.dispose(myDiffPanel);
      myDiffPanel = null;
    }
  }
}
