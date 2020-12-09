// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.errorTreeView;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.changes.issueLinks.ClickableTreeCellRenderer;
import com.intellij.openapi.vcs.changes.issueLinks.TreeNodePartListener;
import com.intellij.ui.CustomizeColoredTreeCellRenderer;
import com.intellij.ui.MultilineTreeCellRenderer;
import com.intellij.ui.SimpleColoredComponent;
import com.intellij.ui.render.RenderingUtil;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.accessibility.AbstractAccessibleContextDelegate;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.accessibility.Accessible;
import javax.accessibility.AccessibleContext;
import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreeCellRenderer;
import java.awt.*;

public final class NewErrorTreeRenderer extends MultilineTreeCellRenderer {
  private final MyWrapperRenderer myWrapperRenderer;
  private final CallingBackColoredTreeCellRenderer myColoredTreeCellRenderer;
  private final MyNotSelectedColoredTreeCellRenderer myRightCellRenderer;

  private NewErrorTreeRenderer() {
    myColoredTreeCellRenderer = new CallingBackColoredTreeCellRenderer();
    myRightCellRenderer = new MyNotSelectedColoredTreeCellRenderer();
    myWrapperRenderer = new MyWrapperRenderer(myColoredTreeCellRenderer, myRightCellRenderer);
  }

  public static JScrollPane install(JTree tree) {
    final NewErrorTreeRenderer renderer = new NewErrorTreeRenderer();
    //new TreeLinkMouseListener(renderer.myColoredTreeCellRenderer).install(tree);
    new TreeNodePartListener(renderer.myRightCellRenderer).installOn(tree);
    return MultilineTreeCellRenderer.installRenderer(tree, renderer);
  }

  @Override
  public Component getTreeCellRendererComponent(JTree tree,
                                                Object value,
                                                boolean selected,
                                                boolean expanded,
                                                boolean leaf,
                                                int row,
                                                boolean hasFocus) {
    final ErrorTreeElement element = getElement(value);
    if (element != null) {
      final CustomizeColoredTreeCellRenderer leftSelfRenderer = element.getLeftSelfRenderer();
      final CustomizeColoredTreeCellRenderer rightSelfRenderer = element.getRightSelfRenderer();
      if (leftSelfRenderer != null || rightSelfRenderer != null) {
        myColoredTreeCellRenderer.setCurrentCallback(leftSelfRenderer);
        myRightCellRenderer.setCurrentCallback(rightSelfRenderer);
        return myWrapperRenderer.getTreeCellRendererComponent(tree, value, selected, expanded, leaf, row, hasFocus);
      }
    }
    return super.getTreeCellRendererComponent(tree, value, selected, expanded, leaf, row, hasFocus);
  }

  private static class MyNotSelectedColoredTreeCellRenderer extends SimpleColoredComponent implements ClickableTreeCellRenderer {
    private CustomizeColoredTreeCellRenderer myCurrentCallback;

    @Override
    public Component getTreeCellRendererComponent(JTree tree,
                                                  Object value,
                                                  boolean selected,
                                                  boolean expanded,
                                                  boolean leaf,
                                                  int row,
                                                  boolean hasFocus) {
      if (myCurrentCallback instanceof CustomizeColoredTreeCellRendererReplacement) {
        return ((CustomizeColoredTreeCellRendererReplacement)myCurrentCallback)
          .getTreeCellRendererComponent(tree, value, selected, expanded, leaf, row, hasFocus);
      }

      clear();
      setBackground(UIUtil.getBgFillColor(tree));

      if (myCurrentCallback != null) {
        myCurrentCallback.customizeCellRenderer(this, tree, value, selected, expanded, leaf, row, hasFocus);
      }

      if (getFont() == null) {
        setFont(tree.getFont());
      }
      return this;
    }

    @Override
    @Nullable
    public Object getTag() {
      return myCurrentCallback == null? null : myCurrentCallback.getTag();
    }

    public void setCurrentCallback(final CustomizeColoredTreeCellRenderer currentCallback) {
      myCurrentCallback = currentCallback;
    }
  }

  private static class MyWrapperRenderer implements TreeCellRenderer {
    private final TreeCellRenderer myLeft;
    private final TreeCellRenderer myRight;
    private final MyPanel myPanel;

    public TreeCellRenderer getLeft() {
      return myLeft;
    }

    public TreeCellRenderer getRight() {
      return myRight;
    }

    MyWrapperRenderer(final TreeCellRenderer left, final TreeCellRenderer right) {
      myLeft = left;
      myRight = right;

      myPanel = new MyPanel(new BorderLayout());
    }

    @Override
    public Component getTreeCellRendererComponent(JTree tree,
                                                  Object value,
                                                  boolean selected,
                                                  boolean expanded,
                                                  boolean leaf,
                                                  int row,
                                                  boolean hasFocus) {
      myPanel.removeAll();
      myPanel.setBackground(RenderingUtil.getBackground(tree));
      myPanel.add(myLeft.getTreeCellRendererComponent(tree, value, selected, expanded, leaf, row, hasFocus), BorderLayout.WEST);
      myPanel.add(myRight.getTreeCellRendererComponent(tree, value, selected, expanded, leaf, row, hasFocus), BorderLayout.EAST);
      return myPanel;
    }

    /**
     * In general, a node in a {@link JTree} has 2 lists of children: the list
     * of sub-nodes in the tree, and the list of sub-components of the
     * component used to render the node.
     *
     * However, the accessibility API only allows exposing the former list.
     * That is not a problem when the node to render is a simple {@link JLabel}
     * for example. However, when the node is rendered as a complex component
     * such as a {@link JPanel} with sub-components, screen readers only have
     * access to the {@link JPanel}, without the ability to access to the
     * sub-components.
     *
     * This is exactly what is happening in with our {@link MyPanel} class:
     * each node is rendered as a {@link JPanel} with 2 children:
     * a {@link #myLeft} and {@link #myRight} side.
     *
     * There is no general fix for this issue, as we are running into the
     * limitation of the accessibility API explained above, so we implement
     * an arbitrary best-effort approach, which works with most usages:
     * The accessibility of the panel is the accessibility of the right
     * side component, except for the name, which comes from the left side.
     *
     * This works well for the error messages in the context of
     * {@link NewErrorTreeViewPanel}, as the left side is a simple label
     * ("Error") and the right side is a {@link JEditorPane} containing the
     * error message.
     */
    private class MyPanel extends JPanel implements Accessible {
      private AccessibleContext myDefaultAccessibleContext;

      MyPanel(LayoutManager layout) {
        super(layout);
      }

      @Override
      public AccessibleContext getAccessibleContext() {
        if (accessibleContext == null) {
          accessibleContext = new AccessibleMyPanel();
        }
        return accessibleContext;
      }

      private AccessibleContext getDefaultAccessibleContext() {
        if (myDefaultAccessibleContext == null) {
          myDefaultAccessibleContext = super.getAccessibleContext();
        }
        return myDefaultAccessibleContext;
      }

      protected class AccessibleMyPanel extends AbstractAccessibleContextDelegate {
        @NotNull
        @Override
        protected AccessibleContext getDelegate() {
          // Most of the accessibility properties come from the right component
          if (myPanel.getComponentCount() >= 2) {
            Component c = myPanel.getComponent(1);
            if (c instanceof Accessible) {
              return c.getAccessibleContext();
            }
          }
          // Fallback to JPanel if our right component is not accessible
          return getDefaultAccessibleContext();
        }

        @Override
        public String getAccessibleName() {
          // Concatenate the name of all accessible child components
          String name = StringUtil.join(getComponents(), c -> {
            if (c instanceof Accessible) {
              return c.getAccessibleContext().getAccessibleName();
            }
            return null;
          }, " ");
          if (StringUtil.isEmpty(name)) {
            // Fallback to JPanel if we have no children
            name = getDefaultAccessibleContext().getAccessibleName();
          }
          return name;
        }
      }
    }
  }

  @NotNull
  public static @Nls String calcPrefix(@Nullable ErrorTreeElement element) {
    if(element instanceof SimpleMessageElement || element instanceof NavigatableMessageElement) {
      String prefix = element.getPresentableText();

      if (element instanceof NavigatableMessageElement) {
        String rendPrefix = ((NavigatableMessageElement)element).getRendererTextPrefix();
        if (!StringUtil.isEmpty(rendPrefix)) prefix += rendPrefix + " ";
      }

      return prefix;
    }
    return "";
  }

  @Override
  protected void initComponent(JTree tree, Object value, boolean selected, boolean expanded, boolean leaf, int row, boolean hasFocus) {
    final ErrorTreeElement element = getElement(value);
    if(element instanceof GroupingElement && ((GroupingElement)element).isRenderWithBoldFont()) {
      setFont(getFont().deriveFont(Font.BOLD));
    }

    String prefix = calcPrefix(element);
    if (element != null) {
      String[] text = element.getText();
      if (text == null) {
        text = ArrayUtilRt.EMPTY_STRING_ARRAY;
      }
      if(text.length > 0 && text[0] == null) {
        text[0] = "";
      }
      setText(text, prefix);
      setIcon(element.getIcon());
    }
  }

  private static ErrorTreeElement getElement(Object value) {
    if (!(value instanceof DefaultMutableTreeNode)) {
      return null;
    }
    final Object userObject = ((DefaultMutableTreeNode)value).getUserObject();
    if (!(userObject instanceof ErrorTreeNodeDescriptor)) {
      return null;
    }
    return ((ErrorTreeNodeDescriptor)userObject).getElement();
  }
}

