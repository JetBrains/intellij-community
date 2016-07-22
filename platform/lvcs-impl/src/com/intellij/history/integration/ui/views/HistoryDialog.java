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

package com.intellij.history.integration.ui.views;

import com.intellij.CommonBundle;
import com.intellij.diff.contents.DiffContent;
import com.intellij.diff.requests.ContentDiffRequest;
import com.intellij.diff.requests.SimpleDiffRequest;
import com.intellij.history.core.LocalHistoryFacade;
import com.intellij.history.integration.IdeaGateway;
import com.intellij.history.integration.LocalHistoryBundle;
import com.intellij.history.integration.LocalHistoryImpl;
import com.intellij.history.integration.revertion.Reverter;
import com.intellij.history.integration.ui.models.FileDifferenceModel;
import com.intellij.history.integration.ui.models.HistoryDialogModel;
import com.intellij.history.integration.ui.models.RevisionProcessingProgress;
import com.intellij.history.utils.LocalHistoryLog;
import com.intellij.icons.AllIcons;
import com.intellij.ide.actions.ContextHelpAction;
import com.intellij.ide.actions.ShowFilePathAction;
import com.intellij.ide.ui.SplitterProportionsDataImpl;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.help.HelpManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.*;
import com.intellij.openapi.ui.popup.Balloon;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.ChangesUtil;
import com.intellij.openapi.vcs.changes.patch.CreatePatchConfigurationPanel;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ex.IdeFocusTraversalPolicy;
import com.intellij.ui.*;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.components.JBLayeredPane;
import com.intellij.util.Consumer;
import com.intellij.util.ImageLoader;
import com.intellij.util.ui.AbstractLayoutManager;
import com.intellij.util.ui.AnimatedIcon;
import com.intellij.util.ui.AsyncProcessIcon;
import com.intellij.util.ui.update.MergingUpdateQueue;
import com.intellij.util.ui.update.Update;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.List;

import static com.intellij.history.integration.LocalHistoryBundle.message;

public abstract class HistoryDialog<T extends HistoryDialogModel> extends FrameWrapper {
  private static final int UPDATE_DIFFS = 1;
  private static final int UPDATE_REVS = UPDATE_DIFFS + 1;

  protected final Project myProject;
  protected final IdeaGateway myGateway;
  protected final VirtualFile myFile;
  private Splitter mySplitter;
  private RevisionsList myRevisionsList;
  private MyDiffContainer myDiffView;
  private ActionToolbar myToolBar;

  private T myModel;

  private MergingUpdateQueue myUpdateQueue;
  private boolean isUpdating;

  protected HistoryDialog(@NotNull Project project, IdeaGateway gw, VirtualFile f, boolean doInit) {
    super(project);
    myProject = project;
    myGateway = gw;
    myFile = f;

    setProject(project);
    setDimensionKey(getPropertiesKey());
    setImage(ImageLoader.loadFromResource("/diff/Diff.png"));
    closeOnEsc();

    if (doInit) init();
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
      public void changeSetFinished() {
        scheduleRevisionsUpdate(null);
      }
    }, this);

    scheduleRevisionsUpdate(null);
  }

  protected void scheduleRevisionsUpdate(@Nullable final Consumer<T> configRunnable) {
    doScheduleUpdate(UPDATE_REVS, () -> {
      synchronized (myModel) {
        if (configRunnable != null) configRunnable.consume(myModel);
        myModel.clearRevisions();
        myModel.getRevisions();// force load
      }
      return () -> myRevisionsList.updateData(myModel);
    });
  }

  protected abstract T createModel(LocalHistoryFacade vcs);

  protected JComponent createComponent() {
    JPanel root = new JPanel(new BorderLayout());

    ExcludingTraversalPolicy traversalPolicy = new ExcludingTraversalPolicy();
    root.setFocusTraversalPolicy(traversalPolicy);
    root.setFocusTraversalPolicyProvider(true);

    Pair<JComponent, Dimension> diffAndToolbarSize = createDiffPanel(root, traversalPolicy);
    myDiffView = new MyDiffContainer(diffAndToolbarSize.first);
    Disposer.register(this, myDiffView);

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

  private JComponent createRevisionsSide(Dimension prefToolBarSize) {
    ActionGroup actions = createRevisionsActions();

    myToolBar = createRevisionsToolbar(actions);
    myRevisionsList = new RevisionsList(new RevisionsList.SelectionListener() {
      public void revisionsSelected(final int first, final int last) {
        scheduleDiffUpdate(Couple.of(first, last));
      }
    });
    addPopupMenuToComponent(myRevisionsList.getComponent(), actions);


    JPanel result = new JPanel(new BorderLayout());
    JPanel toolBarPanel = new JPanel(new BorderLayout());
    toolBarPanel.add(myToolBar.getComponent());
    if (prefToolBarSize != null) {
      toolBarPanel.setPreferredSize(new Dimension(1, prefToolBarSize.height));
    }
    result.add(toolBarPanel, BorderLayout.NORTH);
    JScrollPane scrollPane = ScrollPaneFactory.createScrollPane(myRevisionsList.getComponent());
    scrollPane.setBorder(IdeBorderFactory.createBorder(SideBorder.TOP | SideBorder.RIGHT));
    result.add(scrollPane, BorderLayout.CENTER);

    return result;
  }

  private ActionToolbar createRevisionsToolbar(ActionGroup actions) {
    ActionManager am = ActionManager.getInstance();
    return am.createActionToolbar(ActionPlaces.UNKNOWN, actions, true);
  }

  private ActionGroup createRevisionsActions() {
    DefaultActionGroup result = new DefaultActionGroup();
    result.add(new RevertAction());
    result.add(new CreatePatchAction());
    result.add(Separator.getInstance());
    result.add(new ContextHelpAction(getHelpId()));
    return result;
  }

  private void addPopupMenuToComponent(JComponent comp, final ActionGroup ag) {
    comp.addMouseListener(new PopupHandler() {
      public void invokePopup(Component c, int x, int y) {
        ActionPopupMenu m = createPopupMenu(ag);
        m.getComponent().show(c, x, y);
      }
    });
  }

  private ActionPopupMenu createPopupMenu(ActionGroup ag) {
    ActionManager m = ActionManager.getInstance();
    return m.createActionPopupMenu(ActionPlaces.UNKNOWN, ag);
  }

  private void scheduleDiffUpdate(@Nullable final Couple<Integer> toSelect) {
    doScheduleUpdate(UPDATE_DIFFS, () -> {
      synchronized (myModel) {
        if (toSelect == null) {
          myModel.resetSelection();
        }
        else {
          myModel.selectRevisions(toSelect.first, toSelect.second);
        }
        return doUpdateDiffs(myModel);
      }
    });
  }

  private void doScheduleUpdate(int id, final Computable<Runnable> update) {
    myUpdateQueue.queue(new Update(HistoryDialog.this, id) {
      @Override
      public boolean canEat(Update update1) {
        return getPriority() >= update1.getPriority();
      }

      public void run() {
        if (isDisposed() || myProject.isDisposed()) return;

        invokeAndWait(() -> {
          if (isDisposed() || myProject.isDisposed()) return;

          isUpdating = true;
          updateActions();
          myDiffView.startUpdating();
        });

        Runnable apply = null;
        try {
          apply = update.compute();
        }
        catch (Exception e) {
          LocalHistoryLog.LOG.error(e);
        }

        final Runnable finalApply = apply;
        invokeAndWait(() -> {
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
          myDiffView.finishUpdating();
        });
      }
    });
  }

  private void invokeAndWait(Runnable runnable) {
    try {
      if (SwingUtilities.isEventDispatchThread()) {
        runnable.run();
      }
      else {
        SwingUtilities.invokeAndWait(runnable);
      }
    }
    catch (InterruptedException e) {
      throw new RuntimeException(e);
    }
    catch (InvocationTargetException e) {
      throw new RuntimeException(e);
    }
  }

  protected void updateActions() {
    if (showRevisionsList()) {
      myToolBar.updateActionsImmediately();
    }
  }

  protected abstract Runnable doUpdateDiffs(T model);

  protected ContentDiffRequest createDifference(final FileDifferenceModel m) {
    final Ref<ContentDiffRequest> requestRef = new Ref<>();

    new Task.Modal(myProject, message("message.processing.revisions"), false) {
      public void run(@NotNull final ProgressIndicator i) {
        ApplicationManager.getApplication().runReadAction(() -> {
          RevisionProcessingProgressAdapter p = new RevisionProcessingProgressAdapter(i);
          p.processingLeftRevision();
          DiffContent left = m.getLeftDiffContent(p);

          p.processingRightRevision();
          DiffContent right = m.getRightDiffContent(p);

          requestRef.set(new SimpleDiffRequest(m.getTitle(), left, right, m.getLeftTitle(p), m.getRightTitle(p)));
        });
      }
    }.queue();

    return requestRef.get();
  }

  private void saveSplitterProportion() {
    SplitterProportionsData d = createSplitterData();
    d.saveSplitterProportions(mySplitter);
    d.externalizeToDimensionService(getPropertiesKey());
  }

  private void restoreSplitterProportion() {
    SplitterProportionsData d = createSplitterData();
    d.externalizeFromDimensionService(getPropertiesKey());
    d.restoreSplitterProportions(mySplitter);
  }

  private SplitterProportionsData createSplitterData() {
    return new SplitterProportionsDataImpl();
  }

  protected String getPropertiesKey() {
    return getClass().getName();
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
      if (!askForProceeding(r)) return;

      List<String> errors = r.checkCanRevert();
      if (!errors.isEmpty()) {
        showError(message("message.cannot.revert.because", formatErrors(errors)));
        return;
      }
      r.revert();

      showNotification(r.getCommandName());
    }
    catch (IOException e) {
      showError(message("message.error.during.revert", e));
    }
  }

  private boolean askForProceeding(Reverter r) throws IOException {
    List<String> questions = r.askUserForProceeding();
    if (questions.isEmpty()) return true;

    return Messages.showYesNoDialog(myProject, message("message.do.you.want.to.proceed", formatQuestions(questions)),
                                    CommonBundle.getWarningTitle(), Messages.getWarningIcon()) == Messages.YES;
  }

  private String formatQuestions(List<String> questions) {
    // format into something like this:
    // 1) message one
    // message one continued
    // 2) message two
    // message one continued
    // ...

    if (questions.size() == 1) return questions.get(0);

    String result = "";
    for (int i = 0; i < questions.size(); i++) {
      result += (i + 1) + ") " + questions.get(i) + "\n";
    }
    return result.substring(0, result.length() - 1);
  }

  private void showNotification(final String title) {
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

  private String formatErrors(List<String> errors) {
    if (errors.size() == 1) return errors.get(0);

    String result = "";
    for (String e : errors) {
      result += "\n    -" + e;
    }
    return result;
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
      myModel.createPatch(p.getFileName(), p.getBaseDirName(), p.isReversePatch(), p.getEncoding());

      showNotification(LocalHistoryBundle.message("message.patch.created"));
      ShowFilePathAction.openFile(new File(p.getFileName()));
    }
    catch (VcsException e) {
      showError(message("message.error.during.create.patch", e));
    }
    catch (IOException e) {
      showError(message("message.error.during.create.patch", e));
    }
  }

  private File getDefaultPatchFile() {
    return FileUtil.findSequentNonexistentFile(new File(myProject.getBasePath()), "local_history", "patch");
  }

  private boolean showAsDialog(CreatePatchConfigurationPanel p) {
    final DialogWrapper dialogWrapper = new MyDialogWrapper(myProject, p);
    dialogWrapper.setTitle(message("create.patch.dialog.title"));
    dialogWrapper.setModal(true);
    dialogWrapper.show();
    return dialogWrapper.getExitCode() == DialogWrapper.OK_EXIT_CODE;
  }


  public void showError(String s) {
    Messages.showErrorDialog(myProject, s, CommonBundle.getErrorTitle());
  }

  protected void showHelp() {
    HelpManager.getInstance().invokeHelp(getHelpId());
  }

  protected abstract class MyAction extends AnAction {
    protected MyAction(String text, String description, Icon icon) {
      super(text, description, icon);
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      doPerform(myModel);
    }

    protected abstract void doPerform(T model);

    @Override
    public void update(AnActionEvent e) {
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

  private class RevertAction extends MyAction {
    public RevertAction() {
      super(message("action.revert"), null, AllIcons.Actions.Rollback);
    }

    @Override
    protected void doPerform(T model) {
      revert();
    }

    @Override
    protected boolean isEnabled(T model) {
      return isRevertEnabled();
    }
  }

  private class CreatePatchAction extends MyAction {
    public CreatePatchAction() {
      super(message("action.create.patch"), null, AllIcons.Actions.CreatePatch);
    }

    @Override
    protected void doPerform(T model) {
      createPatch();
    }

    @Override
    protected boolean isEnabled(T model) {
      return isCreatePatchEnabled();
    }
  }

  private static class RevisionProcessingProgressAdapter implements RevisionProcessingProgress {
    private final ProgressIndicator myIndicator;

    public RevisionProcessingProgressAdapter(ProgressIndicator i) {
      myIndicator = i;
    }

    public void processingLeftRevision() {
      myIndicator.setText(message("message.processing.left.revision"));
    }

    public void processingRightRevision() {
      myIndicator.setText(message("message.processing.right.revision"));
    }

    public void processed(int percentage) {
      myIndicator.setFraction(percentage / 100.0);
    }
  }

  private static class MyDiffContainer extends JBLayeredPane implements Disposable {
    private AnimatedIcon myIcon = new AsyncProcessIcon.Big(this.getClass().getName());

    private JComponent myContent;
    private JComponent myLoadingPanel;

    private MyDiffContainer(JComponent content) {
      setLayout(new MyOverlayLayout());
      myContent = content;
      myLoadingPanel = new JPanel(new MyPanelLayout());
      myLoadingPanel.setOpaque(false);
      myLoadingPanel.add(myIcon);

      add(myContent);
      add(myLoadingPanel, JLayeredPane.POPUP_LAYER);

      finishUpdating();
    }

    public void dispose() {
      myIcon.dispose();
    }

    public void startUpdating() {
      myLoadingPanel.setVisible(true);
      myIcon.resume();
    }

    public void finishUpdating() {
      myIcon.suspend();
      myLoadingPanel.setVisible(false);
    }

    private class MyOverlayLayout extends AbstractLayoutManager {
      public void layoutContainer(Container parent) {
        myContent.setBounds(0, 0, getWidth(), getHeight());
        myLoadingPanel.setBounds(0, 0, getWidth(), getHeight());
      }

      public Dimension preferredLayoutSize(Container parent) {
        return myContent.getPreferredSize();
      }
    }

    private class MyPanelLayout extends AbstractLayoutManager {
      public void layoutContainer(Container parent) {
        Dimension size = myIcon.getPreferredSize();
        myIcon.setBounds((getWidth() - size.width) / 2, (getHeight() - size.height) / 2, size.width, size.height);
      }

      public Dimension preferredLayoutSize(Container parent) {
        return myContent.getPreferredSize();
      }
    }
  }

  private static class MyDialogWrapper extends DialogWrapper {
    @NotNull private final CreatePatchConfigurationPanel myPanel;

    protected MyDialogWrapper(@Nullable Project project, @NotNull CreatePatchConfigurationPanel centralPanel) {
      super(project, true);
      myPanel = centralPanel;
      init();
      initValidation();
    }

    @Nullable
    @Override
    protected JComponent createCenterPanel() {
      return myPanel.getPanel();
    }

    @Nullable
    @Override
    public JComponent getPreferredFocusedComponent() {
      return IdeFocusTraversalPolicy.getPreferredFocusedComponent(myPanel.getPanel());
    }

    @Nullable
    @Override
    protected ValidationInfo doValidate() {
      return myPanel.validateFields();
    }
  }
}
