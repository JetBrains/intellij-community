/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.ide.util.newProjectWizard.impl;

import com.intellij.facet.impl.ui.libraries.FrameworkLibraryProvider;
import com.intellij.framework.FrameworkVersion;
import com.intellij.framework.addSupport.FrameworkSupportInModuleProvider;
import com.intellij.framework.addSupport.FrameworkVersionListener;
import com.intellij.ide.util.frameworkSupport.FrameworkSupportConfigurable;
import com.intellij.ide.util.frameworkSupport.FrameworkSupportModel;
import com.intellij.ide.util.frameworkSupport.FrameworkSupportModelListener;
import com.intellij.ide.util.frameworkSupport.FrameworkSupportProvider;
import com.intellij.ide.util.newProjectWizard.FrameworkSupportNode;
import com.intellij.ide.util.newProjectWizard.FrameworkSupportNodeBase;
import com.intellij.ide.util.newProjectWizard.FrameworkSupportOptionsComponent;
import com.intellij.ide.util.newProjectWizard.OldFrameworkSupportProviderWrapper;
import com.intellij.ide.util.projectWizard.ModuleBuilder;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ui.configuration.projectRoot.LibrariesContainer;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.util.EventDispatcher;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * @author nik
 */
public abstract class FrameworkSupportModelBase extends UserDataHolderBase implements FrameworkSupportModel {
  private final Project myProject;
  private final ModuleBuilder myModuleBuilder;
  private final LibrariesContainer myLibrariesContainer;
  private final EventDispatcher<FrameworkSupportModelListener> myDispatcher = EventDispatcher.create(FrameworkSupportModelListener.class);
  private final EventDispatcher<FrameworkVersionListener> myVersionEventDispatcher = EventDispatcher.create(FrameworkVersionListener.class);
  private final Map<String, FrameworkSupportNode> mySettingsMap = new HashMap<>();
  private final Map<String, FrameworkSupportOptionsComponent> myOptionsComponentsMap = new HashMap<>();
  private final Map<String, FrameworkVersion> mySelectedVersions = new HashMap<>();
  private final Map<String, String> myFrameworkVersions = new HashMap<>();
  private FrameworkLibraryProvider myLibraryProvider;

  public FrameworkSupportModelBase(final @Nullable Project project, @Nullable ModuleBuilder builder, @NotNull LibrariesContainer librariesContainer) {
    myProject = project;
    myModuleBuilder = builder;
    myLibrariesContainer = librariesContainer;
  }

  @NotNull
  public abstract String getBaseDirectoryForLibrariesPath();

  public void registerComponent(@NotNull final FrameworkSupportInModuleProvider provider, @NotNull final FrameworkSupportNode node) {
    mySettingsMap.put(provider.getFrameworkType().getId(), node);
  }

  public void registerOptionsComponent(FrameworkSupportInModuleProvider provider, FrameworkSupportOptionsComponent component) {
    myOptionsComponentsMap.put(provider.getFrameworkType().getId(), component);
  }

  public Project getProject() {
    return myProject;
  }

  public ModuleBuilder getModuleBuilder() {
    return myModuleBuilder;
  }

  public boolean isFrameworkSelected(@NotNull @NonNls final String providerId) {
    final FrameworkSupportNode node = mySettingsMap.get(providerId);
    return node != null && node.isChecked();
  }

  public void addFrameworkListener(@NotNull final FrameworkSupportModelListener listener) {
    myDispatcher.addListener(listener);
  }

  @Override
  public void addFrameworkListener(@NotNull final FrameworkSupportModelListener listener, @NotNull Disposable parentDisposable) {
    myDispatcher.addListener(listener, parentDisposable);
  }

  public void addFrameworkVersionListener(@NotNull FrameworkVersionListener listener, @NotNull Disposable parentDisposable) {
    myVersionEventDispatcher.addListener(listener, parentDisposable);
  }

  public void removeFrameworkListener(@NotNull final FrameworkSupportModelListener listener) {
    myDispatcher.removeListener(listener);
  }

  public void setFrameworkComponentEnabled(@NotNull @NonNls final String providerId, final boolean enable) {
    final FrameworkSupportNode node = mySettingsMap.get(providerId);
    if (node != null && enable != node.isChecked()) {
      node.setChecked(enable);
      // ensure configurable to be created and registered to disposer
      node.getConfigurable();
      if (myFrameworkVersions.get(providerId) == null) {
        for (FrameworkVersion version : node.getUserObject().getFrameworkType().getVersions()) {
          if (version.getAvailabilityCondition().isAvailableFor(this)) {
            setSelectedVersion(providerId, version);
          }
        }
      }
    }
  }

  @Override
  public void updateFrameworkLibraryComponent(@NotNull String providerId) {
    FrameworkSupportOptionsComponent component = myOptionsComponentsMap.get(providerId);
    if (component != null) {
      component.updateLibrariesPanel();
    }
  }

  public FrameworkSupportConfigurable getFrameworkConfigurable(@NotNull @NonNls String providerId) {
    FrameworkSupportConfigurable configurable = findFrameworkConfigurable(providerId);
    if (configurable == null) {
      throw new IllegalArgumentException("provider '" + providerId + "' not found");
    }
    return configurable;
  }

  @Nullable
  @Override
  public FrameworkSupportConfigurable findFrameworkConfigurable(@NotNull @NonNls String providerId) {
    final FrameworkSupportNode node = mySettingsMap.get(providerId);
    if (node == null) {
      return null;
    }
    return ((OldFrameworkSupportProviderWrapper.FrameworkSupportConfigurableWrapper)node.getConfigurable()).getConfigurable();
  }

  public void setSelectedVersion(@NotNull String frameworkOrGroupId, @NotNull FrameworkVersion version) {
    FrameworkVersion oldVersion = mySelectedVersions.put(frameworkOrGroupId, version);
    if (!Comparing.equal(oldVersion, version)) {
      for (Map.Entry<String, FrameworkSupportNode> entry : mySettingsMap.entrySet()) {
        if (hasParentWithId(entry.getValue(), frameworkOrGroupId)) {
          if (!entry.getValue().getId().equals(frameworkOrGroupId)) {
            FrameworkSupportOptionsComponent component = myOptionsComponentsMap.get(entry.getKey());
            if (component != null) {
              component.updateVersionsComponent();
            }
          }
          updateFrameworkLibraryComponent(entry.getKey());
        }
      }
    }
    myVersionEventDispatcher.getMulticaster().versionChanged(version);
  }

  private static boolean hasParentWithId(final FrameworkSupportNode node, @NotNull String frameworkOrGroupId) {
    FrameworkSupportNodeBase current = node;
    while (current != null) {
      if (current.getId().equals(frameworkOrGroupId)) return true;
      current = current.getParentNode();
    }
    return false;
  }

  public FrameworkLibraryProvider getLibraryProvider() {
    return myLibraryProvider;
  }

  public void setLibraryProvider(@Nullable FrameworkLibraryProvider libraryProvider) {
    myLibraryProvider = libraryProvider;
    for (FrameworkSupportOptionsComponent optionsComponent : myOptionsComponentsMap.values()) {
      optionsComponent.updateLibrariesPanel();
    }
  }

  @Nullable
  public <V extends FrameworkVersion> V getSelectedVersion(@NotNull String frameworkOrGroupId) {
    return (V)mySelectedVersions.get(frameworkOrGroupId);
  }

  public void onFrameworkSelectionChanged(FrameworkSupportNode node) {
    final FrameworkSupportInModuleProvider provider = node.getUserObject();
    //todo[nik]
    boolean checked = node.isChecked();
    if (provider instanceof OldFrameworkSupportProviderWrapper) {
      final FrameworkSupportProvider oldProvider = ((OldFrameworkSupportProviderWrapper) provider).getProvider();
      selectFramework(oldProvider, checked);
    }
    for (FrameworkSupportInModuleProvider.FrameworkDependency dependency : provider.getDependenciesFrameworkIds()) {
      if (!dependency.isOptional()) {
        String id = dependency.getFrameworkId();
        setFrameworkComponentEnabled(id, true);
      }
    }
  }

  public void selectFramework(FrameworkSupportProvider provider, boolean checked) {
    final FrameworkSupportModelListener multicaster = myDispatcher.getMulticaster();
    if (checked) {
      multicaster.frameworkSelected(provider);
    }
    else {
      multicaster.frameworkUnselected(provider);
    }
  }

  public void fireWizardStepUpdated() {
    myDispatcher.getMulticaster().wizardStepUpdated();
  }

  @NotNull
  public LibrariesContainer getLibrariesContainer() {
    return myLibrariesContainer;
  }

  public void setSelectedLibraryVersion(String id, String version) {
    myFrameworkVersions.put(id, version);
    myVersionEventDispatcher.getMulticaster().versionChanged(getSelectedVersion(id));
  }

  public String getFrameworkVersion(String id) {
    FrameworkVersion version = mySelectedVersions.get(id);
    return version == null ? myFrameworkVersions.get(id) : version.getVersionNumber();
  }
}
