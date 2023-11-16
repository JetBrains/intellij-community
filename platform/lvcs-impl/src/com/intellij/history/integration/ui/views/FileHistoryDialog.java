// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.history.integration.ui.views;

import com.intellij.diff.DiffManager;
import com.intellij.diff.DiffRequestPanel;
import com.intellij.diff.requests.ContentDiffRequest;
import com.intellij.diff.requests.MessageDiffRequest;
import com.intellij.diff.tools.fragmented.UnifiedDiffPanel;
import com.intellij.diff.tools.util.DiffSplitter;
import com.intellij.find.EditorSearchSession;
import com.intellij.find.SearchTextArea;
import com.intellij.find.editorHeaderActions.Utils;
import com.intellij.history.core.LocalHistoryFacade;
import com.intellij.history.integration.IdeaGateway;
import com.intellij.history.integration.ui.models.EntireFileHistoryDialogModel;
import com.intellij.history.integration.ui.models.FileDifferenceModel;
import com.intellij.history.integration.ui.models.FileHistoryDialogModel;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CustomShortcutSet;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.impl.EditorComponentImpl;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.LoadingDecorator;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.ExcludingTraversalPolicy;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.SideBorder;
import com.intellij.ui.components.ProgressBarLoadingDecorator;
import com.intellij.util.Alarm;
import com.intellij.util.concurrency.annotations.RequiresEdt;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.event.DocumentEvent;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.Future;

public class FileHistoryDialog extends HistoryDialog<FileHistoryDialogModel> {
  private DiffRequestPanel myDiffPanel;
  private SearchTextArea mySearchTextArea;
  private Future<?> myFilterFuture;
  private final Alarm myAlarm = new Alarm(Alarm.ThreadToUse.SWING_THREAD, this);

  public FileHistoryDialog(@NotNull Project p, IdeaGateway gw, VirtualFile f) {
    this(p, gw, f, true);
  }

  protected FileHistoryDialog(@NotNull Project p, IdeaGateway gw, VirtualFile f, boolean doInit) {
    super(p, gw, f, doInit);
  }

  @Override
  protected FileHistoryDialogModel createModel(LocalHistoryFacade vcs) {
    return new EntireFileHistoryDialogModel(myProject, myGateway, vcs, myFile);
  }

  @Override
  protected Pair<JComponent, Dimension> createDiffPanel(JPanel root, ExcludingTraversalPolicy traversalPolicy) {
    myDiffPanel = DiffManager.getInstance().createRequestPanel(myProject, this, ApplicationManager.getApplication().isUnitTestMode() ? null : getFrame());
    myDiffPanel.setRequest(new MessageDiffRequest(""));
    return Pair.create(myDiffPanel.getComponent(), null);
  }

  @Override
  protected void addExtraToolbar(@NotNull JPanel toolBarPanel) {
    mySearchTextArea = new SearchTextArea(new JTextArea(), true);
    mySearchTextArea.setBorder(IdeBorderFactory.createBorder(SideBorder.LEFT | SideBorder.TOP | SideBorder.RIGHT));
    new NextOccurenceAction(true).registerCustomShortcutSet(Utils.shortcutSetOf(ContainerUtil.concat(
      Utils.shortcutsOf(IdeActions.ACTION_FIND_NEXT),
      Utils.shortcutsOf(IdeActions.ACTION_EDITOR_MOVE_CARET_DOWN)
    )), mySearchTextArea);
    new NextOccurenceAction(false).registerCustomShortcutSet(Utils.shortcutSetOf(ContainerUtil.concat(
      Utils.shortcutsOf(IdeActions.ACTION_FIND_PREVIOUS),
      Utils.shortcutsOf(IdeActions.ACTION_EDITOR_MOVE_CARET_UP)
    )), mySearchTextArea);
    new DumbAwareAction() {
      @Override
      public void actionPerformed(@NotNull AnActionEvent e) {
        IdeFocusManager.getInstance(myProject).requestFocus(mySearchTextArea.getTextArea(), true);
      }
    }.registerCustomShortcutSet(Utils.shortcutSetOf(Utils.shortcutsOf(IdeActions.ACTION_FIND)), myRevisionsList.getComponent());
    new DumbAwareAction() {
      @Override
      public void actionPerformed(@NotNull AnActionEvent e) {
        mySearchTextArea.getTextArea().setText("");
        IdeFocusManager.getInstance(myProject).requestFocus(myRevisionsList.getComponent(), true);
      }
    }.registerCustomShortcutSet(new CustomShortcutSet(KeyEvent.VK_ESCAPE), mySearchTextArea.getTextArea());
    LoadingDecorator decorator = new ProgressBarLoadingDecorator(mySearchTextArea, this, 500) {
      @Override
      protected boolean isOnTop() { return false; }
    };
    mySearchTextArea.getTextArea().getDocument().addDocumentListener(new DocumentAdapter() {
      @Override
      protected void textChanged(@NotNull DocumentEvent e) {
        myAlarm.cancelAllRequests();
        myAlarm.addRequest(() -> applyFilterText(StringUtil.nullize(mySearchTextArea.getTextArea().getText()), decorator), 100);
      }
    });
    toolBarPanel.add(decorator.getComponent(), BorderLayout.CENTER);
  }

  @Override
  protected ContentDiffRequest createDifference(FileDifferenceModel m) {
    if (myRevisionsList.isEmpty()) return null;
    ContentDiffRequest request = super.createDifference(m);
    ApplicationManager.getApplication().invokeLater(this::updateEditorSearch, ModalityState.stateForComponent(myRevisionsList.getComponent()));
    return request;
  }

  @RequiresEdt
  private void applyFilterText(@Nullable String filter, @NotNull LoadingDecorator decorator) {
    decorator.stopLoading();
    if (myFilterFuture != null) {
      myFilterFuture.cancel(true);
      myFilterFuture = null;
    }
    if (StringUtil.isEmpty(filter)) {
      applyFilteredRevisions(null);
    }
    else {
      decorator.startLoading(false);
      updateEditorSearch();
      myFilterFuture = ApplicationManager.getApplication().executeOnPooledThread(() -> {
        FileHistoryDialogModel model = myModel;
        Set<Long> revisions;
        if (model != null) {
          revisions = model.filterContents(filter);
        } else {
          revisions = Collections.emptySet();
        }
        decorator.stopLoading();
        UIUtil.invokeLaterIfNeeded(() -> applyFilteredRevisions(revisions));
      });
    }
  }

  private void applyFilteredRevisions(Set<Long> revisions) {
    boolean wasEmpty = myRevisionsList.isEmpty();
    myRevisionsList.setFilteredRevisions(revisions);
    if (wasEmpty != myRevisionsList.isEmpty()) {
      myForceUpdateDiff = true;
    }
    updateEditorSearch();
  }


  private void updateEditorSearch() {
    Editor editor = findLeftEditor();
    if (editor == null) return;
    String filter = mySearchTextArea.getTextArea().getText();
    EditorSearchSession session = EditorSearchSession.get(editor);
    if (StringUtil.isEmpty(filter)) {
      if (session != null) {
        boolean focused = mySearchTextArea.getTextArea().isFocusOwner();
        session.close();
        if (focused) {
          IdeFocusManager.getInstance(myProject).requestFocus(mySearchTextArea.getTextArea(), false);
        }
      }
      return;
    }
    if (session == null) {
      session = EditorSearchSession.start(editor, myProject);
      session.searchForward();
    }
    session.setTextInField(filter);
  }

  private @Nullable Editor findLeftEditor() {
    DiffSplitter splitter = UIUtil.findComponentOfType(myDiffPanel.getComponent(), DiffSplitter.class);
    JComponent editorPanel;
    if (splitter != null) {
      editorPanel = splitter.getFirstComponent();
    }
    else {
      editorPanel = UIUtil.findComponentOfType(myDiffPanel.getComponent(), UnifiedDiffPanel.class);
    }
    EditorComponentImpl comp = editorPanel == null ? null : UIUtil.findComponentOfType(editorPanel, EditorComponentImpl.class);
    return comp == null ? null : comp.getEditor();
  }

  @Override
  protected void setDiffBorder(Border border) {
  }

  @Override
  public void dispose() {
    super.dispose();
    if (myDiffPanel != null) {
      Disposer.dispose(myDiffPanel);
    }
  }

  @Override
  protected Runnable doUpdateDiffs(final @NotNull FileHistoryDialogModel model) {
    final FileDifferenceModel diffModel = model.getDifferenceModel();
    return () -> myDiffPanel.setRequest(createDifference(diffModel));
  }

  @Override
  protected String getHelpId() {
    return "reference.dialogs.showhistory";
  }

  private final class NextOccurenceAction extends DumbAwareAction {
    private final boolean myForward;
    private NextOccurenceAction(boolean forward) {
      myForward = forward;
    }
    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      Editor editor = findLeftEditor();
      if (editor == null) return;
      EditorSearchSession session = EditorSearchSession.get(editor);
      if (session != null && session.hasMatches()) {
        if (session.isLast(myForward)) {
          myRevisionsList.moveSelection(myForward);
        }
        else if (myForward) {
          session.searchForward();
        }
        else {
          session.searchBackward();
        }
      }
    }
  }
}
