package com.intellij.ui;

import javax.swing.*;
import javax.swing.tree.TreeCellRenderer;
import java.awt.*;

/**
 * @author Vladimir Kondratyev
 */
public abstract class ColoredTreeCellRenderer extends SimpleColoredComponent implements TreeCellRenderer{
  /**
   * Defines whether the tree is selected or not
   */
  protected boolean mySelected;
  /**
   * Defines whether the tree has focus or not
   */
  protected boolean myFocused;

  public final Component getTreeCellRendererComponent(
    JTree tree,
    Object value,
    boolean selected,
    boolean expanded,
    boolean leaf,
    int row,
    boolean hasFocus
  ){
    clear();

    mySelected = selected;
    myFocused = tree.hasFocus();

    // We paint background if and only if tree path is selected and tree has focus.
    // If path is selected and tree is not focused then we just paint focused border.
    if(selected){
      setPaintFocusBorder(true);
      if(myFocused){
        setBackground(UIManager.getColor("Tree.selectionBackground"));
      }else{
        setBackground(null);
      }
    }else{
      setBackground(null);
    }

    customizeCellRenderer(tree,value,selected,expanded,leaf,row,hasFocus);

    return this;
  }

  /**
   * When the item is selected then we use default tree's selection foreground.
   * It guaranties readability of selected text in any LAF.
   */
  public void append(String fragment,SimpleTextAttributes attributes){
    if(mySelected && myFocused){
      super.append(
        fragment,
        new SimpleTextAttributes(
          attributes.getStyle(), UIManager.getColor("Tree.selectionForeground")
        )
      );
    }else{
      super.append(fragment,attributes);
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
