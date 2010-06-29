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
package com.intellij.ide.errorTreeView;

import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.vcs.changes.issueLinks.ClickableTreeCellRenderer;
import com.intellij.openapi.vcs.changes.issueLinks.TreeNodePartListener;
import com.intellij.ui.CustomizeColoredTreeCellRenderer;
import com.intellij.ui.MultilineTreeCellRenderer;
import com.intellij.ui.SimpleColoredComponent;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreeCellRenderer;
import java.awt.*;

/**
 *
 */
public class NewErrorTreeRenderer extends MultilineTreeCellRenderer {
  private final static Icon ourFileIcon = IconLoader.getIcon("/fileTypes/java.png");
  private final static Icon ourErrorIcon = IconLoader.getIcon("/compiler/error.png");
  private final static Icon ourWarningIcon = IconLoader.getIcon("/compiler/warning.png");
  private final static Icon ourInfoIcon = IconLoader.getIcon("/compiler/information.png");

  private final MyWrapperRenderer myWrapperRenderer;
  private final CallingBackColoredTreeCellRenderer myColoredTreeCellRenderer;
  private final MyNotSelectedColoredTreeCellRenderer myRightCellRenderer;

  private NewErrorTreeRenderer() {
    myColoredTreeCellRenderer = new CallingBackColoredTreeCellRenderer();
    myRightCellRenderer = new MyNotSelectedColoredTreeCellRenderer();
    myWrapperRenderer = new MyWrapperRenderer(myColoredTreeCellRenderer, myRightCellRenderer);
  }

  public static JBScrollPane install(JTree tree) {
    final NewErrorTreeRenderer renderer = new NewErrorTreeRenderer();
    //new TreeLinkMouseListener(renderer.myColoredTreeCellRenderer).install(tree);
    new TreeNodePartListener(renderer.myRightCellRenderer).install(tree);
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

    public Component getTreeCellRendererComponent(JTree tree,
                                                  Object value,
                                                  boolean selected,
                                                  boolean expanded,
                                                  boolean leaf,
                                                  int row,
                                                  boolean hasFocus) {
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
    private final JPanel myPanel;

    public TreeCellRenderer getLeft() {
      return myLeft;
    }

    public TreeCellRenderer getRight() {
      return myRight;
    }

    public MyWrapperRenderer(final TreeCellRenderer left, final TreeCellRenderer right) {
      myLeft = left;
      myRight = right;

      myPanel = new JPanel(new BorderLayout());
    }

    public Component getTreeCellRendererComponent(JTree tree,
                                                  Object value,
                                                  boolean selected,
                                                  boolean expanded,
                                                  boolean leaf,
                                                  int row,
                                                  boolean hasFocus) {
      myPanel.setBackground(tree.getBackground());
      myPanel.add(myLeft.getTreeCellRendererComponent(tree, value, selected, expanded, leaf, row, hasFocus), BorderLayout.WEST);
      myPanel.add(myRight.getTreeCellRendererComponent(tree, value, selected, expanded, leaf, row, hasFocus), BorderLayout.EAST);
      return myPanel;
    }
  }

  protected void initComponent(JTree tree, Object value, boolean selected, boolean expanded, boolean leaf, int row, boolean hasFocus) {
    final ErrorTreeElement element = getElement(value);
    if(element instanceof GroupingElement) {
      setFont(getFont().deriveFont(Font.BOLD));
    }

    if(element instanceof SimpleMessageElement || element instanceof NavigatableMessageElement) {
      String prefix = element.getKind().getPresentableText();

      if (element instanceof NavigatableMessageElement) {
        prefix += ((NavigatableMessageElement)element).getRendererTextPrefix() + " ";
      }

      setText(element.getText(), prefix);
    }
    else if (element != null){
      String[] text = element.getText();
      if (text == null) {
        text = ArrayUtil.EMPTY_STRING_ARRAY;
      }
      if(text.length > 0 && text[0] == null) {
        text[0] = "";
      }
      setText(text, null);
    }

    Icon icon = null;

    if (element instanceof GroupingElement) {
      final GroupingElement groupingElement = (GroupingElement)element;

      icon = groupingElement.getFile() != null ? groupingElement.getFile().getFileType().getIcon() : ourFileIcon;
    }
    else if (element instanceof SimpleMessageElement || element instanceof NavigatableMessageElement) {
      ErrorTreeElementKind kind = element.getKind();
      if (ErrorTreeElementKind.ERROR.equals(kind)) {
        icon = ourErrorIcon;
      }
      else if (ErrorTreeElementKind.WARNING.equals(kind)) {
        icon = ourWarningIcon;
      }
      else if (ErrorTreeElementKind.INFO.equals(kind)) {
        icon = ourInfoIcon;
      }
    }

    setIcon(icon);
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

