/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
import com.intellij.diff.DiffContext;
import com.intellij.diff.contents.DiffContent;
import com.intellij.diff.requests.ContentDiffRequest;
import com.intellij.diff.tools.simple.SimpleThreesideDiffChange;
import com.intellij.diff.tools.simple.SimpleThreesideDiffViewer;
import com.intellij.diff.tools.util.base.HighlightPolicy;
import com.intellij.diff.tools.util.base.IgnorePolicy;
import com.intellij.diff.util.DiffLineSeparatorRenderer;
import com.intellij.diff.util.DiffUtil;
import com.intellij.diff.util.TextDiffTypeFactory.TextDiffTypeImpl;
import com.intellij.diff.util.ThreeSide;
import com.intellij.openapi.diff.DiffBundle;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.FoldRegion;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.event.*;
import com.intellij.openapi.editor.ex.DocumentEx;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.ex.util.EditorUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.util.EventDispatcher;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import javax.swing.*;
import java.awt.*;
import java.util.Arrays;
import java.util.List;

import static com.intellij.diff.tools.util.base.TextDiffSettingsHolder.TextDiffSettings;

/**
 * The panel from the Settings, that allows to see changes to diff/merge coloring scheme right away.
 */
class DiffPreviewPanel implements PreviewPanel {
  private final JPanel myPanel;
  private final MyViewer myViewer;

  private final EventDispatcher<ColorAndFontSettingsListener> myDispatcher = EventDispatcher.create(ColorAndFontSettingsListener.class);

  public DiffPreviewPanel() {
    myViewer = new MyViewer();
    myViewer.init();

    for (ThreeSide side : ThreeSide.values()) {
      final EditorMouseListener motionListener = new EditorMouseListener(side);
      final EditorClickListener clickListener = new EditorClickListener(side);
      Editor editor = myViewer.getEditor(side);
      editor.addEditorMouseMotionListener(motionListener);
      editor.addEditorMouseListener(clickListener);
      editor.getCaretModel().addCaretListener(clickListener);
    }

    myPanel = JBUI.Panels.simplePanel(myViewer.getComponent()).withBorder(IdeBorderFactory.createBorder());
  }

  @Override
  public Component getPanel() {
    return myPanel;
  }

  @Override
  public void updateView() {
    List<SimpleThreesideDiffChange> changes = myViewer.getChanges();
    for (SimpleThreesideDiffChange change : changes) {
      change.reinstallHighlighters();
    }
    myViewer.repaint();
  }

  public void setColorScheme(final EditorColorsScheme highlighterSettings) {
    for (EditorEx editorEx : myViewer.getEditors()) {
      editorEx.setColorsScheme(editorEx.createBoundColorSchemeDelegate(highlighterSettings));
      editorEx.getColorsScheme().setAttributes(EditorColors.FOLDED_TEXT_ATTRIBUTES, null);
      editorEx.reinitSettings();
    }
  }

  private static class SampleRequest extends ContentDiffRequest {
    private final List<DiffContent> myContents;

    public SampleRequest() {
      myContents = Arrays.asList(DiffPreviewProvider.getContents());
    }

    @NotNull
    @Override
    public List<DiffContent> getContents() {
      return myContents;
    }

    @NotNull
    @Override
    public List<String> getContentTitles() {
      return ContainerUtil.list(null, null, null);
    }

    @Nullable
    @Override
    public String getTitle() {
      return DiffBundle.message("merge.color.options.dialog.title");
    }
  }

  private static class SampleContext extends DiffContext {
    public SampleContext() {
      TextDiffSettings settings = new TextDiffSettings();
      settings.setHighlightPolicy(HighlightPolicy.BY_WORD);
      settings.setIgnorePolicy(IgnorePolicy.IGNORE_WHITESPACES);
      settings.setContextRange(2);
      settings.setExpandByDefault(false);
      putUserData(TextDiffSettings.KEY, settings);
    }

    @Nullable
    @Override
    public Project getProject() {
      return null;
    }

    @Override
    public boolean isWindowFocused() {
      return false;
    }

    @Override
    public boolean isFocused() {
      return false;
    }

    @Override
    public void requestFocus() {
    }
  }

  @Override
  public void addListener(@NotNull final ColorAndFontSettingsListener listener) {
    myDispatcher.addListener(listener);
  }

  private class EditorMouseListener extends EditorMouseMotionAdapter {
    @NotNull private final ThreeSide mySide;

    private EditorMouseListener(@NotNull ThreeSide side) {
      mySide = side;
    }

    @Override
    public void mouseMoved(EditorMouseEvent e) {
      int line = getLineNumber(mySide, e);
      if (getChange(mySide, line) != null || getFoldRegion(mySide, line) != null) {
        EditorUtil.setHandCursor(e.getEditor());
      }
    }
  }

  private class EditorClickListener extends EditorMouseAdapter implements CaretListener {
    @NotNull private final ThreeSide mySide;

    private EditorClickListener(@NotNull ThreeSide side) {
      mySide = side;
    }

    @Override
    public void mouseClicked(EditorMouseEvent e) {
      selectColorForLine(mySide, getLineNumber(mySide, e));
    }

    @Override
    public void caretPositionChanged(CaretEvent e) {
      selectColorForLine(mySide, e.getNewPosition().line);
    }
  }

  private void selectColorForLine(@NotNull ThreeSide side, int line) {
    SimpleThreesideDiffChange change = getChange(side, line);
    if (change != null) {
      TextDiffTypeImpl diffType = ObjectUtils.tryCast(change.getDiffType(), TextDiffTypeImpl.class);
      if (diffType != null) {
        myDispatcher.getMulticaster().selectionInPreviewChanged(diffType.getKey().getExternalName());
      }
      return;
    }

    FoldRegion region = getFoldRegion(side, line);
    if (region != null) {
      myDispatcher.getMulticaster().selectionInPreviewChanged(DiffLineSeparatorRenderer.BACKGROUND.getExternalName());
      return;
    }
  }

  private int getLineNumber(@NotNull ThreeSide side, EditorMouseEvent e) {
    EditorEx editor = myViewer.getEditor(side);
    LogicalPosition logicalPosition = editor.xyToLogicalPosition(e.getMouseEvent().getPoint());
    int offset = editor.logicalPositionToOffset(logicalPosition);
    return editor.getDocument().getLineNumber(offset);
  }

  @Nullable
  private SimpleThreesideDiffChange getChange(@NotNull ThreeSide side, int line) {
    for (SimpleThreesideDiffChange change : myViewer.getChanges()) {
      int startLine = change.getStartLine(side);
      int endLine = change.getEndLine(side);
      if (DiffUtil.isSelectedByLine(line, startLine, endLine)) return change;
    }
    return null;
  }

  @Nullable
  private FoldRegion getFoldRegion(@NotNull ThreeSide side, int line) {
    EditorEx editor = myViewer.getEditor(side);
    DocumentEx document = editor.getDocument();
    for (FoldRegion region : editor.getFoldingModel().getAllFoldRegions()) {
      if (region.isExpanded()) continue;
      int line1 = document.getLineNumber(region.getStartOffset());
      int line2 = document.getLineNumber(region.getEndOffset());
      if (line1 <= line && line <= line2) return region;
    }
    return null;
  }

  @Override
  public void blinkSelectedHighlightType(final Object selected) {
  }

  @Override
  public void disposeUIResources() {
    Disposer.dispose(myViewer);
  }

  @NotNull
  @TestOnly
  public SimpleThreesideDiffViewer testGetViewer() {
    return myViewer;
  }

  private static class MyViewer extends SimpleThreesideDiffViewer {
    public MyViewer() {super(new SampleContext(), new SampleRequest());}

    @Override
    protected boolean forceRediffSynchronously() {
      return true;
    }

    public void repaint() {
      myPanel.repaint();
    }
  }
}
