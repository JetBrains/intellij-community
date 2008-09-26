/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.ui.popup.list;

import com.intellij.openapi.ui.popup.ListItemDescriptor;
import com.intellij.ui.ErrorLabel;
import com.intellij.ui.GroupedElementsRenderer;
import com.intellij.ui.components.panels.OpaquePanel;

import javax.swing.*;
import java.awt.*;

public class GroupedItemsListRenderer extends GroupedElementsRenderer.List implements ListCellRenderer {


  protected ListItemDescriptor myDescriptor;

  protected JLabel myNextStepLabel;


  public GroupedItemsListRenderer(ListItemDescriptor aPopup) {
    myDescriptor = aPopup;
  }

  public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
    final JComponent result =
        configureComponent(myDescriptor.getTextFor(value), myDescriptor.getTooltipFor(value), myDescriptor.getIconFor(value),
                           myDescriptor.getIconFor(value), isSelected, myDescriptor.hasSeparatorAboveOf(value),
                           myDescriptor.getCaptionAboveOf(value), -1);

    customizeComponent(list, value, isSelected);

    return result;
  }


  protected JComponent createItemComponent() {
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

  protected void customizeComponent(JList list, Object value, boolean isSelected) {
  }


}
