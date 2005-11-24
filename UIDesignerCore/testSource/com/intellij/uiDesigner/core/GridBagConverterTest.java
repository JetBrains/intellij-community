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
package com.intellij.uiDesigner.core;

import com.intellij.uiDesigner.compiler.GridBagConverter;
import junit.framework.TestCase;

import javax.swing.*;
import java.awt.*;

/**
 * Created by IntelliJ IDEA.
 * User: yole
 * Date: 23.11.2005
 * Time: 13:47:38
 * To change this template use File | Settings | File Templates.
 */
public class GridBagConverterTest extends TestCase {
  /**
   * button 1
   * <empty>
   * button 2
   */
  public void testLayout2() {
    final GridBagLayout layoutManager = new GridBagLayout();
    final JPanel panel = new JPanel(layoutManager);

    final JButton button1 = new JButton();
    button1.setMinimumSize(new Dimension(9, 7));
    button1.setPreferredSize(new Dimension(50, 10));

    final JButton button2 = new JButton();
    button2.setMinimumSize(new Dimension(15, 6));
    button2.setPreferredSize(new Dimension(50, 10));

    GridBagConverter converter = new GridBagConverter();
    final GridConstraints button1Constraints = new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH,
                                                                   GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK,
                                                                   null, null, null);
    converter.addComponent(button1, button1Constraints);

    final GridConstraints button2Constraints = new GridConstraints(2, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH,
                                                                   GridConstraints.SIZEPOLICY_CAN_GROW | GridConstraints.SIZEPOLICY_CAN_SHRINK,
                                                                   GridConstraints.SIZEPOLICY_FIXED, null, null, null);

    converter.addComponent(button2, button2Constraints);

    applyConversionResults(panel, converter);

    assertEquals(20, panel.getPreferredSize().height);
    assertEquals(50, panel.getPreferredSize().width);

    assertEquals(17, panel.getMinimumSize().height);
    assertEquals(50, panel.getMinimumSize().width);

    panel.setSize(new Dimension(500, 100));
    panel.doLayout();

    assertEquals(50, button1.getHeight());
    assertEquals(50, button2.getHeight());
  }

  public void testLayout3() {
    final JPanel panel = new JPanel(new GridBagLayout());

    final JButton button1 = new JButton();
    button1.setPreferredSize(new Dimension(100,20));
    final JButton button2 = new JButton();
    button2.setPreferredSize(new Dimension(100,100));

    GridBagConverter converter = new GridBagConverter();
    converter.addComponent(button1, new GridConstraints(0,0,1,1,GridConstraints.ANCHOR_CENTER,GridConstraints.FILL_BOTH,
      GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null));

    converter.addComponent(button2, new GridConstraints(1,0,1,1,GridConstraints.ANCHOR_CENTER,GridConstraints.FILL_BOTH,
      GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null));

    applyConversionResults(panel, converter);
    final Dimension preferredSize = panel.getPreferredSize();
    assertEquals(120, preferredSize.height);
  }

  public void testLayout4() {
    final JPanel panel = new JPanel(new GridBagLayout());

    // button 1  button 3
    // button 2  button 3

    final JButton button1 = new JButton();
    button1.setPreferredSize(new Dimension(100,10));
    final JButton button2 = new JButton();
    button2.setPreferredSize(new Dimension(100,10));
    final JButton button3 = new JButton();
    button3.setPreferredSize(new Dimension(100,200));

    GridBagConverter converter = new GridBagConverter();
    converter.addComponent(button1, new GridConstraints(0,0,1,1,GridConstraints.ANCHOR_CENTER,GridConstraints.FILL_BOTH,
      GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_WANT_GROW + GridConstraints.SIZEPOLICY_CAN_SHRINK, null, null, null));

    converter.addComponent(button2, new GridConstraints(1,0,1,1,GridConstraints.ANCHOR_CENTER,GridConstraints.FILL_BOTH,
      GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_WANT_GROW + GridConstraints.SIZEPOLICY_CAN_SHRINK, null, null, null));

    converter.addComponent(button3, new GridConstraints(0,1,2,1,GridConstraints.ANCHOR_CENTER,GridConstraints.FILL_BOTH,
      GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_WANT_GROW + GridConstraints.SIZEPOLICY_CAN_SHRINK, null, null, null));

    applyConversionResults(panel, converter);
    final Dimension preferredSize = panel.getPreferredSize();
    assertEquals(200, preferredSize.height);
  }

  /*
  public void testLayout5_1() {
    final JPanel panel = new JPanel(new GridBagLayout());

    // label textfield(span 2)
    // textfield(span 2)

    final JTextField label = new JTextField();
    label.setPreferredSize(new Dimension(10,30));

    final JTextField field1 = new JTextField();
    field1.setPreferredSize(new Dimension(100,30));

    final JTextField field2 = new JTextField();
    field2.setPreferredSize(new Dimension(100,30));

    GridBagConverter converter = new GridBagConverter();
    converter.addComponent(label, new GridConstraints(0,0,1,1,GridConstraints.ANCHOR_CENTER,GridConstraints.FILL_BOTH,
      GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, new Dimension(0,0), null, null));

    converter.addComponent(field1, new GridConstraints(0,1,1,2,GridConstraints.ANCHOR_CENTER,GridConstraints.FILL_BOTH,
      GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, new Dimension(0,0), null, null));

    converter.addComponent(field2, new GridConstraints(1,0,1,2,GridConstraints.ANCHOR_CENTER,GridConstraints.FILL_BOTH,
      GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, new Dimension(0,0), null, null));

    applyConversionResults(panel, converter);
    final Dimension preferredSize = panel.getPreferredSize();
    assertEquals(110, preferredSize.width);
    assertEquals(60, preferredSize.height);
  }
  */

  private void applyConversionResults(final JPanel panel, final GridBagConverter converter) {
    GridBagConverter.Result[] results = converter.convert();
    for(int i=0; i<results.length; i++)  {
      GridBagConverter.Result result = results [i];
      if (result.minimumSize != null) {
        result.component.setMinimumSize(result.minimumSize);
      }
      panel.add(result.component, result.constraints);
    }
  }
}
