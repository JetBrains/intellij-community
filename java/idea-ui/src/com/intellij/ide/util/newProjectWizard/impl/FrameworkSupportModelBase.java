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

import com.intellij.framework.addSupport.FrameworkSupportInModuleProvider;
import com.intellij.ide.util.frameworkSupport.FrameworkSupportConfigurable;
import com.intellij.ide.util.frameworkSupport.FrameworkSupportModel;
import com.intellij.ide.util.frameworkSupport.FrameworkSupportModelListener;
import com.intellij.ide.util.frameworkSupport.FrameworkSupportProvider;
import com.intellij.ide.util.newProjectWizard.FrameworkSupportNode;
import com.intellij.ide.util.newProjectWizard.OldFrameworkSupportProviderWrapper;
import com.intellij.ide.util.projectWizard.ModuleBuilder;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ui.configuration.projectRoot.LibrariesContainer;
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
  private final Map<String, FrameworkSupportNode> mySettingsMap = new HashMap<String, FrameworkSupportNode>();

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

  public void removeFrameworkListener(@NotNull final FrameworkSupportModelListener listener) {
    myDispatcher.removeListener(listener);
  }

  public void setFrameworkComponentEnabled(@NotNull @NonNls final String providerId, final boolean enable) {
    final FrameworkSupportNode node = mySettingsMap.get(providerId);
    if (node != null && enable != node.isChecked()) {
      node.setChecked(enable);
    }
  }

  public FrameworkSupportConfigurable getFrameworkConfigurable(@NotNull @NonNls String providerId) {
    final FrameworkSupportNode node = mySettingsMap.get(providerId);
    if (node == null) {
      throw new IllegalArgumentException("provider '" + providerId + " not found");
    }
    return ((OldFrameworkSupportProviderWrapper.FrameworkSupportConfigurableWrapper)node.getConfigurable()).getConfigurable();
  }

  public void onFrameworkSelectionChanged(FrameworkSupportNode node) {
    final FrameworkSupportModelListener multicaster = myDispatcher.getMulticaster();
    final FrameworkSupportInModuleProvider provider = node.getProvider();
    //todo[nik]
    if (provider instanceof OldFrameworkSupportProviderWrapper) {
      final FrameworkSupportProvider oldProvider = ((OldFrameworkSupportProviderWrapper) provider).getProvider();
      if (node.isChecked()) {
        multicaster.frameworkSelected(oldProvider);
      }
      else {
        multicaster.frameworkUnselected(oldProvider);
      }
    }
  }

  public void fireWizardStepUpdated() {
    myDispatcher.getMulticaster().wizardStepUpdated();
  }

  @NotNull
  public LibrariesContainer getLibrariesContainer() {
    return myLibrariesContainer;
  }
}
