package com.intellij.ide.util.newProjectWizard;

import com.intellij.ui.CheckedTreeNode;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * @author nik
 */
public abstract class FrameworkSupportNodeBase extends CheckedTreeNode {
  protected final FrameworkSupportNodeBase myParentNode;
  protected final List<FrameworkSupportNodeBase> myChildren = new ArrayList<FrameworkSupportNodeBase>();

  public FrameworkSupportNodeBase(Object userObject, final FrameworkSupportNodeBase parentNode) {
    super(userObject);
    setChecked(false);
    myParentNode = parentNode;
    if (parentNode != null) {
      parentNode.add(this);
      parentNode.myChildren.add(this);
    }
  }

  public static void sortByName(List<FrameworkSupportNodeBase> nodes) {
    if (nodes.isEmpty()) return;

    Collections.sort(nodes, new Comparator<FrameworkSupportNodeBase>() {
      public int compare(final FrameworkSupportNodeBase o1, final FrameworkSupportNodeBase o2) {
        return o1.getTitle().compareToIgnoreCase(o2.getTitle());
      }
    });
    for (FrameworkSupportNodeBase node : nodes) {
      node.sortChildren();
    }
  }

  @NotNull
  protected abstract String getTitle();

  @NotNull
  public abstract Icon getIcon();

  public List<FrameworkSupportNodeBase> getChildren() {
    return myChildren;
  }

  public FrameworkSupportNodeBase getParentNode() {
    return myParentNode;
  }

  private void sortChildren() {
    sortByName(myChildren);
  }
}
