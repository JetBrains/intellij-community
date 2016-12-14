package org.jetbrains.debugger.memory.view;

import com.intellij.debugger.ui.impl.watch.NodeDescriptorProvider;
import com.intellij.debugger.ui.tree.NodeDescriptor;
import com.intellij.debugger.ui.tree.ValueDescriptor;
import com.intellij.openapi.actionSystem.DataKey;
import com.intellij.openapi.project.Project;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.evaluation.XDebuggerEditorsProvider;
import com.intellij.xdebugger.frame.*;
import com.intellij.xdebugger.impl.actions.XDebuggerActions;
import com.intellij.xdebugger.impl.frame.XValueMarkers;
import com.intellij.xdebugger.impl.ui.tree.XDebuggerTree;
import com.intellij.xdebugger.impl.ui.tree.XDebuggerTreeState;
import com.intellij.xdebugger.impl.ui.tree.nodes.XValueNodeImpl;
import com.sun.jdi.ObjectReference;
import com.sun.jdi.Value;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.tree.TreePath;
import java.util.ArrayList;
import java.util.List;

public class InstancesTree extends XDebuggerTree {
  public static final DataKey<XDebugSession> DEBUG_SESSION_DATA_KEY = DataKey.create("InstancesTree.DebugSession");
  private final XValueNodeImpl myRoot;
  private final Runnable myOnRootExpandAction;
  private final XDebugSession myDebugSession;
  private List<XValueChildrenList> myChildren;

  InstancesTree(@NotNull Project project,
                @NotNull XDebugSession debugSession,
                @NotNull XDebuggerEditorsProvider editorsProvider,
                @Nullable XValueMarkers<?, ?> valueMarkers,
                @NotNull Runnable onRootExpand) {
    super(project, editorsProvider, null, XDebuggerActions.INSPECT_TREE_POPUP_GROUP, valueMarkers);
    myOnRootExpandAction = onRootExpand;
    myRoot = new XValueNodeImpl(this, null, "root", new MyRootValue());
    myDebugSession = debugSession;

    myRoot.children();
    setRoot(myRoot, false);
    myRoot.setLeaf(false);
    setSelectionRow(0);
    expandNodesOnLoad(node -> node == myRoot);
  }

  void addChildren(@NotNull XValueChildrenList children, boolean last) {
    if (myChildren == null) {
      myChildren = new ArrayList<>();
    }

    myChildren.add(children);
    myRoot.addChildren(children, last);
  }

  void rebuildTree(@NotNull RebuildPolicy policy, @NotNull XDebuggerTreeState state) {
    if (policy == RebuildPolicy.RELOAD_INSTANCES) {
      myChildren = null;
    }

    rebuildAndRestore(state);
  }

  void rebuildTree(@NotNull RebuildPolicy policy) {
    rebuildTree(policy, XDebuggerTreeState.saveState(this));
  }

  void setMessage(@Nullable Icon icon, @NotNull String text, @NotNull SimpleTextAttributes textAttributes) {
    myChildren = null;
    myRoot.clearChildren();
    myRoot.setMessage(text, icon, textAttributes, null);
  }

  @Nullable
  ObjectReference getSelectedReference() {
    TreePath selectionPath = getSelectionPath();
    Object selectedItem = selectionPath != null ? selectionPath.getLastPathComponent() : null;
    if (selectedItem instanceof XValueNodeImpl) {
      XValueNodeImpl xValueNode = (XValueNodeImpl) selectedItem;
      XValue valueContainer = xValueNode.getValueContainer();

      if (valueContainer instanceof NodeDescriptorProvider) {
        NodeDescriptor descriptor = ((NodeDescriptorProvider) valueContainer).getDescriptor();

        if (descriptor instanceof ValueDescriptor) {
          Value value = ((ValueDescriptor) descriptor).getValue();

          if (value instanceof ObjectReference) return (ObjectReference) value;
        }
      }
    }

    return null;
  }

  @Nullable
  @Override
  public Object getData(@NonNls String dataId) {
    if (DEBUG_SESSION_DATA_KEY.is(dataId)) {
      return myDebugSession;
    }

    return super.getData(dataId);
  }

  enum RebuildPolicy {
    RELOAD_INSTANCES, ONLY_UPDATE_LABELS
  }

  private class MyRootValue extends XValue {
    @Override
    public void computeChildren(@NotNull XCompositeNode node) {
      if (myChildren == null) {
        myOnRootExpandAction.run();
      } else {
        for (XValueChildrenList children : myChildren) {
          myRoot.addChildren(children, false);
        }

        myRoot.addChildren(XValueChildrenList.EMPTY, true);
      }
    }

    @Override
    public void computePresentation(@NotNull XValueNode node, @NotNull XValuePlace place) {
      node.setPresentation(null, "", "", true);
    }
  }
}
