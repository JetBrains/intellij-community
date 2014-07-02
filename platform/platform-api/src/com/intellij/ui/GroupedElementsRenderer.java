/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

import com.intellij.openapi.util.registry.Registry;
import com.intellij.ui.components.panels.OpaquePanel;
import com.intellij.util.ui.GraphicsUtil;
import com.intellij.util.ui.UIUtil;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.border.EmptyBorder;
import javax.swing.tree.TreeCellRenderer;
import java.awt.*;

import static javax.swing.SwingConstants.CENTER;
import static javax.swing.SwingConstants.LEFT;

public abstract class GroupedElementsRenderer {
  public static final Color POPUP_SEPARATOR_FOREGROUND = new JBColor(Color.gray.brighter(), Gray._43);
  public static final Color POPUP_SEPARATOR_TEXT_FOREGROUND = Color.gray;
  public static final Color SELECTED_FRAME_FOREGROUND = Color.black;

  protected SeparatorWithText mySeparatorComponent = new SeparatorWithText() {
    @Override
    protected void paintComponent(Graphics g) {
      if (Registry.is("ide.new.project.settings")) {
        g.setColor(POPUP_SEPARATOR_FOREGROUND);
        Rectangle viewR = new Rectangle(0, getVgap(), getWidth() - 1, getHeight() - getVgap() - 1);
        Rectangle iconR = new Rectangle();
        Rectangle textR = new Rectangle();
        String s = SwingUtilities
          .layoutCompoundLabel(g.getFontMetrics(), getCaption(), null, CENTER,
                               LEFT,
                               CENTER,
                               LEFT,
                               viewR, iconR, textR, 0);
        GraphicsUtil.setupAAPainting(g);
        g.setColor(Gray._255.withAlpha(80));
        g.drawString(s, textR.x + 10, textR.y + 1 + g.getFontMetrics().getAscent());
        g.setColor(new Color(0x5F6D7B));
        g.drawString(s, textR.x + 10, textR.y + g.getFontMetrics().getAscent());
      } else {
        super.paintComponent(g);
      }
    }
  };

  protected JComponent myComponent;
  protected MyComponent myRendererComponent;

  protected JLabel myTextLabel;


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
    if (myTextLabel instanceof EngravedLabel) {
      ((EngravedLabel)myTextLabel).setShadowColor(isSelected ? UIUtil.getTreeSelectionBackground() : null);
    }

    if (isSelected) {
      //myComponent.setBorder(getSelectedBorder());
      setSelected(myComponent);
      setSelected(myTextLabel);
    } else {
      //myComponent.setBorder(getBorder());
      setDeselected(myComponent);
      setDeselected(myTextLabel);
    }

    adjustOpacity(myTextLabel, isSelected);

    myRendererComponent.setPrefereedWidth(preferredForcedWidth);

    return myRendererComponent;
  }

  protected static void adjustOpacity(JComponent component, boolean selected) {
    if (selected) {
      if (UIUtil.isUnderNimbusLookAndFeel()) {
        component.setOpaque(true);
      }
    }
    else {
      if (UIUtil.isUnderGTKLookAndFeel() || UIUtil.isUnderNimbusLookAndFeel()) {
        component.setOpaque(false);
      }
    }
  }

  protected final void setSelected(JComponent aComponent) {
    aComponent.setBackground(getSelectionBackground());
    aComponent.setForeground(getSelectionForeground());
  }


  protected final  void setDeselected(JComponent aComponent) {
    aComponent.setBackground(getBackground());
    aComponent.setForeground(Registry.is("ide.new.project.settings") ? Gray._60 : getForeground());
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
    protected final void layout() {
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
    protected final Color getSelectionBackground() {
      return UIUtil.getTreeSelectionBackground();
    }

    @Override
    protected final Color getSelectionForeground() {
      return UIUtil.getTreeSelectionForeground();
    }

    @Override
    protected final Color getBackground() {
      return UIUtil.getTreeTextBackground();
    }

    @Override
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
