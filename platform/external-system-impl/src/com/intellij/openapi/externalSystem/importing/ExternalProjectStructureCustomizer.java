// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.externalSystem.importing;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.externalSystem.ExternalSystemUiAware;
import com.intellij.openapi.externalSystem.model.DataNode;
import com.intellij.openapi.externalSystem.model.Key;
import com.intellij.openapi.externalSystem.model.project.Identifiable;
import com.intellij.openapi.util.Couple;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Collections;
import java.util.Set;

/**
 * Provides structure of external system project data node and data node's UI info to show this structure.
 *
 * @see com.intellij.openapi.externalSystem.service.ui.ExternalProjectDataSelectorDialog
 * @see DataNode
 * @see Key
 */
public abstract class ExternalProjectStructureCustomizer {
  public static final ExtensionPointName<ExternalProjectStructureCustomizer> EP_NAME =
    ExtensionPointName.create("com.intellij.externalProjectStructureCustomizer");

  /**
   * Set of data keys, which respective data can be marked as ignored in External Project Structure Dialog
   * @return data keys
   */
  public abstract @NotNull Set<? extends Key<?>> getIgnorableDataKeys();

  /**
   * Set of data keys, which respective data can be represented in External Project Structure Dialog
   * @return data keys
   */
  public @NotNull Set<? extends Key<?>> getPublicDataKeys() {
    return Collections.emptySet();
  }

  /**
   * Set of data keys, which respective data can have dependencies or can depend on other data
   *
   * @return data keys
   */
  public @NotNull Set<? extends Key<? extends Identifiable>> getDependencyAwareDataKeys() {
    return Collections.emptySet();
  }

  /**
   * @return icon for data node that represents this data node info.
   */
  public abstract @Nullable Icon suggestIcon(@NotNull DataNode<?> node, @NotNull ExternalSystemUiAware uiAware);

  /**
   * @return presentation text and description that represents this data node info.
   * Node description can be nullable.
   * @see com.intellij.openapi.externalSystem.service.ui.ExternalProjectDataSelectorDialog
   */
  public @NotNull Couple<@Nls String> getRepresentationName(@NotNull DataNode<?> node) {
    return Couple.of(node.getKey().toString(), null);
  }
}
