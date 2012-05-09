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
package com.intellij.ui.popup.util;

import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.highlighter.EditorHighlighter;
import com.intellij.openapi.editor.highlighter.EditorHighlighterFactory;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;

import javax.swing.*;
import java.awt.*;

/**
* Created with IntelliJ IDEA.
* User: zajac
* Date: 5/6/12
* Time: 2:04 AM
* To change this template use File | Settings | File Templates.
*/
public class DetailViewImpl extends JPanel implements DetailView {
  private final Project myProject;
  private Editor myEditor;
  private ItemWrapper myWrapper;
  private JPanel myDetailPanel;

  public DetailViewImpl(Project project) {
    super(new BorderLayout());
    myProject = project;
    setPreferredSize(new Dimension(600, 400));
  }

  @Override
  public void updateWithItem(ItemWrapper wrapper) {
    if (myWrapper != wrapper) {
      myWrapper = wrapper;
      if (wrapper != null) {
        wrapper.updateDetailView(this);
      }
      else {
        cleanup();
        repaint();
      }

      revalidate();
    }
  }

  private void cleanup() {
    removeAll();
    if (getEditor() != null) {
      EditorFactory.getInstance().releaseEditor(getEditor());
      setEditor(null);
    }
  }

  @Override
  public void removeNotify() {
    super.removeNotify();
    cleanup();
  }

  @Override
  public Editor getEditor() {
    return myEditor;
  }

  public void setEditor(Editor editor) {
    myEditor = editor;
  }

  @Override
  public void navigateInPreviewEditor(VirtualFile file, LogicalPosition positionToNavigate) {
    Document document = FileDocumentManager.getInstance().getDocument(file);
    Project project = myProject;

    if (document != null) {
      if (getEditor() == null || getEditor().getDocument() != document) {
        cleanup();
        setEditor(EditorFactory.getInstance().createViewer(document, project));
        EditorHighlighter highlighter = EditorHighlighterFactory.getInstance()
          .createEditorHighlighter(file, EditorColorsManager.getInstance().getGlobalScheme(), project);
        ((EditorEx)getEditor()).setHighlighter(highlighter);
        ((EditorEx)getEditor()).setFile(file);

        getEditor().getSettings().setAnimatedScrolling(false);
        getEditor().getSettings().setRefrainFromScrolling(false);
        getEditor().getSettings().setLineNumbersShown(true);
        getEditor().getSettings().setFoldingOutlineShown(false);

        add(getEditor().getComponent(), BorderLayout.CENTER);
      }

      getEditor().getCaretModel().moveToLogicalPosition(positionToNavigate);
      validate();
      getEditor().getScrollingModel().scrollToCaret(ScrollType.CENTER);
    }
    else {
      cleanup();

      JLabel label = new JLabel("Navigate to selected " + (file.isDirectory() ? "directory " : "file ") + "in Project View");
      label.setHorizontalAlignment(JLabel.CENTER);
      add(label);
    }
  }

  @Override
  public JPanel getDetailPanel() {
    return myDetailPanel;
  }

  @Override
  public void setDetailPanel(JPanel panel) {
    myDetailPanel = panel;
    add(panel, BorderLayout.SOUTH);
  }
}
