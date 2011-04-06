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
import com.intellij.util.ui.GridBag;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.font.TextAttribute;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * @author peter
 */
public class Advertiser {
  private final List<JLabel> myTexts = new CopyOnWriteArrayList<JLabel>();
  private final JPanel myComponent = new JPanel(new GridBagLayout()) {
    @Override
    public Dimension getPreferredSize() {
      Dimension sup = super.getPreferredSize();
      int maxSize = 0;
      for (JLabel label : getTexts()) {
        maxSize = Math.max(maxSize, label.getPreferredSize().width);
      }
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
    List<JLabel> texts = getTexts();
    myNextLabel.setVisible(texts.size() > 1);
    myTextPanel.removeAll();
    if (!texts.isEmpty()) {
      myTextPanel.add(texts.get(myCurrentItem % texts.size()));
    }
    myComponent.revalidate();
    myComponent.repaint();
  }

  private synchronized List<JLabel> getTexts() {
    return new ArrayList<JLabel>(myTexts);
  }

  public void showRandomText() {
    myCurrentItem = new Random().nextInt(myTexts.size());
    updateAdvertisements();
  }

  public synchronized void clearAdvertisements() {
    myTexts.clear();
    myCurrentItem = 0;
    updateAdvertisements();
  }

  private static Font adFont() {
    Font font = UIUtil.getLabelFont();
    return font.deriveFont((float)(font.getSize() - 2));
  }

  public synchronized void addAdvertisement(@NotNull String text) {
    JLabel label = new JLabel(text + "  ");
    label.setFont(adFont());
    myTexts.add(label);
    UIUtil.invokeLaterIfNeeded(new Runnable() {
      @Override
      public void run() {
        updateAdvertisements();
      }
    });
  }

  public JComponent getAdComponent() {
    return myComponent;
  }

}
