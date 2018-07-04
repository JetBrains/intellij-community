/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
import com.intellij.util.containers.JBIterable;
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
import java.util.*;
import java.util.List;

public class RepositoryLibraryPropertiesEditor {
  private static final Logger LOG = Logger.getInstance(RepositoryLibraryPropertiesEditor.class);
  @NotNull private final Project project;
  State currentState;
  List<String> versions;
  @Nullable
  private VersionKind versionKind;
  private final RepositoryLibraryPropertiesModel initialModel;
  private final RepositoryLibraryPropertiesModel model;
  private final RepositoryLibraryDescription repositoryLibraryDescription;
  private ComboBox versionKindSelector;
  private ComboBox versionSelector;
  private JPanel mainPanel;
  private JButton myReloadButton;
  private JBCheckBox downloadSourcesCheckBox;
  private JBCheckBox downloadJavaDocsCheckBox;
  private JBLabel mavenCoordinates;
  private final ThreeStateCheckBox myIncludeTransitiveDepsCheckBox;
  private JPanel myPropertiesPanel;
  private JPanel myTransitiveDependenciesPanel;

  @NotNull private final ModelChangeListener onChangeListener;
  private final SwingActionLink myManageDependenciesLink;

  public interface ModelChangeListener {
    void onChange(RepositoryLibraryPropertiesEditor editor);
  }

  public RepositoryLibraryPropertiesEditor(@Nullable Project project,
                                           RepositoryLibraryPropertiesModel model,
                                           RepositoryLibraryDescription description) {
    this(project, model, description, true, new ModelChangeListener() {
      @Override
      public void onChange(RepositoryLibraryPropertiesEditor editor) {

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
    myManageDependenciesLink = new SwingActionLink(new AbstractAction("Configure") {
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
      public void onChange(RepositoryLibraryPropertiesEditor editor) {
        onChangeListener.onChange(editor);
        mavenCoordinates.setText(repositoryLibraryDescription.getMavenCoordinates(model.getVersion()));
      }
    };
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

  private static VersionKind getVersionKind(String version) {
    if (Strings.isNullOrEmpty(version)) {
      return VersionKind.Unselected;
    }
    else if (version.equals(RepositoryLibraryDescription.ReleaseVersionId)) {
      return VersionKind.Release;
    }
    else if (version.equals(RepositoryLibraryDescription.LatestVersionId)) {
      return VersionKind.Latest;
    }
    else {
      return VersionKind.Select;
    }
  }

  private static int getSelection(String selectedVersion, List<String> versions) {
    VersionKind versionKind = getVersionKind(selectedVersion);
    int releaseIndex = JBIterable.from(versions).takeWhile(version -> version.endsWith(RepositoryLibraryDescription.SnapshotVersionSuffix)).size();

    switch (versionKind) {
      case Unselected:
        return -1;
      case Release:
        return releaseIndex == versions.size() ? -1 : releaseIndex;
      case Latest:
        return 0;
      case Select:
        if (versions.indexOf(selectedVersion) == -1) {
          versions.add(0, selectedVersion);
        }
        return versions.indexOf(selectedVersion);
    }
    return -1;
  }

  private void initVersionKindSelector() {
    final List<String> versionKinds = Arrays.asList(
      RepositoryLibraryDescription.ReleaseVersionDisplayName, RepositoryLibraryDescription.LatestVersionDisplayName, "Select..."
    );
    CollectionComboBoxModel<String> versionKindSelectorModel = new CollectionComboBoxModel<>(versionKinds);
    //noinspection unchecked
    versionKindSelector.setModel(versionKindSelectorModel);
    versionKindSelector.addItemListener(new ItemListener() {
      @Override
      public void itemStateChanged(ItemEvent e) {
        VersionKind newVersionKind = getSelectedVersionKind();
        if (newVersionKind != versionKind) {
          versionKind = newVersionKind;
          versionKindChanged();
        }
      }
    });
    setSelectedVersionKind(getVersionKind(model.getVersion()));
  }

  private void versionKindChanged() {
    versionSelector.setEnabled(versionKind == VersionKind.Select);
    model.setVersion(getSelectedVersion());
    int selection = getSelection(model.getVersion(), versions);
    versionSelector.setSelectedIndex(selection);
    onChangeListener.onChange(this);
    updateManageDependenciesLink();
  }

  private VersionKind getSelectedVersionKind() {
    switch (versionKindSelector.getSelectedIndex()) {
      case 0:
        return VersionKind.Release;
      case 1:
        return VersionKind.Latest;
      case 2:
        return VersionKind.Select;
      default:
        return VersionKind.Unselected;
    }
  }

  private void setSelectedVersionKind(VersionKind versionKind) {
    versionSelector.setEnabled(versionKind == VersionKind.Select);
    switch (versionKind) {
      case Unselected:
        versionKindSelector.setSelectedIndex(-1);
        break;
      case Release:
        versionKindSelector.setSelectedItem(0);
        break;
      case Latest:
        versionKindSelector.setSelectedIndex(1);
        break;
      case Select:
        versionKindSelector.setSelectedIndex(2);
        break;
    }
  }

  private void setState(State state) {
    currentState = state;
    ((CardLayout)myPropertiesPanel.getLayout()).show(myPropertiesPanel, state.name());
    onChangeListener.onChange(this);
  }

  private void reloadVersionsAsync() {
    setState(State.Loading);
    JarRepositoryManager.getAvailableVersions(project, repositoryLibraryDescription).onSuccess(result -> versionsLoaded(new ArrayList<>(result)));
  }

  private void initVersionsPanel() {
    final int selection = getSelection(model.getVersion(), versions);
    CollectionComboBoxModel<String> versionSelectorModel = new CollectionComboBoxModel<>(versions);
    //noinspection unchecked
    versionSelector.setModel(versionSelectorModel);
    versionSelector.setSelectedIndex(selection);
    setState(State.Loaded);
    initVersionKindSelector();
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

  private void versionsLoaded(final @Nullable List<String> versions) {
    this.versions = versions;
    if (versions == null || versions.isEmpty()) {
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
    if(versionKind == null) return null;

    switch (versionKind) {
      case Unselected:
        return null;
      case Release:
        return RepositoryLibraryDescription.ReleaseVersionId;
      case Latest:
        return RepositoryLibraryDescription.LatestVersionId;
      case Select:
        return (String)versionSelector.getSelectedItem();
    }
    return null;
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

  private enum VersionKind {
    Unselected,
    Release,
    Latest,
    Select
  }

  private enum State {
    Loading,
    FailedToLoad,
    Loaded
  }
}
