package com.intellij.ui;

import javax.swing.*;
import java.awt.*;

/**
 * @author Vladimir Kondratyev
 */
public abstract class ColoredListCellRenderer extends SimpleColoredComponent implements ListCellRenderer{
  private boolean mySelected;

  public ColoredListCellRenderer(){
    setFocusBorderAroundIcon(true);
  }

  public Component getListCellRendererComponent(
    final JList list,
    final Object value,
    final int index,
    final boolean selected,
    final boolean hasFocus
  ){
    clear();

    mySelected=selected;
    if(selected){
      setBackground(list.getSelectionBackground());
    }else{
      setBackground(null);
    }

    setPaintFocusBorder(hasFocus);

    customizeCellRenderer(list,value,index,selected,hasFocus);

    return this;
  }

  /**
   * When the item is selected then we use default tree's selection foreground.
   * It guaranties readability of selected text in any LAF.
   */
  public final void append(final String fragment,final SimpleTextAttributes attributes){
    if(mySelected){
      super.append(
        fragment,
        new SimpleTextAttributes(
          attributes.getStyle(), UIManager.getColor("List.selectionForeground")
        )
      );
    }else{
      super.append(fragment,attributes);
    }
  }

  public Dimension getPreferredSize() {
    // There is a bug in BasicComboPopup. It does not add renderer into CellRendererPane,
    // so font can be null here.

    final Font oldFont = getFont();
    if(oldFont == null){
      setFont(UIManager.getFont("List.font"));
    }
    final Dimension result = super.getPreferredSize();
    if(oldFont == null){
      setFont(null);
    }

    return result;
  }

  protected abstract void customizeCellRenderer(
    JList list,
    Object value,
    int index,
    boolean selected,
    boolean hasFocus
  );
}
