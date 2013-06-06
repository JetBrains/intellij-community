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
package com.intellij.ide.plugins;

import com.intellij.ide.IdeBundle;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.util.containers.HashMap;
import gnu.trove.TObjectIntHashMap;

import java.util.Comparator;
import java.util.Map;
import java.util.Stack;

/**
 * @author Eugene Zhuravlev
 *         Date: Aug 3, 2004
 */
public class PluginDescriptorComparator implements Comparator<IdeaPluginDescriptor>{
  private final TObjectIntHashMap<PluginId> myIdToNumberMap = new TObjectIntHashMap<PluginId>();
  private int myAvailableNumber = 1;

  public PluginDescriptorComparator(IdeaPluginDescriptor[] descriptors) throws Exception{
    final Map<PluginId, IdeaPluginDescriptor> idToDescriptorMap = new HashMap<PluginId, IdeaPluginDescriptor>();
    for (final IdeaPluginDescriptor descriptor : descriptors) {
      idToDescriptorMap.put(descriptor.getPluginId(), descriptor);
    }
    myIdToNumberMap.put(PluginId.getId(PluginManagerCore.CORE_PLUGIN_ID), 0);

    final Stack<PluginId> visited = new Stack<PluginId>();
    for (int idx = 0; idx < descriptors.length && myIdToNumberMap.size() != descriptors.length; idx++) {
      assignNumbers(descriptors[idx].getPluginId(), idToDescriptorMap, visited);
      visited.clear();
    }
  }

  private void assignNumbers(PluginId id, Map<PluginId, IdeaPluginDescriptor> idToDescriptorMap, Stack<PluginId> visited) throws Exception {
    visited.push(id);
    try {
      final IdeaPluginDescriptor ideaPluginDescriptor = idToDescriptorMap.get(id);
      if (ideaPluginDescriptor == null) {
        // missing optional dependency
        return;
      }
      final PluginId[] parentIds = ideaPluginDescriptor.getDependentPluginIds();
      for (final PluginId parentId : parentIds) {
        if (visited.contains(parentId)) {
          throw new Exception(IdeBundle.message("error.plugins.should.not.have.cyclic.dependencies") + id + "->" + parentId + "->...->" + id);
        }
      }
      for (PluginId parentId1 : parentIds) {
        assignNumbers(parentId1, idToDescriptorMap, visited);
      }
      if (!myIdToNumberMap.contains(id)) {
        myIdToNumberMap.put(id, myAvailableNumber++);
      }
    }
    finally {
      visited.pop();
    }
  }

  public int compare(IdeaPluginDescriptor d1, IdeaPluginDescriptor d2) {
    return myIdToNumberMap.get(d1.getPluginId()) - myIdToNumberMap.get(d2.getPluginId());
  }
}
