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
package com.intellij.dvcs.push;

import com.intellij.dvcs.DvcsUtil;
import com.intellij.dvcs.push.ui.*;
import com.intellij.dvcs.repo.Repository;
import com.intellij.dvcs.repo.RepositoryManager;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.ui.CheckedTreeNode;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.UIUtil;
import com.intellij.vcs.log.VcsFullCommitDetails;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

public class PushController implements Disposable {

  @NotNull private final Project myProject;
  @NotNull private final List<? extends Repository> myPreselectedRepositories;
  @NotNull private final List<PushSupport<?, ?, ?>> myPushSupports;
  @NotNull private final PushLog myPushLog;
  @NotNull private final VcsPushDialog myDialog;
  private boolean mySingleRepoProject;
  private static final int DEFAULT_CHILDREN_PRESENTATION_NUMBER = 20;
  private final ExecutorService myExecutorService = Executors.newSingleThreadExecutor();

  private final Map<RepositoryNode, MyRepoModel> myView2Model = new TreeMap<RepositoryNode, MyRepoModel>();
  //todo need to sort repositories in ui tree using natural order

  public PushController(@NotNull Project project,
                        @NotNull VcsPushDialog dialog,
                        @NotNull List<? extends Repository> preselectedRepositories) {
    myProject = project;
    myPreselectedRepositories = preselectedRepositories;
    myPushSupports = getAffectedSupports(myProject);
    mySingleRepoProject = isSingleRepoProject(myPushSupports);
    CheckedTreeNode rootNode = new CheckedTreeNode(null);
    createTreeModel(rootNode);
    myPushLog = new PushLog(myProject, rootNode);
    myPushLog.getTree().addPropertyChangeListener(PushLogTreeUtil.EDIT_MODE_PROP, new PropertyChangeListener() {
      @Override
      public void propertyChange(PropertyChangeEvent evt) {
        Boolean isEditMode = (Boolean)evt.getNewValue();
        myDialog.enableOkActions(!isEditMode && isPushAllowed());
      }
    });
    myDialog = dialog;
    startLoadingCommits();
    Disposer.register(dialog.getDisposable(), this);
  }

  private static boolean isSingleRepoProject(@NotNull List<PushSupport<?, ?, ?>> pushSupports) {
    int repos = 0;
    for (PushSupport support : pushSupports) {
      repos += support.getRepositoryManager().getRepositories().size();
    }
    return repos == 1;
  }

  @NotNull
  private static List<PushSupport<?, ?, ?>> getAffectedSupports(@NotNull Project project) {
    return ContainerUtil.filter(Extensions.getExtensions(PushSupport.PUSH_SUPPORT_EP, project), new Condition<PushSupport>() {
      @Override
      public boolean value(PushSupport support) {
        return !support.getRepositoryManager().getRepositories().isEmpty();
      }
    });
  }

  public boolean isForcePushEnabled() {
    return ContainerUtil.exists(myView2Model.values(), new Condition<MyRepoModel>() {
      @Override
      public boolean value(MyRepoModel model) {
        return model.getSupport().isForcePushEnabled();
      }
    });
  }

  public boolean isForcePushAllowed() {
    return ContainerUtil.and(myView2Model.values(), new Condition<MyRepoModel>() {
      @Override
      public boolean value(MyRepoModel model) {
        return !model.isSelected() ||
               (model.getTarget() != null && model.getSupport().isForcePushAllowed(model.getRepository(), model.getTarget()));
      }
    });
  }

  private void startLoadingCommits() {
    //todo should be reworked
    Map<RepositoryNode, MyRepoModel> priorityLoading = ContainerUtil.newLinkedHashMap();
    Map<RepositoryNode, MyRepoModel> others = ContainerUtil.newLinkedHashMap();
    for (Map.Entry<RepositoryNode, MyRepoModel> entry : myView2Model.entrySet()) {
      MyRepoModel model = entry.getValue();
      if (myPreselectedRepositories.contains(model.getRepository())) {
        priorityLoading.put(entry.getKey(), model);
      }
      else if (model.getSupport().shouldRequestIncomingChangesForNotCheckedRepositories()) {
        others.put(entry.getKey(), model);
      }
    }
    loadCommitsFromMap(priorityLoading);
    loadCommitsFromMap(others);
  }

  private void loadCommitsFromMap(@NotNull Map<RepositoryNode, MyRepoModel> items) {
    for (Map.Entry<RepositoryNode, MyRepoModel> entry : items.entrySet()) {
      RepositoryNode node = entry.getKey();
      loadCommits(entry.getValue(), node, true);
    }
  }

  private void createTreeModel(@NotNull CheckedTreeNode rootNode) {
    for (PushSupport<? extends Repository, ? extends PushSource, ? extends PushTarget> support : myPushSupports) {
      createNodesForVcs(support, rootNode);
    }
  }

  private <R extends Repository, S extends PushSource, T extends PushTarget> void createNodesForVcs(
    @NotNull PushSupport<R, S, T> pushSupport, @NotNull CheckedTreeNode rootNode) {
    for (R repository : pushSupport.getRepositoryManager().getRepositories()) {
      createRepoNode(pushSupport, repository, rootNode);
    }
  }

  private <R extends Repository, S extends PushSource, T extends PushTarget> void createRepoNode(@NotNull final PushSupport<R, S, T> support,
                                                                                                 @NotNull final R repository,
                                                                                                 @NotNull final CheckedTreeNode rootNode) {
    T target = support.getDefaultTarget(repository);
    String repoName = getDisplayedRepoName(repository);
    S source = support.getSource(repository);
    final MyRepoModel<R, S, T> model = new MyRepoModel<R, S, T>(repository, support, mySingleRepoProject,
                                                                source, target,
                                                                DEFAULT_CHILDREN_PRESENTATION_NUMBER);
    if (target == null) {
      model.setError(VcsError.createEmptyTargetError(repoName));
    }
    final PushTargetPanel<T> pushTargetPanel = support.createTargetPanel(repository, target);
    RepositoryWithBranchPanel<T> repoPanel = new RepositoryWithBranchPanel<T>(myProject, repoName,
                                                                              source.getPresentation(), pushTargetPanel);
    final RepositoryNode repoNode = mySingleRepoProject
                                    ? new SingleRepositoryNode(repoPanel)
                                    : new RepositoryNode(repoPanel, target != null);
    myView2Model.put(repoNode, model);
    repoPanel.addRepoNodeListener(new RepositoryNodeListener<T>() {
      @Override
      public void onTargetChanged(T newTarget) {
        model.setSelected(true);
        repoNode.setChecked(true);
        model.setTarget(newTarget);
        model.clearErrors();
        loadCommits(model, repoNode, false);
      }

      @Override
      public void onSelectionChanged(boolean isSelected) {
        model.setSelected(isSelected);
        rootNode.setChecked(isSelected);
        myDialog.enableOkActions(isPushAllowed());
        if (isSelected && !model.hasCommitInfo() && !model.getSupport().shouldRequestIncomingChangesForNotCheckedRepositories()) {
          //download incoming if was not loaded before and marked as selected
          loadCommits(model, repoNode, false);
        }
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
    if (!getOtherReposLastNames(repository).contains(candidate)) {
      return candidate;
    }
    return name;
  }

  @NotNull
  private Set<String> getOtherReposLastNames(@NotNull Repository except) {
    Set<String> repos = ContainerUtil.newHashSet();
    for (PushSupport<?, ?, ?> support : myPushSupports) {
      for (Repository repo : support.getRepositoryManager().getRepositories()) {
        if (!repo.equals(except)) {
          repos.add(repo.getRoot().getName());
        }
      }
    }
    return repos;
  }

  public boolean isPushAllowed() {
    JTree tree = myPushLog.getTree();
    return !tree.isEditing() && ContainerUtil.exists(myPushSupports, new Condition<PushSupport<?, ?, ?>>() {
      @Override
      public boolean value(PushSupport<?, ?, ?> support) {
        return isPushAllowed(support);
      }
    });
  }

  private boolean isPushAllowed(@NotNull PushSupport<?, ?, ?> pushSupport) {
    Collection<RepositoryNode> nodes = getNodesForSupport(pushSupport);
    if (pushSupport.getRepositoryManager().isSyncEnabled()) {
      return hasCheckedNode(nodes) && allNodesAreLoaded(nodes);
    }
    return hasCheckedNode(nodes);
  }

  private static boolean allNodesAreLoaded(@NotNull Collection<RepositoryNode> nodes) {
    return !ContainerUtil.exists(nodes, new Condition<RepositoryNode>() {
      @Override
      public boolean value(@NotNull RepositoryNode node) {
        return node.isLoading();
      }
    });
  }

  private static boolean hasCheckedNode(@NotNull Collection<RepositoryNode> nodes) {
    return ContainerUtil.exists(nodes, new Condition<RepositoryNode>() {
      @Override
      public boolean value(@NotNull RepositoryNode node) {
        return node.isChecked();
      }
    });
  }

  @NotNull
  private Collection<RepositoryNode> getNodesForSupport(final PushSupport<?, ?, ?> support) {
    return ContainerUtil.mapNotNull(myView2Model.entrySet(), new Function<Map.Entry<RepositoryNode, MyRepoModel>, RepositoryNode>() {
      @Override
      public RepositoryNode fun(Map.Entry<RepositoryNode, MyRepoModel> entry) {
        return entry.getValue().getSupport().equals(support) ? entry.getKey() : null;
      }
    });
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
    final AtomicReference<OutgoingResult> result = new AtomicReference<OutgoingResult>();
    Runnable task = new Runnable() {
      @Override
      public void run() {
        final R repository = model.getRepository();
        OutgoingResult outgoing = support.getOutgoingCommitsProvider()
          .getOutgoingCommits(repository, new PushSpec<S, T>(model.getSource(), model.getTarget()), initial);
        result.compareAndSet(null, outgoing);
        UIUtil.invokeAndWaitIfNeeded(new Runnable() {
          @Override
          public void run() {
            OutgoingResult outgoing = result.get();
            List<VcsError> errors = outgoing.getErrors();
            boolean shouldBeSelected;
            if (!errors.isEmpty()) {
              shouldBeSelected = false;
              myPushLog.setChildren(node, ContainerUtil.map(errors, new Function<VcsError, DefaultMutableTreeNode>() {
                @Override
                public DefaultMutableTreeNode fun(final VcsError error) {
                  VcsLinkedText errorLinkText = new VcsLinkedText(error.getText(), new VcsLinkListener() {
                    @Override
                    public void hyperlinkActivated(@NotNull DefaultMutableTreeNode sourceNode) {
                      error.handleError(new CommitLoader() {
                        @Override
                        public void reloadCommits() {
                          loadCommits(model, node, false);
                        }
                      });
                    }
                  });
                  return new TextWithLinkNode(errorLinkText);
                }
              }));
            }
            else {
              List<? extends VcsFullCommitDetails> commits = outgoing.getCommits();
              shouldBeSelected = shouldSelectInitially(target, repository, model.getSupport().getRepositoryManager(), !commits.isEmpty());
              model.setLoadedCommits(commits);
              myPushLog.setChildren(node,
                                    getPresentationForCommits(PushController.this.myProject, model.getLoadedCommits(),
                                                              model.getNumberOfShownCommits()));
              if (!commits.isEmpty()) {
                myPushLog.selectIfNothingSelected(node);
              }
            }
            node.stopLoading();
            if (shouldBeSelected) { // never remove selection; initially all checkboxes are not selected
              node.setChecked(true);
              model.setSelected(true);
            }
            myDialog.enableOkActions(isPushAllowed());
          }
        });
      }
    };
    node.startLoading(myPushLog.getTree(), myExecutorService.submit(task, result));
  }

  private boolean shouldSelectInitially(@NotNull PushTarget target, @NotNull Repository repository,
                                        @NotNull RepositoryManager repositoryManager, boolean hasCommits) {
    boolean shouldBeSelected;
    if (mySingleRepoProject) {
      shouldBeSelected = true;
    }
    else if (repositoryManager.isSyncEnabled()) {
      shouldBeSelected = hasCommits || target.hasSomethingToPush();
    }
    else {
      shouldBeSelected = (hasCommits || target.hasSomethingToPush()) && myPreselectedRepositories.contains(repository);
    }
    return shouldBeSelected;
  }

  public PushLog getPushPanelLog() {
    return myPushLog;
  }

  public void push(final boolean force) {
    Task.Backgroundable task = new Task.Backgroundable(myProject, "Pushing...", false) {
      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        for (PushSupport support : myPushSupports) {
          doPush(support, force);
        }
      }
    };
    task.queue();
  }

  private <R extends Repository, S extends PushSource, T extends PushTarget> void doPush(@NotNull PushSupport<R, S, T> support,
                                                                                         boolean force) {
    VcsPushOptionValue options = myDialog.getAdditionalOptionValue(support);
    Pusher<R, S, T> pusher = support.getPusher();
    pusher.push(collectPushSpecsForVcs(support), options, force);
  }

  @NotNull
  private <R extends Repository, S extends PushSource, T extends PushTarget> Map<R, PushSpec<S, T>> collectPushSpecsForVcs(@NotNull PushSupport<R, S, T> pushSupport) {
    Map<R, PushSpec<S, T>> pushSpecs = ContainerUtil.newHashMap();
    Collection<MyRepoModel> repositoriesInformation = getSelectedRepoNode();
    for (MyRepoModel repoModel : repositoriesInformation) {
      if (pushSupport.equals(repoModel.getSupport())) {
        //todo improve generics: unchecked casts
        T target = (T)repoModel.getTarget();
        if (target != null) {
          pushSpecs.put((R)repoModel.getRepository(), new PushSpec<S, T>((S)repoModel.getSource(), target));
        }
      }
    }
    return pushSpecs;
  }

  public Collection<MyRepoModel> getSelectedRepoNode() {
    if (mySingleRepoProject) {
      return myView2Model.values();
    }
    return ContainerUtil.filter(myView2Model.values(), new Condition<MyRepoModel>() {
      @Override
      public boolean value(MyRepoModel model) {
        return model.isSelected();
      }
    });
  }

  @Override
  public void dispose() {
    myExecutorService.shutdownNow();
  }

  private void addMoreCommits(RepositoryNode repositoryNode) {
    MyRepoModel repoModel = myView2Model.get(repositoryNode);
    repoModel.increaseShownCommits();
    myPushLog.setChildren(repositoryNode,
                          getPresentationForCommits(
                            myProject,
                            repoModel.getLoadedCommits(),
                            repoModel.getNumberOfShownCommits()
                          ));
  }


  @NotNull
  public List<DefaultMutableTreeNode> getPresentationForCommits(@NotNull final Project project,
                                                                @NotNull List<? extends VcsFullCommitDetails> commits,
                                                                int commitsNum) {
    Function<VcsFullCommitDetails, DefaultMutableTreeNode> commitToNode = new Function<VcsFullCommitDetails, DefaultMutableTreeNode>() {
      @Override
      public DefaultMutableTreeNode fun(VcsFullCommitDetails commit) {
        return new CommitNode(project, commit);
      }
    };
    List<DefaultMutableTreeNode> childrenToShown = new ArrayList<DefaultMutableTreeNode>();
    for (int i = 0; i < commits.size(); ++i) {
      if (i >= commitsNum) {
        final VcsLinkedText moreCommitsLink = new VcsLinkedText("<a href='loadMore'>...</a>", new VcsLinkListener() {
          @Override
          public void hyperlinkActivated(@NotNull DefaultMutableTreeNode sourceNode) {
            addMoreCommits((RepositoryNode)sourceNode);
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
  public Map<PushSupport, VcsPushOptionsPanel> createAdditionalPanels() {
    Map<PushSupport, VcsPushOptionsPanel> result = ContainerUtil.newLinkedHashMap();
    for (PushSupport support : myPushSupports) {
      ContainerUtil.putIfNotNull(support, support.createOptionsPanel(), result);
    }
    return result;
  }

  private static class MyRepoModel<Repo extends Repository, S extends PushSource, T extends PushTarget> {
    @NotNull final Repo myRepository;
    @NotNull private PushSupport<Repo, S, T> mySupport;
    @NotNull private final S mySource;
    @Nullable private T myTarget;
    @Nullable VcsError myTargetError;

    int myNumberOfShownCommits;
    @NotNull List<? extends VcsFullCommitDetails> myLoadedCommits = Collections.emptyList();
    boolean myIsSelected;

    public MyRepoModel(@NotNull Repo repository,
                       @NotNull PushSupport<Repo, S, T> supportForRepo,
                       boolean isSelected, @NotNull S source, @Nullable T target,
                       int num) {
      myRepository = repository;
      mySupport = supportForRepo;
      myIsSelected = isSelected;
      mySource = source;
      myTarget = target;
      myNumberOfShownCommits = num;
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
      return myIsSelected;
    }

    public AbstractVcs<?> getVcs() {
      return myRepository.getVcs();
    }

    @Nullable
    public VcsError getError() {
      return myTargetError;
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

    public void setSelected(boolean isSelected) {
      myIsSelected = isSelected;
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
  }
}
