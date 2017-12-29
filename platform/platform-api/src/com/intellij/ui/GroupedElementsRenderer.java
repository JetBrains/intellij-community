// Copyright 2000-2017 JetBrains s.r.o.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
package com.intellij.ui;

import com.intellij.ui.components.panels.OpaquePanel;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.accessibility.AccessibleContextUtil;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.border.EmptyBorder;
import javax.swing.tree.TreeCellRenderer;
import java.awt.*;

public abstract class GroupedElementsRenderer {
  public static final Color POPUP_SEPARATOR_FOREGROUND = new JBColor(Color.gray.brighter(), Gray.x51);
  public static final Color POPUP_SEPARATOR_TEXT_FOREGROUND = Color.gray;
  public static final Color SELECTED_FRAME_FOREGROUND = Color.black;

  protected SeparatorWithText mySeparatorComponent = createSeparator();

  protected abstract JComponent createItemComponent();

  protected JComponent myComponent;
  protected MyComponent myRendererComponent;

  protected ErrorLabel myTextLabel;
  
  public GroupedElementsRenderer() {
    myRendererComponent = new MyComponent();

    myComponent = createItemComponent();

    layout();
  }

  protected abstract void layout();

  protected SeparatorWithText createSeparator() {
    return new SeparatorWithText();
  }

  protected final JComponent configureComponent(String text, String tooltip, Icon icon, Icon disabledIcon, boolean isSelected, boolean hasSeparatorAbove, String separatorTextAbove, int preferredForcedWidth) {
    mySeparatorComponent.setVisible(hasSeparatorAbove);
    mySeparatorComponent.setCaption(separatorTextAbove);
    mySeparatorComponent.setMinimumWidth(preferredForcedWidth);

    myTextLabel.setText(text);
    myRendererComponent.setToolTipText(tooltip);
    AccessibleContextUtil.setName(myRendererComponent, myTextLabel);
    AccessibleContextUtil.setDescription(myRendererComponent, myTextLabel);

    myTextLabel.setIcon(icon);
    myTextLabel.setDisabledIcon(disabledIcon);

    setSelected(myComponent, isSelected);
    setSelected(myTextLabel, isSelected);

    myRendererComponent.setPrefereedWidth(preferredForcedWidth);

    return myRendererComponent;
  }

  /** @deprecated backgrounds are set uniformly via setSelected() / setDeselected() (to be removed in IDEA 16) */
  @SuppressWarnings("UnusedDeclaration")
  protected static void adjustOpacity(JComponent component, boolean selected) {
    if (!selected) {
      if (UIUtil.isUnderGTKLookAndFeel()) {
        component.setOpaque(false);
      }
    }
  }

  protected final void setSelected(JComponent aComponent) {
    setSelected(aComponent, true);
  }

  protected final void setDeselected(JComponent aComponent) {
    setSelected(aComponent, false);
  }

  protected final void setSelected(JComponent aComponent, boolean selected) {
    UIUtil.setBackgroundRecursively(aComponent, selected ? getSelectionBackground() : getBackground());
    aComponent.setForeground(selected ? getSelectionForeground() : getForeground());
  }

  protected abstract Color getSelectionBackground();

  protected abstract Color getSelectionForeground();

  protected abstract Color getBackground();

  protected abstract Color getForeground();

  protected Border getDefaultItemComponentBorder() {
    return getBorder();
  }

  private static Border getSelectedBorder() {
    return UIUtil.isToUseDottedCellBorder() ? new DottedBorder(UIUtil.getListCellPadding(), SELECTED_FRAME_FOREGROUND) : new EmptyBorder(UIUtil.getListCellPadding());
  }

  private static Border getBorder() {
    return new EmptyBorder(UIUtil.getListCellPadding());
  }

  public abstract static class List extends GroupedElementsRenderer {
    @Override
    protected void layout() {
      myRendererComponent.add(mySeparatorComponent, BorderLayout.NORTH);
      myRendererComponent.add(myComponent, BorderLayout.CENTER);
    }

    @Override
    protected final Color getSelectionBackground() {
      return UIUtil.getListSelectionBackground();
    }

    @Override
    protected final Color getSelectionForeground() {
      return UIUtil.getListSelectionForeground();
    }

    @Override
    protected Color getBackground() {
      return UIUtil.getListBackground();
    }

    @Override
    protected Color getForeground() {
      return UIUtil.getListForeground();
    }
  }

  public abstract static class Tree extends GroupedElementsRenderer implements TreeCellRenderer {

    @Override
    protected void layout() {
      myRendererComponent.add(mySeparatorComponent, BorderLayout.NORTH);
      myRendererComponent.add(myComponent, BorderLayout.WEST);
    }

    @Override
    protected Color getSelectionBackground() {
      return UIUtil.getTreeSelectionBackground();
    }

    @Override
    protected Color getSelectionForeground() {
      return UIUtil.getTreeSelectionForeground();
    }

    @Override
    protected Color getBackground() {
      return UIUtil.getTreeTextBackground();
    }

    @Override
    protected Color getForeground() {
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
