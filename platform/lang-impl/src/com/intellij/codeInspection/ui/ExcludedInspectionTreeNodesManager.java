/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.codeInspection.ui;


import com.intellij.codeInspection.CommonProblemDescriptor;
import com.intellij.codeInspection.offlineViewer.OfflineProblemDescriptorNode;
import com.intellij.codeInspection.offlineViewer.OfflineRefElementNode;
import com.intellij.codeInspection.reference.RefEntity;
import com.intellij.util.containers.FactoryMap;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.Map;
import java.util.Set;

/**
 * @author Dmitry Batkovich
 */
public class ExcludedInspectionTreeNodesManager {
  @SuppressWarnings("MismatchedQueryAndUpdateOfCollection")
  private final Map<Class, Set<Object>> myExcludedNodeObjects = new FactoryMap<Class, Set<Object>>() {
    @Nullable
    @Override
    protected Set<Object> create(Class key) {
      return new THashSet<Object>();
    }
  };

  private final boolean myOffline;

  public ExcludedInspectionTreeNodesManager(boolean offline) {
    myOffline = offline;
  }

  public synchronized boolean isExcluded(InspectionTreeNode node) {
    final Set<?> excluded = myExcludedNodeObjects.get(node.getClass());
    return excluded.contains(node.getUserObject());
  }

  public synchronized void exclude(InspectionTreeNode node) {
    myExcludedNodeObjects.get(node.getClass()).add(node.getUserObject());
  }

  public synchronized void amnesty(InspectionTreeNode node) {
    myExcludedNodeObjects.get(node.getClass()).remove(node.getUserObject());
  }

  public synchronized boolean containsRefEntity(@NotNull RefEntity entity) {
    return myExcludedNodeObjects.getOrDefault(myOffline ? OfflineRefElementNode.class : RefElementNode.class, Collections.emptySet()).contains(entity);
  }

  public synchronized boolean containsProblemDescriptor(@NotNull CommonProblemDescriptor descriptor) {
    return myExcludedNodeObjects.getOrDefault(myOffline ? OfflineProblemDescriptorNode.class : ProblemDescriptionNode.class, Collections.emptySet()).contains(descriptor);
  }
}
