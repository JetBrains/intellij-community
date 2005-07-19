/*
 * Copyright 2000-2005 JetBrains s.r.o.
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
package com.intellij.ide.projectView;

import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

public abstract class ProjectViewNode <Value> extends AbstractTreeNode<Value> {

  private static final Logger LOG = Logger.getInstance("#com.intellij.ide.projectView.TreeNode");

  private ViewSettings mySettings;

  protected ProjectViewNode(Project project, Value value, ViewSettings viewSettings) {
    super(project, value);
    mySettings = viewSettings;
  }

  public boolean isAlwaysShowPlus() {
    return false;
  }

  public boolean isAlwaysExpand() {
    return false;
  }

  public abstract boolean contains(VirtualFile file);

  public final ViewSettings getSettings() {
    return mySettings;
  }

  public static List<AbstractTreeNode> wrap(List objects,
                                            Project project,
                                            Class<? extends AbstractTreeNode> nodeClass,
                                            ViewSettings settings) {
    try {
      ArrayList<AbstractTreeNode> result = new ArrayList<AbstractTreeNode>();
      for (int i = 0; i < objects.size(); i++) {
        result.add(createTreeNode(nodeClass, project, objects.get(i), settings));
      }
      return result;
    }
    catch (Exception e) {
      LOG.error(e);
      return new ArrayList<AbstractTreeNode>();
    }
  }

  public static AbstractTreeNode createTreeNode(Class<? extends AbstractTreeNode> nodeClass,
                                                Project project,
                                                Object value,
                                                ViewSettings settings) throws NoSuchMethodException,
                                                                              InstantiationException,
                                                                              IllegalAccessException,
                                                                              InvocationTargetException {
    Constructor<? extends AbstractTreeNode> constructor = nodeClass.getConstructor(
      new Class[]{Project.class, Object.class, ViewSettings.class});
    return constructor.newInstance(new Object[]{project, value, settings});
  }

  protected boolean someChildContainsFile(final VirtualFile file) {
    Collection<AbstractTreeNode> kids = getChildren();
    for (Iterator<AbstractTreeNode> iterator = kids.iterator(); iterator.hasNext();) {
      ProjectViewNode node = (ProjectViewNode)iterator.next();
      if (node.contains(file)) return true;
    }
    return false;
  }
}
