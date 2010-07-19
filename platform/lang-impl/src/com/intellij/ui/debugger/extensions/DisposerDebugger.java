/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.intellij.ui.debugger.extensions;

import com.intellij.ide.projectView.PresentationData;
import com.intellij.ide.projectView.TreeStructureProvider;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.ide.util.treeView.AbstractTreeStructureBase;
import com.intellij.ide.util.treeView.AlphaComparator;
import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.ui.Splitter;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.objectTree.ObjectNode;
import com.intellij.openapi.util.objectTree.ObjectTree;
import com.intellij.openapi.util.objectTree.ObjectTreeListener;
import com.intellij.openapi.vcs.history.TextTransferrable;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.debugger.UiDebuggerExtension;
import com.intellij.ui.speedSearch.ElementFilter;
import com.intellij.ui.tabs.JBTabs;
import com.intellij.ui.tabs.TabInfo;
import com.intellij.ui.tabs.TabsListener;
import com.intellij.ui.tabs.impl.JBTabsImpl;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.ui.treeStructure.filtered.FilteringTreeBuilder;
import com.intellij.util.ui.update.MergingUpdateQueue;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.text.DefaultCaret;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeSelectionModel;
import java.awt.*;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.*;
import java.util.List;

public class DisposerDebugger implements UiDebuggerExtension, Disposable  {

  private static final Logger LOG = Logger.getInstance("#com.intellij.ui.debugger.extensions.DisposerDebugger");

  private JComponent myComponent;
  private JBTabsImpl myTreeTabs;

  private void initUi() {
    myComponent = new JPanel();

    myTreeTabs = new JBTabsImpl(null, null, this);

    final Splitter splitter = new Splitter(true);

    final JBTabsImpl bottom = new JBTabsImpl(null, null, this);
    final AllocationPanel allocations = new AllocationPanel(myTreeTabs);
    bottom.addTab(new TabInfo(allocations).setText("Allocation")).setActions(allocations.getActions(), ActionPlaces.UNKNOWN);


    splitter.setFirstComponent(myTreeTabs);
    splitter.setSecondComponent(bottom);

    myComponent.setLayout(new BorderLayout());
    myComponent.add(splitter, BorderLayout.CENTER);
    JLabel countLabel = new JLabel("Total disposable count: " + Disposer.getTree().size());
    myComponent.add(countLabel, BorderLayout.SOUTH);

    addTree(new DisposerTree(this), "All", false);
    addTree(new DisposerTree(this), "Watch", true);
  }

  private void addTree(DisposerTree tree, String name, boolean canClear) {
    final DefaultActionGroup group = new DefaultActionGroup();
    if (canClear) {
      group.add(new ClearAction(tree));
    }

    myTreeTabs.addTab(new TabInfo(tree).setText(name).setObject(tree).setActions(group, ActionPlaces.UNKNOWN));
  }

  private class ClearAction extends AnAction {
    private final DisposerTree myTree;

    private ClearAction(DisposerTree tree) {
      super("Clear");
      myTree = tree;
    }

    @Override
    public void update(AnActionEvent e) {
      e.getPresentation().setIcon(IconLoader.getIcon("/debugger/watch.png"));
    }

    public void actionPerformed(AnActionEvent e) {
      myTree.clear();
    }
  }

  private static class AllocationPanel extends JPanel implements TreeSelectionListener {

    private final JEditorPane myAllocation;
    private final DefaultActionGroup myActions;

    private final JBTabs myTreeTabs;

    private AllocationPanel(JBTabs treeTabs) {
      myTreeTabs = treeTabs;

      myAllocation = new JEditorPane();
      final DefaultCaret caret = new DefaultCaret();
      myAllocation.setCaret(caret);
      caret.setUpdatePolicy(DefaultCaret.NEVER_UPDATE);
      myAllocation.setEditable(false);

      setLayout(new BorderLayout());
      add(ScrollPaneFactory.createScrollPane(myAllocation), BorderLayout.CENTER);


      treeTabs.addListener(new TabsListener.Adapter() {
        @Override
        public void selectionChanged(TabInfo oldSelection, TabInfo newSelection) {
          updateText();
        }

        @Override
        public void beforeSelectionChanged(TabInfo oldSelection, TabInfo newSelection) {
          removeListener(oldSelection);
          addListener(newSelection);
        }
      });

      myActions = new DefaultActionGroup();
      myActions.add(new CopyAllocationAction());
    }

    private void addListener(TabInfo info) {
      if (info == null) return;
      ((DisposerTree)info.getObject()).getTree().getSelectionModel().addTreeSelectionListener(this);
    }

    private void removeListener(TabInfo info) {
      if (info == null) return;
      ((DisposerTree)info.getObject()).getTree().getSelectionModel().removeTreeSelectionListener(this);
    }

    public void valueChanged(TreeSelectionEvent e) {
      updateText();
    }

    private void updateText() {
      if (myTreeTabs.getSelectedInfo() == null) return;

      final DisposerTree tree = (DisposerTree)myTreeTabs.getSelectedInfo().getObject();

      final DisposerNode node = tree.getSelectedNode();
      if (node != null) {
        final Throwable allocation = node.getAllocation();
        if (allocation != null) {
          final StringWriter s = new StringWriter();
          final PrintWriter writer = new PrintWriter(s);
          allocation.printStackTrace(writer);
          myAllocation.setText(s.toString());
          return;
        }
      }

      myAllocation.setText(null);
    }

    public ActionGroup getActions() {
      return myActions;
    }

    private class CopyAllocationAction extends AnAction {
      public CopyAllocationAction() {
        super("Copy", "Copy allocation to clipboard", IconLoader.getIcon("/actions/copy.png"));
      }

      @Override
      public void update(AnActionEvent e) {
        e.getPresentation().setEnabled(myAllocation.getDocument().getLength() > 0);
      }

      public void actionPerformed(AnActionEvent e) {
        try {
          Toolkit.getDefaultToolkit().getSystemClipboard()
            .setContents(new TextTransferrable(myAllocation.getText(), myAllocation.getText()), null);
        }
        catch (HeadlessException e1) {
          LOG.error(e1);
        }
      }
    }

  }

  private static class DisposerTree extends JPanel implements Disposable, ObjectTreeListener, ElementFilter<DisposerNode> {

    private final FilteringTreeBuilder myTreeBuilder;
    private final Tree myTree;
    private long myModificationToFilter;

    private DisposerTree(Disposable parent) {
      myModificationToFilter = -1;

      final DisposerStructure structure = new DisposerStructure(this);
      final DefaultTreeModel model = new DefaultTreeModel(new DefaultMutableTreeNode());
      final Tree tree = new Tree(model);
      tree.setRootVisible(false);
      tree.setShowsRootHandles(true);
      tree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
      myTreeBuilder = new FilteringTreeBuilder(null, tree, DisposerTree.this, structure, AlphaComparator.INSTANCE) {
        @Override
        public boolean isAutoExpandNode(NodeDescriptor nodeDescriptor) {
          return structure.getRootElement() == getOriginalNode(nodeDescriptor);
        }
      };
      myTreeBuilder.setFilteringMerge(200, MergingUpdateQueue.ANY_COMPONENT);
      Disposer.register(this, myTreeBuilder);
      myTree = tree;

      setLayout(new BorderLayout());
      add(ScrollPaneFactory.createScrollPane(myTree), BorderLayout.CENTER);

      Disposer.getTree().addListener(this);

      Disposer.register(parent, this);
    }

    public boolean shouldBeShowing(DisposerNode value) {
      return value.getValue().getModification() > myModificationToFilter;
    }

    public void objectRegistered(Object node) {
      queueUpdate();
    }



    public void objectExecuted(Object node) {
      queueUpdate();
    }

    private void queueUpdate() {
      myTreeBuilder.refilter();
    }

    public void dispose() {
      Disposer.getTree().removeListener(this);
    }

    @Nullable
    public DisposerNode getSelectedNode() {
      final Set<DisposerNode> nodes = myTreeBuilder.getSelectedElements(DisposerNode.class);
      return nodes.size() == 1 ? nodes.iterator().next() : null;
    }

    public Tree getTree() {
      return myTree;
    }

    public void clear() {
      myModificationToFilter = Disposer.getTree().getModification();
      myTreeBuilder.refilter();
    }
  }

  public JComponent getComponent() {
    if (myComponent == null) {
      initUi();
    }

    return myComponent;
  }

  public String getName() {
    return "Disposer";
  }

  public void dispose() {
  }


  private static class DisposerStructure extends AbstractTreeStructureBase {
    private final DisposerNode myRoot;

    private DisposerStructure(DisposerTree tree) {
      super(null);
      myRoot = new DisposerNode(tree, null);
    }

    public List<TreeStructureProvider> getProviders() {
      return null;
    }

    public Object getRootElement() {
      return myRoot;
    }

    public void commit() {
    }

    public boolean hasSomethingToCommit() {
      return false;
    }
  }

  private static class DisposerNode extends AbstractTreeNode<ObjectNode> {
    private final DisposerTree myTree;

    private DisposerNode(DisposerTree tree, ObjectNode value) {
      super(null, value);
      myTree = tree;
    }

    @NotNull
    public Collection<? extends AbstractTreeNode> getChildren() {
      final ObjectNode value = getValue();
      if (value != null) {
        final Collection subnodes = value.getChildren();
        final ArrayList<DisposerNode> children = new ArrayList<DisposerNode>(subnodes.size());
        for (Iterator iterator = subnodes.iterator(); iterator.hasNext();) {
          children.add(new DisposerNode(myTree, (ObjectNode)iterator.next()));
        }
        return children;
      }
      else {
        final ObjectTree<Disposable> tree = Disposer.getTree();
        final THashSet<Disposable> root = tree.getRootObjects();
        ArrayList<DisposerNode> children = new ArrayList<DisposerNode>(root.size());
        for (Disposable each : root) {
          children.add(new DisposerNode(myTree, tree.getNode(each)));
        }
        return children;
      }
    }

    @Nullable
    public Throwable getAllocation() {
      return getValue() != null ? getValue().getAllocation() : null;
    }

    @Override
    protected boolean shouldUpdateData() {
      return true;
    }

    protected void update(PresentationData presentation) {
      if (getValue() != null) {
        final Object object = getValue().getObject();
        final String classString = object.getClass().toString();
        final String objectString = object.toString();

        presentation.setPresentableText(objectString);

        if (getValue().getOwnModification() < myTree.myModificationToFilter) {
          presentation.setForcedTextForeground(Color.gray);
        }

        if (objectString != null) {
          final int dogIndex = objectString.lastIndexOf("@");
          if (dogIndex >= 0) {
            final String fqNameObject = objectString.substring(0, dogIndex);
            final String fqNameClass = classString.substring("class ".length());
            if (fqNameObject.equals(fqNameClass)) return;
          }
        }

        presentation.setLocationString(classString);
      }
    }
  }

  public void disposeUiResources() {
    myComponent = null;
  }
}
