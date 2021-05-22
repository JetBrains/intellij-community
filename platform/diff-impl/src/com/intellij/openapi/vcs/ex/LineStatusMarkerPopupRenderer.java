// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.ex;

import com.intellij.codeInsight.hint.HintManager;
import com.intellij.codeInsight.hint.HintManagerImpl;
import com.intellij.diff.DiffApplicationSettings;
import com.intellij.diff.DiffContentFactory;
import com.intellij.diff.DiffManager;
import com.intellij.diff.comparison.ByWord;
import com.intellij.diff.comparison.ComparisonPolicy;
import com.intellij.diff.contents.DiffContent;
import com.intellij.diff.contents.DocumentContent;
import com.intellij.diff.fragments.DiffFragment;
import com.intellij.diff.requests.SimpleDiffRequest;
import com.intellij.diff.util.DiffUtil;
import com.intellij.icons.AllIcons;
import com.intellij.ide.lightEdit.LightEditCompatible;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.ActionUtil;
import com.intellij.openapi.diff.DiffBundle;
import com.intellij.openapi.diff.LineStatusMarkerDrawUtil;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.PlainTextFileType;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.progress.util.BackgroundTaskUtil;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.EditorTextField;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.event.MouseEvent;
import java.util.List;

import static com.intellij.diff.util.DiffUtil.getLineCount;

public abstract class LineStatusMarkerPopupRenderer extends LineStatusMarkerRenderer {
  /**
   * @deprecated Use {@link LineStatusMarkerPopupRenderer#LineStatusMarkerPopupRenderer(LineStatusTrackerI)}
   */
  @Deprecated
  @ApiStatus.ScheduledForRemoval(inVersion = "2021.3")
  public LineStatusMarkerPopupRenderer(@NotNull LineStatusTrackerBase<?> tracker) {
    this((LineStatusTrackerI<?>)tracker);
  }

  public LineStatusMarkerPopupRenderer(@NotNull LineStatusTrackerI<?> tracker) {
    super(tracker);
  }

  @Override
  protected boolean canDoAction(@NotNull Editor editor, @NotNull List<? extends Range> ranges, @NotNull MouseEvent e) {
    return LineStatusMarkerDrawUtil.isInsideMarkerArea(e);
  }

  @Override
  protected void doAction(@NotNull Editor editor, @NotNull List<? extends Range> ranges, @NotNull MouseEvent e) {
    Range range = ranges.get(0);
    if (ranges.size() > 1) {
      scrollAndShow(editor, range);
    }
    else {
      showHint(editor, range, e);
    }
  }


  @NotNull
  protected abstract List<AnAction> createToolbarActions(@NotNull Editor editor, @NotNull Range range, @Nullable Point mousePosition);

  @NotNull
  protected FileType getFileType() {
    VirtualFile virtualFile = myTracker.getVirtualFile();
    return virtualFile != null ? virtualFile.getFileType() : PlainTextFileType.INSTANCE;
  }

  @Nullable
  protected JComponent createAdditionalInfoPanel(@NotNull Editor editor,
                                                 @NotNull Range range,
                                                 @Nullable Point mousePosition,
                                                 @NotNull Disposable disposable) {
    return null;
  }


  public void scrollAndShow(@NotNull Editor editor, @NotNull Range range) {
    if (!myTracker.isValid()) return;
    moveToRange(editor, range);
    showAfterScroll(editor, range);
  }

  public static void moveToRange(@NotNull Editor editor, @NotNull Range range) {
    final Document document = editor.getDocument();
    int targetLine = !range.hasLines() ? range.getLine2() : range.getLine2() - 1;
    int line = Math.min(targetLine, getLineCount(document) - 1);
    int lastOffset = document.getLineStartOffset(line);
    editor.getCaretModel().moveToOffset(lastOffset);
    editor.getScrollingModel().scrollToCaret(ScrollType.CENTER);
  }

  public void showAfterScroll(@NotNull Editor editor, @NotNull Range range) {
    editor.getScrollingModel().runActionOnScrollingFinished(() -> {
      reopenRange(editor, range, null);
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

    JComponent editorComponent = null;
    if (range.hasVcsLines()) {
      String content = getVcsContent(range).toString();
      EditorTextField textField = LineStatusMarkerPopupPanel.createTextField(editor, content);

      LineStatusMarkerPopupPanel.installBaseEditorSyntaxHighlighters(myTracker.getProject(), textField, myTracker.getVcsDocument(),
                                                                     getVcsTextRange(range), getFileType());

      installWordDiff(editor, textField, range, disposable);

      editorComponent = LineStatusMarkerPopupPanel.createEditorComponent(editor, textField);
    }

    List<AnAction> actions = createToolbarActions(editor, range, mousePosition);
    ActionToolbar toolbar = LineStatusMarkerPopupPanel.buildToolbar(editor, actions, disposable);

    JComponent additionalInfoPanel = createAdditionalInfoPanel(editor, range, mousePosition, disposable);

    LineStatusMarkerPopupPanel.showPopupAt(editor, toolbar, editorComponent, additionalInfoPanel, mousePosition, disposable, null);
  }

  protected void reopenRange(@NotNull Editor editor, @NotNull Range range, @Nullable Point mousePosition) {
    Range newRange = myTracker.findRange(range);
    if (newRange != null) {
      showHintAt(editor, newRange, mousePosition);
    } else {
      HintManagerImpl.getInstanceImpl().hideHints(HintManager.HIDE_BY_SCROLLING, false, false);
    }
  }

  private void installWordDiff(@NotNull Editor editor,
                               @NotNull EditorTextField textField,
                               @NotNull Range range,
                               @NotNull Disposable disposable) {
    if (!DiffApplicationSettings.getInstance().SHOW_LST_WORD_DIFFERENCES) return;
    if (!range.hasLines() || !range.hasVcsLines()) return;

    CharSequence vcsContent = getVcsContent(range);
    CharSequence currentContent = getCurrentContent(range);
    int currentStartOffset = getCurrentTextRange(range).getStartOffset();

    List<DiffFragment> wordDiff = BackgroundTaskUtil.tryComputeFast(
      indicator -> ByWord.compare(vcsContent, currentContent, ComparisonPolicy.DEFAULT, indicator), 200);
    if (wordDiff == null) return;

    LineStatusMarkerPopupPanel.installMasterEditorWordHighlighters(editor, currentStartOffset, wordDiff, disposable);
    LineStatusMarkerPopupPanel.installPopupEditorWordHighlighters(textField, wordDiff);
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

    public RangeMarkerAction(@NotNull Editor editor, @NotNull Range range, @Nullable @NonNls String actionId) {
      myRange = range;
      myEditor = editor;
      if (actionId != null) ActionUtil.copyFrom(this, actionId);
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      Range newRange = myTracker.findRange(myRange);
      e.getPresentation().setEnabled(newRange != null && !myEditor.isDisposed() && isEnabled(myEditor, newRange));
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      Range newRange = myTracker.findRange(myRange);
      if (newRange != null) actionPerformed(myEditor, newRange);
    }

    protected abstract boolean isEnabled(@NotNull Editor editor, @NotNull Range range);

    protected abstract void actionPerformed(@NotNull Editor editor, @NotNull Range range);
  }

  public class ShowNextChangeMarkerAction extends RangeMarkerAction implements LightEditCompatible {
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
      if (targetRange != null) {
        scrollAndShow(editor, targetRange);
      }
    }
  }

  public class ShowPrevChangeMarkerAction extends RangeMarkerAction implements LightEditCompatible {
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
      if (targetRange != null) {
        scrollAndShow(editor, targetRange);
      }
    }
  }

  public class CopyLineStatusRangeAction extends RangeMarkerAction implements LightEditCompatible {
    public CopyLineStatusRangeAction(@NotNull Editor editor, @NotNull Range range) {
      super(editor, range, IdeActions.ACTION_COPY);
    }

    @Override
    protected boolean isEnabled(@NotNull Editor editor, @NotNull Range range) {
      return range.hasVcsLines();
    }

    @Override
    protected void actionPerformed(@NotNull Editor editor, @NotNull Range range) {
      final String content = getVcsContent(range) + "\n";
      CopyPasteManager.getInstance().setContents(new StringSelection(content));
    }
  }

  public class ShowLineStatusRangeDiffAction extends RangeMarkerAction implements LightEditCompatible {
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

      SimpleDiffRequest request = new SimpleDiffRequest(DiffBundle.message("dialog.title.diff.for.range"),
                                                        vcsContent, currentContent,
                                                        DiffBundle.message("diff.content.title.up.to.date"),
                                                        DiffBundle.message("diff.content.title.current.range"));

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

  public class ToggleByWordDiffAction extends ToggleAction implements DumbAware, LightEditCompatible {
    @NotNull private final Editor myEditor;
    @NotNull private final Range myRange;
    @Nullable private final Point myMousePosition;

    public ToggleByWordDiffAction(@NotNull Editor editor,
                                  @NotNull Range range,
                                  @Nullable Point position) {
      super(DiffBundle.message("highlight.words"), null, AllIcons.Actions.Highlighting);
      myEditor = editor;
      myRange = range;
      myMousePosition = position;
    }

    @Override
    public boolean isSelected(@NotNull AnActionEvent e) {
      return DiffApplicationSettings.getInstance().SHOW_LST_WORD_DIFFERENCES;
    }

    @Override
    public void setSelected(@NotNull AnActionEvent e, boolean state) {
      if (!myTracker.isValid()) return;
      DiffApplicationSettings.getInstance().SHOW_LST_WORD_DIFFERENCES = state;

      reopenRange(myEditor, myRange, myMousePosition);
    }
  }
}
