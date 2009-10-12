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

/*
 * Created by IntelliJ IDEA.
 * User: Anna.Kozlova
 * Date: 16-Jul-2006
 * Time: 16:29:04
 */
package com.intellij.ide.projectView.impl;

import com.intellij.util.ArrayUtil;
import com.intellij.util.Consumer;
import com.intellij.util.Function;

import java.util.ArrayList;
import java.util.Map;

public class ModuleGroupUtil {
  private ModuleGroupUtil() {
  }

  public static <T> T buildModuleGroupPath(final ModuleGroup group,
                                           T parentNode,
                                           final Map<ModuleGroup, T> map,
                                           final Consumer<ParentChildRelation<T>> insertNode,
                                           final Function<ModuleGroup, T> createNewNode) {
    final ArrayList<String> path = new ArrayList<String>();
    final String[] groupPath = group.getGroupPath();
    for (String pathElement : groupPath) {
      path.add(pathElement);
      final ModuleGroup moduleGroup = new ModuleGroup(ArrayUtil.toStringArray(path));
      T moduleGroupNode = map.get(moduleGroup);
      if (moduleGroupNode == null) {
        moduleGroupNode = createNewNode.fun(moduleGroup);
        map.put(moduleGroup, moduleGroupNode);
        insertNode.consume(new ParentChildRelation<T>(parentNode, moduleGroupNode));
      }
      parentNode = moduleGroupNode;
    }
    return parentNode;
  }

  public static <T> T updateModuleGroupPath(final ModuleGroup group,
                                            T parentNode,
                                            final Function<ModuleGroup, T> needToCreateNode,
                                            final Consumer<ParentChildRelation<T>> insertNode,
                                            final Function<ModuleGroup, T> createNewNode) {
    final ArrayList<String> path = new ArrayList<String>();
    final String[] groupPath = group.getGroupPath();
    for (String pathElement : groupPath) {
      path.add(pathElement);
      final ModuleGroup moduleGroup = new ModuleGroup(ArrayUtil.toStringArray(path));
      T moduleGroupNode = needToCreateNode.fun(moduleGroup);
      if (moduleGroupNode == null) {
        moduleGroupNode = createNewNode.fun(moduleGroup);
        insertNode.consume(new ParentChildRelation<T>(parentNode, moduleGroupNode));
      }
      parentNode = moduleGroupNode;
    }
    return parentNode;
  }

  public static class ParentChildRelation<T> {
    private final T myParent;
    private final T myChild;

    public ParentChildRelation(final T parent, final T child) {
      myParent = parent;
      myChild = child;
    }


    public T getParent() {
      return myParent;
    }

    public T getChild() {
      return myChild;
    }
  }
}
