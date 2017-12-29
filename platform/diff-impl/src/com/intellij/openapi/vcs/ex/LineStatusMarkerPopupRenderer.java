/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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

  @Nullable
  protected JComponent createAdditionalInfoPanel(@NotNull Editor editor, @NotNull Range range) {
    return null;
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
      Range newRange = myTracker.findRange(range);
      if (newRange != null) showHintAt(editor, newRange, null);
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

    JComponent additionalInfoPanel = createAdditionalInfoPanel(editor, range);

    PopupPanel popupPanel = new PopupPanel(editor, toolbar, editorComponent, additionalInfoPanel);

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

    final CharSequence vcsContent = getVcsContent(range);
    final CharSequence currentContent = getCurrentContent(range);

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

    int currentStartShift = getCurrentTextRange(range).getStartOffset();
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

    TextRange vcsTextRange = getVcsTextRange(range);
    String content = getVcsContent(range).toString();

    EditorHighlighterFactory highlighterFactory = EditorHighlighterFactory.getInstance();
    EditorHighlighter highlighter = highlighterFactory.createEditorHighlighter(myTracker.getProject(), getFileName(myTracker.getDocument()));
    highlighter.setText(myTracker.getVcsDocument().getImmutableCharSequence());
    FragmentedEditorHighlighter fragmentedHighlighter = new FragmentedEditorHighlighter(highlighter, vcsTextRange);

    Color backgroundColor = EditorFragmentComponent.getBackgroundColor(editor, true);

    EditorTextField field = new EditorTextField(content);
    field.setBorder(null);
    field.setOneLineMode(false);
    field.ensureWillComputePreferredSize();
    field.setFontInheritedFromLAF(false);

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
                      @Nullable JComponent editorComponent,
                      @Nullable JComponent additionalInfo) {
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

      if (additionalInfo != null) {
        toolbarPanel.add(additionalInfo, BorderLayout.EAST);
      }

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


  @NotNull
  private CharSequence getCurrentContent(Range range) {
    return DiffUtil.getLinesContent(myTracker.getDocument(), range.getLine1(), range.getLine2());
  }

  @NotNull
  private CharSequence getVcsContent(Range range) {
    return DiffUtil.getLinesContent(myTracker.getVcsDocument(), range.getVcsLine1(), range.getVcsLine2());
  }

  @NotNull
  private TextRange getCurrentTextRange(@NotNull Range range) {
    return DiffUtil.getLinesRange(myTracker.getDocument(), range.getLine1(), range.getLine2());
  }

  @NotNull
  private TextRange getVcsTextRange(@NotNull Range range) {
    return DiffUtil.getLinesRange(myTracker.getVcsDocument(), range.getVcsLine1(), range.getVcsLine2());
  }


  protected abstract class RangeMarkerAction extends DumbAwareAction {
    @NotNull private final Range myRange;
    @NotNull private final Editor myEditor;

    public RangeMarkerAction(@NotNull Editor editor, @NotNull Range range, @NotNull String actionId) {
      myRange = range;
      myEditor = editor;
      ActionUtil.copyFrom(this, actionId);
    }

    @Override
    public void update(AnActionEvent e) {
      Range newRange = myTracker.findRange(myRange);
      e.getPresentation().setEnabled(newRange != null && !myEditor.isDisposed() && isEnabled(myEditor, newRange));
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      Range newRange = myTracker.findRange(myRange);
      if (newRange != null) actionPerformed(myEditor, newRange);
    }

    protected abstract boolean isEnabled(@NotNull Editor editor, @NotNull Range range);

    protected abstract void actionPerformed(@NotNull Editor editor, @NotNull Range range);
  }

  public class ShowNextChangeMarkerAction extends RangeMarkerAction {
    public ShowNextChangeMarkerAction(@NotNull Editor editor, @NotNull Range range) {
      super(editor, range, "VcsShowNextChangeMarker");
    }

    @Override
    protected boolean isEnabled(@NotNull Editor editor, @NotNull Range range) {
      return myTracker.getNextRange(range.getLine1()) != null;
    }

    @Override
    protected void actionPerformed(@NotNull Editor editor, @NotNull Range range) {
      Range targetRange = myTracker.getNextRange(range.getLine1());
      if (targetRange != null) LineStatusMarkerPopupRenderer.this.scrollAndShow(editor, targetRange);
    }
  }

  public class ShowPrevChangeMarkerAction extends RangeMarkerAction {
    public ShowPrevChangeMarkerAction(@NotNull Editor editor, @NotNull Range range) {
      super(editor, range, "VcsShowPrevChangeMarker");
    }

    @Override
    protected boolean isEnabled(@NotNull Editor editor, @NotNull Range range) {
      return myTracker.getPrevRange(range.getLine1()) != null;
    }

    @Override
    protected void actionPerformed(@NotNull Editor editor, @NotNull Range range) {
      Range targetRange = myTracker.getPrevRange(range.getLine1());
      if (targetRange != null) LineStatusMarkerPopupRenderer.this.scrollAndShow(editor, targetRange);
    }
  }

  public class CopyLineStatusRangeAction extends RangeMarkerAction {
    public CopyLineStatusRangeAction(@NotNull Editor editor, @NotNull Range range) {
      super(editor, range, IdeActions.ACTION_COPY);
    }

    @Override
    protected boolean isEnabled(@NotNull Editor editor, @NotNull Range range) {
      return Range.DELETED == range.getType() || Range.MODIFIED == range.getType();
    }

    @Override
    protected void actionPerformed(@NotNull Editor editor, @NotNull Range range) {
      final String content = getVcsContent(range) + "\n";
      CopyPasteManager.getInstance().setContents(new StringSelection(content));
    }
  }

  public class ShowLineStatusRangeDiffAction extends RangeMarkerAction {
    public ShowLineStatusRangeDiffAction(@NotNull Editor editor, @NotNull Range range) {
      super(editor, range, IdeActions.ACTION_SHOW_DIFF_COMMON);
    }

    @Override
    protected boolean isEnabled(@NotNull Editor editor, @NotNull Range range) {
      return true;
    }

    @Override
    protected void actionPerformed(@NotNull Editor editor, @NotNull Range range) {
      Range ourRange = expand(range, myTracker.getDocument(), myTracker.getVcsDocument());

      DiffContent vcsContent = createDiffContent(myTracker.getVcsDocument(),
                                                 myTracker.getVirtualFile(),
                                                 getVcsTextRange(ourRange));
      DiffContent currentContent = createDiffContent(myTracker.getDocument(),
                                                     myTracker.getVirtualFile(),
                                                     getCurrentTextRange(ourRange));

      SimpleDiffRequest request = new SimpleDiffRequest(VcsBundle.message("dialog.title.diff.for.range"),
                                                        vcsContent, currentContent,
                                                        VcsBundle.message("diff.content.title.up.to.date"),
                                                        VcsBundle.message("diff.content.title.current.range"));

      DiffManager.getInstance().showDiff(myTracker.getProject(), request);
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
      if (!myTracker.isValid()) return;
      VcsApplicationSettings.getInstance().SHOW_LST_WORD_DIFFERENCES = state;

      Range newRange = myTracker.findRange(myRange);
      if (newRange != null) LineStatusMarkerPopupRenderer.this.showHintAt(myEditor, newRange, myMousePosition);
    }
  }
}
