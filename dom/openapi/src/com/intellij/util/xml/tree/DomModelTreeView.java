package com.intellij.util.xml.tree;

import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.util.Disposer;
import com.intellij.ui.components.panels.Wrapper;
import com.intellij.ui.treeStructure.*;
import com.intellij.ui.treeStructure.actions.CollapseAllAction;
import com.intellij.ui.treeStructure.actions.ExpandAllAction;
import com.intellij.util.ui.tree.TreeUtil;
import com.intellij.util.xml.DomChangeAdapter;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomManager;
import com.intellij.problems.WolfTheProblemSolver;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.TreeExpansionEvent;
import javax.swing.event.TreeExpansionListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Collection;

public class DomModelTreeView extends Wrapper implements DataProvider, Disposable {

  @NonNls public static String DOM_MODEL_TREE_VIEW_KEY = "DOM_MODEL_TREE_VIEW_KEY";
  @NonNls public static String DOM_MODEL_TREE_VIEW_POPUP = "DOM_MODEL_TREE_VIEW_POPUP";

  private final SimpleTree myTree;
  private final LazySimpleTreeBuilder myBuilder;
  private DomManager myDomManager;
  @Nullable private DomElement myRootElement;

  public DomModelTreeView(Project project) {
    this(null, DomManager.getDomManager(project), false);
  }

  public DomModelTreeView(DomElement rootElement) {
    this(rootElement, false);
  }

  public DomModelTreeView(DomElement rootElement, boolean isRootVisible) {
    this(rootElement, rootElement.getManager(), isRootVisible);
  }

  private DomModelTreeView(DomElement rootElement, DomManager manager, boolean isRootVisible) {
    myDomManager = manager;
    myRootElement = rootElement;
    myTree = new SimpleTree(new DefaultTreeModel(new DefaultMutableTreeNode()));
    myTree.setRootVisible(isRootVisible);
    myTree.setShowsRootHandles(true);

    ToolTipManager.sharedInstance().registerComponent(myTree);
    TreeUtil.installActions(myTree);

    final SimpleTreeStructure treeStructure = rootElement != null ? new DomModelTreeStructure(rootElement) : getTreeStructure();
    myBuilder = new LazySimpleTreeBuilder(myTree, (DefaultTreeModel)myTree.getModel(), treeStructure, WeightBasedComparator.INSTANCE);

    myBuilder.setNodeDescriptorComparator(null);

    if (rootElement != null) {
      myBuilder.initRoot();
    }

    add(myTree, BorderLayout.CENTER);

    myTree.addTreeExpansionListener(new TreeExpansionListener() {
      public void treeExpanded(TreeExpansionEvent event) {
        final SimpleNode simpleNode = myTree.getNodeFor(event.getPath());

        if (simpleNode instanceof AbstractDomElementNode) {
          ((AbstractDomElementNode)simpleNode).setExpanded(true);
        }
      }

      public void treeCollapsed(TreeExpansionEvent event) {
        final SimpleNode simpleNode = myTree.getNodeFor(event.getPath());

        if (simpleNode instanceof AbstractDomElementNode) {
          ((AbstractDomElementNode)simpleNode).setExpanded(false);
          simpleNode.update();
        }
      }
    });

    final ChangeListener changeListener = new ChangeListener();
    myDomManager.addDomEventListener(changeListener, this);
    WolfTheProblemSolver.getInstance(myDomManager.getProject()).addProblemListener(changeListener, this);

    myTree.setPopupGroup(getPopupActions(), DOM_MODEL_TREE_VIEW_POPUP);
  }

  public final void updateTree() {
    myBuilder.updateFromRoot();
  }

  public DomElement getRootElement() {
    return myRootElement;
  }

  protected final Project getProject() {
    return myDomManager.getProject();
  }

  protected SimpleTreeStructure getTreeStructure() {
    return new SimpleTreeStructure() {
      public Object getRootElement() {
        return null;
      }
    };
  }

  public LazySimpleTreeBuilder getBuilder() {
    return myBuilder;
  }

  public void dispose() {
    myBuilder.dispose();
  }

  public SimpleTree getTree() {
    return myTree;
  }

  protected ActionGroup getPopupActions() {
    DefaultActionGroup group = new DefaultActionGroup();

    group.add(ActionManager.getInstance().getAction("DomElementsTreeView.TreePopup"));
    group.addSeparator();

    group.add(new ExpandAllAction(myTree));
    group.add(new CollapseAllAction(myTree));

    return group;
  }

  @Nullable
  public Object getData(String dataId) {
    if (DOM_MODEL_TREE_VIEW_KEY.equals(dataId)) {
      return this;
    }
    return null;
  }

  public void setSelectedDomElement(final DomElement domElement) {
    if (domElement == null) return;

    final List<SimpleNode> parentsNodes = getNodesFor(domElement);


    if (parentsNodes.size() > 0) {
      getTree().setSelectedNode(getBuilder(), parentsNodes.get(parentsNodes.size() - 1), true);
    }
  }

  private List<SimpleNode> getNodesFor(final DomElement domElement) {
    final List<SimpleNode> parentsNodes = new ArrayList<SimpleNode>();

    myBuilder.setWaiting(false);
    myTree.accept(myBuilder, new SimpleNodeVisitor() {
      public boolean accept(SimpleNode simpleNode) {
        if (simpleNode instanceof BaseDomElementNode) {
          final DomElement nodeElement = ((AbstractDomElementNode)simpleNode).getDomElement();
          if (isParent(nodeElement, domElement)) {
            parentsNodes.add(simpleNode);
          }
        }
        return false;
      }
    });

    return parentsNodes;
  }

  private static boolean isParent(final DomElement potentialParent, final DomElement domElement) {
    DomElement currParent = domElement;
    while (currParent != null) {
      if (currParent.equals(potentialParent)) return true;

      currParent = currParent.getParent();
    }
    return false;
  }

  private class ChangeListener extends DomChangeAdapter implements WolfTheProblemSolver.ProblemListener {

    protected void elementChanged(DomElement element) {
      update();
    }

    public void problemsChanged(Collection<VirtualFile> added, Collection<VirtualFile> removed) {
      update();
    }

    private void update() {
      if (myTree.isShowing()) {
        myBuilder.queueUpdate();
      }
    }
  }
}

