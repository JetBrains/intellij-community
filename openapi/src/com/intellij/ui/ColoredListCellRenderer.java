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

import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

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
  public final void append(@NotNull final String fragment, @NotNull final SimpleTextAttributes attributes) {
    if(mySelected) {
      super.append(
        fragment,
        new SimpleTextAttributes(
          attributes.getStyle(), UIUtil.getListSelectionForeground()
        )
      );
    }
    else if (attributes.getFgColor() == null) {
      super.append(fragment, new SimpleTextAttributes(attributes.getStyle(), UIUtil.getListForeground()));
    }
    else {
      super.append(fragment, attributes);
    }
  }

  public Dimension getPreferredSize() {
    // There is a bug in BasicComboPopup. It does not add renderer into CellRendererPane,
    // so font can be null here.

    final Font oldFont = getFont();
    if(oldFont == null){
      setFont(UIUtil.getListFont());
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
