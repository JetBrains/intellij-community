package com.intellij.ide.projectView;

import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;
import java.util.Collection;
import java.util.Iterator;

public abstract class ProjectViewNode <Value> extends AbstractTreeNode<Value> {

  static private final Logger LOG = Logger.getInstance("#com.intellij.ide.projectView.TreeNode");

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
