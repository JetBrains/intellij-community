// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.util.IconLoader;
import com.intellij.util.ui.JBInsets;
import com.intellij.util.ui.JBUI;

import javax.swing.*;
import java.awt.*;

/**
* @author Konstantin Bulenkov
*/
public class RatesPanel extends JPanel {
  public static final int MAX_RATE = 5;

  private static final Icon STAR = AllIcons.Ide.Rating;

  private static final Icon STAR3 = AllIcons.Ide.Rating1;
  private static final Icon STAR4 = AllIcons.Ide.Rating2;
  private static final Icon STAR5 = AllIcons.Ide.Rating3;
  private static final Icon STAR6 = AllIcons.Ide.Rating4;
  private static final Icon[] STARs = new Icon[]{IconLoader.getDisabledIcon(STAR), STAR3, STAR3, STAR4, STAR4, STAR5, STAR5, STAR6, STAR6, STAR};

  private final JLabel[] myLabels = new JLabel[MAX_RATE];

  public RatesPanel() {
    super(new GridBagLayout());
    setOpaque(false);
    GridBagConstraints gc =
      new GridBagConstraints(GridBagConstraints.RELATIVE, 0, 1, 1, 0, 0, GridBagConstraints.NORTHWEST, GridBagConstraints.NONE,
                             JBInsets.emptyInsets(), 0, 0);
    for (int i = 0, myLabelsLength = myLabels.length; i < myLabelsLength; i++) {
      myLabels[i] = new JLabel();
      myLabels[i].setOpaque(false);
      add(myLabels[i], gc);
    }
  }

  public void setRate(String rating) {
    double dblRating = 0d;
    if (rating != null) {
      try {
        dblRating = Double.parseDouble(rating);
      }
      catch (NumberFormatException ignore) { }
    }

    final int intRating = (int)dblRating;

    for (int i = 0; i < intRating; i++) {
      myLabels[i].setIcon(STAR);
    }

    if (intRating < MAX_RATE) {
      myLabels[intRating].setIcon(STARs[((Double)(dblRating * 10)).intValue() % 10]);
      for (int i = 1 + intRating; i < MAX_RATE; i++) {
        myLabels[i].setIcon(IconLoader.getDisabledIcon(STAR));
      }
    }
  }

  @Override
  public Dimension getPreferredSize() {
    return JBUI.size(55, 11);
  }
}
