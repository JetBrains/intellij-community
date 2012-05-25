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
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.highlighter.EditorHighlighter;
import com.intellij.openapi.editor.highlighter.EditorHighlighterFactory;
import com.intellij.openapi.editor.markup.HighlighterLayer;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.components.JBScrollPane;
import org.jetbrains.annotations.Nullable;

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

  private JBScrollPane myDetailScrollPanel;
  private JPanel myDetailPanelWrapper;
  private JLabel myNothingToShow = new JLabel("Nothing to show");
  private JLabel myNothingToShowInEditor = new JLabel("Nothing to show");
  private RangeHighlighter myHighlighter;

  public void setScheme(EditorColorsScheme scheme) {
    myScheme = scheme;
  }

  private EditorColorsScheme myScheme = EditorColorsManager.getInstance().getGlobalScheme();

  public DetailViewImpl(Project project) {
    super(new BorderLayout());
    myProject = project;
    setPreferredSize(new Dimension(700, 400));
    myNothingToShow.setHorizontalAlignment(JLabel.CENTER);
    myNothingToShowInEditor.setHorizontalAlignment(JLabel.CENTER);
  }

  @Override
  public void updateWithItem(ItemWrapper wrapper) {
    if (myWrapper != wrapper) {
      myWrapper = wrapper;
      if (wrapper != null) {
        wrapper.updateDetailView(this);
      }
      else {
        clearEditor();
        repaint();
      }

      revalidate();
    }
  }

  @Override
  public void clearEditor() {
    if (getEditor() != null) {
      clearHightlighting();
      remove(getEditor().getComponent());
      EditorFactory.getInstance().releaseEditor(getEditor());
      setEditor(null);
      add(myNothingToShowInEditor, BorderLayout.CENTER);
      repaint();
    }
  }

  @Override
  public void removeNotify() {
    super.removeNotify();
    clearEditor();
  }

  @Override
  public Editor getEditor() {
    return myEditor;
  }

  public void setEditor(Editor editor) {
    myEditor = editor;
  }

  @Override
  public void navigateInPreviewEditor(VirtualFile file, LogicalPosition positionToNavigate, @Nullable TextAttributes lineAttributes) {
    Document document = FileDocumentManager.getInstance().getDocument(file);
    Project project = myProject;

    if (document != null) {
      if (getEditor() == null || getEditor().getDocument() != document) {
        clearEditor();
        remove(myNothingToShowInEditor);
        setEditor(EditorFactory.getInstance().createViewer(document, project));

        final EditorColorsScheme scheme = getScheme();

        EditorHighlighter highlighter = EditorHighlighterFactory.getInstance().createEditorHighlighter(file, scheme, project);

        ((EditorEx)getEditor()).setFile(file);
        ((EditorEx)getEditor()).setHighlighter(highlighter);

        getEditor().getSettings().setAnimatedScrolling(false);
        getEditor().getSettings().setRefrainFromScrolling(false);
        getEditor().getSettings().setLineNumbersShown(true);
        getEditor().getSettings().setFoldingOutlineShown(false);

        add(getEditor().getComponent(), BorderLayout.CENTER);
      }

      getEditor().getCaretModel().moveToLogicalPosition(positionToNavigate);
      validate();
      getEditor().getScrollingModel().scrollToCaret(ScrollType.CENTER);

      clearHightlighting();
      if (lineAttributes != null){
        myHighlighter = getEditor().getMarkupModel().addLineHighlighter(positionToNavigate.line, HighlighterLayer.SELECTION - 1,
                                                                        lineAttributes);

      }
    }
    else {
      clearEditor();

      JLabel label = new JLabel("Navigate to selected " + (file.isDirectory() ? "directory " : "file ") + "in Project View");
      label.setHorizontalAlignment(JLabel.CENTER);
      add(label);
    }
  }

  public EditorColorsScheme getScheme() {
    return myScheme;
  }



  private void clearHightlighting() {
    if (myHighlighter != null) {
      getEditor().getMarkupModel().removeHighlighter(myHighlighter);
      myHighlighter = null;
    }
  }

  @Override
  public JPanel getDetailPanel() {
    return myDetailPanel;
  }

  @Override
  public void setDetailPanel(@Nullable final JPanel panel) {
    if (panel == myDetailPanel) return;

    if (panel != null) {
      if (myDetailScrollPanel == null) {
        myDetailPanelWrapper = new JPanel(new GridLayout(1, 1));
        myDetailPanelWrapper.setBorder(BorderFactory.createEmptyBorder(5, 30, 5, 30));
        myDetailPanelWrapper.add(panel);

        myDetailScrollPanel =
          new JBScrollPane(myDetailPanelWrapper, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED, ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        myDetailScrollPanel.setPreferredSize(myDetailPanelWrapper.getPreferredSize());
        myDetailScrollPanel.setBorder(null);
        add(myDetailScrollPanel, BorderLayout.SOUTH);
      } else {
        myDetailPanelWrapper.removeAll();
        myDetailPanelWrapper.add(panel);
      }
    }
    else {
      myDetailPanelWrapper.removeAll();
      myDetailPanelWrapper.add(myNothingToShow);
    }
    myDetailPanel = panel;
  }
}
