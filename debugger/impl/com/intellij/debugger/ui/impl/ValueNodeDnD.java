package com.intellij.debugger.ui.impl;

import com.intellij.debugger.ui.impl.watch.DebuggerTreeNodeImpl;
import com.intellij.debugger.ui.impl.watch.ValueDescriptorImpl;
import com.intellij.ide.dnd.DnDAction;
import com.intellij.ide.dnd.DnDDragStartBean;
import com.intellij.ide.dnd.DnDManager;
import com.intellij.ide.dnd.DnDSource;
import com.intellij.ide.dnd.aware.DnDAwareTree;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.Pair;
import com.intellij.util.ui.Tree;
import org.jetbrains.annotations.Nullable;

import javax.swing.tree.TreePath;
import java.awt.*;

public class ValueNodeDnD {
  private DnDAwareTree myTree;

  public ValueNodeDnD(DnDAwareTree tree, Disposable parent) {
    myTree = tree;

    tree.enableDnd(parent);

    DnDManager.getInstance().registerSource(new DnDSource() {
      public boolean canStartDragging(final DnDAction action, final Point dragOrigin) {
        return getNodesToDrag().length > 0;
      }

      public DnDDragStartBean startDragging(final DnDAction action, final Point dragOrigin) {
        DebuggerTreeNodeImpl[] nodes = getNodesToDrag();
        return new DnDDragStartBean(nodes);
      }

      @Nullable
      public Pair<Image, Point> createDraggedImage(final DnDAction action, final Point dragOrigin) {
        DebuggerTreeNodeImpl[] nodes = getNodesToDrag();

        Pair<Image, Point> image;
        if (nodes.length == 1) {
          image = DnDAwareTree.getDragImage(myTree, new TreePath(nodes[0].getPath()), dragOrigin);
        } else {
          image = DnDAwareTree.getDragImage(myTree, nodes.length + " elements", dragOrigin);
        }

        return image;
      }

      public void dragDropEnd() {
      }

      public void dropActionChanged(final int gestureModifiers) {
      }
    }, tree);
  }

  private DebuggerTreeNodeImpl[] getNodesToDrag() {
    return myTree.getSelectedNodes(DebuggerTreeNodeImpl.class, new Tree.NodeFilter<DebuggerTreeNodeImpl>() {
      public boolean accept(final DebuggerTreeNodeImpl node) {
        return node.getDescriptor() instanceof ValueDescriptorImpl;
      }
    });
  }

}
