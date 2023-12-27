// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.jarRepository.settings;

import com.intellij.ide.JavaUiBundle;
import com.intellij.jarRepository.JarRepositoryManager;
import com.intellij.jarRepository.RemoteRepositoriesConfiguration;
import com.intellij.jarRepository.RemoteRepositoryDescription;
import com.intellij.jarRepository.services.MavenRepositoryServicesManager;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.InputValidator;
import com.intellij.openapi.ui.MessageDialogBuilder;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.platform.backend.workspace.WorkspaceModel;
import com.intellij.platform.workspace.storage.MutableEntityStorage;
import com.intellij.platform.workspace.storage.instrumentation.MutableEntityStorageInstrumentation;
import com.intellij.ui.CollectionListModel;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.ListUtil;
import com.intellij.ui.components.JBList;
import com.intellij.ui.dsl.listCellRenderer.BuilderKt;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static com.intellij.jarRepository.settings.JarRepositoryLibraryBindUtils.countBindLibraries;
import static com.intellij.jarRepository.settings.JarRepositoryLibraryBindUtils.updateLibrariesRepositoryId;
import static com.intellij.ui.ListUtil.removeSelectedItems;

public final class RemoteRepositoriesConfigurable implements SearchableConfigurable, Configurable.NoScroll {
  private JPanel myMainPanel;

  private JBList<String> myServiceList;
  private JButton myAddServiceButton;
  private JButton myEditServiceButton;
  private JButton myRemoveServiceButton;
  private JButton myTestServiceButton;

  private JBList<RemoteRepositoryDescription> myJarRepositoryList;
  private JButton myAddRepoButton;
  private JButton myEditRepoButton;
  private JButton myRemoveRepoButton;
  private JButton myResetToDefaultReposButton;
  private JButton myResetToDefaultServicesButton;
  private JPanel myMavenPanel;
  private JPanel myServiceListPanel;

  private final Project myProject;
  private final CollectionListModel<String> myServicesModel = new CollectionListModel<>();
  private final CollectionListModel<RemoteRepositoryDescription> myReposModel = new CollectionListModel<>();
  private final WorkspaceModel myWorkspaceModel;
  private MutableEntityStorage myMutableEntityStorage;

  public RemoteRepositoriesConfigurable(Project project) {
    myProject = project;
    myWorkspaceModel = WorkspaceModel.getInstance(project);
    resetMutableEntityStorage();
    configControls();
  }

  @Override
  public boolean isModified() {
    return isServiceListModified() || isRepoListModified() || ((MutableEntityStorageInstrumentation)myMutableEntityStorage).hasChanges();
  }

  private boolean isServiceListModified() {
    return !myServicesModel.getItems().equals(MavenRepositoryServicesManager.getInstance(myProject).getUrls());
  }

  private boolean isRepoListModified() {
    final List<RemoteRepositoryDescription> repos = RemoteRepositoriesConfiguration.getInstance(myProject).getRepositories();
    return !myReposModel.getItems().equals(repos);
  }

  private void configControls() {
    myMavenPanel.setBorder(IdeBorderFactory.createTitledBorder(JavaUiBundle.message("settings.remote.repo.maven.jar.repositories"), false, JBUI.insetsTop(8)).setShowLine(false));
    myServiceListPanel.setBorder(IdeBorderFactory.createTitledBorder(JavaUiBundle.message(
      "settings.remote.repo.artifactory.or.nexus.service.urls"), false, JBUI.insetsTop(8)).setShowLine(false));

    setupCommonListControls(
      myServiceList, myServicesModel, myAddServiceButton, myEditServiceButton,
      JavaUiBundle.message("settings.remote.repo.artifactory.or.nexus"), JavaUiBundle.message("settings.remote.repo.service.url"),
      JavaUiBundle.message("settings.remote.repo.no.services"), DataAdapter.STRING_ADAPTER
    );
    ListUtil.addRemoveListener(myRemoveServiceButton, myServiceList);

    setupCommonListControls(
      myJarRepositoryList, myReposModel, myAddRepoButton, myEditRepoButton,
      JavaUiBundle.message("settings.remote.repo.maven.repository.url"),
      JavaUiBundle.message("settings.remote.repo.Maven.Repository.URL"),
      JavaUiBundle.message("settings.remote.repo.no.remote.repositories"), DataAdapter.REPOSITORY_DESCRIPTION_ADAPTER
    );
    setupRepoRemoveButton();

    ListUtil.disableWhenNoSelection(myTestServiceButton, myServiceList);
    myTestServiceButton.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        final String value = myServiceList.getSelectedValue();
        if (!StringUtil.isEmpty(value)) {
          myTestServiceButton.setEnabled(false);
          JarRepositoryManager.searchRepositories(myProject, Collections.singletonList(value), infos -> {
            myTestServiceButton.setEnabled(true);
            if (infos.isEmpty()) {
              Messages.showMessageDialog(JavaUiBundle.message("settings.remote.repo.no.repositories.found"),
                                         JavaUiBundle.message("settings.remote.repo.service.connection.failed"), Messages.getWarningIcon());
            }
            else {
              Messages.showMessageDialog(JavaUiBundle.message("settings.remote.repo.repositories.found", infos.size()),
                                         JavaUiBundle.message("settings.remote.repo.service.connection.successful"), Messages.getInformationIcon());
            }
            return true;
          });
        }
      }
    });
    myResetToDefaultReposButton.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        Set<String> currentIds = myReposModel.getItems().stream().map(RemoteRepositoryDescription::getId).collect(Collectors.toSet());
        int bindLibrariesCount = countBindLibraries(myMutableEntityStorage, currentIds);
        if (bindLibrariesCount == 0) {
          resetReposModel(RemoteRepositoryDescription.DEFAULT_REPOSITORIES);
          return;
        }

        boolean resetConfirmed = MessageDialogBuilder.yesNo(
          JavaUiBundle.message("jar.repository.manager.confirm.reset.default.repositories.dialog.title"),
          JavaUiBundle.message("jar.repository.manager.confirm.reset.default.repositories.dialog.text", bindLibrariesCount)
        ).ask(myProject);

        if (resetConfirmed) {
          updateLibrariesRepositoryId(myMutableEntityStorage, currentIds, null);
          resetReposModel(RemoteRepositoryDescription.DEFAULT_REPOSITORIES);
        }
      }
    });

    myResetToDefaultServicesButton.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        resetServicesModel(MavenRepositoryServicesManager.DEFAULT_SERVICES);
      }
    });
  }

  private interface DataAdapter<Data, Presentation> {
    DataAdapter<String, String> STRING_ADAPTER = new DataAdapter<>() {
      @Override
      public String toPresentation(@Nls String s) {
        return s;
      }

      @Override
      public String create(String s) {
        return s;
      }

      @Override
      public String change(String current, String changes) {
        return changes;
      }
    };

    DataAdapter<RemoteRepositoryDescription, String> REPOSITORY_DESCRIPTION_ADAPTER = new DataAdapter<>() {
      @Override
      public String toPresentation(RemoteRepositoryDescription description) {
        return description.getUrl();
      }

      @Override
      public RemoteRepositoryDescription create(String url) {
        final UUID uuid = UUID.randomUUID();
        return new RemoteRepositoryDescription(uuid.toString(), uuid.toString(), url);
      }

      @Override
      public RemoteRepositoryDescription change(RemoteRepositoryDescription current, String url) {
        return new RemoteRepositoryDescription(current.getId(), current.getName(), url);
      }
    };
    @Nls Presentation toPresentation(Data data);
    Data create(Presentation presentation);
    Data change(Data current, Presentation changes);
  }

  private static <T> void setupCommonListControls(final JBList<T> list,
                                                  final CollectionListModel<T> model,
                                                  final JButton addButton,
                                                  final JButton editButton,
                                                  final @NlsContexts.DialogMessage String modificationDialogTitle,
                                                  final String modificationDialogHint,
                                                  final @NlsContexts.StatusText String emptyListHint, DataAdapter<T, String> adapter) {
    list.setModel(model);
    list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    list.setCellRenderer(BuilderKt.textListCellRenderer(adapter::toPresentation));
    addButton.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        final T value = list.getSelectedValue();
        @NonNls String defaultValue = "https://";
        String initialValue = value == null ? defaultValue : adapter.toPresentation(value);
        final String text = Messages.showInputDialog(
          modificationDialogTitle, JavaUiBundle.message("dialog.title.add.repository.0", modificationDialogHint), Messages.getQuestionIcon(),
          initialValue, new URLInputValidator()
        );
        if (StringUtil.isNotEmpty(text)) {
          model.add(adapter.create(text));
          list.setSelectedValue(text, true);
        }
      }
    });
    editButton.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        final int index = list.getSelectedIndex();
        final T element = model.getElementAt(index);
        final String text = Messages.showInputDialog(
          modificationDialogTitle, JavaUiBundle.message("dialog.title.edit.repository.0", modificationDialogHint), Messages.getQuestionIcon(), adapter.toPresentation(element), new URLInputValidator()
        );
        if (StringUtil.isNotEmpty(text)) {
          model.setElementAt(adapter.change(element, text), index);
        }
      }
    });
    ListUtil.disableWhenNoSelection(editButton, list);
    list.getEmptyText().setText(emptyListHint);
  }

  private void setupRepoRemoveButton() {
    myRemoveRepoButton.addActionListener(event -> {
      final int index = myJarRepositoryList.getSelectedIndex();
      if (index < 0 || index > myJarRepositoryList.getItemsCount()) {
        return;
      }
      final RemoteRepositoryDescription remoteRepository = myReposModel.getElementAt(index);

      int bindLibrariesCount = countBindLibraries(myMutableEntityStorage, remoteRepository);
      if (bindLibrariesCount > 0) {
        var dialog = new RepositoryRemoveDialog(myProject, remoteRepository, myReposModel.getItems(), bindLibrariesCount);
        if (!dialog.showAndGet()) {
          return;
        }
        updateLibrariesRepositoryId(myMutableEntityStorage, remoteRepository, dialog.getSelectedRepository());
      }

      removeSelectedItems(myJarRepositoryList);
      myJarRepositoryList.requestFocusInWindow();
    });
  }


  @Override
  public String getDisplayName() {
    return JavaUiBundle.message("configurable.RemoteRepositoriesConfigurable.display.name");
  }

  @Override
  public String getHelpTopic() {
    return "reference.jar.repositories";
  }

  @Override
  @NotNull
  public String getId() {
    return getClass().getName();
  }

  @Override
  public JComponent createComponent() {
    return myMainPanel;
  }

  @Override
  public void apply() throws ConfigurationException {
    List<String> newUrls = ContainerUtil.map(myReposModel.getItems(), RemoteRepositoryDescription::getUrl);
    List<String> oldUrls = ContainerUtil.map(RemoteRepositoriesConfiguration.getInstance(myProject).getRepositories(), RemoteRepositoryDescription::getUrl);
    MavenRepositoryServicesManager.getInstance(myProject).setUrls(myServicesModel.getItems());
    RemoteRepositoriesConfiguration.getInstance(myProject).setRepositories(myReposModel.getItems());
    applyMutableEntityStorageChanges();

    if (!newUrls.containsAll(oldUrls) || ((MutableEntityStorageInstrumentation)myMutableEntityStorage).hasChanges()) {
      RepositoryLibrariesReloaderKt.reloadAllRepositoryLibraries(myProject);
    }

    resetMutableEntityStorage();
  }

  @Override
  public void reset() {
    resetServicesModel(MavenRepositoryServicesManager.getInstance(myProject).getUrls());
    resetReposModel(RemoteRepositoriesConfiguration.getInstance(myProject).getRepositories());
    resetMutableEntityStorage();
  }

  private void resetServicesModel(final List<String> urls) {
    myServicesModel.removeAll();
    myServicesModel.add(urls);
  }

  private void resetReposModel(final List<RemoteRepositoryDescription> repositories) {
    myReposModel.replaceAll(repositories);
  }

  private void resetMutableEntityStorage() {
    myMutableEntityStorage = MutableEntityStorage.from(myWorkspaceModel.getCurrentSnapshot());
  }

  private void applyMutableEntityStorageChanges() {
    try {
      WriteAction.run(() -> {
        myWorkspaceModel.updateProjectModel(
          "Update libraries bindings to remote repositories on repository remove", it -> {
            it.addDiff(myMutableEntityStorage);
            return null;
          });
      });
    }
    catch (Throwable e) {
      throw new RuntimeException(e);
    }
  }

  private static final class URLInputValidator implements InputValidator {
    @Override
    public boolean checkInput(String inputString) {
      try {
        URL url = new URL(inputString);
        return StringUtil.isNotEmpty(url.getHost()) || "file".equals(url.getProtocol());
      }
      catch (MalformedURLException e) {
        return false;
      }
    }

    @Override
    public boolean canClose(String inputString) {
      return checkInput(inputString);
    }
  }
}
