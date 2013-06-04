package com.intellij.ide.util.newProjectWizard;

import com.intellij.ui.CheckedTreeNode;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * @author nik
 */
public abstract class FrameworkSupportNodeBase extends CheckedTreeNode {
  private final FrameworkSupportNodeBase myParentNode;

  public FrameworkSupportNodeBase(Object userObject, final FrameworkSupportNodeBase parentNode) {
    super(userObject);
    setChecked(false);
    myParentNode = parentNode;
    if (parentNode != null) {
      parentNode.add(this);
    }
  }

  public static void sortByName(@Nullable List<FrameworkSupportNodeBase> nodes) {
    if (nodes == null) return;

    Collections.sort(nodes, new Comparator<FrameworkSupportNodeBase>() {
      public int compare(final FrameworkSupportNodeBase o1, final FrameworkSupportNodeBase o2) {
        if (o1 instanceof FrameworkGroupNode && !(o2 instanceof FrameworkGroupNode)) return -1;
        if (o2 instanceof FrameworkGroupNode && !(o1 instanceof FrameworkGroupNode)) return 1;
        if (o1.getChildCount() < o2.getChildCount()) return 1;
        if (o1.getChildCount() > o2.getChildCount()) return -1;
        return o1.getTitle().compareToIgnoreCase(o2.getTitle());
      }
    });
    for (FrameworkSupportNodeBase node : nodes) {
      sortByName(node.children);
    }
  }

  @NotNull
  protected abstract String getTitle();

  @NotNull
  public abstract Icon getIcon();

  @NotNull
  public abstract String getId();

  @NotNull
  public List<FrameworkSupportNodeBase> getChildren() {
    return children != null ? children : Collections.<FrameworkSupportNodeBase>emptyList();
  }

  public FrameworkSupportNodeBase getParentNode() {
    return myParentNode;
  }
}
