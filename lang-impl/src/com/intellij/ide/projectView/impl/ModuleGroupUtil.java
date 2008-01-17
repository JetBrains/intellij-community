/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

/*
 * Created by IntelliJ IDEA.
 * User: Anna.Kozlova
 * Date: 16-Jul-2006
 * Time: 16:29:04
 */
package com.intellij.ide.projectView.impl;

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
      final ModuleGroup moduleGroup = new ModuleGroup(path.toArray(new String[path.size()]));
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
      final ModuleGroup moduleGroup = new ModuleGroup(path.toArray(new String[path.size()]));
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
    private T myParent;
    private T myChild;

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