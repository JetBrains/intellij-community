// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
import com.intellij.openapi.util.NullableComputable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public abstract class ModuleStructureExtension {

  public static final ExtensionPointName<ModuleStructureExtension> EP_NAME =
    ExtensionPointName.create("com.intellij.configuration.ModuleStructureExtension");

  public void reset(Project project) {
  }

  public boolean addModuleNodeChildren(Module module, MasterDetailsComponent.MyNode moduleNode, Runnable treeNodeNameUpdater) {
    return false;
  }

  public void moduleRemoved(final Module module) {
  }

  public boolean isModified() {
    return false;
  }

  public void apply() throws ConfigurationException {
  }

  public void disposeUIResources() {
  }

  public List<RemoveConfigurableHandler<?>> getRemoveHandlers() {
    return Collections.emptyList();
  }

  public Collection<AnAction> createAddActions(final NullableComputable<MasterDetailsComponent.MyNode> selectedNodeRetriever,
                                               final Runnable treeNodeNameUpdater,
                                               final Project project,
                                               final MasterDetailsComponent.MyNode root) {
    return Collections.emptyList();
  }

  public boolean canBeCopied(final NamedConfigurable configurable) {
    return false;
  }

  public void copy(final NamedConfigurable configurable, final Runnable treeNodeNameUpdater) {
  }

  public void addRootNodes(final MasterDetailsComponent.MyNode parent, final Project project, final Runnable treeUpdater) {
  }

  public @Nullable Comparator<MasterDetailsComponent.MyNode> getNodeComparator() {
    return null;
  }

  /**
   * @return callback or {@code null} if not handled
   */
  public @Nullable ActionCallback selectOrderEntry(final @NotNull Module module, final @Nullable OrderEntry entry) {
    return null;
  }

  public void afterModelCommit() {
  }
}
