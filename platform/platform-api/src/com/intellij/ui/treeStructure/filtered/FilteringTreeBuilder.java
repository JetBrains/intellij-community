package com.intellij.ui.treeStructure.filtered;

import com.intellij.ide.util.treeView.AbstractTreeBuilder;
import com.intellij.ide.util.treeView.AbstractTreeStructure;
import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.ActionCallback;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Ref;
import com.intellij.ui.speedSearch.ElementFilter;
import com.intellij.ui.treeStructure.*;
import com.intellij.util.ui.update.MergingUpdateQueue;
import com.intellij.util.ui.update.Update;
import org.jetbrains.annotations.Nullable;

import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.*;
import java.util.Comparator;

public class FilteringTreeBuilder extends AbstractTreeBuilder {

  private Object myLastSuccessfulSelect;
  private final Tree myTree;

  private MergingUpdateQueue myRefilterQueue;

  public FilteringTreeBuilder(Project project,
                              Tree tree,
                              ElementFilter filter,
                              AbstractTreeStructure structure,
                              Comparator<NodeDescriptor> comparator) {
    super(tree, (DefaultTreeModel)tree.getModel(), new FilteringTreeStructure(project, filter, structure), comparator);
    myTree = tree;
    initRootNode();

    if (filter instanceof ElementFilter.Active) {
      ((ElementFilter.Active)filter).addListener(new ElementFilter.Listener() {
        public ActionCallback update(final Object preferredSelection, final boolean adjustSelection, final boolean now) {
          return refilter(preferredSelection, adjustSelection, now);
        }
      }, this);
    }
  }

  public boolean isAlwaysShowPlus(NodeDescriptor nodeDescriptor) {
    return false;
  }

  public boolean isAutoExpandNode(NodeDescriptor nodeDescriptor) {
    return true;
  }

  protected final DefaultMutableTreeNode createChildNode(final NodeDescriptor childDescr) {
    return new PatchedDefaultMutableTreeNode(childDescr);
  }

  public void setFilteringMerge(int gap, @Nullable JComponent modalityStateComponent) {
    if (myRefilterQueue != null) {
      Disposer.dispose(myRefilterQueue);
      myRefilterQueue = null;
    }

    if (gap >= 0) {
      JComponent stateComponent = modalityStateComponent;
      if (stateComponent == null) {
        stateComponent = myTree;
      }

      myRefilterQueue = new MergingUpdateQueue("FilteringTreeBuilder", gap, false, stateComponent, this, myTree);
      myRefilterQueue.setRestartTimerOnAdd(true);
    }
  }

  protected boolean isSelectable(Object nodeObject) {
    return true;
  }

  public void refilter() {
    refilter(null, true, false);
  }

  public ActionCallback refilter(final Object preferredSelection, final boolean adjustSelection, final boolean now) {
    if (myRefilterQueue == null || now) {
      return refilterNow(preferredSelection, adjustSelection);
    }
    else {
      final ActionCallback result = new ActionCallback();
      myRefilterQueue.queue(new Update(this) {
        public void run() {
          refilterNow(preferredSelection, adjustSelection).notifyWhenDone(result);
        }

        @Override
        public void setRejected() {
          super.setRejected();
          result.setDone();
        }
      });

      return result;
    }
  }

  private ActionCallback refilterNow(Object preferredSelection, final boolean adjustSelection) {
    final ActionCallback result = new ActionCallback();

    Object selectedObject = getSelected();

    final Ref<Object> toSelect = new Ref<Object>(isSelectable(selectedObject) ? selectedObject : null);
    if (preferredSelection != null) {
      toSelect.set(preferredSelection);
    }


    ((FilteringTreeStructure)getTreeStructure()).refilter();
    updateFromRoot();

    result.setDone();

    if (adjustSelection) {
      boolean wasSelected = false;
      if (toSelect.get() != null && isSelectable(toSelect.get()) && isSimpleTree()) {
        wasSelected = ((SimpleTree)myTree).select(this, new SimpleNodeVisitor() {
          public boolean accept(SimpleNode simpleNode) {
            if (simpleNode instanceof FilteringTreeStructure.Node) {
              FilteringTreeStructure.Node node = (FilteringTreeStructure.Node)simpleNode;
              return node.getDelegate().equals(toSelect.get());
            }
            else {
              return false;
            }
          }
        }, true);
      }

      if (!wasSelected && isSimpleTree()) {
        ((SimpleTree)myTree).select(this, new SimpleNodeVisitor() {
          public boolean accept(SimpleNode simpleNode) {
            if (simpleNode instanceof FilteringTreeStructure.Node) {

              final boolean isRoot = getTreeStructure().getRootElement() == simpleNode;
              if (isRoot && !myTree.isRootVisible()) return false;

              FilteringTreeStructure.Node node = (FilteringTreeStructure.Node)simpleNode;
              if (isSelectable(node.getDelegate())) {
                return true;
              }
            }
            else {
              return false;
            }
            return false;
          }
        }, true);
      }

      if (!wasSelected && myLastSuccessfulSelect != null && isSimpleTree()) {
        wasSelected = ((SimpleTree)myTree).select(this, new SimpleNodeVisitor() {
          public boolean accept(SimpleNode simpleNode) {
            if (simpleNode instanceof FilteringTreeStructure.Node) {
              Object object = ((FilteringTreeStructure.Node)simpleNode).getDelegate();
              return myLastSuccessfulSelect.equals(object);
            }
            return false;
          }
        }, true);
        if (wasSelected) {
          myLastSuccessfulSelect = getSelected();
        }
      }
      else if (wasSelected) {
        myLastSuccessfulSelect = getSelected();
      }
    }

    return result;
  }

  //todo kirillk
  private boolean isSimpleTree() {
    return myTree instanceof SimpleTree;
  }

  @Nullable
  private Object getSelected() {
    if (isSimpleTree()) {
      FilteringTreeStructure.Node selected = (FilteringTreeStructure.Node)((SimpleTree)myTree).getSelectedNode();
      return selected != null ? selected.getDelegate() : null;
    } else {
      final Object[] nodes = myTree.getSelectedNodes(Object.class, null);
      return nodes.length > 0 ? nodes[0] : null;
    }
  }

  public FilteringTreeStructure.Node getVisibleNodeFor(Object nodeObject) {
    return ((FilteringTreeStructure)getTreeStructure()).getVisibleNodeFor(nodeObject);
  }

  public Object getOriginalNode(Object node) {
    return ((FilteringTreeStructure.Node)node).getDelegate();
  }

  @Override
  protected Object transformElement(Object object) {
    return getOriginalNode(object);
  }
}
