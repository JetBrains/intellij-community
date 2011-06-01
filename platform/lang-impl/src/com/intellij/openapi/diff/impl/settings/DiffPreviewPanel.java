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

package com.intellij.openapi.diff.impl.settings;

import com.intellij.application.options.colors.ColorAndFontSettingsListener;
import com.intellij.application.options.colors.PreviewPanel;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.diff.DiffBundle;
import com.intellij.openapi.diff.DiffContent;
import com.intellij.openapi.diff.DiffRequest;
import com.intellij.openapi.diff.impl.incrementalMerge.Change;
import com.intellij.openapi.diff.impl.incrementalMerge.MergeList;
import com.intellij.openapi.diff.impl.incrementalMerge.MergeSearchHelper;
import com.intellij.openapi.diff.impl.incrementalMerge.ui.EditorPlace;
import com.intellij.openapi.diff.impl.incrementalMerge.ui.MergePanel2;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.event.*;
import com.intellij.openapi.editor.ex.util.EditorUtil;
import com.intellij.openapi.project.Project;
import com.intellij.util.EventDispatcher;
import com.intellij.util.diff.FilesTooBigForDiffException;

import javax.swing.*;
import java.awt.*;

public class DiffPreviewPanel implements PreviewPanel {
  private final MergePanel2.AsComponent myMergePanelComponent;
  private final JPanel myPanel = new JPanel(new BorderLayout());

  private final EventDispatcher<ColorAndFontSettingsListener> myDispatcher = EventDispatcher.create(ColorAndFontSettingsListener.class);

  public DiffPreviewPanel(Disposable parent) {
    myMergePanelComponent = new MergePanel2.AsComponent(parent);
    myPanel.add(myMergePanelComponent, BorderLayout.CENTER);
    myMergePanelComponent.setToolbarEnabled(false);
    MergePanel2 mergePanel = getMergePanel();
    mergePanel.setEditorProperty(MergePanel2.LINE_NUMBERS, Boolean.FALSE);
    mergePanel.setEditorProperty(MergePanel2.LINE_MARKERS_AREA, Boolean.FALSE);
    mergePanel.setEditorProperty(MergePanel2.ADDITIONAL_LINES, 1);
    mergePanel.setEditorProperty(MergePanel2.ADDITIONAL_COLUMNS, 1);
    mergePanel.setScrollToFirstDiff(false);

    for (int i = 0; i < MergePanel2.EDITORS_COUNT; i++) {
      final EditorMouseListener motionListener = new EditorMouseListener(i);
      final EditorClickListener clickListener = new EditorClickListener(i);
      mergePanel.getEditorPlace(i).addListener(new EditorPlace.EditorListener() {
        public void onEditorCreated(EditorPlace place) {
          Editor editor = place.getEditor();
          editor.addEditorMouseMotionListener(motionListener);
          editor.addEditorMouseListener(clickListener);
          editor.getCaretModel().addCaretListener(clickListener);
        }

        public void onEditorReleased(Editor releasedEditor) {
          releasedEditor.removeEditorMouseMotionListener(motionListener);
          releasedEditor.removeEditorMouseListener(clickListener);
        }
      });
      Editor editor = mergePanel.getEditor(i);
      if (editor != null) {
        editor.addEditorMouseMotionListener(motionListener);
        editor.addEditorMouseListener(clickListener);
      }
    }
  }

  public Component getPanel() {
    return myPanel;
  }

  public void updateView() {
    MergeList mergeList = getMergePanel().getMergeList();
    if (mergeList != null) mergeList.updateMarkup();
    myMergePanelComponent.repaint();

  }

  public void setMergeRequest(Project project) throws FilesTooBigForDiffException {
    getMergePanel().setDiffRequest(new SampleMerge(project));
  }

  private MergePanel2 getMergePanel() {
    return myMergePanelComponent.getMergePanel();
  }

  public void setColorScheme(final EditorColorsScheme highlighterSettings) {
    getMergePanel().setColorScheme(highlighterSettings);
    getMergePanel().setEditorProperty(MergePanel2.HIGHLIGHTER_SETTINGS, highlighterSettings);
  }

  private class EditorMouseListener extends EditorMouseMotionAdapter {
    private final int myIndex;

    private EditorMouseListener(int index) {
      myIndex = index;
    }

    public void mouseMoved(EditorMouseEvent e) {
      MergePanel2 mergePanel = getMergePanel();
      Editor editor = mergePanel.getEditor(myIndex);
      if (MergeSearchHelper.findChangeAt(e, mergePanel, myIndex) != null) EditorUtil.setHandCursor(editor);
    }
  }

  public static class SampleMerge extends DiffRequest {
    public SampleMerge(Project project) {
      super(project);
    }

    public DiffContent[] getContents() {
      return DiffPreviewProvider.getContents();
    }

    public String[] getContentTitles() { return new String[]{"", "", ""}; }
    public String getWindowTitle() { return DiffBundle.message("merge.color.options.dialog.title"); }
  }

  public void addListener(final ColorAndFontSettingsListener listener) {
    myDispatcher.addListener(listener);
  }

  private class EditorClickListener extends EditorMouseAdapter implements CaretListener {
    private final int myIndex;

    private EditorClickListener(int i) {
      myIndex = i;
    }

    public void mouseClicked(EditorMouseEvent e) {
      select(MergeSearchHelper.findChangeAt(e, getMergePanel(), myIndex));
    }

    private void select(Change change) {
      if (change == null) return;
      myDispatcher.getMulticaster().selectionInPreviewChanged(change.getType().getTextDiffType().getDisplayName());
     }

    public void caretPositionChanged(CaretEvent e) {
      select(MergeSearchHelper.findChangeAt(e, getMergePanel(), myIndex));
    }
  }

  public void blinkSelectedHighlightType(final Object selected) {
    
  }

  public void disposeUIResources() {
  }
}
