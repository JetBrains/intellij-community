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
package com.intellij.ide.util.newProjectWizard;

import com.intellij.facet.impl.ui.libraries.LibraryCompositionSettings;
import com.intellij.facet.impl.ui.libraries.LibraryOptionsPanel;
import com.intellij.framework.FrameworkVersion;
import com.intellij.framework.addSupport.FrameworkSupportInModuleConfigurable;
import com.intellij.framework.addSupport.FrameworkSupportInModuleProvider;
import com.intellij.framework.library.FrameworkLibraryVersion;
import com.intellij.framework.library.FrameworkLibraryVersionFilter;
import com.intellij.framework.library.impl.FrameworkLibraryVersionImpl;
import com.intellij.ide.util.frameworkSupport.FrameworkSupportConfigurableListener;
import com.intellij.ide.util.frameworkSupport.FrameworkSupportModelAdapter;
import com.intellij.ide.util.newProjectWizard.impl.FrameworkSupportModelBase;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.roots.ui.configuration.libraries.CustomLibraryDescription;
import com.intellij.openapi.roots.ui.configuration.projectRoot.LibrariesContainer;
import com.intellij.openapi.ui.VerticalFlowLayout;
import com.intellij.openapi.util.Disposer;
import com.intellij.ui.SeparatorFactory;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;
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
  private final FrameworkVersionComponent myFrameworkVersionComponent;
  private LibraryCompositionSettings myLibraryCompositionSettings;
  private LibraryOptionsPanel myLibraryOptionsPanel;
  private final FrameworkSupportInModuleConfigurable myConfigurable;
  private final JPanel myLibraryOptionsPanelWrapper;

  public FrameworkSupportOptionsComponent(FrameworkSupportModelBase model,
                                          LibrariesContainer container,
                                          Disposable parentDisposable,
                                          final FrameworkSupportInModuleProvider provider,
                                          final FrameworkSupportInModuleConfigurable configurable) {
    myModel = model;
    myConfigurable = configurable;
    VerticalFlowLayout layout = new VerticalFlowLayout(VerticalFlowLayout.TOP, 0, 3, true, false);
    layout.setVerticalFill(true);
    myMainPanel = new JPanel(layout);
    myModel.registerOptionsComponent(provider, this);
    List<FrameworkVersion> versions = provider.getFrameworkType().getVersions();
    if (!versions.isEmpty()) {
      myFrameworkVersionComponent = new FrameworkVersionComponent(model, provider.getFrameworkType().getId(), versions, "Versions:");
      myMainPanel.add(myFrameworkVersionComponent.getMainPanel());
    }
    else {
      myFrameworkVersionComponent = null;
    }

    final JComponent component = myConfigurable.createComponent();
    if (component != null) {
      myMainPanel.add(component);
    }

    final boolean addSeparator = component != null || myFrameworkVersionComponent != null;
    myLibraryOptionsPanelWrapper = new JPanel(new BorderLayout());
    myMainPanel.add(myLibraryOptionsPanelWrapper);
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
      myLibraryOptionsPanel = new LibraryOptionsPanel(description, () -> myModel.getBaseDirectoryForLibrariesPath(), createLibraryVersionFilter(), container, !myConfigurable.isOnlyLibraryAdded()) {
        @Override
        protected void onVersionChanged(@Nullable String version) {
          if (myFrameworkVersionComponent == null) {
            myModel.setSelectedLibraryVersion(provider.getId(), version);
          }
        }
      };
      myLibraryOptionsPanel.setLibraryProvider(myModel.getLibraryProvider());
      Disposer.register(myConfigurable, myLibraryOptionsPanel);
      if (addSeparator) {
        JComponent separator1 = SeparatorFactory.createSeparator("Libraries", null);
        separator1.setBorder(JBUI.Borders.empty(5, 0, 5, 5));
        myLibraryOptionsPanelWrapper.add(BorderLayout.NORTH, separator1);
      }
      myLibraryOptionsPanelWrapper.add(BorderLayout.CENTER, myLibraryOptionsPanel.getMainPanel());
      myLibraryOptionsPanelWrapper.setVisible(myConfigurable.isVisible());
    }
  }

  public void updateLibrariesPanel() {
    if (myLibraryOptionsPanel != null) {
      myLibraryOptionsPanel.setVersionFilter(createLibraryVersionFilter());
      myLibraryOptionsPanel.setLibraryProvider(myModel.getLibraryProvider());
      myLibraryOptionsPanelWrapper.setVisible(myConfigurable.isVisible());
    }
  }

  public void updateVersionsComponent() {
    if (myFrameworkVersionComponent != null) {
      myFrameworkVersionComponent.updateVersionsList();
    }
  }


  private FrameworkLibraryVersionFilter createLibraryVersionFilter() {
    return new FrameworkLibraryVersionFilter() {
      @Override
      public boolean isAccepted(@NotNull FrameworkLibraryVersion version) {
        return myConfigurable.getLibraryVersionFilter().isAccepted(version) &&
               ((FrameworkLibraryVersionImpl)version).getAvailabilityCondition().isAvailableFor(myModel);
      }
    };
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
