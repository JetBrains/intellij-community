/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.codeInsight.lookup.impl;

import com.google.common.collect.ImmutableMap;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.util.ui.GridBag;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.font.TextAttribute;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * @author peter
 */
public class Advertiser {
  private final List<String> myTexts = new CopyOnWriteArrayList<String>();
  private final JPanel myComponent = new JPanel(new GridBagLayout()) {
    @Override
    public Dimension getPreferredSize() {
      if (myTexts.isEmpty()) {
        return new Dimension(-1, 0);
      }

      int maxSize = 0;
      for (String label : myTexts) {
        maxSize = Math.max(maxSize, createLabel(label).getPreferredSize().width);
      }

      Dimension sup = super.getPreferredSize();
      return new Dimension(maxSize + sup.width - myTextPanel.getPreferredSize().width, sup.height);
    }
  };
  private volatile int myCurrentItem = 0;
  private JPanel myTextPanel;
  private JLabel myNextLabel;

  public Advertiser() {
    myTextPanel = new JPanel(new BorderLayout());

    myNextLabel = new JLabel(">>");
    myNextLabel.setFont(adFont().deriveFont(ImmutableMap.<TextAttribute, Object>builder().put(TextAttribute.UNDERLINE, TextAttribute.UNDERLINE_ON).build()));
    myNextLabel.setForeground(Color.blue);
    myNextLabel.addMouseListener(new MouseAdapter() {
      @Override
      public void mouseClicked(MouseEvent e) {
        myCurrentItem++;
        updateAdvertisements();
      }
    });
    myNextLabel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

    GridBag gb = new GridBag();
    myComponent.add(myTextPanel, gb.next());
    myComponent.add(myNextLabel, gb.next());
    myComponent.add(new JPanel(), gb.next().fillCellHorizontally().weightx(1));
  }

  private void updateAdvertisements() {
    myNextLabel.setVisible(myTexts.size() > 1);
    myTextPanel.removeAll();
    if (!myTexts.isEmpty()) {
      String text = myTexts.get(myCurrentItem % myTexts.size());
      myTextPanel.add(createLabel(text));
    }
    myComponent.revalidate();
    myComponent.repaint();
  }

  private static JLabel createLabel(String text) {
    JLabel label = new JLabel(text + "  ");
    label.setFont(adFont());
    return label;
  }

  public void showRandomText() {
    int count = myTexts.size();
    myCurrentItem = count > 0 ? new Random().nextInt(count) : 0;
    updateAdvertisements();
  }

  public void clearAdvertisements() {
    ApplicationManager.getApplication().assertIsDispatchThread();
    myTexts.clear();
    myCurrentItem = 0;
    updateAdvertisements();
  }

  private static Font adFont() {
    Font font = UIUtil.getLabelFont();
    return font.deriveFont((float)(font.getSize() - 2));
  }

  public void addAdvertisement(@NotNull String text) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    myTexts.add(text);
    updateAdvertisements();
  }

  public JComponent getAdComponent() {
    return myComponent;
  }

}
