// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui;

import com.intellij.ui.components.panels.NonOpaquePanel;
import com.intellij.ui.components.panels.OpaquePanel;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.accessibility.AccessibleContextUtil;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.border.EmptyBorder;
import javax.swing.tree.TreeCellRenderer;
import java.awt.*;

import static com.intellij.ui.RelativeFont.BOLD;

public abstract class GroupedElementsRenderer {
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

    myRendererComponent.setPreferredWidth(preferredForcedWidth);

    return myRendererComponent;
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

  protected void setSeparatorFont(Font font) {
    mySeparatorComponent.setFont(BOLD.derive(font));
  }

  protected abstract Color getSelectionBackground();

  protected abstract Color getSelectionForeground();

  protected abstract Color getBackground();

  protected abstract Color getForeground();

  protected Border getDefaultItemComponentBorder() {
    return getBorder();
  }

  private static Border getBorder() {
    return new EmptyBorder(UIUtil.getListCellPadding());
  }

  public abstract static class List extends GroupedElementsRenderer {
    @Override
    protected void layout() {
      myRendererComponent.add(mySeparatorComponent, BorderLayout.NORTH);

      JComponent centerComponent = new NonOpaquePanel(myComponent) {
        @Override
        public Dimension getPreferredSize() {
          return UIUtil.updateListRowHeight(super.getPreferredSize());
        }
      };

      myRendererComponent.add(centerComponent, BorderLayout.CENTER);
    }

    @Override
    protected final Color getSelectionBackground() {
      return UIUtil.getListSelectionBackground(true);
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
      return UIUtil.getTreeBackground();
    }

    @Override
    protected Color getForeground() {
      return UIUtil.getTreeForeground();
    }
  }

  protected class MyComponent extends OpaquePanel {

    private int myPrefWidth = -1;

    public MyComponent() {
      super(new BorderLayout(), GroupedElementsRenderer.this.getBackground());
    }

    public void setPreferredWidth(final int minWidth) {
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
