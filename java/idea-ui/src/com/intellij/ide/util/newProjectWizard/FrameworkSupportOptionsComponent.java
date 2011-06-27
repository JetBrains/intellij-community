/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.ide.util.newProjectWizard;

import com.intellij.facet.impl.ui.libraries.LibraryCompositionSettings;
import com.intellij.facet.impl.ui.libraries.LibraryOptionsPanel;
import com.intellij.ide.util.frameworkSupport.*;
import com.intellij.ide.util.newProjectWizard.impl.FrameworkSupportModelBase;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.roots.ui.configuration.libraries.CustomLibraryDescription;
import com.intellij.openapi.roots.ui.configuration.projectRoot.LibrariesContainer;
import com.intellij.openapi.ui.VerticalFlowLayout;
import com.intellij.openapi.util.Disposer;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.SeparatorFactory;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.List;

/**
 * @author nik
 */
public class FrameworkSupportOptionsComponent {
  private final JPanel myMainPanel;
  private final FrameworkSupportModelBase myModel;
  private LibraryCompositionSettings myLibraryCompositionSettings;
  private LibraryOptionsPanel myLibraryOptionsPanel;
  private FrameworkSupportConfigurable myConfigurable;

  public FrameworkSupportOptionsComponent(FrameworkSupportModelBase model,
                                          LibrariesContainer container, final FrameworkSupportNode node,
                                          Disposable parentDisposable) {
    myModel = model;
    myConfigurable = node.getConfigurable();
    VerticalFlowLayout layout = new VerticalFlowLayout();
    layout.setVerticalFill(true);
    myMainPanel = new JPanel(layout);

    JComponent separator = SeparatorFactory.createSeparator(node.getTitle() + " Settings", null);
    separator.setBorder(IdeBorderFactory.createEmptyBorder(0, 0, 5, 5));
    myMainPanel.add(separator);

    final JComponent component = myConfigurable.getComponent();
    if (component != null) {
      myMainPanel.add(component);
    }

    final boolean addSeparator = component != null;
    final JPanel librariesOptionsPanelWrapper = new JPanel(new BorderLayout());
    myMainPanel.add(librariesOptionsPanelWrapper);
    myConfigurable.addListener(new FrameworkSupportConfigurableListener() {
      public void frameworkVersionChanged() {
        updateLibrariesPanel();
      }
    });
    model.addFrameworkListener(new FrameworkSupportModelAdapter() {
      @Override
      public void wizardStepUpdated() {
        updateLibrariesPanel();
      }
    }, parentDisposable);

    final CustomLibraryDescription description = createLibraryDescription();
    if (description != null) {
      myLibraryOptionsPanel = new LibraryOptionsPanel(description, myModel.getBaseDirectoryForLibrariesPath(), myConfigurable.getSelectedVersion(),
                                                      container, !myConfigurable.isLibraryOnly());
      Disposer.register(myConfigurable, myLibraryOptionsPanel);
      if (addSeparator) {
        JComponent separator1 = SeparatorFactory.createSeparator("Libraries", null);
        separator1.setBorder(IdeBorderFactory.createEmptyBorder(5, 0, 5, 5));
        librariesOptionsPanelWrapper.add(BorderLayout.NORTH, separator1);
      }
      librariesOptionsPanelWrapper.add(BorderLayout.CENTER, myLibraryOptionsPanel.getMainPanel());
    }
  }

  private void updateLibrariesPanel() {
    if (myLibraryOptionsPanel != null) {
      myLibraryOptionsPanel.changeBaseDirectoryPath(myModel.getBaseDirectoryForLibrariesPath());
      final FrameworkVersion version = myConfigurable.getSelectedVersion();
      myLibraryOptionsPanel.updateDownloadableVersions(version);
    }
  }

  @Nullable
  private CustomLibraryDescription createLibraryDescription() {
    List<FrameworkVersion> versions = myConfigurable.getVersions();
    if (versions.isEmpty()) return null;

    if (versions.get(0) instanceof FrameworkVersionWithLibrary) {
      return ((FrameworkVersionWithLibrary)versions.get(0)).getLibraryDescription();
    }

    return OldCustomLibraryDescription.createByVersions(versions);
  }


  public JPanel getMainPanel() {
    return myMainPanel;
  }

  @Nullable
  public LibraryCompositionSettings getLibraryCompositionSettings() {
    if (myLibraryCompositionSettings == null && myLibraryOptionsPanel != null) {
      myLibraryCompositionSettings = myLibraryOptionsPanel.apply();
    }
    return myLibraryCompositionSettings;
  }

  public LibraryOptionsPanel getLibraryOptionsPanel() {
    return myLibraryOptionsPanel;
  }
}
