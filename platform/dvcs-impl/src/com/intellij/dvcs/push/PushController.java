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
import com.intellij.openapi.Disposable;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.openapi.ui.popup.util.PopupUtil;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.ui.CheckedTreeNode;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.hash.HashMap;
import com.intellij.util.ui.UIUtil;
import com.intellij.vcs.log.VcsFullCommitDetails;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

public class PushController implements Disposable {

  @NotNull private final Project myProject;
  @NotNull private final List<PushSupport<?,?,?>> myPushSupports;
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
    myPushSupports = getAffectedSupports(myProject);
    mySingleRepoProject = isSingleRepoProject(myPushSupports);
    CheckedTreeNode rootNode = new CheckedTreeNode(null);
    createTreeModel(rootNode, preselectedRepositories);
    myPushLog = new PushLog(myProject, rootNode);
    myDialog = dialog;
    myDialog.updateButtons();
    startLoadingCommits();
    Disposer.register(dialog.getDisposable(), this);
    selectFirstChecked();
  }

  private static boolean isSingleRepoProject(@NotNull List<PushSupport<?,?,?>> pushSupports) {
    int repos = 0;
    for (PushSupport support : pushSupports) {
      repos += support.getRepositoryManager().getRepositories().size();
    }
    return repos == 1;
  }

  @NotNull
  private static List<PushSupport<?,?,?>> getAffectedSupports(@NotNull Project project) {
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
    return ContainerUtil.exists(myView2Model.values(), new Condition<MyRepoModel>() {
      @Override
      public boolean value(MyRepoModel model) {
        return model.isSelected() && model.getSupport().isForcePushAllowed(model.getRepository(), model.getTarget());
      }
    });
  }

  private void selectFirstChecked() {
    Map.Entry<RepositoryNode, MyRepoModel> selected =
      ContainerUtil.find(myView2Model.entrySet(), new Condition<Map.Entry<RepositoryNode, MyRepoModel>>() {
        @Override
        public boolean value(Map.Entry<RepositoryNode, MyRepoModel> entry) {
          return entry.getValue().isSelected();
        }
      });
    if (selected != null) {
      myPushLog.selectNode(selected.getKey());
    }
  }

  @Nullable
  public ValidationInfo validate() {
    ValidationInfo validInfo = new ValidationInfo("There are no selected repository to push!");
    for (Map.Entry<RepositoryNode, MyRepoModel> entry : myView2Model.entrySet()) {
      MyRepoModel model = entry.getValue();
      if (model.isSelected()) {
        if (model.hasError()) return new ValidationInfo(model.getError().getText());
        validInfo = null;
      }
    }
    return validInfo;
  }

  private void startLoadingCommits() {
    //todo should be reworked
    Map<RepositoryNode, MyRepoModel> priorityLoading = new HashMap<RepositoryNode, MyRepoModel>();
    Map<RepositoryNode, MyRepoModel> others = new HashMap<RepositoryNode, MyRepoModel>();
    for (Map.Entry<RepositoryNode, MyRepoModel> entry : myView2Model.entrySet()) {
      MyRepoModel model = entry.getValue();
      if (model.isSelected()) {
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

  private void createTreeModel(@NotNull CheckedTreeNode rootNode, @NotNull List<? extends Repository> preselectedRepositories) {
    for (PushSupport<? extends Repository, ? extends PushSource, ? extends PushTarget> support : myPushSupports) {
      createNodesForVcs(support, rootNode, preselectedRepositories);
    }
  }

  private <R extends Repository, S extends PushSource, T extends PushTarget> void createNodesForVcs(
    @NotNull PushSupport<R, S, T> pushSupport,
    @NotNull CheckedTreeNode rootNode,
    @NotNull List<? extends Repository> preselectedRepositories)
  {
    for (R repository : pushSupport.getRepositoryManager().getRepositories()) {
      createRepoNode(pushSupport, repository, rootNode, preselectedRepositories.contains(repository));
    }
  }

  private <R extends Repository, S extends PushSource, T extends PushTarget> void createRepoNode(@NotNull final PushSupport<R, S, T> support,
                                                                                                 @NotNull final R repository,
                                                                                                 @NotNull CheckedTreeNode rootNode,
                                                                                                 boolean isSelected) {
    T target = support.getDefaultTarget(repository);
    String repoName = DvcsUtil.getShortRepositoryName(repository);
    final MyRepoModel<R, S, T> model = new MyRepoModel<R, S, T>(repository, support, mySingleRepoProject || isSelected,
                                                                support.getSource(repository), target,
                                                                DEFAULT_CHILDREN_PRESENTATION_NUMBER);
    if (target == null) {
      model.setError(VcsError.createEmptyTargetError(repoName));
    }
    final PushTargetPanel<T> pushTargetPanel = support.createTargetPanel(repository, target);
    RepositoryWithBranchPanel<T> repoPanel =
      new RepositoryWithBranchPanel<T>(repoName, support.getSource(repository).getPresentation(), pushTargetPanel);
    repoPanel.setInputVerifier(new InputVerifier() {
      @Override
      public boolean verify(JComponent input) {
        ValidationInfo error = pushTargetPanel.verify();
        if (error != null) {
          //noinspection ConstantConditions
          PopupUtil.showBalloonForComponent(error.component, error.message, MessageType.WARNING, false, myProject);
        }
        return error == null;
      }
    });
    final RepositoryNode repoNode = mySingleRepoProject
                                    ? new SingleRepositoryNode(repoPanel)
                                    : new RepositoryNode(repoPanel);
    myView2Model.put(repoNode, model);
    repoNode.setChecked(model.isSelected());
    repoPanel.addRepoNodeListener(new RepositoryNodeListener<T>() {
      @Override
      public void onTargetChanged(T newTarget) {
        model.setTarget(newTarget);
        model.clearErrors();
        loadCommits(model, repoNode, false);
        myDialog.updateButtons();
      }

      @Override
      public void onSelectionChanged(boolean isSelected) {
        model.setSelected(isSelected);
        repoNode.setChecked(isSelected);
        myDialog.updateButtons();
        if (isSelected && !model.hasCommitInfo() && !model.getSupport().shouldRequestIncomingChangesForNotCheckedRepositories()) {
          //download incoming if was not loaded before and marked as selected
          loadCommits(model, repoNode, false);
        }
      }
    });
    rootNode.add(repoNode);
  }

  private <R extends Repository, S extends PushSource, T extends PushTarget> void loadCommits(@NotNull final MyRepoModel<R, S, T> model,
                                                                                              @NotNull final RepositoryNode node,
                                                                                              final boolean initial) {
    node.stopLoading();
    final T target = model.getTarget();
    if (target == null) return;   //todo should be removed when commit loader executor will be modified
    myPushLog.startLoading(node);
    final PushSupport<R, S, T> support = model.getSupport();
    final AtomicReference<OutgoingResult> result = new AtomicReference<OutgoingResult>();
    Runnable task = new Runnable() {
      @Override
      public void run() {
        OutgoingResult outgoing = support.getOutgoingCommitsProvider()
          .getOutgoingCommits(model.getRepository(), new PushSpec<S, T>(model.getSource(), model.getTarget()), initial);
        result.compareAndSet(null, outgoing);
        UIUtil.invokeAndWaitIfNeeded(new Runnable() {
          @Override
          public void run() {
            OutgoingResult outgoing = result.get();
            List<VcsError> errors = outgoing.getErrors();
            if (!errors.isEmpty()) {
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
              model.setLoadedCommits(outgoing.getCommits());
              myPushLog.setChildren(node,
                                    getPresentationForCommits(PushController.this.myProject, model.getLoadedCommits(),
                                                              model.getNumberOfShownCommits()));
            }
          }
        });
      }
    };
    node.startLoading(myExecutorService.submit(task, result));
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
        return new VcsFullCommitDetailsNode(project, commit);
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
