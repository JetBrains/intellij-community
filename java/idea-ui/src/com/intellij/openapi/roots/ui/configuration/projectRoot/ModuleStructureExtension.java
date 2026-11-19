// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.roots.ui.configuration.projectRoot;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.OrderEntry;
import com.intellij.openapi.ui.MasterDetailsComponent;
import com.intellij.openapi.ui.NamedConfigurable;
import com.intellij.openapi.util.ActionCallback;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.function.Supplier;

public abstract class ModuleStructureExtension {
  public static final ExtensionPointName<ModuleStructureExtension> EP_NAME =
    ExtensionPointName.create("com.intellij.configuration.ModuleStructureExtension");

  public void reset(Project project) { }

  public void addModuleNodeChildren(Module module, MasterDetailsComponent.MyNode moduleNode, Runnable treeNodeNameUpdater) { }

  public void moduleRemoved(Module module) { }

  public boolean isModified() {
    return false;
  }

  public void apply() throws ConfigurationException { }

  public void disposeUIResources() { }

  public List<RemoveConfigurableHandler<?>> getRemoveHandlers() {
    return List.of();
  }

  public Collection<AnAction> createAddActions(
    Supplier<MasterDetailsComponent.@Nullable MyNode> selectedNodeRetriever,
    Runnable treeNodeNameUpdater,
    Project project,
    MasterDetailsComponent.MyNode root
  ) {
    return List.of();
  }

  public boolean canBeCopied(NamedConfigurable<?> configurable) {
    return false;
  }

  public void copy(NamedConfigurable<?> configurable, Runnable treeNodeNameUpdater) { }

  public @Nullable Comparator<MasterDetailsComponent.MyNode> getNodeComparator() {
    return null;
  }

  /// @return callback or `null` if not handled
  public @Nullable ActionCallback selectOrderEntry(@NotNull Module module, @Nullable OrderEntry entry) {
    return null;
  }

  public void afterModelCommit() { }
}
