/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.ui.popup.list;

import com.intellij.openapi.ui.popup.ListItemDescriptor;
import com.intellij.ui.components.panels.OpaquePanel;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;

public class GroupedItemsListRenderer implements ListCellRenderer {

  private final static Border ourSelectedBorder = new DottedBorder(GroupedItemsListRenderer.SELECTED_FRAME_FOREGROUND);
  private final static Border ourBorder = BorderFactory.createEmptyBorder(1, 1, 1, 1);
  private ListSeparatorComponent mySeparatorComponent = new ListSeparatorComponent();

  protected ListItemDescriptor myDescriptor;

  protected ErrorLabel myTextLabel;
  protected JLabel myNextStepLabel;

  private JPanel myComponent;
  private JPanel myRendererComponent;

  private static final Color POPUP_SEPARATOR_FOREGROUND = Color.gray;
  private static final Color SELECTED_FRAME_FOREGROUND = Color.black;


  public GroupedItemsListRenderer(ListItemDescriptor aPopup) {
    myDescriptor = aPopup;

    myRendererComponent = new OpaquePanel(new BorderLayout(), Color.white);
    myComponent = createItemComponent();

    myRendererComponent.add(mySeparatorComponent, BorderLayout.NORTH);
    myRendererComponent.add(myComponent, BorderLayout.CENTER);
  }

  public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
    prepareItemComponent(list, value, isSelected);

    mySeparatorComponent.setVisible(myDescriptor.hasSeparatorAboveOf(value));
    mySeparatorComponent.setCaption(myDescriptor.getCaptionAboveOf(value));

    if (isSelected) {
      myComponent.setBorder(ourSelectedBorder);
      setSelected(list, myComponent);
    }
    else {
      myComponent.setBorder(ourBorder);
      setDeselected(list, myComponent);
    }

    return myRendererComponent;
  }

  protected Border getDefaultItemComponentBorder() {
    return ourBorder;
  }

  protected JPanel createItemComponent() {
    JPanel result = new OpaquePanel(new BorderLayout(4, 4), Color.white);

    myTextLabel = new ErrorLabel();
    myTextLabel.setOpaque(true);
    myTextLabel.setBorder(BorderFactory.createEmptyBorder(1, 1, 1, 1));

    myNextStepLabel = new JLabel();
    myNextStepLabel.setOpaque(true);

    result.add(myTextLabel, BorderLayout.CENTER);
    result.add(myNextStepLabel, BorderLayout.EAST);

    result.setBorder(getDefaultItemComponentBorder());

    return result;
  }

  protected void setSelected(JList aList, JComponent aComponent) {
    aComponent.setBackground(aList.getSelectionBackground());
    aComponent.setForeground(aList.getSelectionForeground());

  }

  protected void setDeselected(JList aList, JComponent aComponent) {
    aComponent.setBackground(aList.getBackground());
    aComponent.setForeground(aList.getForeground());
  }

  protected void prepareItemComponent(JList list, Object value, boolean isSelected) {
    String text = myDescriptor.getTextFor(value);
    myTextLabel.setText(text);
    myTextLabel.setToolTipText(myDescriptor.getTooltipFor(value));

    myTextLabel.setIcon(myDescriptor.getIconFor(value));
    myTextLabel.setDisabledIcon(myDescriptor.getIconFor(value));

    if (isSelected) {
      setSelected(list, myTextLabel);
      setSelected(list, myNextStepLabel);
    }
    else {
      setDeselected(list, myTextLabel);
      setDeselected(list, myNextStepLabel);
    }
  }

  protected int getCaptionPadding(final int preferredWidth, int width) {
    return (width - preferredWidth) / 2;
  }

  class ListSeparatorComponent extends JComponent {

    private static final int VGAP = 3;
    private static final int HGAP = 3;

    private String myCaption = "";

    ListSeparatorComponent() {
      setBorder(BorderFactory.createEmptyBorder(VGAP, 0, VGAP, 0));
      @NonNls final String labelFont = "Label.font";
      setFont(UIManager.getFont(labelFont));
      setFont(getFont().deriveFont(Font.BOLD));
    }

    public Dimension getPreferredSize() {

      if (hasCaption()) {
        FontMetrics fm = getFontMetrics(getFont());
        int preferredHeight = fm.getHeight();
        int preferredWidth = getPreferredWidth(fm);

        return new Dimension(preferredWidth, preferredHeight + VGAP * 2);
      }

      return new Dimension(0, VGAP * 2 + 1);
    }

    private int getPreferredWidth(FontMetrics fm) {
      return fm.stringWidth(myCaption) + 2 * HGAP;
    }

    private boolean hasCaption() {
      return myCaption != null && !"".equals(myCaption.trim());
    }

    public Dimension getMinimumSize() {
      return getPreferredSize();
    }

    protected void paintComponent(Graphics g) {
      g.setColor(POPUP_SEPARATOR_FOREGROUND);

      if (hasCaption()) {
        FontMetrics fm = getFontMetrics(getFont());
        int baseline = VGAP + fm.getAscent();

        final int preferredWidth = getPreferredSize().width;
        final int padding = getCaptionPadding(preferredWidth, getWidth());
        final int lineY = VGAP + fm.getHeight() / 2;

        g.drawLine(0, lineY, padding, lineY);
        g.drawString(myCaption, padding + HGAP, baseline);
        g.drawLine(padding + preferredWidth, lineY, getWidth() - 1, lineY);
      }
      else {
        g.drawLine(0, VGAP, getWidth() - 1, VGAP);
      }
    }

    public void setCaption(String captionAboveOf) {
      myCaption = captionAboveOf;
    }

  }

}
