/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.application.options.codeStyle.arrangement.node;

import com.intellij.application.options.codeStyle.arrangement.ArrangementNodeDisplayManager;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.util.Consumer;
import com.intellij.util.ui.GridBag;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;

/**
 * @author Denis Zhdanov
 * @since 9/27/12 3:10 PM
 */
public class ArrangementCheckBoxNode<T>/* extends ArrangementTreeNode
  implements ArrangementRepresentationAwareNode, ArrangementEditableNode*/
{
  @NotNull private final JPanel     myRenderer = new JPanel(new GridBagLayout());
  @NotNull private final JBCheckBox myCheckBox = new JBCheckBox();

  @NotNull private final T myData;

  @Nullable private Consumer<ArrangementCheckBoxNode<T>> myListener;

  public ArrangementCheckBoxNode(@NotNull ArrangementNodeDisplayManager displayManager, @NotNull T data) {
    //super(null);
    myCheckBox.setText(displayManager.getDisplayValue(data));
    myData = data;
    myCheckBox.setBackground(UIUtil.getTreeBackground());
    myRenderer.add(myCheckBox, new GridBag().anchor(GridBagConstraints.WEST));
    myCheckBox.addItemListener(new ItemListener() {
      @Override
      public void itemStateChanged(ItemEvent e) {
        if (myListener != null) {
          myListener.consume(ArrangementCheckBoxNode.this);
        }
      }
    });
  }

  @NotNull
  //@Override
  public JComponent getRenderer() {
    return myRenderer;
  }

  @NotNull
  //@Override
  public JComponent getEditor() {
    return myRenderer;
  }

  public boolean isSelected() {
    return myCheckBox.isSelected();
  }

  public void setSelected(boolean selected) {
    myCheckBox.setSelected(selected);
  }

  @NotNull
  public T getValue() {
    return myData;
  }

  public void setChangeCallback(@Nullable Consumer<ArrangementCheckBoxNode<T>> listener) {
    myListener = listener;
  }

  @Override
  public String toString() {
    return myData.toString();
  }
}
