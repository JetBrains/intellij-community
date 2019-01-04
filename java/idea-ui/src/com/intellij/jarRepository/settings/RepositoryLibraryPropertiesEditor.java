// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.jarRepository.settings;

import com.google.common.base.Strings;
import com.intellij.jarRepository.JarRepositoryManager;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.ui.CollectionComboBoxModel;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.labels.SwingActionLink;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.ThreeStateCheckBox;
import com.intellij.util.ui.UI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.aether.ArtifactDependencyNode;
import org.jetbrains.idea.maven.utils.library.RepositoryLibraryDescription;
import org.jetbrains.idea.maven.utils.library.propertiesEditor.RepositoryLibraryPropertiesModel;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

public class RepositoryLibraryPropertiesEditor {
  private static final Logger LOG = Logger.getInstance(RepositoryLibraryPropertiesEditor.class);
  @NotNull private final Project project;
  State currentState;
  List<String> versions;
  private final RepositoryLibraryPropertiesModel initialModel;
  private final RepositoryLibraryPropertiesModel model;
  private final RepositoryLibraryDescription repositoryLibraryDescription;
  private ComboBox<VersionItem> versionSelector;
  private JPanel mainPanel;
  private JButton myReloadButton;
  private JBCheckBox downloadSourcesCheckBox;
  private JBCheckBox downloadJavaDocsCheckBox;
  private JBCheckBox downloadAnnotationsCheckBox;
  private JBLabel mavenCoordinates;
  private final ThreeStateCheckBox myIncludeTransitiveDepsCheckBox;
  private JPanel myPropertiesPanel;
  private JPanel myTransitiveDependenciesPanel;

  @NotNull private final ModelChangeListener onChangeListener;
  private final SwingActionLink myManageDependenciesLink;

  public interface ModelChangeListener {
    void onChange(@NotNull RepositoryLibraryPropertiesEditor editor);
  }

  public RepositoryLibraryPropertiesEditor(@Nullable Project project,
                                           RepositoryLibraryPropertiesModel model,
                                           RepositoryLibraryDescription description) {
    this(project, model, description, true, new ModelChangeListener() {
      @Override
      public void onChange(@NotNull RepositoryLibraryPropertiesEditor editor) {

      }
    });
  }


  public RepositoryLibraryPropertiesEditor(@Nullable Project project,
                                           final RepositoryLibraryPropertiesModel model,
                                           RepositoryLibraryDescription description,
                                           boolean allowExcludingTransitiveDependencies,
                                           @NotNull final ModelChangeListener onChangeListener) {
    this.initialModel = model.clone();
    this.model = model;
    this.project = project == null ? ProjectManager.getInstance().getDefaultProject() : project;
    repositoryLibraryDescription = description;
    mavenCoordinates.setCopyable(true);
    myIncludeTransitiveDepsCheckBox = new ThreeStateCheckBox(UIUtil.replaceMnemonicAmpersand("Include &transitive dependencies"));
    myIncludeTransitiveDepsCheckBox.setThirdStateEnabled(false);
    myTransitiveDependenciesPanel.add(myIncludeTransitiveDepsCheckBox);
    myManageDependenciesLink = new SwingActionLink(new AbstractAction("Configure...") {
      @Override
      public void actionPerformed(ActionEvent e) {
        configureTransitiveDependencies();
      }
    });
    myManageDependenciesLink.setBorder(UI.Borders.emptyLeft(10));
    myTransitiveDependenciesPanel.add(myManageDependenciesLink);
    myTransitiveDependenciesPanel.setVisible(allowExcludingTransitiveDependencies);
    myReloadButton.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        reloadVersionsAsync();
      }
    });
    this.onChangeListener = new ModelChangeListener() {
      @Override
      public void onChange(@NotNull RepositoryLibraryPropertiesEditor editor) {
        onChangeListener.onChange(editor);
        mavenCoordinates.setText(repositoryLibraryDescription.getMavenCoordinates(model.getVersion()));
      }
    };
    versionSelector.setRenderer(new VersionSelectorCellRenderer());
    updateManageDependenciesLink();
    reloadVersionsAsync();
  }

  private void configureTransitiveDependencies() {
    String selectedVersion = getSelectedVersion();
    LOG.assertTrue(selectedVersion != null);

    ArtifactDependencyNode root = JarRepositoryManager.loadDependenciesTree(repositoryLibraryDescription, selectedVersion, project);
    if (root == null) return;

    Set<String> dependencies = new DependencyExclusionEditor(root, mainPanel).selectExcludedDependencies(model.getExcludedDependencies());
    if (dependencies != null) {
      model.setExcludedDependencies(dependencies);
      updateIncludeTransitiveDepsCheckBoxState();
      onChangeListener.onChange(this);
    }
  }

  private static VersionItem toVersionItem(String version) {
    if (Strings.isNullOrEmpty(version)) {
      return null;
    }
    else if (version.equals(RepositoryLibraryDescription.ReleaseVersionId)) {
      return VersionItem.LatestRelease.INSTANCE;
    }
    else if (version.equals(RepositoryLibraryDescription.LatestVersionId)) {
      return VersionItem.LatestVersion.INSTANCE;
    }
    else {
      return new VersionItem.ExactVersion(version);
    }
  }

  private void setState(State state) {
    currentState = state;
    ((CardLayout)myPropertiesPanel.getLayout()).show(myPropertiesPanel, state.name());
    onChangeListener.onChange(this);
  }

  private void reloadVersionsAsync() {
    setState(State.Loading);
    JarRepositoryManager.getAvailableVersions(project, repositoryLibraryDescription)
      .onSuccess(result -> versionsLoaded(new ArrayList<>(result)));
  }

  private void initVersionsPanel() {
    CollectionComboBoxModel<VersionItem> versionSelectorModel = new CollectionComboBoxModel<>();
    versionSelectorModel.add(VersionItem.LatestRelease.INSTANCE);
    versionSelectorModel.add(VersionItem.LatestVersion.INSTANCE);
    versionSelectorModel.add(ContainerUtil.map(versions, VersionItem.ExactVersion::new));
    versionSelector.setModel(versionSelectorModel);
    versionSelector.setSelectedItem(toVersionItem(model.getVersion()));
    setState(State.Loaded);
    versionSelector.addItemListener(new ItemListener() {
      @Override
      public void itemStateChanged(ItemEvent e) {
        model.setVersion(getSelectedVersion());
        onChangeListener.onChange(RepositoryLibraryPropertiesEditor.this);
        updateManageDependenciesLink();
      }
    });
    downloadSourcesCheckBox.setSelected(model.isDownloadSources());
    downloadSourcesCheckBox.addChangeListener(new ChangeListener() {
      @Override
      public void stateChanged(ChangeEvent e) {
        model.setDownloadSources(downloadSourcesCheckBox.isSelected());
        onChangeListener.onChange(RepositoryLibraryPropertiesEditor.this);
      }
    });
    downloadJavaDocsCheckBox.setSelected(model.isDownloadJavaDocs());
    downloadJavaDocsCheckBox.addChangeListener(new ChangeListener() {
      @Override
      public void stateChanged(ChangeEvent e) {
        model.setDownloadJavaDocs(downloadJavaDocsCheckBox.isSelected());
        onChangeListener.onChange(RepositoryLibraryPropertiesEditor.this);
      }
    });
    downloadAnnotationsCheckBox.setSelected(model.isDownloadAnnotations());
    downloadAnnotationsCheckBox.addChangeListener(new ChangeListener() {
      @Override
      public void stateChanged(ChangeEvent e) {
        model.setDownloadAnnotations(downloadAnnotationsCheckBox.isSelected());
        onChangeListener.onChange(RepositoryLibraryPropertiesEditor.this);
      }
    });

    updateIncludeTransitiveDepsCheckBoxState();
    myIncludeTransitiveDepsCheckBox.addChangeListener(new ChangeListener() {
      @Override
      public void stateChanged(ChangeEvent e) {
        updateManageDependenciesLink();
        ThreeStateCheckBox.State state = myIncludeTransitiveDepsCheckBox.getState();
        if (state != ThreeStateCheckBox.State.DONT_CARE) {
          model.setExcludedDependencies(Collections.emptyList());
        }
        model.setIncludeTransitiveDependencies(state != ThreeStateCheckBox.State.NOT_SELECTED);
        onChangeListener.onChange(RepositoryLibraryPropertiesEditor.this);
      }
    });
    updateManageDependenciesLink();
  }

  private void updateIncludeTransitiveDepsCheckBoxState() {
    myIncludeTransitiveDepsCheckBox.setState(!model.isIncludeTransitiveDependencies() ? ThreeStateCheckBox.State.NOT_SELECTED :
                                             model.getExcludedDependencies().isEmpty() ? ThreeStateCheckBox.State.SELECTED : ThreeStateCheckBox.State.DONT_CARE);
  }

  private void updateManageDependenciesLink() {
    boolean enable = myIncludeTransitiveDepsCheckBox.getState() != ThreeStateCheckBox.State.NOT_SELECTED && getSelectedVersion() != null;
    myManageDependenciesLink.setEnabled(enable);
  }

  private void versionsLoaded(@NotNull List<String> versions) {
    this.versions = versions;
    if (versions.isEmpty()) {
      versionsFailedToLoad();
      return;
    }

    ApplicationManager.getApplication().invokeLater(this::initVersionsPanel, ModalityState.any());
  }

  private void versionsFailedToLoad() {
    ApplicationManager.getApplication().invokeLater(() -> setState(State.FailedToLoad), ModalityState.any());
  }

  @Nullable
  public String getSelectedVersion() {
    VersionItem selectedItem = (VersionItem)versionSelector.getSelectedItem();
    return selectedItem != null ? selectedItem.getVersionId() : null;
  }

  public JPanel getMainPanel() {
    return mainPanel;
  }

  public boolean isValid() {
    return currentState == State.Loaded;
  }

  public boolean hasChanges() {
    return !model.equals(initialModel);
  }

  private enum State {
    Loading,
    FailedToLoad,
    Loaded
  }
}
