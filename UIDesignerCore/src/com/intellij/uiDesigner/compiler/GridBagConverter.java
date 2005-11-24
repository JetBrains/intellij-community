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
package com.intellij.uiDesigner.compiler;

import com.intellij.uiDesigner.core.GridConstraints;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.Iterator;

/**
 * Created by IntelliJ IDEA.
 * User: yole
 * Date: 24.11.2005
 * Time: 12:56:00
 * To change this template use File | Settings | File Templates.
 * @noinspection ForLoopReplaceableByForEach, unchecked
 */
public class GridBagConverter {
  private ArrayList myComponents = new ArrayList();
  private ArrayList myConstraints = new ArrayList();

  public void addComponent(final JComponent component, final GridConstraints constraints) {
    myComponents.add(component);
    myConstraints.add(constraints);
  }

  public static class Result {
    public JComponent component;
    public GridBagConstraints constraints;
    public Dimension preferredSize;
    public Dimension minimumSize;
    public Dimension maxSize;

    public Result(final JComponent component) {
      this.component = component;
      constraints = new GridBagConstraints();
    }
  }

  public Result[] convert() {
    Result[] results = new Result[myComponents.size()];
    for(int i=0; i<myComponents.size(); i++) {
      results [i] = convert((JComponent) myComponents.get(i), (GridConstraints) myConstraints.get(i));
    }
    return results;
 }

  private Result convert(final JComponent component, final GridConstraints constraints) {
    final Result result = new Result(component);
    result.constraints.gridx = constraints.getColumn();
    result.constraints.gridy = constraints.getRow();
    result.constraints.gridwidth = constraints.getColSpan();
    result.constraints.gridheight = constraints.getRowSpan();
    result.constraints.weightx = getWeight(constraints, true);
    result.constraints.weighty = getWeight(constraints, false);
    switch(constraints.getFill()) {
      case GridConstraints.FILL_HORIZONTAL: result.constraints.fill = GridBagConstraints.HORIZONTAL; break;
      case GridConstraints.FILL_VERTICAL: result.constraints.fill = GridBagConstraints.VERTICAL; break;
      case GridConstraints.FILL_BOTH: result.constraints.fill = GridBagConstraints.BOTH; break;
    }

    Dimension minSize = new Dimension(component == null ? constraints.myMinimumSize : component.getMinimumSize());
    if ((constraints.getHSizePolicy() & GridConstraints.SIZEPOLICY_CAN_SHRINK) == 0) {
      minSize.width = constraints.myPreferredSize.width >= 0 || component == null
                      ? constraints.myPreferredSize.width
                      : component.getPreferredSize().width;
    }
    if ((constraints.getVSizePolicy() & GridConstraints.SIZEPOLICY_CAN_SHRINK) == 0) {
      minSize.height = constraints.myPreferredSize.height >= 0 || component == null
                       ? constraints.myPreferredSize.height
                       : component.getPreferredSize().height;
    }

    if (minSize.width != -1 || minSize.height != -1) {
      result.minimumSize = minSize;
    }

    return result;
  }

  private double getWeight(final GridConstraints constraints, final boolean horizontal) {
    int policy = horizontal ? constraints.getHSizePolicy() : constraints.getVSizePolicy();
    if ((policy & GridConstraints.SIZEPOLICY_WANT_GROW) != 0) {
      return 1.0;
    }
    for (Iterator iterator = myConstraints.iterator(); iterator.hasNext();) {
      GridConstraints otherConstraints = (GridConstraints)iterator.next();
      if (otherConstraints != constraints) {
        boolean sameRow = horizontal
                      ? otherConstraints.getRow() == constraints.getRow()
                      : otherConstraints.getColumn() == constraints.getColumn();
        if (sameRow) {
          int otherPolicy = horizontal ? otherConstraints.getHSizePolicy() : otherConstraints.getVSizePolicy();
          if ((otherPolicy & GridConstraints.SIZEPOLICY_WANT_GROW) != 0) {
            return 0.0;
          }
        }
      }
    }
    return 1.0;
  }
}
