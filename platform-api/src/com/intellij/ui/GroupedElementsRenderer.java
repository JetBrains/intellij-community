package com.intellij.ui;

import com.intellij.ui.components.panels.OpaquePanel;
import com.intellij.util.ui.UIUtil;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;

public abstract class GroupedElementsRenderer {
  public static final Color POPUP_SEPARATOR_FOREGROUND = Color.gray;
  public static final Color SELECTED_FRAME_FOREGROUND = Color.black;

  private final static Border ourSelectedBorder = new DottedBorder(SELECTED_FRAME_FOREGROUND);
  private final static Border ourBorder = BorderFactory.createEmptyBorder(1, 1, 1, 1);

  private SeparatorWithText mySeparatorComponent = new SeparatorWithText();
  private JComponent myComponent;
  private JPanel myRendererComponent;

  protected ErrorLabel myTextLabel;


  public GroupedElementsRenderer() {
    myRendererComponent = new OpaquePanel(new BorderLayout(), getBackground());

    myComponent = createItemComponent();

    myRendererComponent.add(mySeparatorComponent, BorderLayout.NORTH);
    myRendererComponent.add(myComponent, BorderLayout.WEST);
  }
  
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
      myComponent.setBorder(ourSelectedBorder);
      setSelected(myComponent);
      setSelected(myTextLabel);
    }
    else {
      myComponent.setBorder(ourBorder);
      setDeselected(myComponent);
      setDeselected(myTextLabel);
    }

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
    return ourBorder;
  }

  public static abstract class List extends GroupedElementsRenderer {
    protected Color getSelectionBackground() {
      return UIUtil.getListSelectionBackground();
    }

    protected Color getSelectionForeground() {
      return UIUtil.getListSelectionForeground();
    }

    protected Color getBackground() {
      return UIUtil.getListBackground();
    }

    protected Color getForeground() {
      return UIUtil.getListForeground();
    }
  }

  public static abstract class Tree extends GroupedElementsRenderer {
    protected Color getSelectionBackground() {
      return UIUtil.getTreeSelectionBackground();
    }

    protected Color getSelectionForeground() {
      return UIUtil.getTreeSelectionForeground();
    }

    protected Color getBackground() {
      return UIUtil.getTreeTextBackground();
    }

    protected Color getForeground() {
      return UIUtil.getTreeTextForeground();
    }
  }

}