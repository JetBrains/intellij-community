package com.intellij.debugger.ui.impl;

import com.intellij.debugger.actions.DebuggerAction;
import com.intellij.debugger.actions.DebuggerActions;
import com.intellij.debugger.impl.DebuggerStateManager;
import com.intellij.debugger.ui.impl.watch.DebuggerTree;
import com.intellij.debugger.ui.impl.watch.DebuggerTreeNodeImpl;
import com.intellij.debugger.ui.impl.watch.ValueDescriptorImpl;
import com.intellij.ide.dnd.DnDAction;
import com.intellij.ide.dnd.DnDDragStartBean;
import com.intellij.ide.dnd.DnDManager;
import com.intellij.ide.dnd.DnDSource;
import com.intellij.ide.dnd.aware.DnDAwareTree;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.util.ui.Tree;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.event.KeyEvent;

public class VariablesPanel extends DebuggerTreePanel implements DataProvider{

  @NonNls private static final String HELP_ID = "debugging.debugFrame";

  public VariablesPanel(Project project, DebuggerStateManager stateManager, Disposable parent) {
    super(project, stateManager);
    setBorder(null);


    final FrameDebuggerTree frameTree = getFrameTree();

    add(new JScrollPane(frameTree), BorderLayout.CENTER);
    registerDisposable(DebuggerAction.installEditAction(frameTree, DebuggerActions.EDIT_NODE_SOURCE));

    overrideShortcut(frameTree, DebuggerActions.SET_VALUE, KeyStroke.getKeyStroke(KeyEvent.VK_F2, 0));

    myTree.enableDnd(parent);

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
    }, myTree);
  }

  private DebuggerTreeNodeImpl[] getNodesToDrag() {
    return myTree.getSelectedNodes(DebuggerTreeNodeImpl.class, new Tree.NodeFilter<DebuggerTreeNodeImpl>() {
      public boolean accept(final DebuggerTreeNodeImpl node) {
        return node.getDescriptor() instanceof ValueDescriptorImpl;
      }
    });
  }


  protected DebuggerTree createTreeView() {
    return new FrameDebuggerTree(getProject());
  }


  protected ActionPopupMenu createPopupMenu() {
    ActionGroup group = (ActionGroup)ActionManager.getInstance().getAction(DebuggerActions.FRAME_PANEL_POPUP);
    return ActionManager.getInstance().createActionPopupMenu(DebuggerActions.FRAME_PANEL_POPUP, group);
  }

  public Object getData(String dataId) {
    if (DataConstants.HELP_ID.equals(dataId)) {
      return HELP_ID;
    }
    return super.getData(dataId);
  }


  public FrameDebuggerTree getFrameTree() {
    return (FrameDebuggerTree) getTree();
  }

}