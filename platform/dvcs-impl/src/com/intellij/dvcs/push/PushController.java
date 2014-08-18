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
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.progress.impl.ProgressManagerImpl;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.ui.CheckedTreeNode;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.hash.HashMap;
import com.intellij.vcs.log.VcsFullCommitDetails;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

public class PushController implements Disposable {

  @NotNull private final Project myProject;
  @NotNull private final List<PushSupport<? extends Repository>> myPushSupports;
  @NotNull private final PushLog myPushLog;
  @NotNull private final VcsPushDialog myDialog;
  private boolean mySingleRepoProject;
  private static final int DEFAULT_CHILDREN_PRESENTATION_NUMBER = 20;
  private final Map<PushSupport, MyPushOptionValueModel> myAdditionalValuesMap;

  private final Map<RepositoryNode, MyRepoModel> myView2Model = new HashMap<RepositoryNode, MyRepoModel>();


  public PushController(@NotNull Project project,
                        @NotNull VcsPushDialog dialog,
                        @NotNull List<? extends Repository> preselectedRepositories) {
    myProject = project;
    //todo what would be in case of null
    myPushSupports = Arrays.asList(Extensions.getExtensions(PushSupport.PUSH_SUPPORT_EP, myProject));
    CheckedTreeNode rootNode = new CheckedTreeNode(null);
    mySingleRepoProject = createTreeModel(rootNode, preselectedRepositories);
    myPushLog = new PushLog(myProject, rootNode);
    myAdditionalValuesMap = new HashMap<PushSupport, MyPushOptionValueModel>();
    myDialog = dialog;
    myDialog.updateButtons();
    startLoadingCommits();
    Disposer.register(dialog.getDisposable(), this);
  }

  @Nullable
  public ValidationInfo validate() {
    ValidationInfo validInfo = new ValidationInfo("There are no selected repository to push!");
    for (Map.Entry<RepositoryNode, MyRepoModel> entry : myView2Model.entrySet()) {
      MyRepoModel model = entry.getValue();
      if (model.isSelected()) {
        //has one or more selected roots
        validInfo = null;
        RepositoryNode node = entry.getKey();
        PushTarget target = model.getSpec().getTarget();
        //todo add validation for model -> hasErrors, too
        if (target == null) {
          JComponent editingComponent = myPushLog.startEditNode(node);
          return new ValidationInfo("Invalid remote for repository " + DvcsUtil.getShortRepositoryName(model.getRepository()),
                                    editingComponent);
        }
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
      else {
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

  //return is single repository project or not
  private boolean createTreeModel(@NotNull CheckedTreeNode rootNode, @NotNull List<? extends Repository> preselectedRepositories) {
    if (myPushSupports.isEmpty()) return true;
    int repoCount = 0;
    for (PushSupport<? extends Repository> support : myPushSupports) {
      repoCount += createNodesForVcs(support, rootNode, preselectedRepositories);
    }
    return repoCount == 1;
  }

  private <T extends Repository> int createNodesForVcs(@NotNull PushSupport<T> pushSupport,
                                                       @NotNull CheckedTreeNode rootNode,
                                                       @NotNull List<? extends Repository> preselectedRepositories) {
    RepositoryManager<T> repositoryManager = pushSupport.getRepositoryManager();
    List<T> repositories = repositoryManager.getRepositories();
    for (T repository : repositories) {
      createRepoNode(pushSupport, repository, rootNode, preselectedRepositories.contains(repository), repositories.size() == 1);
    }
    return repositories.size();
  }

  private <T extends Repository> void createRepoNode(@NotNull final PushSupport<T> support,
                                                     @NotNull final T repository,
                                                     @NotNull CheckedTreeNode rootNode,
                                                     boolean isSelected,
                                                     boolean isSingleRepositoryProject) {
    PushTarget target = support.getDefaultTarget(repository);
    final MyRepoModel model = new MyRepoModel(repository, support, isSelected, new PushSpec(support.getSource(repository), target),
                                              DEFAULT_CHILDREN_PRESENTATION_NUMBER);
    RepositoryWithBranchPanel repoPanel = new RepositoryWithBranchPanel(myProject, DvcsUtil.getShortRepositoryName(repository),
                                                                        support.getSource(repository).getPresentation(),
                                                                        target == null ? "" : target.getPresentation(),
                                                                        support.getTargetNames(repository));
    final RepositoryNode repoNode = isSingleRepositoryProject ? new SingleRepositoryNode(repoPanel) : new RepositoryNode(repoPanel);
    myView2Model.put(repoNode, model);
    repoNode.setChecked(model.isSelected());
    repoPanel.addRepoNodeListener(new RepositoryNodeListener() {
      @Override
      public void onTargetChanged(String newValue) {
        VcsError validationError = support.validate(model.getRepository(), newValue);
        if (validationError == null) {
          myView2Model.get(repoNode).setSpec(new PushSpec(model.getSpec().getSource(), support.createTarget(repository, newValue)));
          loadCommits(model, repoNode, false);
        }
        else {
          //todo may be should store validation errors in model and get errors during dialog validation
          myView2Model.get(repoNode).setSpec(new PushSpec(model.getSpec().getSource(), null));
        }
        myDialog.updateButtons();
      }

      @Override
      public void onSelectionChanged(boolean isSelected) {
        myView2Model.get(repoNode).setSelected(isSelected);
        repoNode.setChecked(isSelected);
        myDialog.updateButtons();
      }
    });
    rootNode.add(repoNode);
  }

  private void loadCommits(@NotNull final MyRepoModel model,
                           @NotNull final RepositoryNode node,
                           final boolean initial) {
    node.stopLoading();
    myPushLog.startLoading(node);
    final ProgressIndicator indicator = node.startLoading();
    final PushSupport support = model.getSupport();
    final AtomicReference<OutgoingResult> result = new AtomicReference<OutgoingResult>();
    Task.Backgroundable task = new Task.Backgroundable(myProject, "Loading Commits", true) {

      @Override
      public void onCancel() {
        node.stopLoading();
      }

      @Override
      public void onSuccess() {
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
          }), model.isSelected());
        }
        else {
          model.setLoadedCommits(outgoing.getCommits());
          myPushLog.setChildren(node,
                                getPresentationForCommits(PushController.this.myProject, model.getLoadedCommits(),
                                                          model.getNumberOfShownCommits()), model.isSelected());
        }
      }

      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        OutgoingResult outgoing = support.getOutgoingCommitsProvider()
          .getOutgoingCommits(model.getRepository(), model.getSpec(), initial);
        result.compareAndSet(null, outgoing);
      }
    };

    ProgressManagerImpl.runProcessWithProgressAsynchronously(task, indicator, null, ModalityState.any());
  }


  public PushLog getPushPanelInfo() {
    return myPushLog;
  }

  public void push(final boolean force) {
    Task.Backgroundable task = new Task.Backgroundable(myProject, "Pushing...", false) {
      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        for (PushSupport support : myPushSupports) {
          MyPushOptionValueModel additionalOptionsModel = myAdditionalValuesMap.get(support);
          support.getPusher()
            .push(collectPushInfoForVcs(support), additionalOptionsModel == null ? null : additionalOptionsModel.getCurrentValue(), force);
        }
      }
    };
    task.queue();
  }

  @NotNull
  private Map<Repository, PushSpec> collectPushInfoForVcs(@NotNull final PushSupport pushSupport) {
    Map<Repository, PushSpec> pushSpecs = new HashMap<Repository, PushSpec>();
    Collection<MyRepoModel> repositoriesInformation = getSelectedRepoNode();
    for (MyRepoModel repoModel : repositoriesInformation) {
      if (pushSupport.equals(repoModel.getSupport())) {
        pushSpecs.put(repoModel.getRepository(), repoModel.getSpec());
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
    for (RepositoryNode node : myView2Model.keySet()) {
      node.stopLoading();
    }
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
  public List<VcsPushOptionsPanel> getAdditionalPanels() {
    List<VcsPushOptionsPanel> additionalPanels = new ArrayList<VcsPushOptionsPanel>();
    for (final PushSupport support : myPushSupports) {
      if (hasRepoForPushSupport(support)) {
        final VcsPushOptionsPanel panel = support.getVcsPushOptionsPanel();
        if (panel != null) {
          additionalPanels.add(panel);
          myAdditionalValuesMap.put(support, new MyPushOptionValueModel(panel.getValue()));
          panel.addValueChangeListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
              myAdditionalValuesMap.get(support).setCurrentValue(panel.getValue());
            }
          });
        }
      }
    }
    return additionalPanels;
  }

  private boolean hasRepoForPushSupport(@NotNull final PushSupport support) {
    return ContainerUtil.exists(myView2Model.values(), new Condition<MyRepoModel>() {
      @Override
      public boolean value(MyRepoModel model) {
        return support.equals(model.getSupport());
      }
    });
  }

  private static class MyRepoModel {
    @NotNull final Repository myRepository;
    @NotNull private PushSupport mySupport;

    @NotNull PushSpec mySpec;
    int myNumberOfShownCommits;

    List<? extends VcsFullCommitDetails> myLoadedCommits;
    boolean myIsSelected;

    public MyRepoModel(@NotNull Repository repository,
                       @NotNull PushSupport supportForRepo,
                       boolean isSelected,
                       @NotNull PushSpec spec,
                       int num) {
      myRepository = repository;
      mySupport = supportForRepo;
      myIsSelected = isSelected;
      mySpec = spec;
      myNumberOfShownCommits = num;
    }

    @NotNull
    public Repository getRepository() {
      return myRepository;
    }

    @NotNull
    public PushSupport getSupport() {
      return mySupport;
    }

    public boolean isSelected() {
      return myIsSelected;
    }

    public AbstractVcs<?> getVcs() {
      return myRepository.getVcs();
    }

    @NotNull
    public PushSpec getSpec() {
      return mySpec;
    }

    public void setSpec(@NotNull PushSpec spec) {
      mySpec = spec;
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

    public List<? extends VcsFullCommitDetails> getLoadedCommits() {
      return myLoadedCommits;
    }

    public void setLoadedCommits(List<? extends VcsFullCommitDetails> loadedCommits) {
      myLoadedCommits = loadedCommits;
    }
  }

  private static class MyPushOptionValueModel {
    @NotNull private VcsPushOptionValue myCurrentValue;

    public MyPushOptionValueModel(@NotNull VcsPushOptionValue currentValue) {
      myCurrentValue = currentValue;
    }

    public void setCurrentValue(@NotNull VcsPushOptionValue currentValue) {
      myCurrentValue = currentValue;
    }

    @NotNull
    public VcsPushOptionValue getCurrentValue() {
      return myCurrentValue;
    }
  }
}
