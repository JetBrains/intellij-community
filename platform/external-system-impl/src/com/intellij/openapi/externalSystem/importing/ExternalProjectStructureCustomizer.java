/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
  @NotNull
  public abstract Set<? extends Key<?>> getIgnorableDataKeys();

  /**
   * Set of data keys, which respective data can be represented in External Project Structure Dialog
   * @return data keys
   */
  @NotNull
  public Set<? extends Key<?>> getPublicDataKeys() {
    return Collections.emptySet();
  }

  /**
   * Set of data keys, which respective data can have dependencies or can depend on other data
   *
   * @return data keys
   */
  @NotNull
  public Set<? extends Key<? extends Identifiable>> getDependencyAwareDataKeys() {
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
