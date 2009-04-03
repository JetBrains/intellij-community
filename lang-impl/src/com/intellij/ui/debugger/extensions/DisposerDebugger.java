package com.intellij.ui.debugger.extensions;

import com.intellij.ide.projectView.PresentationData;
import com.intellij.ide.projectView.TreeStructureProvider;
import com.intellij.ide.util.treeView.*;
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
import com.intellij.ui.debugger.UiDebuggerExtension;
import com.intellij.ui.tabs.JBTabs;
import com.intellij.ui.tabs.TabInfo;
import com.intellij.ui.tabs.TabsListener;
import com.intellij.ui.tabs.impl.JBTabsImpl;
import com.intellij.util.Alarm;
import com.intellij.util.ui.Tree;
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

public class DisposerDebugger extends JComponent implements UiDebuggerExtension {

  private static final Logger LOG = Logger.getInstance("#com.intellij.ui.debugger.extensions.DisposerDebugger");


  private JBTabsImpl myTreeTabs;

  public DisposerDebugger() {
    myTreeTabs = new JBTabsImpl(null, null, this);

    final Splitter splitter = new Splitter(true);

    final JBTabsImpl bottom = new JBTabsImpl(null, null, this);
    final AllocationPanel allocations = new AllocationPanel(myTreeTabs);
    bottom.addTab(new TabInfo(allocations).setText("Allocation"))
      .setActions(allocations.getActions(), ActionPlaces.UNKNOWN);


    splitter.setFirstComponent(myTreeTabs);
    splitter.setSecondComponent(bottom);

    setLayout(new BorderLayout());
    add(splitter, BorderLayout.CENTER);

    addTree(new DisposerTree(this), "All");
  }

  private void addTree(DisposerTree tree, String name) {
    myTreeTabs.addTab(new TabInfo(tree).setText(name).setObject(tree));
  }

  private static class AllocationPanel extends JPanel implements TreeSelectionListener {

    private JEditorPane myAllocation;
    private DefaultActionGroup myActions;

    private JBTabs myTreeTabs;

    private AllocationPanel(JBTabs treeTabs) {
      myTreeTabs = treeTabs;

      myAllocation = new JEditorPane();
      final DefaultCaret caret = new DefaultCaret();
      myAllocation.setCaret(caret);
      caret.setUpdatePolicy(DefaultCaret.NEVER_UPDATE);
      myAllocation.setEditable(false);

      setLayout(new BorderLayout());
      add(new JScrollPane(myAllocation), BorderLayout.CENTER);


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

  private static class DisposerTree extends JPanel implements Disposable, ObjectTreeListener, Runnable  {

    private AbstractTreeBuilder myTreeBuilder;
    private Tree myTree;

    private Alarm myUpdateAlarm = new Alarm();

    private DisposerTree(Disposable parent) {
      Disposer.register(parent, this);

      final DisposerStructure structure = new DisposerStructure();
      final DefaultTreeModel model = new DefaultTreeModel(new DefaultMutableTreeNode());
      final Tree tree = new Tree(model);
      tree.setRootVisible(false);
      tree.setShowsRootHandles(true);
      tree.setCellRenderer(new NodeRenderer());
      tree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
      myTreeBuilder = new AbstractTreeBuilder(tree, model, structure, AlphaComparator.INSTANCE) {
        @Override
        protected Object getTreeStructureElement(NodeDescriptor nodeDescriptor) {
          return nodeDescriptor;
        }

        @Override
        protected boolean isAutoExpandNode(NodeDescriptor nodeDescriptor) {
          return structure.getRootElement() == nodeDescriptor;
        }
      };
      Disposer.register(this, myTreeBuilder);
      myTreeBuilder.updateFromRoot();
      myTree = tree;

      setLayout(new BorderLayout());
      add(new JScrollPane(myTree), BorderLayout.CENTER);

      Disposer.getTree().addListener(this);
    }

    public void objectRegistered(Object node) {
      queueUpdate();
    }

    public void objectExecuted(Object node) {
      queueUpdate();
    }

    public void run() {
      if (!myTreeBuilder.isDisposed()) {
        myTreeBuilder.updateFromRoot();
      }
    }

    private void queueUpdate() {
      //noinspection SSBasedInspection
      SwingUtilities.invokeLater(new Runnable() {
        public void run() {
          myUpdateAlarm.cancelAllRequests();
          myUpdateAlarm.addRequest(this, 200);
        }
      });
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
  }

  public JComponent getComponent() {
    return this;
  }

  public String getName() {
    return "Disposer";
  }

  public void dispose() {
  }


  private static class DisposerStructure extends AbstractTreeStructureBase {
    private DisposerNode myRoot;

    private DisposerStructure() {
      super(null);
      myRoot = new DisposerNode(null);
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
    private DisposerNode(ObjectNode value) {
      super(null, value);
    }

    @NotNull
    public Collection<? extends AbstractTreeNode> getChildren() {
      final ObjectNode value = getValue();
      if (value != null) {
        final Collection subnodes = value.getChildren();
        final ArrayList<DisposerNode> children = new ArrayList<DisposerNode>(subnodes.size());
        for (Iterator iterator = subnodes.iterator(); iterator.hasNext();) {
          children.add(new DisposerNode((ObjectNode)iterator.next()));
        }
        return children;
      }
      else {
        final ObjectTree<Disposable> tree = Disposer.getTree();
        final THashSet<Disposable> root = tree.getRootObjects();
        ArrayList<DisposerNode> children = new ArrayList<DisposerNode>(root.size());
        for (Disposable each : root) {
          children.add(new DisposerNode(tree.getNode(each)));
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
        presentation.setLocationString(classString);
      }
    }
  }

}