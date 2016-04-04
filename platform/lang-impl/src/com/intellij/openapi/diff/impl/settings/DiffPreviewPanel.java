/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
import com.intellij.diff.DiffContentFactory;
import com.intellij.diff.DiffContext;
import com.intellij.diff.contents.DiffContent;
import com.intellij.diff.requests.ContentDiffRequest;
import com.intellij.diff.tools.simple.SimpleThreesideDiffChange;
import com.intellij.diff.tools.simple.SimpleThreesideDiffViewer;
import com.intellij.diff.tools.util.base.HighlightPolicy;
import com.intellij.diff.tools.util.base.IgnorePolicy;
import com.intellij.diff.tools.util.base.TextDiffSettingsHolder;
import com.intellij.diff.util.DiffUtil;
import com.intellij.diff.util.ThreeSide;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.diff.DiffBundle;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.event.*;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.ex.util.EditorUtil;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.util.EventDispatcher;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.awt.*;
import java.util.List;

/**
 * The panel from the Settings, that allows to see changes to diff/merge coloring scheme right away.
 */
public class DiffPreviewPanel implements PreviewPanel {
  private final SimpleThreesideDiffViewer myViewer;

  private final EventDispatcher<ColorAndFontSettingsListener> myDispatcher = EventDispatcher.create(ColorAndFontSettingsListener.class);

  public DiffPreviewPanel(@NotNull Disposable parent) {
    myViewer = new SimpleThreesideDiffViewer(new SampleContext(), new SampleRequest()) {
      @Override
      protected boolean forceRediffSynchronously() {
        return true;
      }
    };
    myViewer.init();
    Disposer.register(parent, myViewer);

    for (ThreeSide side : ThreeSide.values()) {
      final EditorMouseListener motionListener = new EditorMouseListener(side);
      final EditorClickListener clickListener = new EditorClickListener(side);
      Editor editor = myViewer.getEditor(side);
      editor.addEditorMouseMotionListener(motionListener);
      editor.addEditorMouseListener(clickListener);
      editor.getCaretModel().addCaretListener(clickListener);
    }
  }

  @Override
  public Component getPanel() {
    return myViewer.getComponent();
  }

  @Override
  public void updateView() {
    List<SimpleThreesideDiffChange> changes = myViewer.getChanges();
    for (SimpleThreesideDiffChange change : changes) {
      change.destroyHighlighter();
      change.installHighlighter();
    }
  }

  public void setColorScheme(final EditorColorsScheme highlighterSettings) {
    for (EditorEx editorEx : myViewer.getEditors()) {
      editorEx.setColorsScheme(highlighterSettings);
    }
  }

  private static class SampleRequest extends ContentDiffRequest {
    private final List<DiffContent> myContents;

    public SampleRequest() {
      com.intellij.openapi.diff.DiffContent[] contents = DiffPreviewProvider.getContents();
      myContents = ContainerUtil.list(convert(contents[0]), convert(contents[1]), convert(contents[2]));
    }

    private static DiffContent convert(@NotNull com.intellij.openapi.diff.DiffContent content) {
      Document document = content.getDocument();
      FileType fileType = content.getContentType();
      return DiffContentFactory.getInstance().create(null, document, fileType);
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
      TextDiffSettingsHolder.TextDiffSettings settings = new TextDiffSettingsHolder.TextDiffSettings();
      settings.setHighlightPolicy(HighlightPolicy.BY_WORD);
      settings.setIgnorePolicy(IgnorePolicy.IGNORE_WHITESPACES);
      putUserData(TextDiffSettingsHolder.KEY, settings);
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
      if (getChange(mySide, e) != null) EditorUtil.setHandCursor(e.getEditor());
    }
  }

  private class EditorClickListener extends EditorMouseAdapter implements CaretListener {
    @NotNull private final ThreeSide mySide;

    private EditorClickListener(@NotNull ThreeSide side) {
      mySide = side;
    }

    @Override
    public void mouseClicked(EditorMouseEvent e) {
      selectChange(getChange(mySide, e));
    }

    @Override
    public void caretPositionChanged(CaretEvent e) {
      selectChange(getChange(mySide, e.getNewPosition().line));
    }

    @Override
    public void caretAdded(CaretEvent e) {
    }

    @Override
    public void caretRemoved(CaretEvent e) {
    }
  }

  private void selectChange(@Nullable SimpleThreesideDiffChange change) {
    if (change == null) return;
    myDispatcher.getMulticaster().selectionInPreviewChanged(change.getDiffType().getName());
  }

  @Nullable
  private SimpleThreesideDiffChange getChange(ThreeSide side, EditorMouseEvent e) {
    EditorEx editor = myViewer.getEditor(side);
    LogicalPosition logicalPosition = editor.xyToLogicalPosition(e.getMouseEvent().getPoint());
    int offset = editor.logicalPositionToOffset(logicalPosition);
    int line = editor.getDocument().getLineNumber(offset);
    return getChange(side, line);
  }

  @Nullable
  private SimpleThreesideDiffChange getChange(ThreeSide side, int line) {
    for (SimpleThreesideDiffChange change : myViewer.getChanges()) {
      int startLine = change.getStartLine(side);
      int endLine = change.getEndLine(side);
      if (DiffUtil.isSelectedByLine(line, startLine, endLine)) return change;
    }
    return null;
  }

  @Override
  public void blinkSelectedHighlightType(final Object selected) {
  }

  @Override
  public void disposeUIResources() {
  }

  @NotNull
  @TestOnly
  public SimpleThreesideDiffViewer testGetViewer() {
    return myViewer;
  }
}
