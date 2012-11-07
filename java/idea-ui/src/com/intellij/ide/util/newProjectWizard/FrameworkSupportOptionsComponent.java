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
import com.intellij.framework.addSupport.FrameworkSupportInModuleConfigurable;
import com.intellij.ide.util.frameworkSupport.FrameworkSupportConfigurableListener;
import com.intellij.ide.util.frameworkSupport.FrameworkSupportModelAdapter;
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

/**
 * @author nik
 */
public class FrameworkSupportOptionsComponent {
  private final JPanel myMainPanel;
  private final FrameworkSupportModelBase myModel;
  private LibraryCompositionSettings myLibraryCompositionSettings;
  private LibraryOptionsPanel myLibraryOptionsPanel;
  private FrameworkSupportInModuleConfigurable myConfigurable;

  public FrameworkSupportOptionsComponent(FrameworkSupportModelBase model, LibrariesContainer container, Disposable parentDisposable,
                                          final FrameworkSupportInModuleConfigurable configurable, final @Nullable String title) {
    myModel = model;
    myConfigurable = configurable;
    VerticalFlowLayout layout = new VerticalFlowLayout();
    layout.setVerticalFill(true);
    myMainPanel = new JPanel(layout);

    //if (title != null) {
    //  myMainPanel.setBorder(IdeBorderFactory.createTitledBorder(title, true));
    //}

    final JComponent component = myConfigurable.createComponent();
    if (component != null) {
      myMainPanel.add(component);
    }

    final boolean addSeparator = component != null;
    final JPanel librariesOptionsPanelWrapper = new JPanel(new BorderLayout());
    myMainPanel.add(librariesOptionsPanelWrapper);
    if (myConfigurable instanceof OldFrameworkSupportProviderWrapper.FrameworkSupportConfigurableWrapper) {
      ((OldFrameworkSupportProviderWrapper.FrameworkSupportConfigurableWrapper)myConfigurable).getConfigurable().addListener(
        new FrameworkSupportConfigurableListener() {
          public void frameworkVersionChanged() {
            updateLibrariesPanel();
          }
        });
    }
    model.addFrameworkListener(new FrameworkSupportModelAdapter() {
      @Override
      public void wizardStepUpdated() {
        updateLibrariesPanel();
      }
    }, parentDisposable);

    final CustomLibraryDescription description = myConfigurable.createLibraryDescription();
    if (description != null) {
      myLibraryOptionsPanel = new LibraryOptionsPanel(description, myModel.getBaseDirectoryForLibrariesPath(), myConfigurable.getLibraryVersionFilter(),
                                                      container, !myConfigurable.isOnlyLibraryAdded());
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
      myLibraryOptionsPanel.setVersionFilter(myConfigurable.getLibraryVersionFilter());
      myLibraryOptionsPanel.getMainPanel().setVisible(myConfigurable.isVisible());
    }
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
