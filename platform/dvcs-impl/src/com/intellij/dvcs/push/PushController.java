// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.dvcs.push;

import com.intellij.dvcs.DvcsUtil;
import com.intellij.dvcs.push.ui.*;
import com.intellij.dvcs.repo.Repository;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.ui.CheckedTreeNode;
import com.intellij.util.Function;
import com.intellij.util.concurrency.SequentialTaskExecutor;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.progress.StepsProgressIndicator;
import com.intellij.vcs.log.VcsFullCommitDetails;
import org.jetbrains.annotations.CalledInAny;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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

import static com.intellij.util.ObjectUtils.chooseNotNull;

public final class PushController implements Disposable {
  private static final Logger LOG = Logger.getInstance(PushController.class);

  @NotNull private final Project myProject;
  @Nullable private final PushSource myPushSource;
  @NotNull private final Collection<? extends Repository> myAllRepos;
  @NotNull private final List<? extends Repository> myPreselectedRepositories;
  @NotNull private final List<PushSupport<Repository, PushSource, PushTarget>> myPushSupports;
  @NotNull private final PushLog myPushLog;
  @NotNull private final VcsPushDialog myDialog;
  @NotNull private final ModalityState myModalityState;
  @Nullable private final Repository myCurrentlyOpenedRepository;
  private final boolean mySingleRepoProject;
  private static final int DEFAULT_CHILDREN_PRESENTATION_NUMBER = 20;
  @NonNls
  private final ExecutorService myExecutorService = SequentialTaskExecutor.createSequentialApplicationPoolExecutor("DVCS Push");

  private final Map<RepositoryNode, MyRepoModel<Repository, PushSource, PushTarget>> myView2Model = new TreeMap<>();

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
    myPushLog = new PushLog(myProject, rootNode, isSyncStrategiesAllowed());
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

  @NotNull
  private <R extends Repository, S extends PushSource, T extends PushTarget> List<PushSupport<R, S, T>> getAffectedSupports() {
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
    return Registry.is("vcs.push.all.with.commits") ||
           model.getSupport().getRepositoryManager().isSyncEnabled() ||
           preselectByUser(model.getRepository());
  }

  private RepositoryNode findNodeByRepo(@Nullable final Repository repository) {
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
        createRepoNode(repository, rootNode, chooseNotNull(myPushSource, support.getSource(repository)), support);
      }
    }
  }

  @Nullable
  private <R extends Repository, S extends PushSource, T extends PushTarget> PushSupport<R, S, T> getPushSupportByRepository(@NotNull final R repository) {
    //noinspection unchecked
    return (PushSupport<R, S, T>)ContainerUtil.find(
      myPushSupports,
      (Condition<PushSupport<? extends Repository, ? extends PushSource, ? extends PushTarget>>)support -> support.getVcs().equals(repository.getVcs()));
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
    repoPanel.addRepoNodeListener(new RepositoryNodeListener<T>() {
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
      public void onTargetInEditMode(@NotNull String currentValue) {
        myPushLog.fireEditorUpdated(currentValue);
      }
    });
    rootNode.add(repoNode);
  }

  // TODO This logic shall be moved to some common place and used instead of DvcsUtil.getShortRepositoryName
  @NotNull
  private String getDisplayedRepoName(@NotNull Repository repository) {
    String name = DvcsUtil.getShortRepositoryName(repository);
    int slash = name.lastIndexOf(File.separatorChar);
    if (slash < 0) {
      return name;
    }
    String candidate = name.substring(slash + 1);
    return !containedInOtherNames(repository, candidate) ? candidate : name;
  }

  private boolean containedInOtherNames(@NotNull final Repository except, final String candidate) {
    return ContainerUtil.exists(myAllRepos, repository -> !repository.equals(except) && repository.getRoot().getName().equals(candidate));
  }

  public boolean isPushAllowed() {
    JTree tree = myPushLog.getTree();
    return !tree.isEditing() && ContainerUtil.exists(myPushSupports, support -> isPushAllowed(support));
  }

  private boolean isPushAllowed(@NotNull PushSupport<?, ?, ?> pushSupport) {
    return ContainerUtil.exists(getNodesForSupport(pushSupport), node -> {
      //if node is selected then target should not be null
      return node.isChecked() && myView2Model.get(node).getTarget() != null;
    });
  }

  @NotNull
  private Collection<RepositoryNode> getNodesForSupport(final PushSupport<?, ?, ?> support) {
    return ContainerUtil
      .mapNotNull(myView2Model.entrySet(), entry -> support.equals(entry.getValue().getSupport()) ? entry.getKey() : null);
  }

  private static boolean hasLoadingNodes(@NotNull Collection<RepositoryNode> nodes) {
    return ContainerUtil.exists(nodes, node -> node.isLoading());
  }

  private <R extends Repository, S extends PushSource, T extends PushTarget> void loadCommits(@NotNull final MyRepoModel<R, S, T> model,
                                                                                              @NotNull final RepositoryNode node,
                                                                                              final boolean initial) {
    node.cancelLoading();
    final T target = model.getTarget();
    if (target == null) {
      node.stopLoading();
      return;
    }
    node.setEnabled(true);
    final PushSupport<R, S, T> support = model.getSupport();
    final AtomicReference<OutgoingResult> result = new AtomicReference<>();
    Runnable task = () -> {
      final R repository = model.getRepository();
      OutgoingResult outgoing = support.getOutgoingCommitsProvider()
        .getOutgoingCommits(repository, new PushSpec<>(model.getSource(), model.getTarget()), initial);
      result.compareAndSet(null, outgoing);
      try {
        ApplicationManager.getApplication().invokeAndWait(() -> {
          OutgoingResult outgoing1 = result.get();
          List<VcsError> errors = outgoing1.getErrors();
          boolean shouldBeSelected;
          if (!errors.isEmpty()) {
            shouldBeSelected = false;
            model.setLoadedCommits(ContainerUtil.emptyList());
            myPushLog.setChildren(node, ContainerUtil.map(errors, (Function<VcsError, DefaultMutableTreeNode>)error -> {
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
    myPushLog.getTree().setPaintBusy(hasLoadingNodes(myView2Model.keySet()));
  }

  private boolean shouldSelectNodeAfterLoad(@NotNull MyRepoModel<?, ?, ?> model) {
    if (mySingleRepoProject) return true;
    return model.isSelected() &&
           (hasCommitsToPush(model) ||
            // set force check only for async with no registry option
            !(model.getSupport().getRepositoryManager().isSyncEnabled() || Registry.is("vcs.push.all.with.commits")));
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

    @NotNull
    public String getFailedHandlerName() {
      return myFailedHandlerName;
    }

    @NotNull
    public List<String> getSkippedHandlers() {
      return mySkippedHandlers;
    }
  }

  @NotNull
  @CalledInAny
  public PrePushHandler.Result executeHandlers(@NotNull ProgressIndicator indicator) throws ProcessCanceledException, HandlerException {
    List<PrePushHandler> handlers = PrePushHandler.EP_NAME.getExtensionList(myProject);
    if (handlers.isEmpty()) return PrePushHandler.Result.OK;
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
        prePushHandlerResult = handler.handle(pushDetails, stepsIndicator);
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
      pusher.push(specs, options, force);
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

  @NotNull
  private List<PushInfo> preparePushDetails() {
    List<PushInfo> allDetails = new ArrayList<>();
    Collection<MyRepoModel<Repository, PushSource, PushTarget>> repoModels = getSelectedRepoNode();

    for (MyRepoModel<?, ?, ?> model : repoModels) {
      PushTarget target = model.getTarget();
      if (target == null) {
        continue;
      }
      PushSpec<PushSource, PushTarget> pushSpec = new PushSpec<>(model.getSource(), target);

      List<VcsFullCommitDetails> loadedCommits = new ArrayList<>(model.getLoadedCommits());
      if (loadedCommits.isEmpty()) {
        //Note: loadCommits is cancellable - it tracks current thread's progress indicator under the hood!
        loadedCommits.addAll(loadCommits(model));
      }

      //sort commits in the time-ascending order
      Collections.reverse(loadedCommits);
      allDetails.add(new PushInfoImpl(model.getRepository(), pushSpec, loadedCommits));
    }
    return Collections.unmodifiableList(allDetails);
  }

  @NotNull
  public Map<PushSupport<Repository, PushSource, PushTarget>, Collection<PushInfo>> getSelectedPushSpecs() {
    Map<PushSupport<Repository, PushSource, PushTarget>, Collection<PushInfo>> result = new HashMap<>();
    for (MyRepoModel<Repository, PushSource, PushTarget> repoModel : getSelectedRepoNode()) {
      PushTarget target = repoModel.getTarget();
      if (target != null) {
        PushSpec<PushSource, PushTarget> spec = new PushSpec<>(repoModel.getSource(), target);
        PushInfoImpl pushInfo = new PushInfoImpl(repoModel.getRepository(), spec, ContainerUtil.emptyList());
        Collection<PushInfo> list = result.get(repoModel.mySupport);
        if (list == null) {
          list = new ArrayList<>();
          result.put(repoModel.mySupport, list);
        }
        list.add(pushInfo);
      }
    }
    return result;
  }

  @NotNull
  private <R extends Repository, S extends PushSource, T extends PushTarget> Map<R, PushSpec<S, T>> collectPushSpecsForVcs(@NotNull PushSupport<R, S, T> pushSupport) {
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

  private Collection<MyRepoModel<Repository, PushSource, PushTarget>> getSelectedRepoNode() {
    if (mySingleRepoProject) {
      return myView2Model.values();
    }

    //return all selected despite a loading state;
    return ContainerUtil.mapNotNull(myView2Model.entrySet(),
                                    entry -> {
                                      MyRepoModel<Repository, PushSource, PushTarget> model = entry.getValue();
                                      return model.isSelected() &&
                                             model.getTarget() != null ? model :
                                             null;
                                    });
  }

  @Override
  public void dispose() {
    myExecutorService.shutdownNow();
  }

  @NotNull
  public Project getProject() {
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


  @NotNull
  private List<DefaultMutableTreeNode> getPresentationForCommits(@NotNull final Project project,
                                                                 @NotNull List<? extends VcsFullCommitDetails> commits,
                                                                 int commitsNum) {
    Function<VcsFullCommitDetails, DefaultMutableTreeNode> commitToNode = commit -> new CommitNode(project, commit);
    List<DefaultMutableTreeNode> childrenToShown = new ArrayList<>();
    for (int i = 0; i < commits.size(); ++i) {
      if (i >= commitsNum) {
        @NonNls
        final VcsLinkedTextComponent moreCommitsLink = new VcsLinkedTextComponent("<a href='loadMore'>...</a>", new VcsLinkListener() {
          @Override
          public void hyperlinkActivated(@NotNull DefaultMutableTreeNode sourceNode, @NotNull MouseEvent event) {
            TreeNode parent = sourceNode.getParent();
            if (parent instanceof RepositoryNode) {
              addMoreCommits((RepositoryNode)parent);
            }
          }
        });
        childrenToShown.add(new TextWithLinkNode(moreCommitsLink));
        break;
      }
      childrenToShown.add(commitToNode.fun(commits.get(i)));
    }
    return childrenToShown;
  }

  @NotNull
  public Map<PushSupport<?, ?, ?>, VcsPushOptionsPanel> createAdditionalPanels() {
    Map<PushSupport<?, ?, ?>, VcsPushOptionsPanel> result = new LinkedHashMap<>();
    for (PushSupport<?, ?, ?> support : myPushSupports) {
      ContainerUtil.putIfNotNull(support, support.createOptionsPanel(), result);
    }
    return result;
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

    @NotNull
    @Override
    public Repository getRepository() {
      return myRepository;
    }

    @NotNull
    @Override
    public PushSpec<PushSource, PushTarget> getPushSpec() {
      return myPushSpec;
    }

    @NotNull
    @Override
    public List<VcsFullCommitDetails> getCommits() {
      return myCommits;
    }
  }

  private static final class MyRepoModel<Repo extends Repository, S extends PushSource, T extends PushTarget> {
    @NotNull private final Repo myRepository;
    @NotNull private final PushSupport<Repo, S, T> mySupport;
    @NotNull private final S mySource;
    @Nullable private T myTarget;
    @Nullable VcsError myTargetError;

    int myNumberOfShownCommits;
    @NotNull List<? extends VcsFullCommitDetails> myLoadedCommits = Collections.emptyList();
    @NotNull private final CheckBoxModel myCheckBoxModel;

    MyRepoModel(@NotNull Repo repository,
                       @NotNull PushSupport<Repo, S, T> supportForRepo,
                       boolean isSelected, @NotNull S source, @Nullable T target) {
      myRepository = repository;
      mySupport = supportForRepo;
      myCheckBoxModel = new CheckBoxModel(isSelected);
      mySource = source;
      myTarget = target;
      myNumberOfShownCommits = DEFAULT_CHILDREN_PRESENTATION_NUMBER;
    }

    @NotNull
    public Repo getRepository() {
      return myRepository;
    }

    @NotNull
    public PushSupport<Repo, S, T> getSupport() {
      return mySupport;
    }

    @NotNull
    public S getSource() {
      return mySource;
    }

    @Nullable
    public T getTarget() {
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

    @NotNull
    public List<? extends VcsFullCommitDetails> getLoadedCommits() {
      return myLoadedCommits;
    }

    public void setLoadedCommits(@NotNull List<? extends VcsFullCommitDetails> loadedCommits) {
      myLoadedCommits = loadedCommits;
    }

    public boolean hasCommitInfo() {
      return myTargetError != null || !myLoadedCommits.isEmpty();
    }

    @NotNull
    public CheckBoxModel getCheckBoxModel() {
      return myCheckBoxModel;
    }

    public void setChecked(boolean checked) {
      myCheckBoxModel.setChecked(checked);
    }
  }

}
