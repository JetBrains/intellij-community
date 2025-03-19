// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.dvcs.push;

import com.intellij.diff.util.DiffUtil;
import com.intellij.dvcs.DvcsUtil;
import com.intellij.dvcs.push.ui.*;
import com.intellij.dvcs.repo.Repository;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.options.advanced.AdvancedSettings;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.HtmlChunk;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.ui.CheckedTreeNode;
import com.intellij.util.Function;
import com.intellij.util.concurrency.SequentialTaskExecutor;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.progress.StepsProgressIndicator;
import com.intellij.util.ui.JBUI;
import com.intellij.vcs.commit.PostCommitChecksHandler;
import com.intellij.vcs.log.VcsFullCommitDetails;
import org.jetbrains.annotations.*;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeNode;
import java.awt.event.MouseEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

public final class PushController implements Disposable {
  private static final Logger LOG = Logger.getInstance(PushController.class);

  private final @NotNull Project myProject;
  private final @Nullable PushSource myPushSource;
  private final @NotNull Collection<? extends Repository> myAllRepos;
  private final @NotNull List<? extends Repository> myPreselectedRepositories;
  private final @NotNull List<PushSupport<Repository, PushSource, PushTarget>> myPushSupports;
  private final @NotNull PushLog myPushLog;
  private final @NotNull VcsPushDialog myDialog;
  private final @NotNull ModalityState myModalityState;
  private final @Nullable Repository myCurrentlyOpenedRepository;
  private final boolean mySingleRepoProject;
  private static final int DEFAULT_CHILDREN_PRESENTATION_NUMBER = 20;
  private final @NonNls ExecutorService myExecutorService = SequentialTaskExecutor.createSequentialApplicationPoolExecutor("DVCS Push");

  private final Map<RepositoryNode, MyRepoModel<Repository, PushSource, PushTarget>> myView2Model = new TreeMap<>();

  private boolean myHasCommitWarning;

  public PushController(@NotNull Project project,
                        @NotNull VcsPushDialog dialog,
                        @NotNull Collection<? extends Repository> allRepos,
                        @NotNull List<? extends Repository> preselectedRepositories,
                        @Nullable Repository currentRepo, @Nullable PushSource pushSource) {
    myProject = project;
    myAllRepos = allRepos;
    myPreselectedRepositories = preselectedRepositories;
    myCurrentlyOpenedRepository = currentRepo;
    myPushSource = pushSource;
    myPushSupports = getAffectedSupports();
    mySingleRepoProject = isSingleRepoProject();
    myDialog = dialog;
    myModalityState = ModalityState.stateForComponent(myDialog.getRootPane());
    CheckedTreeNode rootNode = new CheckedTreeNode(null);
    createTreeModel(rootNode);
    myPushLog = new PushLog(myProject, rootNode, myModalityState, isSyncStrategiesAllowed());
    myPushLog.getTree().addPropertyChangeListener(PushLogTreeUtil.EDIT_MODE_PROP, new PropertyChangeListener() {
      @Override
      public void propertyChange(PropertyChangeEvent evt) {
        // when user starts edit we need to force disable ok actions, because tree.isEditing() still false;
        // after editing completed okActions will be enabled automatically by dialog validation
        myDialog.enableOkActions(!(Boolean)evt.getNewValue());
      }
    });
    startLoadingCommits();
    Disposer.register(dialog.getDisposable(), this);
  }

  private boolean isSyncStrategiesAllowed() {
    return !mySingleRepoProject &&
           ContainerUtil.and(getAffectedSupports(), support -> support.mayChangeTargetsSync());
  }

  private boolean isSingleRepoProject() {
    return myAllRepos.size() == 1;
  }

  private @NotNull <R extends Repository, S extends PushSource, T extends PushTarget> List<PushSupport<R, S, T>> getAffectedSupports() {
    Collection<AbstractVcs> vcss = ContainerUtil.map2Set(myAllRepos, repository -> repository.getVcs());
    return ContainerUtil.map(vcss, vcs -> {
      //noinspection unchecked
      return DvcsUtil.getPushSupport(vcs);
    });
  }

  private void startLoadingCommits() {
    Map<RepositoryNode, MyRepoModel<?, ?, ?>> priorityLoading = new LinkedHashMap<>();
    Map<RepositoryNode, MyRepoModel<?, ?, ?>> others = new LinkedHashMap<>();
    RepositoryNode nodeForCurrentEditor = findNodeByRepo(myCurrentlyOpenedRepository);
    if (nodeForCurrentEditor != null) {
      MyRepoModel<?, ?, ?> currentRepoModel = myView2Model.get(nodeForCurrentEditor);
      //for ASYNC with no preselected -> check current repo
      if (isPreChecked(currentRepoModel) || myPreselectedRepositories.isEmpty()) {
        // put current editor repo to be loaded at first
        priorityLoading.put(nodeForCurrentEditor, currentRepoModel);
        currentRepoModel.setChecked(true);
      }
    }

    for (Map.Entry<RepositoryNode, MyRepoModel<Repository, PushSource, PushTarget>> entry : myView2Model.entrySet()) {
      MyRepoModel<?, ?, ?> model = entry.getValue();
      RepositoryNode repoNode = entry.getKey();
      if (isPreChecked(model)) {
        priorityLoading.putIfAbsent(repoNode, model);
        model.setChecked(true);
      }
      else if (model.getSupport().shouldRequestIncomingChangesForNotCheckedRepositories()) {
        others.put(repoNode, model);
      }
    }
    if (myPreselectedRepositories.isEmpty()) {
      boolean shouldScrollTo = myView2Model.values().stream().noneMatch(MyRepoModel::isSelected);
      myPushLog.highlightNodeOrFirst(nodeForCurrentEditor, shouldScrollTo);
    }
    loadCommitsFromMap(priorityLoading);
    loadCommitsFromMap(others);
  }

  private boolean isPreChecked(@NotNull MyRepoModel<?, ?, ?> model) {
    return AdvancedSettings.getBoolean("vcs.push.all.with.commits") ||
           model.getSupport().getRepositoryManager().isSyncEnabled() ||
           preselectByUser(model.getRepository());
  }

  private RepositoryNode findNodeByRepo(final @Nullable Repository repository) {
    if (repository == null) return null;
    Map.Entry<RepositoryNode, MyRepoModel<Repository, PushSource, PushTarget>> entry =
      ContainerUtil.find(myView2Model.entrySet(), entry1 -> {
        MyRepoModel<?, ?, ?> model = entry1.getValue();
        return model.getRepository().getRoot().equals(repository.getRoot());
      });
    return entry != null ? entry.getKey() : null;
  }

  private void loadCommitsFromMap(@NotNull Map<RepositoryNode, MyRepoModel<?, ?, ?>> items) {
    for (Map.Entry<RepositoryNode, MyRepoModel<?, ?, ?>> entry : items.entrySet()) {
      RepositoryNode node = entry.getKey();
      loadCommits(entry.getValue(), node, true);
    }
  }

  private void createTreeModel(@NotNull CheckedTreeNode rootNode) {
    for (Repository repository : DvcsUtil.sortRepositories(myAllRepos)) {
      PushSupport<Repository, PushSource, PushTarget> support = getPushSupportByRepository(repository);
      if (support != null) {
        PushSource source = myPushSource != null ? myPushSource : support.getSource(repository);
        if (source != null) {
          createRepoNode(repository, rootNode, source, support);
        }
      }
    }
  }

  private @Nullable <R extends Repository, S extends PushSource, T extends PushTarget> PushSupport<R, S, T> getPushSupportByRepository(final @NotNull R repository) {
    //noinspection unchecked
    return (PushSupport<R, S, T>)ContainerUtil.find(myPushSupports, support -> support.getVcs().equals(repository.getVcs()));
  }

  private <R extends Repository, S extends PushSource, T extends PushTarget> void createRepoNode(@NotNull R repository,
                                                                                                 @NotNull CheckedTreeNode rootNode,
                                                                                                 @NotNull S source,
                                                                                                 @NotNull PushSupport<R, S, T> pushSupport) {
    T target = pushSupport.getDefaultTarget(repository, source);
    String repoName = getDisplayedRepoName(repository);
    MyRepoModel<R, S, T> model = new MyRepoModel<>(repository, pushSupport, mySingleRepoProject, source, target);
    if (target == null) {
      model.setError(VcsError.createEmptyTargetError(repoName));
    }

    final PushTargetPanel<T> pushTargetPanel = pushSupport.createTargetPanel(repository, source, target);
    final RepositoryWithBranchPanel<T> repoPanel = new RepositoryWithBranchPanel<>(myProject, repoName,
                                                                                   source.getPresentation(), pushTargetPanel);
    CheckBoxModel checkBoxModel = model.getCheckBoxModel();
    final RepositoryNode repoNode = mySingleRepoProject
                                    ? new SingleRepositoryNode(repoPanel, checkBoxModel)
                                    : new RepositoryNode(repoPanel, checkBoxModel, target != null);
    // TODO: Implement IDEA-136937, until that do not change below class to avoid breakage of Gerrit plugin
    // (https://github.com/uwolfer/gerrit-intellij-plugin/issues/275)
    //noinspection Convert2Lambda
    pushTargetPanel.setFireOnChangeAction(new Runnable() {
      @Override
      public void run() {
        repoPanel.fireOnChange();
        ((DefaultTreeModel)myPushLog.getTree().getModel()).nodeChanged(repoNode); // tell the tree to repaint the changed node
      }
    });

    //noinspection unchecked
    myView2Model.put(repoNode, (MyRepoModel<Repository, PushSource, PushTarget>)model);
    repoPanel.addRepoNodeListener(new RepositoryNodeListener<>() {
      @Override
      public void onTargetChanged(T newTarget) {
        repoNode.setChecked(true);
        if (!newTarget.equals(model.getTarget()) || model.hasError() || !model.hasCommitInfo()) {
          model.setTarget(newTarget);
          model.clearErrors();
          loadCommits(model, repoNode, false);
        }
      }

      @Override
      public void onSelectionChanged(boolean isSelected) {
        myDialog.updateOkActions();
        if (isSelected && !model.hasCommitInfo() && !model.getSupport().shouldRequestIncomingChangesForNotCheckedRepositories()) {
          loadCommits(model, repoNode, false);
        }
      }

      @Override
      public void onTargetInEditMode(@NotNull @Nls String currentValue) {
        myPushLog.fireEditorUpdated(currentValue);
      }
    });
    rootNode.add(repoNode);
  }

  // TODO This logic shall be moved to some common place and used instead of DvcsUtil.getShortRepositoryName
  private @Nls @NotNull String getDisplayedRepoName(@NotNull Repository repository) {
    String name = DvcsUtil.getShortRepositoryName(repository);
    int slash = name.lastIndexOf(File.separatorChar);
    if (slash < 0) {
      return name;
    }
    String candidate = name.substring(slash + 1);
    return !containedInOtherNames(repository, candidate) ? candidate : name;
  }

  private boolean containedInOtherNames(final @NotNull Repository except, final String candidate) {
    return ContainerUtil.exists(myAllRepos, repository -> !repository.equals(except) && repository.getRoot().getName().equals(candidate));
  }

  public boolean isPushAllowed() {
    JTree tree = myPushLog.getTree();
    if (tree.isEditing()) return false;

    return ContainerUtil.exists(myView2Model.values(), model -> {
      return model.isSelected() && isPushAllowed(model);
    });
  }

  public boolean hasCommitWarnings() {
    return myHasCommitWarning;
  }

  private static boolean isPushAllowed(@NotNull MyRepoModel<Repository, PushSource, PushTarget> model) {
    PushTarget target = model.getTarget();
    if (target == null) return false;
    PushSupport<Repository, PushSource, PushTarget> pushSupport = model.getSupport();
    return pushSupport.canBePushed(model.getRepository(), model.getSource(), target);
  }

  private static boolean hasLoadingNodes(@NotNull Collection<? extends RepositoryNode> nodes) {
    return ContainerUtil.exists(nodes, node -> node.isLoading());
  }

  private <R extends Repository, S extends PushSource, T extends PushTarget> void loadCommits(final @NotNull MyRepoModel<R, S, T> model,
                                                                                              final @NotNull RepositoryNode node,
                                                                                              final boolean initial) {
    if (myDialog.isDisposed()) return;

    node.cancelLoading();
    node.setEnabled(true);

    final T target = model.getTarget();
    if (target == null) {
      node.stopLoading();
      return;
    }
    final PushSupport<R, S, T> support = model.getSupport();
    final AtomicReference<OutgoingResult> result = new AtomicReference<>();
    Runnable task = () -> {
      final R repository = model.getRepository();
      OutgoingResult outgoing = support.getOutgoingCommitsProvider()
        .getOutgoingCommits(repository, new PushSpec<>(model.getSource(), model.getTarget()), initial);
      result.compareAndSet(null, outgoing);
      try {
        ApplicationManager.getApplication().invokeAndWait(() -> {
          if (myDialog.isDisposed()) return;
          OutgoingResult outgoing1 = result.get();
          List<VcsError> errors = outgoing1.getErrors();
          boolean shouldBeSelected;
          if (!errors.isEmpty()) {
            shouldBeSelected = false;
            model.setLoadedCommits(ContainerUtil.emptyList());
            myPushLog.setChildren(node, ContainerUtil.map(errors, error -> {
              VcsLinkedTextComponent errorLinkText = new VcsLinkedTextComponent(error.getText(), new VcsLinkListener() {
                @Override
                public void hyperlinkActivated(@NotNull DefaultMutableTreeNode sourceNode, @NotNull MouseEvent event) {
                  error.handleError(new CommitLoader() {
                    @Override
                    public void reloadCommits() {
                      node.setChecked(true);
                      loadCommits(model, node, false);
                    }
                  });
                }
              });
              return new TextWithLinkNode(errorLinkText);
            }));
            if (node.isChecked()) {
              node.setChecked(false);
            }
          }
          else {
            List<? extends VcsFullCommitDetails> commits = outgoing1.getCommits();
            model.setLoadedCommits(commits);
            shouldBeSelected = shouldSelectNodeAfterLoad(model);
            myPushLog.setChildren(node, getPresentationForCommits(myProject, model.getLoadedCommits(), model.getNumberOfShownCommits()));
            if (!commits.isEmpty() && shouldBeSelected) {
              myPushLog.selectIfNothingSelected(node);
            }
          }
          node.stopLoading();
          updateLoadingPanel();
          if (shouldBeSelected) {
            node.setChecked(true);
          }
          else if (initial) {
            //do not un-check if user checked manually and no errors occurred, only initial check may be changed
            node.setChecked(false);
          }
          myDialog.updateOkActions();
        }, myModalityState);
      }
      catch (ProcessCanceledException ignore) {
      }
      catch (Exception e) {
        LOG.error(e);
      }
    };
    node.startLoading(myPushLog.getTree(), myExecutorService.submit(task, result), initial);
    updateLoadingPanel();
  }

  private void updateLoadingPanel() {
    myPushLog.setBusyLoading(hasLoadingNodes(myView2Model.keySet()));
  }

  private boolean shouldSelectNodeAfterLoad(@NotNull MyRepoModel<?, ?, ?> model) {
    if (mySingleRepoProject) return true;
    return model.isSelected() &&
           (hasCommitsToPush(model) ||
            // set force check only for async with no registry option
            !(model.getSupport().getRepositoryManager().isSyncEnabled() || AdvancedSettings.getBoolean("vcs.push.all.with.commits")));
  }

  private boolean preselectByUser(@NotNull Repository repository) {
    return mySingleRepoProject || myPreselectedRepositories.contains(repository);
  }

  private static boolean hasCommitsToPush(@NotNull MyRepoModel<?, ?, ?> model) {
    PushTarget target = model.getTarget();
    assert target != null;
    return (!model.getLoadedCommits().isEmpty() || target.hasSomethingToPush());
  }

  public PushLog getPushPanelLog() {
    return myPushLog;
  }

  /**
   * An exception thrown if a {@link PrePushHandler} has failed to make the decision
   * by whatever reason: either it had been cancelled, or an execution exception had occurred.
   */
  public static class HandlerException extends RuntimeException {

    /**
     * Name of the handler on which an exception happened.
     */
    private final String myFailedHandlerName;

    /**
     * Names of handlers which were skipped because {@link #myFailedHandlerName} had failed.
     */
    private final List<String> mySkippedHandlers;

    public HandlerException(@NotNull String failedHandlerName,
                            @NotNull List<String> skippedHandlers,
                            @NotNull Throwable cause) {
      super(cause);
      myFailedHandlerName = failedHandlerName;
      mySkippedHandlers = skippedHandlers;
    }

    public @NotNull String getFailedHandlerName() {
      return myFailedHandlerName;
    }

    public @NotNull List<String> getSkippedHandlers() {
      return mySkippedHandlers;
    }
  }

  @CalledInAny
  public @NotNull PrePushHandler.Result executeHandlers(@NotNull ProgressIndicator indicator) throws ProcessCanceledException, HandlerException {
    List<PrePushHandler> handlers = PrePushHandler.EP_NAME.getExtensionList();
    if (handlers.isEmpty()) {
      return PrePushHandler.Result.OK;
    }

    List<PushInfo> pushDetails = preparePushDetails();
    StepsProgressIndicator stepsIndicator = new StepsProgressIndicator(indicator, handlers.size());
    stepsIndicator.setIndeterminate(false);
    stepsIndicator.setFraction(0);
    for (int index = 0; index < handlers.size(); index++) {
      PrePushHandler handler = handlers.get(index);
      stepsIndicator.checkCanceled();
      stepsIndicator.setText(handler.getPresentableName());
      PrePushHandler.Result prePushHandlerResult;
      try {
        prePushHandlerResult = handler.handle(myProject, pushDetails, stepsIndicator);
      }
      catch (Throwable e) {
        List<String> skippedHandlers = handlers.stream()
          .skip(index + 1)
          .map(h -> h.getPresentableName())
          .collect(Collectors.toList());

        throw new HandlerException(handler.getPresentableName(), skippedHandlers, e);
      }

      if (prePushHandlerResult != PrePushHandler.Result.OK) {
        return prePushHandlerResult;
      }
      //the handler could change an indeterminate flag
      stepsIndicator.setIndeterminate(false);
      stepsIndicator.nextStep();
    }
    return PrePushHandler.Result.OK;
  }

  public void push(boolean force) {
    for (PushSupport<?, ?, ?> support : myPushSupports) {
      doPushSynchronously(support, force);
    }
  }

  private <R extends Repository, S extends PushSource, T extends PushTarget> void doPushSynchronously(@NotNull PushSupport<R, S, T> support,
                                                                                                      boolean force) {
    VcsPushOptionValue options = myDialog.getAdditionalOptionValue(support);
    Pusher<R, S, T> pusher = support.getPusher();
    Map<R, PushSpec<S, T>> specs = collectPushSpecsForVcs(support);
    if (!specs.isEmpty()) {
      pusher.push(specs, options, force, myDialog.getCustomParams());
    }
  }

  private static <R extends Repository, S extends PushSource, T extends PushTarget> List<? extends VcsFullCommitDetails> loadCommits(@NotNull MyRepoModel<R, S, T> model) {
    PushSupport<R, S, T> support = model.getSupport();
    R repository = model.getRepository();
    S source = model.getSource();
    T target = model.getTarget();
    if (target == null) {
      return ContainerUtil.emptyList();
    }
    OutgoingCommitsProvider<R, S, T> outgoingCommitsProvider = support.getOutgoingCommitsProvider();
    return outgoingCommitsProvider.getOutgoingCommits(repository, new PushSpec<>(source, target), true).getCommits();
  }

  private @NotNull List<PushInfo> preparePushDetails() {
    List<PushInfo> allDetails = new ArrayList<>();

    for (MyRepoModel<Repository, PushSource, PushTarget> model : getSelectedRepoNode()) {
      PushTarget target = Objects.requireNonNull(model.getTarget());
      PushSpec<PushSource, PushTarget> pushSpec = new PushSpec<>(model.getSource(), target);

      List<VcsFullCommitDetails> loadedCommits = new ArrayList<>(model.getLoadedCommits());
      if (loadedCommits.isEmpty()) {
        //Note: loadCommits is cancellable - it tracks current thread's progress indicator under the hood!
        loadedCommits.addAll(loadCommits(model));
      }
      //sort commits in the time-ascending order
      Collections.reverse(loadedCommits);

      PushInfoImpl pushInfo = new PushInfoImpl(model.getRepository(), pushSpec, loadedCommits);
      allDetails.add(pushInfo);
    }
    return Collections.unmodifiableList(allDetails);
  }

  public @NotNull Map<PushSupport<Repository, PushSource, PushTarget>, Collection<PushInfo>> getSelectedPushSpecs() {
    Map<PushSupport<Repository, PushSource, PushTarget>, Collection<PushInfo>> result = new HashMap<>();

    for (MyRepoModel<Repository, PushSource, PushTarget> model : getSelectedRepoNode()) {
      PushTarget target = Objects.requireNonNull(model.getTarget());
      PushSpec<PushSource, PushTarget> pushSpec = new PushSpec<>(model.getSource(), target);

      PushInfoImpl pushInfo = new PushInfoImpl(model.getRepository(), pushSpec, ContainerUtil.emptyList());
      Collection<PushInfo> vcsDetails = result.computeIfAbsent(model.mySupport, key -> new ArrayList<>());
      vcsDetails.add(pushInfo);
    }
    return result;
  }

  private @NotNull @Unmodifiable <R extends Repository, S extends PushSource, T extends PushTarget> Map<R, PushSpec<S, T>> collectPushSpecsForVcs(@NotNull PushSupport<R, S, T> pushSupport) {
    Map<PushSupport<Repository, PushSource, PushTarget>, Collection<PushInfo>> allSpecs = getSelectedPushSpecs();
    Collection<PushInfo> pushInfos = allSpecs.get(pushSupport);
    return pushInfos != null ?
           ContainerUtil.map2Map(pushInfos, pushInfo -> {
             //noinspection unchecked // the model can store entries of different types (if push supports are different)
             PushSpec<S, T> pushSpec = (PushSpec<S, T>)pushInfo.getPushSpec();
             //noinspection unchecked // the model can store entries of different types (if push supports are different)
             return new Pair<>((R)pushInfo.getRepository(), pushSpec);
           }) :
           Collections.emptyMap();
  }

  private @Unmodifiable Collection<MyRepoModel<Repository, PushSource, PushTarget>> getSelectedRepoNode() {
    //return all selected despite a loading state;
    return ContainerUtil.filter(myView2Model.values(), model -> {
      return (mySingleRepoProject || model.isSelected()) && isPushAllowed(model);
    });
  }

  @Override
  public void dispose() {
    Disposer.dispose(myPushLog);
    myExecutorService.shutdownNow();
  }

  public @NotNull Project getProject() {
    return myProject;
  }

  private void addMoreCommits(RepositoryNode repositoryNode) {
    MyRepoModel<?, ?, ?> repoModel = myView2Model.get(repositoryNode);
    repoModel.increaseShownCommits();
    myPushLog.setChildren(repositoryNode,
                          getPresentationForCommits(
                            myProject,
                            repoModel.getLoadedCommits(),
                            repoModel.getNumberOfShownCommits()
                          ));
  }


  private @NotNull List<DefaultMutableTreeNode> getPresentationForCommits(final @NotNull Project project,
                                                                          @NotNull List<? extends VcsFullCommitDetails> commits,
                                                                          int commitsNum) {
    Function<VcsFullCommitDetails, DefaultMutableTreeNode> commitToNode = commit -> new CommitNode(project, commit);
    List<DefaultMutableTreeNode> childrenToShown = new ArrayList<>();
    for (int i = 0; i < commits.size(); ++i) {
      if (i >= commitsNum) {
        final @NonNls VcsLinkedTextComponent moreCommitsLink =
          new VcsLinkedTextComponent(HtmlChunk.link("loadMore", "...").toString(), (sourceNode, event) -> {
            TreeNode parent = sourceNode.getParent();
            if (parent instanceof RepositoryNode) {
              addMoreCommits((RepositoryNode)parent);
            }
          });
        childrenToShown.add(new TextWithLinkNode(moreCommitsLink));
        break;
      }
      childrenToShown.add(commitToNode.fun(commits.get(i)));
    }
    return childrenToShown;
  }

  public @NotNull Map<PushSupport<?, ?, ?>, VcsPushOptionsPanel> createAdditionalPanels() {
    Map<PushSupport<?, ?, ?>, VcsPushOptionsPanel> result = new LinkedHashMap<>();
    for (PushSupport<?, ?, ?> support : myPushSupports) {
      ContainerUtil.putIfNotNull(support, support.createOptionsPanel(), result);
    }

    return result;
  }

  @ApiStatus.Experimental
  public @Unmodifiable Map<String, VcsPushOptionsPanel> createCustomPanels(Collection<? extends Repository> repos) {
    return ContainerUtil.map2MapNotNull(CustomPushOptionsPanelFactory.EP_NAME.getExtensionList(), panelProvider -> {
      try {
        VcsPushOptionsPanel panel = panelProvider.createOptionsPanel(this, repos);
        return panel != null ? Pair.pair(panelProvider.getId(), panel) : null;
      }
      catch (Throwable e) {
        LOG.error(e);
        return null;
      }
    });
  }

  public @NotNull JComponent createTopPanel() {
    List<JComponent> notifications = new ArrayList<>();
    if (myPushSource == null) {
      Runnable closeDialog = () -> myDialog.doCancelAction();
      JComponent commitStatus = PostCommitChecksHandler.getInstance(myProject).createPushStatusNotification(closeDialog);
      if (commitStatus != null) {
        myHasCommitWarning = true;
        notifications.add(commitStatus);
      }
    }

    notifications = DiffUtil.wrapEditorNotificationBorders(notifications);
    JComponent panel = DiffUtil.createStackedComponents(notifications, DiffUtil.TITLE_GAP);
    if (!notifications.isEmpty()) {
      panel.setBorder(JBUI.Borders.customLineBottom(JBUI.CurrentTheme.CustomFrameDecorations.separatorForeground()));
    }
    return panel;
  }

  private static final class PushInfoImpl implements PushInfo {

    private final Repository myRepository;
    private final PushSpec<PushSource, PushTarget> myPushSpec;
    private final List<VcsFullCommitDetails> myCommits;

    private PushInfoImpl(@NotNull Repository repository,
                         @NotNull PushSpec<PushSource, PushTarget> spec,
                         @NotNull List<VcsFullCommitDetails> commits) {
      myRepository = repository;
      myPushSpec = spec;
      myCommits = commits;
    }

    @Override
    public @NotNull Repository getRepository() {
      return myRepository;
    }

    @Override
    public @NotNull PushSpec<PushSource, PushTarget> getPushSpec() {
      return myPushSpec;
    }

    @Override
    public @NotNull List<VcsFullCommitDetails> getCommits() {
      return myCommits;
    }
  }

  private static final class MyRepoModel<Repo extends Repository, S extends PushSource, T extends PushTarget> {
    private final @NotNull Repo myRepository;
    private final @NotNull PushSupport<Repo, S, T> mySupport;
    private final @NotNull S mySource;
    private @Nullable T myTarget;
    @Nullable VcsError myTargetError;

    int myNumberOfShownCommits;
    @NotNull List<? extends VcsFullCommitDetails> myLoadedCommits = Collections.emptyList();
    private final @NotNull CheckBoxModel myCheckBoxModel;

    MyRepoModel(@NotNull Repo repository,
                @NotNull PushSupport<Repo, S, T> supportForRepo,
                boolean isSelected,
                @NotNull S source,
                @Nullable T target) {
      myRepository = repository;
      mySupport = supportForRepo;
      myCheckBoxModel = new CheckBoxModel(isSelected);
      mySource = source;
      myTarget = target;
      myNumberOfShownCommits = DEFAULT_CHILDREN_PRESENTATION_NUMBER;
    }

    public @NotNull Repo getRepository() {
      return myRepository;
    }

    public @NotNull PushSupport<Repo, S, T> getSupport() {
      return mySupport;
    }

    public @NotNull S getSource() {
      return mySource;
    }

    public @Nullable T getTarget() {
      return myTarget;
    }

    public void setTarget(@Nullable T target) {
      myTarget = target;
    }

    public boolean isSelected() {
      return myCheckBoxModel.isChecked();
    }

    public void setError(@Nullable VcsError error) {
      myTargetError = error;
    }

    public void clearErrors() {
      myTargetError = null;
    }

    public boolean hasError() {
      return myTargetError != null;
    }

    public int getNumberOfShownCommits() {
      return myNumberOfShownCommits;
    }

    public void increaseShownCommits() {
      myNumberOfShownCommits *= 2;
    }

    public @NotNull List<? extends VcsFullCommitDetails> getLoadedCommits() {
      return myLoadedCommits;
    }

    public void setLoadedCommits(@NotNull List<? extends VcsFullCommitDetails> loadedCommits) {
      myLoadedCommits = loadedCommits;
    }

    public boolean hasCommitInfo() {
      return myTargetError != null || !myLoadedCommits.isEmpty();
    }

    public @NotNull CheckBoxModel getCheckBoxModel() {
      return myCheckBoxModel;
    }

    public void setChecked(boolean checked) {
      myCheckBoxModel.setChecked(checked);
    }
  }
}
