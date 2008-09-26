package com.intellij.openapi.options.newEditor;

import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurableGroup;
import com.intellij.openapi.project.Project;
import com.intellij.ui.ErrorLabel;
import com.intellij.ui.GroupedElementsRenderer;
import com.intellij.ui.LoadingNode;
import com.intellij.ui.speedSearch.ElementFilter;
import com.intellij.ui.treeStructure.*;
import com.intellij.ui.treeStructure.filtered.FilteringTreeBuilder;
import com.intellij.ui.treeStructure.filtered.FilteringTreeStructure;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreeCellRenderer;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.util.ArrayList;
import java.util.Arrays;

public class OptionsTree extends JPanel {
  private Project myProject;
  private SimpleTree myTree;
  private java.util.List<ConfigurableGroup> myGroups;
  private ElementFilter myFilter;
  private FilteringTreeBuilder myBuilder;
  private Root myRoot;

  public OptionsTree(Project project, ConfigurableGroup[] groups, ElementFilter filter) {
    myProject = project;
    myGroups = Arrays.asList(groups);
    myFilter = filter;


    myRoot = new Root();
    final SimpleTreeStructure structure = new SimpleTreeStructure() {
      public Object getRootElement() {
        return myRoot;
      }
    };

    myTree = new SimpleTree();
    myTree.setRowHeight(-1);
    myTree.setCellRenderer(new Renderer());
    myTree.setRootVisible(false);
    myTree.setShowsRootHandles(false);
    myBuilder = new FilteringTreeBuilder(myProject, myTree, myFilter, structure, new WeightBasedComparator());
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
  }

  void revalidateTree() {
    myTree.setRowHeight(myTree.getRowHeight() == -1 ? -2 : -1);
    myTree.revalidate();
    myTree.repaint();
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

      } else {
        result = configureComponent(value.toString(), null, null, null, selected, false, null, -1);
      }

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
  }

}