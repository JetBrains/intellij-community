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


import com.intellij.openapi.application.ApplicationManager;
import com.intellij.util.containers.FactoryMap;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
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
      return new HashSet<>();
    }
  };

  public boolean isExcluded(InspectionTreeNode node) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    final Set<Object> excluded = myExcludedNodeObjects.get(node.getClass());
    return excluded.contains(node.getUserObject());
  }

  public void exclude(InspectionTreeNode node) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    myExcludedNodeObjects.get(node.getClass()).add(node.getUserObject());
  }

  public void amnesty(InspectionTreeNode node) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    myExcludedNodeObjects.get(node.getClass()).remove(node.getUserObject());
  }
}
