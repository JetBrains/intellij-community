package com.intellij.openapi.options.newEditor;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurableGroup;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.ui.ErrorLabel;
import com.intellij.ui.GroupedElementsRenderer;
import com.intellij.ui.LoadingNode;
import com.intellij.ui.treeStructure.*;
import com.intellij.ui.treeStructure.filtered.FilteringTreeBuilder;
import com.intellij.ui.treeStructure.filtered.FilteringTreeStructure;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.update.MergingUpdateQueue;
import com.intellij.util.ui.update.Update;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreeCellRenderer;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.util.*;
import java.util.List;

public class OptionsTree extends JPanel implements Disposable, OptionsEditorColleague {
  Project myProject;
  SimpleTree myTree;
  List<ConfigurableGroup> myGroups;
  FilteringTreeBuilder myBuilder;
  Root myRoot;
  OptionsEditorContext myContext;

  Map<Configurable, EditorNode> myConfigurable2Node = new HashMap<Configurable, EditorNode>();

  MergingUpdateQueue mySelection;

  public OptionsTree(Project project, ConfigurableGroup[] groups, OptionsEditorContext context) {
    myProject = project;
    myGroups = Arrays.asList(groups);
    myContext = context;


    myRoot = new Root();
    final SimpleTreeStructure structure = new SimpleTreeStructure() {
      public Object getRootElement() {
        return myRoot;
      }
    };

    myTree = new SimpleTree();
    myTree.setRowHeight(-1);
    myTree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
    myTree.setCellRenderer(new Renderer());
    myTree.setRootVisible(false);
    myTree.setShowsRootHandles(false);
    myBuilder = new FilteringTreeBuilder(myProject, myTree, myContext.getFilter(), structure, new WeightBasedComparator(false));
    Disposer.register(this, myBuilder);

    myBuilder.updateFromRoot();

    setLayout(new BorderLayout());

    myTree.addComponentListener(new ComponentAdapter() {
      @Override
      public void componentResized(final ComponentEvent e) {
        revalidateTree();
      }

      @Override
      public void componentMoved(final ComponentEvent e) {
        revalidateTree();
      }

      @Override
      public void componentShown(final ComponentEvent e) {
        revalidateTree();
      }
    });

    final JScrollPane scrolls = new JScrollPane(myTree);
    add(scrolls, BorderLayout.CENTER);

    mySelection = new MergingUpdateQueue("OptionsTree", 250, false, this, this, this).setRestartTimerOnAdd(true);
    myTree.getSelectionModel().addTreeSelectionListener(new TreeSelectionListener() {
      public void valueChanged(final TreeSelectionEvent e) {
        final TreePath path = e.getNewLeadSelectionPath();
        if (path == null) {
          queueSelection(null);
        } else {
          final Base base = extractNode(path.getLastPathComponent());
          queueSelection(base != null ? base.getConfigurable() : null);
        }
      }
    });
  }

  void select(@Nullable Configurable configurable) {
    queueSelection(configurable);
  }

  public void selectFirst() {
    for (ConfigurableGroup eachGroup : myGroups) {
      final Configurable[] kids = eachGroup.getConfigurables();
      if (kids.length > 0) {
        queueSelection(kids[0]);
        return;
      }
    }
  }

  void queueSelection(final Configurable configurable) {
    mySelection.queue(new Update(this) {
      public void run() {
        if (configurable == null) {
          myTree.getSelectionModel().clearSelection();
          myContext.fireSelected(null, OptionsTree.this);
        } else {
          final EditorNode editorNode = myConfigurable2Node.get(configurable);
          myBuilder.select(myBuilder.getVisibleNodeFor(editorNode), new Runnable() {
            public void run() {
              myContext.fireSelected(configurable, OptionsTree.this);
            }
          });
        }
      }
    });
  }

  void revalidateTree() {
    myTree.setRowHeight(myTree.getRowHeight() == -1 ? -2 : -1);
    myTree.revalidate();
    myTree.repaint();
  }

  public JTree getTree() {
    return myTree;
  }

  public List<Configurable> getPathToRoot(final Configurable configurable) {
    final ArrayList<Configurable> path = new ArrayList<Configurable>();

    EditorNode eachNode = myConfigurable2Node.get(configurable);
    if (eachNode == null) return path;

    while (eachNode != null) {
      path.add(eachNode.getConfigurable());
      final SimpleNode parent = eachNode.getParent();
      if (parent instanceof EditorNode) {
        eachNode = (EditorNode)parent;
      } else {
        break;
      }
    }

    return path;
  }


  class Renderer extends GroupedElementsRenderer.Tree implements TreeCellRenderer {

    public Component getTreeCellRendererComponent(final JTree tree,
                                                  final Object value,
                                                  final boolean selected,
                                                  final boolean expanded,
                                                  final boolean leaf,
                                                  final int row,
                                                  final boolean hasFocus) {


      JComponent result;
      Color fg = UIUtil.getTreeTextForeground();

      final Base base = extractNode(value);
      if (base instanceof EditorNode) {
        final EditorNode editor = (EditorNode)base;
        ConfigurableGroup group = null;
        if (editor.getParent() == myRoot) {
          final DefaultMutableTreeNode prevValue = ((DefaultMutableTreeNode)value).getPreviousSibling();
          if (prevValue == null || prevValue instanceof LoadingNode) {
            group = editor.getGroup();
          } else {
            final Base prevBase = extractNode(prevValue);
            if (prevBase instanceof EditorNode) {
              final EditorNode prevEditor = (EditorNode)prevBase;
              if (prevEditor.getGroup() != editor.getGroup()) {
                group = editor.getGroup();
              }
            }
          }
        }

        int forcedWidth = -1;
        if (group != null && tree.isVisible()) {
          final Rectangle bounds = tree.getVisibleRect();
          forcedWidth = bounds.width > 0 ? bounds.width - 2 : forcedWidth;
        }

        result = configureComponent(base.getText(), base.getText(), null, null, selected, group != null,
                                                group != null ? group.getDisplayName() : null, forcedWidth);


        if (base.isError()) {
          fg = Color.red;
        } else if (base.isModified()) {
          fg = Color.blue;
        }
      } else {
        result = configureComponent(value.toString(), null, null, null, selected, false, null, -1);
      }

      myTextLabel.setForeground(fg);

      return result;
    }

    protected JComponent createItemComponent() {
      myTextLabel = new ErrorLabel();
      myTextLabel.setOpaque(true);
      return myTextLabel;
    }
  }

  @Nullable
  private Base extractNode(Object object) {
    if (object instanceof DefaultMutableTreeNode) {
      final DefaultMutableTreeNode uiNode = (DefaultMutableTreeNode)object;
      final Object o = uiNode.getUserObject();
      if (o instanceof FilteringTreeStructure.Node) {
        return (Base)((FilteringTreeStructure.Node)o).getDelegate();
      }
    }

    return null;
  }

  abstract class Base extends CachingSimpleNode {

    protected Base(final SimpleNode aParent) {
      super(aParent);
    }

    String getText() {
      return null;
    }

    boolean isModified() {
      return false;
    }

    boolean isError() {
      return false;
    }

    Configurable getConfigurable() {
      return null;
    }
  }

  class Root extends Base {

    Root() {
      super(null);
    }

    protected SimpleNode[] buildChildren() {
      ArrayList<SimpleNode> result = new ArrayList<SimpleNode>();
      for (int i = 0; i < myGroups.size(); i++) {
        ConfigurableGroup eachGroup = myGroups.get(i);
        final Configurable[] kids = eachGroup.getConfigurables();
        if (kids.length > 0) {
          for (int j = 0; j < kids.length; j++) {
            Configurable eachKid = kids[j];
            result.add(new EditorNode(this, eachKid, eachGroup));
          }
        }
      }

      return result.toArray(new SimpleNode[result.size()]);
    }
  }

  class EditorNode extends Base {

    Configurable myConfigurable;
    ConfigurableGroup myGroup;

    EditorNode(SimpleNode parent, Configurable configurable, @Nullable ConfigurableGroup group) {
      super(parent);
      myConfigurable = configurable;
      myGroup = group;
      myConfigurable2Node.put(configurable, this);
      addPlainText(configurable.getDisplayName());
    }

    protected SimpleNode[] buildChildren() {
      if (myConfigurable instanceof Configurable.Composite) {
        final Configurable[] kids = ((Configurable.Composite)myConfigurable).getConfigurables();
        final EditorNode[] result = new EditorNode[kids.length];
        for (int i = 0; i < kids.length; i++) {
          result[i] = new EditorNode(this, kids[i], null);
        }
        return result;
      } else {
        return NO_CHILDREN;
      }
    }

    @Override
    Configurable getConfigurable() {
      return myConfigurable;
    }

    @Override
    public int getWeight() {
      if (getParent() == myRoot) {
        return Integer.MAX_VALUE - myGroups.indexOf(myGroup);
      } else {
        return 0;
      }
    }

    public ConfigurableGroup getGroup() {
      return myGroup;
    }

    @Override
    String getText() {
      return myConfigurable.getDisplayName();
    }

    @Override
    boolean isModified() {
      return myContext.getModified().contains(myConfigurable);
    }

    @Override
    boolean isError() {
      return myContext.getErrors().containsKey(myConfigurable);
    }
  }

  public void dispose() {
  }

  public void onSelected(final Configurable configurable, final Configurable oldConfigurable) {
    queueSelection(configurable);
  }

  public void onModifiedAdded(final Configurable colleague) {
    myTree.repaint();
  }

  public void onModifiedRemoved(final Configurable configurable) {
    myTree.repaint();
  }

  public void onErrorsChanged() {
  }
}