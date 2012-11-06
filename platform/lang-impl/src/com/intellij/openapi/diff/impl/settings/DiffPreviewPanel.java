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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

/**
 * The panel from the Settings, that allows to see changes to diff/merge coloring scheme right away.
 */
public class DiffPreviewPanel implements PreviewPanel {
  private final MergePanel2.AsComponent myMergePanelComponent;
  private final JPanel myPanel = new JPanel(new BorderLayout());

  private final EventDispatcher<ColorAndFontSettingsListener> myDispatcher = EventDispatcher.create(ColorAndFontSettingsListener.class);

  public DiffPreviewPanel(@NotNull Disposable parent) {
    myMergePanelComponent = new MergePanel2.AsComponent(parent);
    myPanel.add(myMergePanelComponent, BorderLayout.CENTER);
    myMergePanelComponent.setToolbarEnabled(false);
    MergePanel2 mergePanel = getMergePanel();

    for (int i = 0; i < MergePanel2.EDITORS_COUNT; i++) {
      final EditorMouseListener motionListener = new EditorMouseListener(i);
      final EditorClickListener clickListener = new EditorClickListener(i);
      mergePanel.getEditorPlace(i).addListener(new EditorPlace.EditorListener() {
        @Override
        public void onEditorCreated(EditorPlace place) {
          Editor editor = place.getEditor();
          editor.addEditorMouseMotionListener(motionListener);
          editor.addEditorMouseListener(clickListener);
          editor.getCaretModel().addCaretListener(clickListener);
        }

        @Override
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

  @Override
  public Component getPanel() {
    return myPanel;
  }

  @Override
  public void updateView() {
    MergeList mergeList = getMergePanel().getMergeList();
    if (mergeList != null) mergeList.updateMarkup();
    myMergePanelComponent.repaint();

  }

  public void setMergeRequest(@Nullable Project project) throws FilesTooBigForDiffException {
    getMergePanel().setDiffRequest(new SampleMerge(project));
  }

  private MergePanel2 getMergePanel() {
    return myMergePanelComponent.getMergePanel();
  }

  public void setColorScheme(final EditorColorsScheme highlighterSettings) {
    getMergePanel().setColorScheme(highlighterSettings);
    getMergePanel().setHighlighterSettings(highlighterSettings);
  }

  private class EditorMouseListener extends EditorMouseMotionAdapter {
    private final int myIndex;

    private EditorMouseListener(int index) {
      myIndex = index;
    }

    @Override
    public void mouseMoved(EditorMouseEvent e) {
      MergePanel2 mergePanel = getMergePanel();
      Editor editor = mergePanel.getEditor(myIndex);
      if (MergeSearchHelper.findChangeAt(e, mergePanel, myIndex) != null) EditorUtil.setHandCursor(editor);
    }
  }

  public static class SampleMerge extends DiffRequest {
    public SampleMerge(@Nullable Project project) {
      super(project);
    }

    @Override
    @NotNull
    public DiffContent[] getContents() {
      return DiffPreviewProvider.getContents();
    }

    @Override
    public String[] getContentTitles() { return new String[]{"", "", ""}; }
    @Override
    public String getWindowTitle() { return DiffBundle.message("merge.color.options.dialog.title"); }
  }

  @Override
  public void addListener(@NotNull final ColorAndFontSettingsListener listener) {
    myDispatcher.addListener(listener);
  }

  private class EditorClickListener extends EditorMouseAdapter implements CaretListener {
    private final int myIndex;

    private EditorClickListener(int i) {
      myIndex = i;
    }

    @Override
    public void mouseClicked(EditorMouseEvent e) {
      select(MergeSearchHelper.findChangeAt(e, getMergePanel(), myIndex));
    }

    private void select(Change change) {
      if (change == null) return;
      myDispatcher.getMulticaster().selectionInPreviewChanged(change.getType().getTextDiffType().getDisplayName());
     }

    @Override
    public void caretPositionChanged(CaretEvent e) {
      select(MergeSearchHelper.findChangeAt(e, getMergePanel(), myIndex));
    }
  }

  @Override
  public void blinkSelectedHighlightType(final Object selected) {
    
  }

  @Override
  public void disposeUIResources() {
  }
}
