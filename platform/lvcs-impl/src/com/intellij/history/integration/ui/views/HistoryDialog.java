// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.history.integration.ui.views;

import com.intellij.CommonBundle;
import com.intellij.diff.requests.ContentDiffRequest;
import com.intellij.diff.util.DiffUtil;
import com.intellij.history.core.LocalHistoryFacade;
import com.intellij.history.core.changes.ChangeSet;
import com.intellij.history.integration.IdeaGateway;
import com.intellij.history.integration.LocalHistoryImpl;
import com.intellij.history.integration.revertion.Reverter;
import com.intellij.history.integration.ui.models.FileDifferenceModel;
import com.intellij.history.integration.ui.models.HistoryDialogModel;
import com.intellij.history.integration.ui.models.RevisionItem;
import com.intellij.history.utils.LocalHistoryLog;
import com.intellij.icons.AllIcons;
import com.intellij.ide.actions.ContextHelpAction;
import com.intellij.ide.actions.RevealFileAction;
import com.intellij.ide.ui.SplitterProportionsDataImpl;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diff.impl.patch.FilePatch;
import com.intellij.openapi.diff.impl.patch.IdeaTextPatchBuilder;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.*;
import com.intellij.openapi.ui.popup.Balloon;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.ChangesUtil;
import com.intellij.openapi.vcs.changes.CommitContext;
import com.intellij.openapi.vcs.changes.patch.CreatePatchConfigurationPanel;
import com.intellij.openapi.vcs.changes.patch.PatchWriter;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ex.IdeFocusTraversalPolicy;
import com.intellij.platform.lvcs.impl.statistics.LocalHistoryCounter;
import com.intellij.project.ProjectKt;
import com.intellij.ui.*;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.components.JBLoadingPanel;
import com.intellij.util.Consumer;
import com.intellij.util.ui.update.MergingUpdateQueue;
import com.intellij.util.ui.update.Update;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;

import static com.intellij.history.integration.LocalHistoryBundle.message;
import static com.intellij.openapi.vcs.changes.patch.PatchWriter.writeAsPatchToClipboard;

public abstract class HistoryDialog<T extends HistoryDialogModel> extends FrameWrapper {
  private static final int UPDATE_DIFFS = 1;
  private static final int UPDATE_REVS = UPDATE_DIFFS + 1;

  protected final @NotNull Project myProject;
  protected final IdeaGateway myGateway;
  protected final VirtualFile myFile;
  private Splitter mySplitter;
  protected RevisionsList myRevisionsList;
  private JBLoadingPanel myDiffView;
  private ActionToolbar myToolBar;
  protected boolean myForceUpdateDiff;

  protected T myModel;

  private MergingUpdateQueue myUpdateQueue;
  private boolean isUpdating;

  protected HistoryDialog(@NotNull Project project, IdeaGateway gw, VirtualFile f, boolean doInit) {
    super(project);

    myProject = project;
    myGateway = gw;
    myFile = f;

    setImages(DiffUtil.DIFF_FRAME_ICONS.getValue());
    closeOnEsc();

    if (doInit) {
      init();
    }
  }

  @Override
  protected @Nullable String getDimensionKey() {
    return getClass().getName();
  }

  protected void init() {
    LocalHistoryFacade facade = LocalHistoryImpl.getInstanceImpl().getFacade();

    myModel = createModel(facade);
    setTitle(myModel.getTitle());
    JComponent root = createComponent();
    setComponent(root);

    setPreferredFocusedComponent(showRevisionsList() ? myRevisionsList.getComponent() : myDiffView);

    myUpdateQueue = new MergingUpdateQueue(getClass() + ".revisionsUpdate", 500, true, root, this, null, false);
    myUpdateQueue.setRestartTimerOnAdd(true);

    facade.addListener(new LocalHistoryFacade.Listener() {
      @Override
      public void changeSetFinished(@NotNull ChangeSet changeSet) {
        scheduleRevisionsUpdate(null);
      }
    }, this);

    scheduleRevisionsUpdate(null);
  }

  protected void scheduleRevisionsUpdate(final @Nullable Consumer<? super T> configRunnable) {
    doScheduleUpdate(UPDATE_REVS, () -> {
      synchronized (myModel) {
        if (configRunnable != null) configRunnable.consume(myModel);
        myModel.clearRevisions();
        LocalHistoryCounter.INSTANCE.logLoadItems(myProject, myModel.getKind(), () -> {
          return myModel.getRevisions();// force load
        });
      }
      return () -> myRevisionsList.updateData(myModel);
    });
  }

  protected List<RevisionItem> getRevisions() {
    return myModel == null ? Collections.emptyList() : myModel.getRevisions();
  }

  protected abstract T createModel(LocalHistoryFacade vcs);

  protected JComponent createComponent() {
    JPanel root = new JPanel(new BorderLayout());

    ExcludingTraversalPolicy traversalPolicy = new ExcludingTraversalPolicy();
    root.setFocusTraversalPolicy(traversalPolicy);
    root.setFocusTraversalPolicyProvider(true);

    Pair<JComponent, Dimension> diffAndToolbarSize = createDiffPanel(root, traversalPolicy);

    myDiffView = new JBLoadingPanel(new BorderLayout(), this, 200);
    myDiffView.add(diffAndToolbarSize.first, BorderLayout.CENTER);

    JComponent revisionsSide = createRevisionsSide(diffAndToolbarSize.second);

    if (showRevisionsList()) {
      mySplitter = new Splitter(false, 0.3f);

      mySplitter.setFirstComponent(revisionsSide);
      mySplitter.setSecondComponent(myDiffView);

      restoreSplitterProportion();

      root.add(mySplitter);
      setDiffBorder(IdeBorderFactory.createBorder(SideBorder.TOP | SideBorder.LEFT));
    }
    else {
      setDiffBorder(IdeBorderFactory.createBorder(SideBorder.TOP | SideBorder.BOTTOM));
      root.add(myDiffView);
    }

    return root;
  }

  protected boolean showRevisionsList() {
    return true;
  }

  protected abstract void setDiffBorder(Border border);

  @Override
  public void dispose() {
    saveSplitterProportion();
    super.dispose();
  }

  protected abstract Pair<JComponent, Dimension> createDiffPanel(JPanel root, ExcludingTraversalPolicy traversalPolicy);

  private @NotNull JComponent createRevisionsSide(Dimension prefToolBarSize) {
    ActionGroup actions = createRevisionsActions();

    myToolBar = createRevisionsToolbar(actions);
    myRevisionsList = new RevisionsList(new RevisionsList.SelectionListener() {
      @Override
      public void revisionsSelected(final int first, final int last) {
        scheduleDiffUpdate(Couple.of(first, last));
      }
    });
    myToolBar.setTargetComponent(myRevisionsList.getComponent());
    PopupHandler.installPopupMenu(myRevisionsList.getComponent(), actions, "LvcsRevisionsListPopup");


    JPanel result = new JPanel(new BorderLayout());
    JPanel toolBarPanel = new JPanel(new BorderLayout());
    toolBarPanel.add(myToolBar.getComponent(), BorderLayout.WEST);
    addExtraToolbar(toolBarPanel);
    if (prefToolBarSize != null) {
      toolBarPanel.setPreferredSize(new Dimension(1, prefToolBarSize.height));
    }
    result.add(toolBarPanel, BorderLayout.NORTH);
    JScrollPane scrollPane = ScrollPaneFactory.createScrollPane(myRevisionsList.getComponent());
    scrollPane.setBorder(IdeBorderFactory.createBorder(SideBorder.TOP | SideBorder.RIGHT));
    result.add(scrollPane, BorderLayout.CENTER);

    return result;
  }

  protected void addExtraToolbar(JPanel toolBarPanel) {
  }

  private static @NotNull ActionToolbar createRevisionsToolbar(ActionGroup actions) {
    ActionManager am = ActionManager.getInstance();
    return am.createActionToolbar("HistoryDialogRevisions", actions, true);
  }

  private @NotNull ActionGroup createRevisionsActions() {
    DefaultActionGroup result = new DefaultActionGroup();
    result.add(new RevertAction());
    result.add(new CreatePatchAction());
    result.add(Separator.getInstance());
    result.add(new ContextHelpAction(getHelpId()));
    return result;
  }

  private void scheduleDiffUpdate(final @Nullable Couple<Integer> toSelect) {
    doScheduleUpdate(UPDATE_DIFFS, () -> {
      synchronized (myModel) {
        boolean changed = toSelect == null ? myModel.resetSelection() : myModel.selectRevisions(toSelect.first, toSelect.second);
        changed |= myForceUpdateDiff;
        myForceUpdateDiff = false;
        if (changed) {
          return LocalHistoryCounter.INSTANCE.logLoadDiff(myProject, myModel.getKind(), () -> {
            return doUpdateDiffs(myModel);
          });
        }
        return EmptyRunnable.getInstance();
      }
    });
  }

  private void doScheduleUpdate(int id, final Computable<? extends Runnable> update) {
    myUpdateQueue.queue(new Update(this, id) {
      @Override
      public boolean canEat(@NotNull Update update1) {
        return getPriority() >= update1.getPriority();
      }

      @Override
      public void run() {
        if (isDisposed() || myProject.isDisposed()) return;

        ApplicationManager.getApplication().invokeAndWait(() -> {
          if (isDisposed() || myProject.isDisposed()) return;

          isUpdating = true;
          updateActions();
          myDiffView.startLoading();
        });

        Runnable apply = null;
        try {
          apply = update.compute();
        }
        catch (Exception e) {
          LocalHistoryLog.LOG.error(e);
        }

        final Runnable finalApply = apply;
        ApplicationManager.getApplication().invokeAndWait(() -> {
          if (isDisposed() || myProject.isDisposed()) return;

          isUpdating = false;
          if (finalApply != null) {
            try {
              finalApply.run();
            }
            catch (Exception e) {
              LocalHistoryLog.LOG.error(e);
            }
          }
          updateActions();
          myDiffView.stopLoading();
        });
      }
    });
  }

  protected void updateActions() {
    if (showRevisionsList()) {
      myToolBar.updateActionsImmediately();
    }
  }

  protected abstract Runnable doUpdateDiffs(T model);

  protected ContentDiffRequest createDifference(FileDifferenceModel m) {
    return ProgressManager.getInstance().run(new Task.WithResult<>(myProject, message("message.processing.revisions"), false) {
      @Override
      protected ContentDiffRequest compute(@NotNull ProgressIndicator indicator) {
        indicator.setIndeterminate(false);
        RevisionProcessingProgressAdapter p = new RevisionProcessingProgressAdapter(indicator);
        return FileDifferenceModel.createRequest(m, p);
      }
    });
  }

  private void saveSplitterProportion() {
    SplitterProportionsData d = new SplitterProportionsDataImpl();
    d.saveSplitterProportions(mySplitter);
    d.externalizeToDimensionService(getDimensionKey());
  }

  private void restoreSplitterProportion() {
    SplitterProportionsData d = new SplitterProportionsDataImpl();
    d.externalizeFromDimensionService(getDimensionKey());
    d.restoreSplitterProportions(mySplitter);
  }

  //todo
  protected abstract String getHelpId();

  protected void revert() {
    revert(myModel.createReverter());
  }

  private boolean isRevertEnabled() {
    return myModel.isRevertEnabled();
  }

  protected void revert(Reverter r) {
    try {
      List<String> errors = r.checkCanRevert();
      if (!errors.isEmpty()) {
        showError(message("message.cannot.revert.because", formatErrors(errors)));
        return;
      }
      r.revert();

      showNotification(r.getCommandName());
    }
    catch (Exception e) {
      showError(message("message.error.during.revert", e));
    }
  }

  private void showNotification(@NlsContexts.PopupContent String title) {
    SwingUtilities.invokeLater(() -> {
      final Balloon b =
        JBPopupFactory.getInstance().createHtmlTextBalloonBuilder(title, null, MessageType.INFO.getPopupBackground(), null)
          .setFadeoutTime(3000)
          .setShowCallout(false)
          .createBalloon();

      Dimension size = myDiffView.getSize();
      RelativePoint point = new RelativePoint(myDiffView, new Point(size.width / 2, size.height / 2));
      b.show(point, Balloon.Position.above);
    });
  }

  private static String formatErrors(@NotNull List<String> errors) {
    if (errors.size() == 1) return errors.get(0);

    StringBuilder result = new StringBuilder();
    for (String e : errors) {
      result.append("\n    -").append(e);
    }
    return result.toString();
  }

  private boolean isCreatePatchEnabled() {
    return myModel.isCreatePatchEnabled();
  }

  private void createPatch() {
    try {
      if (!myModel.canPerformCreatePatch()) {
        showError(message("message.cannot.create.patch.because.of.unavailable.content"));
        return;
      }

      CreatePatchConfigurationPanel p = new CreatePatchConfigurationPanel(myProject);
      p.setFileName(getDefaultPatchFile());
      p.setCommonParentPath(ChangesUtil.findCommonAncestor(myModel.getChanges()));
      if (!showAsDialog(p)) return;

      Path base = Paths.get(p.getBaseDirName());
      List<FilePatch> patches = IdeaTextPatchBuilder.buildPatch(myProject, myModel.getChanges(), base, p.isReversePatch(), false);
      if (p.isToClipboard()) {
        writeAsPatchToClipboard(myProject, patches, base, new CommitContext());
        showNotification(message("message.patch.copied.to.clipboard"));
      }
      else {
        Path file = Paths.get(p.getFileName());
        PatchWriter.writePatches(myProject, file, base, patches, null, p.getEncoding());
        showNotification(message("message.patch.created"));
        RevealFileAction.openFile(file);
      }
    }
    catch (VcsException | IOException e) {
      showError(message("message.error.during.create.patch", e));
    }
  }

  private @NotNull Path getDefaultPatchFile() {
    return FileUtil.findSequentNonexistentFile(ProjectKt.getStateStore(myProject).getProjectBasePath().toFile(), "local_history", "patch")
      .toPath();
  }

  private boolean showAsDialog(CreatePatchConfigurationPanel p) {
    final DialogWrapper dialogWrapper = new MyDialogWrapper(myProject, p);
    dialogWrapper.setTitle(message("create.patch.dialog.title"));
    dialogWrapper.setModal(true);
    dialogWrapper.show();
    return dialogWrapper.getExitCode() == DialogWrapper.OK_EXIT_CODE;
  }


  public void showError(@NlsContexts.DialogMessage String s) {
    Messages.showErrorDialog(myProject, s, CommonBundle.getErrorTitle());
  }

  protected abstract class MyAction extends AnAction {
    protected MyAction(@NlsActions.ActionText String text, @NlsActions.ActionDescription String description, Icon icon) {
      super(text, description, icon);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      doPerform(myModel);
    }

    protected abstract void doPerform(T model);

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
      return ActionUpdateThread.EDT;
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      Presentation p = e.getPresentation();
      p.setEnabled(isEnabled());
    }

    private boolean isEnabled() {
      return !isUpdating && isEnabled(myModel);
    }

    protected abstract boolean isEnabled(T model);

    public void performIfEnabled() {
      if (isEnabled()) doPerform(myModel);
    }
  }

  private final class RevertAction extends MyAction {
    RevertAction() {
      super(message("action.revert"), null, AllIcons.Actions.Rollback);
    }

    @Override
    protected void doPerform(T model) {
      LocalHistoryCounter.INSTANCE.logActionInvoked(LocalHistoryCounter.ActionKind.RevertRevisions, myModel.getKind());
      revert();
    }

    @Override
    protected boolean isEnabled(T model) {
      return isRevertEnabled();
    }
  }

  private final class CreatePatchAction extends MyAction {
    CreatePatchAction() {
      super(message("action.create.patch"), null, AllIcons.Vcs.Patch);
    }

    @Override
    protected void doPerform(T model) {
      LocalHistoryCounter.INSTANCE.logActionInvoked(LocalHistoryCounter.ActionKind.CreatePatch, myModel.getKind());
      createPatch();
    }

    @Override
    protected boolean isEnabled(T model) {
      return isCreatePatchEnabled();
    }
  }

  private static final class MyDialogWrapper extends DialogWrapper {
    private final @NotNull CreatePatchConfigurationPanel myPanel;

    private MyDialogWrapper(@Nullable Project project, @NotNull CreatePatchConfigurationPanel centralPanel) {
      super(project, true);
      myPanel = centralPanel;
      init();
    }

    @Override
    protected @NotNull JComponent createCenterPanel() {
      return myPanel.getPanel();
    }

    @Override
    public @Nullable JComponent getPreferredFocusedComponent() {
      return IdeFocusTraversalPolicy.getPreferredFocusedComponent(myPanel.getPanel());
    }
  }
}
