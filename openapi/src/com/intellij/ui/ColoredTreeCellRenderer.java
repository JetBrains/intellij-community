/*
 * Copyright 2000-2005 JetBrains s.r.o.
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
package com.intellij.ui;

import com.intellij.ide.util.treeView.AbstractTreeBuilder;
import com.intellij.util.ui.EmptyIcon;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.tree.TreeCellRenderer;
import java.awt.*;

/**
 * @author Vladimir Kondratyev
 */
public abstract class ColoredTreeCellRenderer extends SimpleColoredComponent implements TreeCellRenderer{

  private static Icon LOADING_NODE_ICON = new EmptyIcon(8, 16);

  /**
   * Defines whether the tree is selected or not
   */
  protected boolean mySelected;
  /**
   * Defines whether the tree has focus or not
   */
  protected boolean myFocused;

  protected JTree myTree;

  public final Component getTreeCellRendererComponent(
    JTree tree,
    Object value,
    boolean selected,
    boolean expanded,
    boolean leaf,
    int row,
    boolean hasFocus
  ){
    myTree = tree;

    clear();

    mySelected = selected;
    myFocused = tree.hasFocus();

    // We paint background if and only if tree path is selected and tree has focus.
    // If path is selected and tree is not focused then we just paint focused border.
    if (UIUtil.isUnderQuaquaLookAndFeel()) {
      if (selected) {
        setBackground(UIUtil.getTreeSelectionBackground());
      }
      else {
        setBackground(null);
      }
    }
    else {
      if(selected){
        setPaintFocusBorder(true);
        if(myFocused){
          setBackground(UIUtil.getTreeSelectionBackground());
        }else{
          setBackground(null);
        }
      }else{
        setBackground(null);
      }
    }

    if (value instanceof AbstractTreeBuilder.LoadingNode) {
      setForeground(Color.gray);
      setIcon(LOADING_NODE_ICON);
    } else {
      setForeground(tree.getForeground());
      setIcon(null);
    }

    customizeCellRenderer(tree,value,selected,expanded,leaf,row,hasFocus);

    if (getFont() == null) {
      setFont(tree.getFont());
    }

    return this;
  }

  /**
   * When the item is selected then we use default tree's selection foreground.
   * It guaranties readability of selected text in any LAF.
   */
  public void append(@NotNull @Nls String fragment, @NotNull SimpleTextAttributes attributes, boolean isMainText){
    if(mySelected && myFocused){
      super.append(
        fragment,
        new SimpleTextAttributes(
          attributes.getStyle(), UIUtil.getTreeSelectionForeground()
        ), isMainText);
    }else{
      super.append(fragment,attributes, isMainText);
    }
  }

  /**
   * This method is invoked only for customization of component.
   * All component attributes are cleared when this method is being invoked.
   */
  public abstract void customizeCellRenderer(
    JTree tree,
    Object value,
    boolean selected,
    boolean expanded,
    boolean leaf,
    int row,
    boolean hasFocus
  );
}
