package com.intellij.ui.treeStructure.filtered;

import com.intellij.ide.util.treeView.AbstractTreeBuilder;
import com.intellij.ide.util.treeView.AbstractTreeStructure;
import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Ref;
import com.intellij.ui.speedSearch.ElementFilter;
import com.intellij.ui.treeStructure.PatchedDefaultMutableTreeNode;
import com.intellij.ui.treeStructure.SimpleNode;
import com.intellij.ui.treeStructure.SimpleNodeVisitor;
import com.intellij.ui.treeStructure.SimpleTree;
import com.intellij.util.ui.update.MergingUpdateQueue;
import com.intellij.util.ui.update.Update;
import org.jetbrains.annotations.Nullable;

import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import java.util.Comparator;

public class FilteringTreeBuilder extends AbstractTreeBuilder {

  private Object myLastSuccessfulSelect;
  private SimpleTree myTree;

  private MergingUpdateQueue myRefilterQueue;

  public FilteringTreeBuilder(Project project, SimpleTree tree, ElementFilter filter, AbstractTreeStructure structure, Comparator<NodeDescriptor> comparator) {
    super(tree, (DefaultTreeModel) tree.getModel(), new FilteringTreeStructure(project, filter, structure), comparator);
    myTree = tree;
    initRootNode();

    if (filter instanceof ElementFilter.Active) {
      ((ElementFilter.Active)filter).addListener(new ElementFilter.Listener() {
        public void update(final Object preferredSelection) {
          refilter(preferredSelection);
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

  public void setFilteringMerge(int gap) {
    if (myRefilterQueue != null) {
      Disposer.dispose(myRefilterQueue);
      myRefilterQueue = null;
    }

    if (gap >= 0) {
      myRefilterQueue = new MergingUpdateQueue("FilteringTreeBuilder", gap, false, myTree, this, myTree);
      myRefilterQueue.setRestartTimerOnAdd(true);
    }
  }

  protected boolean isSelectable(Object nodeObject) {
    return true;
  }

  public void refilter() {
    refilter(null);
  }

  public void refilter(final Object preferredSelection) {
    if (myRefilterQueue == null) {
      refilterNow(preferredSelection);
    } else {
      myRefilterQueue.queue(new Update(this) {
        public void run() {
          refilterNow(preferredSelection);
        }
      });
    }
  }

  private void refilterNow(Object preferredSelection) {
    Object selectedObject = getSelected();

    final Ref<Object> toSelect = new Ref<Object>(isSelectable(selectedObject) ? selectedObject : null);
    if (preferredSelection != null) {
      toSelect.set(preferredSelection);
    }


    ((FilteringTreeStructure) getTreeStructure()).refilter();
    updateFromRoot();

    boolean wasSelected = false;
    if (toSelect.get() != null && isSelectable(toSelect.get())) {
      wasSelected = myTree.select(this, new SimpleNodeVisitor() {
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

    if (!wasSelected) {
      myTree.select(this, new SimpleNodeVisitor() {
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

    if (!wasSelected && myLastSuccessfulSelect != null) {
      wasSelected = myTree.select(this, new SimpleNodeVisitor() {
        public boolean accept(SimpleNode simpleNode) {
          if (simpleNode instanceof FilteringTreeStructure.Node) {
            Object object = ((FilteringTreeStructure.Node) simpleNode).getDelegate();
            return myLastSuccessfulSelect.equals(object);
          }
          return false;
        }
      }, true);
      if (wasSelected) {
        myLastSuccessfulSelect = getSelected();
      }
    } else if (wasSelected) {
      myLastSuccessfulSelect = getSelected();
    }
  }

  @Nullable
  private Object getSelected() {
    FilteringTreeStructure.Node selected = (FilteringTreeStructure.Node) myTree.getSelectedNode();
    return selected != null ? selected.getDelegate() : null;
  }

  public FilteringTreeStructure.Node getVisibleNodeFor(Object nodeObject) {
    return ((FilteringTreeStructure)getTreeStructure()).getVisibleNodeFor(nodeObject);
  }

  public Object getOriginalNode(SimpleNode node) {
    return ((FilteringTreeStructure.Node)node).getDelegate();
  }


}