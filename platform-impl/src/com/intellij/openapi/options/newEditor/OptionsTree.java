package com.intellij.openapi.options.newEditor;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurableGroup;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.ui.ErrorLabel;
import com.intellij.ui.GroupedElementsRenderer;
import com.intellij.ui.LoadingNode;
import com.intellij.ui.TreeUIHelper;
import com.intellij.ui.treeStructure.*;
import com.intellij.ui.treeStructure.filtered.FilteringTreeBuilder;
import com.intellij.ui.treeStructure.filtered.FilteringTreeStructure;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.update.MergingUpdateQueue;
import com.intellij.util.ui.update.Update;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreeCellRenderer;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
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

    myTree = new SimpleTree() {
      @Override
      protected void configureUiHelper(final TreeUIHelper helper) {
        helper.installToolTipHandler(this);
      }

      @Override
      public boolean getScrollableTracksViewportWidth() {
        return true;
      }
    };

    myTree.setRowHeight(-1);
    myTree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
    myTree.setCellRenderer(new Renderer());
    myTree.setRootVisible(false);
    myTree.setShowsRootHandles(false);
    myBuilder = new FilteringTreeBuilder(myProject, myTree, myContext.getFilter(), structure, new WeightBasedComparator(false)) {
      @Override
      protected boolean isSelectable(final Object nodeObject) {
        return nodeObject instanceof EditorNode;
      }
    };
    myBuilder.setFilteringMerge(300);
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
    final JScrollBar sb = new JScrollBar(JScrollBar.HORIZONTAL) {
      @Override
      public void setVisible(final boolean aFlag) {
        super.setVisible(aFlag);
      }
    };
    scrolls.setHorizontalScrollBar(sb);

    myTree.setBorder(new EmptyBorder(2, 2, 2, 2));
    add(scrolls, BorderLayout.CENTER);

    mySelection = new MergingUpdateQueue("OptionsTree", 150, false, this, this, this).setRestartTimerOnAdd(true);
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
    myTree.addKeyListener(new KeyListener() {
      public void keyTyped(final KeyEvent e) {
        _onTreeKeyEvent(e);
      }

      public void keyPressed(final KeyEvent e) {
        _onTreeKeyEvent(e);
      }

      public void keyReleased(final KeyEvent e) {
        _onTreeKeyEvent(e);
      }
    });
  }

  protected void _onTreeKeyEvent(KeyEvent e) {
    final KeyStroke stroke = KeyStroke.getKeyStrokeForEvent(e);

    final Object action = myTree.getInputMap().get(stroke);
    if (action == null) {
      onTreeKeyEvent(e);
    }
  }

  protected void onTreeKeyEvent(KeyEvent e) {

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
    final Update update = new Update(this) {
      public void run() {
        if (configurable == null) {
          myTree.getSelectionModel().clearSelection();
          myContext.fireSelected(null, OptionsTree.this);
        }
        else {
          final EditorNode editorNode = myConfigurable2Node.get(configurable);
          myBuilder.select(myBuilder.getVisibleNodeFor(editorNode), new Runnable() {
            public void run() {
              myContext.fireSelected(configurable, OptionsTree.this);
            }
          });
        }
      }
    };
    mySelection.queue(update);
  }

  void revalidateTree() {
    myTree.invalidate();
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

  @Nullable
  public Configurable getParentFor(final Configurable configurable) {
    final List<Configurable> path = getPathToRoot(configurable);
    if (path.size() > 1) {
      return path.get(1);
    } else {
      return null;
    }
  }

  class Renderer extends GroupedElementsRenderer.Tree implements TreeCellRenderer {

    @Override
    protected void layout() {
      myRendererComponent.add(mySeparatorComponent, BorderLayout.NORTH);
      myRendererComponent.add(myComponent, BorderLayout.CENTER);
    }

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
        final TreePath path = tree.getPathForRow(row);
        final boolean toStretch = tree.isVisible() && path != null;

        if (toStretch) {
          final Rectangle visibleRect = tree.getVisibleRect();

          int nestingLevel = tree.isRootVisible() ? path.getPathCount() - 1 : path.getPathCount() - 2;

          final int left = UIManager.getInt("Tree.leftChildIndent");
          final int right = UIManager.getInt("Tree.rightChildIndent");

          final Insets treeInsets = tree.getInsets();

          int indent = (left + right) * nestingLevel + (treeInsets != null ? treeInsets.left + treeInsets.right : 0);

          forcedWidth = visibleRect.width > 0 ? visibleRect.width - indent: forcedWidth;
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

      final Font font = myTextLabel.getFont();
      myTextLabel.setFont(font.deriveFont(myContext.isHoldingFilter() ? Font.BOLD : Font.PLAIN));

      myTextLabel.setForeground(selected ? UIUtil.getTreeSelectionForeground() : fg);

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

  public void processTextEvent(KeyEvent e) {
    myTree.processKeyEvent(e);
  }
}