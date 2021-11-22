/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.openapi.externalSystem.view;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.externalSystem.model.DataNode;
import com.intellij.openapi.externalSystem.model.Key;
import com.intellij.openapi.externalSystem.model.ProjectKeys;
import com.intellij.openapi.externalSystem.model.ProjectSystemId;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Contributes view's info for project data nodes.
 *
 * @see ExternalProjectsView
 */
public abstract class ExternalSystemViewContributor {
  public static final ExtensionPointName<ExternalSystemViewContributor> EP_NAME =
    ExtensionPointName.create("com.intellij.externalSystemViewContributor");

  /**
   * External system id is needed to find applicable contributor for external project tree view.
   */
  public abstract @NotNull ProjectSystemId getSystemId();

  /**
   * This method defines data node's keys filter.
   * All kept nodes with one of these keys will be added into external project tree view.
   *
   * @see ProjectKeys
   */
  public abstract @NotNull List<Key<?>> getKeys();

  /**
   * Converts given {@code dataNodes} into view nodes that will be added into tree project structure of external project tree view.
   *
   * @param externalProjectsView is external project tree view. Data nodes' views will be added into this project view.
   * @param dataNodes            is data nodes to convert.
   * @return view nodes for given data nodes.
   */
  public abstract @NotNull List<ExternalSystemNode<?>> createNodes(
    ExternalProjectsView externalProjectsView,
    MultiMap<Key<?>, DataNode<?>> dataNodes);

  /**
   * Suggests display name for data node.
   * Note: This name can be overridden by data node's view.
   *
   * @param node is data node to show.
   * @return suggested data node's name.
   * @see ExternalSystemNode#getName
   */
  public @Nullable String getDisplayName(@NotNull DataNode node) {
    return null;
  }

  /**
   * Resolves error level for data from node to show it into external project tree view.
   * Note: NONE level means absence of error.
   *
   * @param dataNode is node with data to resolve error level or its absence.
   * @return resolved error level.
   */
  public ExternalProjectsStructure.ErrorLevel getErrorLevel(DataNode<?> dataNode) {
    return ExternalProjectsStructure.ErrorLevel.NONE;
  }
}
