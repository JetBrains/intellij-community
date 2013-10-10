package com.intellij.ide.util.newProjectWizard;

import com.intellij.framework.FrameworkOrGroup;
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
public abstract class FrameworkSupportNodeBase<T extends FrameworkOrGroup> extends CheckedTreeNode {
  private final FrameworkSupportNodeBase myParentNode;

  public FrameworkSupportNodeBase(T userObject, final FrameworkSupportNodeBase parentNode) {
    super(userObject);
    setChecked(false);
    myParentNode = parentNode;
    if (parentNode != null) {
      parentNode.add(this);
    }
  }

  @Override
  public T getUserObject() {
    return (T)super.getUserObject();
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
  protected final String getTitle() {
    return getUserObject().getPresentableName();
  }

  @NotNull
  public final Icon getIcon() {
    return getUserObject().getIcon();
  }

  @NotNull
  public final String getId() {
    return getUserObject().getId();
  }

  @NotNull
  public List<FrameworkSupportNodeBase> getChildren() {
    return children != null ? children : Collections.<FrameworkSupportNodeBase>emptyList();
  }

  public FrameworkSupportNodeBase getParentNode() {
    return myParentNode;
  }
}
