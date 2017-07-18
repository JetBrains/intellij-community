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
package com.intellij.openapi.vcs.ex;

import com.intellij.codeInsight.hint.EditorFragmentComponent;
import com.intellij.codeInsight.hint.EditorHintListener;
import com.intellij.codeInsight.hint.HintManager;
import com.intellij.codeInsight.hint.HintManagerImpl;
import com.intellij.diff.DiffContentFactory;
import com.intellij.diff.DiffManager;
import com.intellij.diff.comparison.ByWord;
import com.intellij.diff.comparison.ComparisonPolicy;
import com.intellij.diff.contents.DiffContent;
import com.intellij.diff.contents.DocumentContent;
import com.intellij.diff.fragments.DiffFragment;
import com.intellij.diff.requests.DiffRequest;
import com.intellij.diff.requests.SimpleDiffRequest;
import com.intellij.diff.util.DiffDrawUtil;
import com.intellij.diff.util.DiffUtil;
import com.intellij.diff.util.TextDiffType;
import com.intellij.icons.AllIcons;
import com.intellij.ide.DataManager;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.ActionUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.highlighter.EditorHighlighter;
import com.intellij.openapi.editor.highlighter.EditorHighlighterFactory;
import com.intellij.openapi.editor.highlighter.FragmentedEditorHighlighter;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.PlainTextFileType;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.progress.util.BackgroundTaskUtil;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vcs.VcsApplicationSettings;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.EditorTextField;
import com.intellij.ui.HintHint;
import com.intellij.ui.HintListener;
import com.intellij.ui.LightweightHint;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.EventObject;
import java.util.List;

import static com.intellij.diff.util.DiffUtil.getDiffType;
import static com.intellij.diff.util.DiffUtil.getLineCount;

public abstract class LineStatusMarkerPopupRenderer extends LineStatusMarkerRenderer {
  public LineStatusMarkerPopupRenderer(@NotNull LineStatusTrackerBase tracker) {
    super(tracker);
  }

  @Override
  protected boolean canDoAction(@NotNull Range range, MouseEvent e) {
    return isInsideMarkerArea(e);
  }

  @Override
  protected void doAction(@NotNull Editor editor, @NotNull Range range, @NotNull MouseEvent e) {
    showHint(editor, range, e);
  }


  @NotNull
  protected abstract List<AnAction> createToolbarActions(@NotNull Editor editor, @NotNull Range range, @Nullable Point mousePosition);

  @NotNull
  protected FileType getFileType() {
    return PlainTextFileType.INSTANCE;
  }

  protected boolean isShowInnerDifferences() {
    return VcsApplicationSettings.getInstance().SHOW_LST_WORD_DIFFERENCES;
  }


  public void scrollAndShow(@NotNull Editor editor, @NotNull Range range) {
    if (!myTracker.isValid()) return;
    final Document document = myTracker.getDocument();
    int line = Math.min(range.getType() == Range.DELETED ? range.getLine2() : range.getLine2() - 1, getLineCount(document) - 1);
    final int lastOffset = document.getLineStartOffset(line);
    editor.getCaretModel().moveToOffset(lastOffset);
    editor.getScrollingModel().scrollToCaret(ScrollType.CENTER);

    showAfterScroll(editor, range);
  }

  public void showAfterScroll(@NotNull Editor editor, @NotNull Range range) {
    editor.getScrollingModel().runActionOnScrollingFinished(() -> {
      showHintAt(editor, range, null);
    });
  }

  public void showHint(@NotNull Editor editor, @NotNull Range range, @NotNull MouseEvent e) {
    final JComponent comp = (JComponent)e.getComponent(); // shall be EditorGutterComponent, cast is safe.
    final JLayeredPane layeredPane = comp.getRootPane().getLayeredPane();
    final Point point = SwingUtilities.convertPoint(comp, ((EditorEx)editor).getGutterComponentEx().getWidth(), e.getY(), layeredPane);
    showHintAt(editor, range, point);
    e.consume();
  }

  public void showHintAt(@NotNull Editor editor, @NotNull Range range, @Nullable Point mousePosition) {
    if (!myTracker.isValid()) return;
    final Disposable disposable = Disposer.newDisposable();

    FileType fileType = getFileType();
    List<DiffFragment> wordDiff = computeWordDiff(range);

    installMasterEditorHighlighters(editor, range, wordDiff, disposable);
    JComponent editorComponent = createEditorComponent(editor, range, fileType, wordDiff);

    ActionToolbar toolbar = buildToolbar(editor, range, mousePosition, disposable);
    toolbar.updateActionsImmediately(); // we need valid ActionToolbar.getPreferredSize() to calc size of popup
    toolbar.setReservePlaceAutoPopupIcon(false);

    PopupPanel popupPanel = new PopupPanel(editor, toolbar, editorComponent);

    LightweightHint hint = new LightweightHint(popupPanel);
    HintListener closeListener = new HintListener() {
      public void hintHidden(final EventObject event) {
        Disposer.dispose(disposable);
      }
    };
    hint.addHintListener(closeListener);

    int line = editor.getCaretModel().getLogicalPosition().line;
    Point point = HintManagerImpl.getHintPosition(hint, editor, new LogicalPosition(line, 0), HintManager.UNDER);
    if (mousePosition != null) { // show right after the nearest line
      int lineHeight = editor.getLineHeight();
      int delta = (point.y - mousePosition.y) % lineHeight;
      if (delta < 0) delta += lineHeight;
      point.y = mousePosition.y + delta;
    }
    point.x -= popupPanel.getEditorTextOffset(); // align main editor with the one in popup

    int flags = HintManager.HIDE_BY_CARET_MOVE | HintManager.HIDE_BY_TEXT_CHANGE | HintManager.HIDE_BY_SCROLLING;
    HintManagerImpl.getInstanceImpl().showEditorHint(hint, editor, point, flags, -1, false, new HintHint(editor, point));

    ApplicationManager.getApplication().getMessageBus().connect(disposable)
      .subscribe(EditorHintListener.TOPIC, (project, newHint, newHintFlags) -> {
        // Ex: if popup re-shown by ToggleByWordDiffAction
        if (newHint.getComponent() instanceof PopupPanel) {
          PopupPanel newPopupPanel = (PopupPanel)newHint.getComponent();
          if (newPopupPanel.getEditor().equals(editor)) {
            hint.hide();
          }
        }
      });

    if (!hint.isVisible()) {
      closeListener.hintHidden(null);
    }
  }

  @Nullable
  private List<DiffFragment> computeWordDiff(@NotNull Range range) {
    if (!isShowInnerDifferences()) return null;
    if (range.getType() != Range.MODIFIED) return null;

    final CharSequence vcsContent = myTracker.getVcsContent(range);
    final CharSequence currentContent = myTracker.getCurrentContent(range);

    return BackgroundTaskUtil.tryComputeFast(indicator -> {
      return ByWord.compare(vcsContent, currentContent, ComparisonPolicy.DEFAULT, indicator);
    }, 200);
  }

  private void installMasterEditorHighlighters(@NotNull Editor editor,
                                               @NotNull Range range,
                                               @Nullable List<DiffFragment> wordDiff,
                                               @NotNull Disposable parentDisposable) {
    if (wordDiff == null) return;
    final List<RangeHighlighter> highlighters = new ArrayList<>();

    int currentStartShift = myTracker.getCurrentTextRange(range).getStartOffset();
    for (DiffFragment fragment : wordDiff) {
      int currentStart = currentStartShift + fragment.getStartOffset2();
      int currentEnd = currentStartShift + fragment.getEndOffset2();
      TextDiffType type = getDiffType(fragment);

      highlighters.addAll(DiffDrawUtil.createInlineHighlighter(editor, currentStart, currentEnd, type));
    }

    Disposer.register(parentDisposable, new Disposable() {
      @Override
      public void dispose() {
        for (RangeHighlighter highlighter : highlighters) {
          highlighter.dispose();
        }
      }
    });
  }

  @Nullable
  private JComponent createEditorComponent(@NotNull Editor editor,
                                           @NotNull Range range,
                                           @Nullable FileType fileType,
                                           @Nullable List<DiffFragment> wordDiff) {
    if (range.getType() == Range.INSERTED) return null;

    Document vcsDocument = myTracker.getVcsDocument();
    TextRange vcsTextRange = myTracker.getVcsTextRange(range);
    String content = vcsTextRange.subSequence(vcsDocument.getImmutableCharSequence()).toString();

    EditorHighlighterFactory highlighterFactory = EditorHighlighterFactory.getInstance();
    EditorHighlighter highlighter = highlighterFactory.createEditorHighlighter(myTracker.getProject(), getFileName(myTracker.getDocument()));
    highlighter.setText(myTracker.getVcsDocument().getImmutableCharSequence());
    FragmentedEditorHighlighter fragmentedHighlighter = new FragmentedEditorHighlighter(highlighter, vcsTextRange);

    Color backgroundColor = EditorFragmentComponent.getBackgroundColor(editor, true);

    EditorTextField field = new EditorTextField(content);
    field.setBorder(null);
    field.setOneLineMode(false);
    field.ensureWillComputePreferredSize();

    field.addSettingsProvider(uEditor -> {
      uEditor.setRendererMode(true);
      uEditor.setBorder(null);

      uEditor.setColorsScheme(editor.getColorsScheme());
      uEditor.setBackgroundColor(backgroundColor);

      DiffUtil.setEditorCodeStyle(myTracker.getProject(), uEditor, fileType);

      uEditor.setHighlighter(fragmentedHighlighter);

      if (wordDiff != null) {
        for (DiffFragment fragment : wordDiff) {
          int vcsStart = fragment.getStartOffset1();
          int vcsEnd = fragment.getEndOffset1();
          TextDiffType type = getDiffType(fragment);

          DiffDrawUtil.createInlineHighlighter(uEditor, vcsStart, vcsEnd, type);
        }
      }
    });

    JPanel panel = JBUI.Panels.simplePanel(field);
    panel.setBorder(EditorFragmentComponent.createEditorFragmentBorder(editor));
    panel.setBackground(backgroundColor);

    DataManager.registerDataProvider(panel, data -> {
      if (CommonDataKeys.HOST_EDITOR.is(data)) {
        return field.getEditor();
      }
      return null;
    });

    return panel;
  }

  private static String getFileName(@NotNull Document document) {
    final VirtualFile file = FileDocumentManager.getInstance().getFile(document);
    if (file == null) return "";
    return file.getName();
  }

  @NotNull
  private ActionToolbar buildToolbar(@NotNull Editor editor,
                                     @NotNull Range range,
                                     @Nullable Point mousePosition,
                                     @NotNull Disposable parentDisposable) {
    List<AnAction> actions = createToolbarActions(editor, range, mousePosition);

    JComponent editorComponent = editor.getComponent();
    for (AnAction action : actions) {
      DiffUtil.registerAction(action, editorComponent);
    }

    Disposer.register(parentDisposable, new Disposable() {
      @Override
      public void dispose() {
        ActionUtil.getActions(editorComponent).removeAll(actions);
      }
    });

    return ActionManager.getInstance().createActionToolbar(ActionPlaces.TOOLBAR, new DefaultActionGroup(actions), true);
  }

  private static class PopupPanel extends JPanel {
    @Nullable private final JComponent myEditorComponent;
    @NotNull private final Editor myEditor;

    public PopupPanel(@NotNull Editor editor,
                      @NotNull ActionToolbar toolbar,
                      @Nullable JComponent editorComponent) {
      super(new BorderLayout());
      setOpaque(false);

      myEditor = editor;
      myEditorComponent = editorComponent;
      boolean isEditorVisible = myEditorComponent != null;

      Color background = ((EditorEx)editor).getBackgroundColor();
      Color borderColor = editor.getColorsScheme().getColor(EditorColors.SELECTED_TEARLINE_COLOR);

      JComponent toolbarComponent = toolbar.getComponent();
      toolbarComponent.setBackground(background);
      toolbarComponent.setBorder(null);

      JComponent toolbarPanel = JBUI.Panels.simplePanel(toolbarComponent);
      toolbarPanel.setBackground(background);
      Border outsideToolbarBorder = JBUI.Borders.customLine(borderColor, 1, 1, isEditorVisible ? 0 : 1, 1);
      Border insideToolbarBorder = JBUI.Borders.empty(1, 5, 1, 5);
      toolbarPanel.setBorder(BorderFactory.createCompoundBorder(outsideToolbarBorder, insideToolbarBorder));

      if (myEditorComponent != null) {
        // default border of EditorFragmentComponent is replaced here with our own.
        Border outsideEditorBorder = JBUI.Borders.customLine(borderColor, 1);
        Border insideEditorBorder = JBUI.Borders.empty(2);
        myEditorComponent.setBorder(BorderFactory.createCompoundBorder(outsideEditorBorder, insideEditorBorder));
      }

      // 'empty space' to the right of toolbar
      JPanel emptyPanel = new JPanel();
      emptyPanel.setOpaque(false);
      emptyPanel.setPreferredSize(new Dimension());

      JPanel topPanel = new JPanel(new BorderLayout());
      topPanel.setOpaque(false);
      topPanel.add(toolbarPanel, BorderLayout.WEST);
      topPanel.add(emptyPanel, BorderLayout.CENTER);

      add(topPanel, BorderLayout.NORTH);
      if (myEditorComponent != null) add(myEditorComponent, BorderLayout.CENTER);

      // transfer clicks into editor
      MouseAdapter listener = new MouseAdapter() {
        @Override
        public void mousePressed(MouseEvent e) {
          transferEvent(e, editor);
        }

        @Override
        public void mouseClicked(MouseEvent e) {
          transferEvent(e, editor);
        }

        public void mouseReleased(MouseEvent e) {
          transferEvent(e, editor);
        }
      };
      emptyPanel.addMouseListener(listener);
    }

    @NotNull
    public Editor getEditor() {
      return myEditor;
    }

    private static void transferEvent(MouseEvent e, Editor editor) {
      editor.getContentComponent().dispatchEvent(SwingUtilities.convertMouseEvent(e.getComponent(), e, editor.getContentComponent()));
    }

    public int getEditorTextOffset() {
      return EditorFragmentComponent.createEditorFragmentBorder(myEditor).getBorderInsets(myEditorComponent).left;
    }
  }


  public class ShowNextChangeMarkerAction extends DumbAwareAction {
    @NotNull private final Editor myEditor;
    @NotNull private final Range myRange;

    public ShowNextChangeMarkerAction(@NotNull Editor editor, @NotNull Range range) {
      myEditor = editor;
      myRange = range;
      ActionUtil.copyFrom(this, "VcsShowNextChangeMarker");
    }

    @Override
    public void update(AnActionEvent e) {
      e.getPresentation().setEnabled(myTracker.getNextRange(myRange) != null);
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      Range range = myTracker.getNextRange(myRange);
      if (range != null) LineStatusMarkerPopupRenderer.this.scrollAndShow(myEditor, range);
    }
  }

  public class ShowPrevChangeMarkerAction extends DumbAwareAction {
    @NotNull private final Editor myEditor;
    @NotNull private final Range myRange;

    public ShowPrevChangeMarkerAction(@NotNull Editor editor, @NotNull Range range) {
      myEditor = editor;
      myRange = range;
      ActionUtil.copyFrom(this, "VcsShowPrevChangeMarker");
    }

    @Override
    public void update(AnActionEvent e) {
      e.getPresentation().setEnabled(myTracker.getPrevRange(myRange) != null);
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      Range range = myTracker.getPrevRange(myRange);
      if (range != null) LineStatusMarkerPopupRenderer.this.scrollAndShow(myEditor, range);
    }
  }

  public class CopyLineStatusRangeAction extends DumbAwareAction {
    private final Range myRange;

    public CopyLineStatusRangeAction(@NotNull Range range) {
      myRange = range;
      ActionUtil.copyFrom(this, IdeActions.ACTION_COPY);
    }

    public void update(final AnActionEvent e) {
      boolean enabled = Range.DELETED == myRange.getType() || Range.MODIFIED == myRange.getType();
      e.getPresentation().setEnabled(myTracker.isValid() && enabled);
    }

    public void actionPerformed(final AnActionEvent e) {
      final String content = myTracker.getVcsContent(myRange) + "\n";
      CopyPasteManager.getInstance().setContents(new StringSelection(content));
    }
  }

  public class ShowLineStatusRangeDiffAction extends DumbAwareAction {
    private final Range myRange;

    public ShowLineStatusRangeDiffAction(@NotNull Range range) {
      myRange = range;
      ActionUtil.copyFrom(this, IdeActions.ACTION_SHOW_DIFF_COMMON);
    }

    public void update(final AnActionEvent e) {
      e.getPresentation().setEnabled(myTracker.isValid());
    }

    @Override
    public void actionPerformed(final AnActionEvent e) {
      DiffManager.getInstance().showDiff(e.getProject(), createDiffData());
    }

    @NotNull
    private DiffRequest createDiffData() {
      Range range = expand(myRange, myTracker.getDocument(), myTracker.getVcsDocument());

      DiffContent vcsContent = createDiffContent(myTracker.getVcsDocument(),
                                                 myTracker.getVirtualFile(),
                                                 myTracker.getVcsTextRange(range));
      DiffContent currentContent = createDiffContent(myTracker.getDocument(),
                                                     myTracker.getVirtualFile(),
                                                     myTracker.getCurrentTextRange(range));

      return new SimpleDiffRequest(VcsBundle.message("dialog.title.diff.for.range"),
                                   vcsContent, currentContent,
                                   VcsBundle.message("diff.content.title.up.to.date"),
                                   VcsBundle.message("diff.content.title.current.range")
      );
    }

    @NotNull
    private DiffContent createDiffContent(@NotNull Document document, @Nullable VirtualFile highlightFile, @NotNull TextRange textRange) {
      final Project project = myTracker.getProject();
      DocumentContent content = DiffContentFactory.getInstance().create(project, document, highlightFile);
      return DiffContentFactory.getInstance().createFragment(project, content, textRange);
    }

    @NotNull
    private Range expand(@NotNull Range range, @NotNull Document document, @NotNull Document uDocument) {
      boolean canExpandBefore = range.getLine1() != 0 && range.getVcsLine1() != 0;
      boolean canExpandAfter = range.getLine2() < getLineCount(document) && range.getVcsLine2() < getLineCount(uDocument);
      int offset1 = range.getLine1() - (canExpandBefore ? 1 : 0);
      int uOffset1 = range.getVcsLine1() - (canExpandBefore ? 1 : 0);
      int offset2 = range.getLine2() + (canExpandAfter ? 1 : 0);
      int uOffset2 = range.getVcsLine2() + (canExpandAfter ? 1 : 0);
      return new Range(offset1, offset2, uOffset1, uOffset2);
    }
  }

  public class ToggleByWordDiffAction extends ToggleAction implements DumbAware {
    @NotNull private final Editor myEditor;
    @NotNull private final Range myRange;
    @Nullable private final Point myMousePosition;

    public ToggleByWordDiffAction(@NotNull Editor editor,
                                  @NotNull Range range,
                                  @Nullable Point position) {
      super("Show Detailed Differences", null, AllIcons.Actions.PreviewDetails);
      myEditor = editor;
      myRange = range;
      myMousePosition = position;
    }

    @Override
    public boolean isSelected(AnActionEvent e) {
      return VcsApplicationSettings.getInstance().SHOW_LST_WORD_DIFFERENCES;
    }

    @Override
    public void setSelected(AnActionEvent e, boolean state) {
      VcsApplicationSettings.getInstance().SHOW_LST_WORD_DIFFERENCES = state;
      reshowPopup();
    }

    public void reshowPopup() {
      LineStatusMarkerPopupRenderer.this.showHintAt(myEditor, myRange, myMousePosition);
    }
  }
}
