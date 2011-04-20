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
package com.intellij.ui;

import com.intellij.ui.components.panels.OpaquePanel;
import com.intellij.util.ui.UIUtil;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.border.EmptyBorder;
import java.awt.*;

public abstract class GroupedElementsRenderer {
  public static final Color POPUP_SEPARATOR_FOREGROUND = Color.gray.brighter();
  public static final Color POPUP_SEPARATOR_TEXT_FOREGROUND = Color.gray;
  public static final Color SELECTED_FRAME_FOREGROUND = Color.black;

  protected SeparatorWithText mySeparatorComponent = new SeparatorWithText();
  protected JComponent myComponent;
  protected MyComponent myRendererComponent;

  protected ErrorLabel myTextLabel;


  public GroupedElementsRenderer() {
    myRendererComponent = new MyComponent();

    myComponent = createItemComponent();

    layout();
  }

  protected abstract void layout();

  protected abstract JComponent createItemComponent();

  protected final JComponent configureComponent(String text, String tooltip, Icon icon, Icon disabledIcon, boolean isSelected, boolean hasSeparatorAbove, String separatorTextAbove, int preferredForcedWidth) {
    mySeparatorComponent.setVisible(hasSeparatorAbove);
    mySeparatorComponent.setCaption(separatorTextAbove);
    mySeparatorComponent.setMinimumWidth(preferredForcedWidth);

    myTextLabel.setText(text);
    myTextLabel.setToolTipText(tooltip);

    myTextLabel.setIcon(icon);
    myTextLabel.setDisabledIcon(disabledIcon);

    if (isSelected) {
      myComponent.setBorder(getSelectedBorder());
      setSelected(myComponent);
      setSelected(myTextLabel);
      if (UIUtil.isUnderNimbusLookAndFeel()) {
        myTextLabel.setOpaque(true);
      }
    }
    else {
      myComponent.setBorder(getBorder());
      setDeselected(myComponent);
      setDeselected(myTextLabel);
      if (UIUtil.isUnderGTKLookAndFeel() || UIUtil.isUnderNimbusLookAndFeel()) {
        myTextLabel.setOpaque(false);
      }
    }

    myRendererComponent.setPrefereedWidth(preferredForcedWidth);

    return myRendererComponent;
  }

  protected final void setSelected(JComponent aComponent) {
    aComponent.setBackground(getSelectionBackground());
    aComponent.setForeground(getSelectionForeground());
  }


  protected final  void setDeselected(JComponent aComponent) {
    aComponent.setBackground(getBackground());
    aComponent.setForeground(getForeground());
  }

  protected abstract Color getSelectionBackground();

  protected abstract Color getSelectionForeground();

  protected abstract Color getBackground();

  protected abstract Color getForeground();

  protected Border getDefaultItemComponentBorder() {
    return getBorder();
  }

  private Border getSelectedBorder() {
    return UIUtil.isToUseDottedCellBorder() ? new DottedBorder(UIUtil.getListCellPadding(), SELECTED_FRAME_FOREGROUND) : new EmptyBorder(UIUtil.getListCellPadding());
  }

  private Border getBorder() {
    return new EmptyBorder(UIUtil.getListCellPadding());
  }

  public static abstract class List extends GroupedElementsRenderer {

    protected final void layout() {
      myRendererComponent.add(mySeparatorComponent, BorderLayout.NORTH);
      myRendererComponent.add(myComponent, BorderLayout.CENTER);
    }

    protected final Color getSelectionBackground() {
      return UIUtil.getListSelectionBackground();
    }

    protected final Color getSelectionForeground() {
      return UIUtil.getListSelectionForeground();
    }

    protected final Color getBackground() {
      return UIUtil.getListBackground();
    }

    protected final Color getForeground() {
      return UIUtil.getListForeground();
    }
  }

  public static abstract class Tree extends GroupedElementsRenderer {

    protected void layout() {
      myRendererComponent.add(mySeparatorComponent, BorderLayout.NORTH);
      myRendererComponent.add(myComponent, BorderLayout.WEST);
    }

    protected final Color getSelectionBackground() {
      return UIUtil.getTreeSelectionBackground();
    }

    protected final Color getSelectionForeground() {
      return UIUtil.getTreeSelectionForeground();
    }

    protected final Color getBackground() {
      return UIUtil.getTreeTextBackground();
    }

    protected final Color getForeground() {
      return UIUtil.getTreeTextForeground();
    }
  }

  protected class MyComponent extends OpaquePanel {

    private int myPrefWidth = -1;

    public MyComponent() {
      super(new BorderLayout(), GroupedElementsRenderer.this.getBackground());
    }

    public void setPrefereedWidth(final int minWidth) {
      myPrefWidth = minWidth;
    }

    @Override
    public Dimension getPreferredSize() {
      final Dimension size = super.getPreferredSize();
      size.width = myPrefWidth == -1 ? size.width : myPrefWidth;
      return size;
    }
  }

}