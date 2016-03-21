/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.ide.ui;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.util.ui.ImageUtil;
import com.intellij.util.ui.JBDimension;
import com.intellij.util.ui.JBInsets;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.image.BufferedImage;
import java.awt.image.ImageFilter;

/**
 * @author Sergey.Malenkov
 */
public class ColorBlindnessInternalAction extends DumbAwareAction {
  @Override
  public void actionPerformed(AnActionEvent event) {
    new ColorDialog(event).show();
  }

  private static final class ColorDialog extends DialogWrapper {
    private final ColorView myView = new ColorView();

    private ColorDialog(AnActionEvent event) {
      super(event.getProject());
      init();
      setTitle("ColorBlindness");
    }

    @Override
    protected JComponent createCenterPanel() {
      myView.setMinimumSize(new JBDimension(360, 200));
      myView.setPreferredSize(new JBDimension(720, 400));

      JComboBox combo = new ComboBox<>(FilterItem.ALL);
      combo.addItemListener(myView);

      JPanel panel = new JPanel(new BorderLayout(10, 10));
      panel.add(BorderLayout.CENTER, myView);
      panel.add(BorderLayout.SOUTH, combo);
      return panel;
    }

    @NotNull
    @Override
    protected Action[] createActions() {
      return new Action[]{getCancelAction()};
    }
  }

  private static final class FilterItem {
    private final ImageFilter myFilter;

    public FilterItem(ImageFilter filter) {
      myFilter = filter;
    }

    @Override
    public String toString() {
      return myFilter == null ? "No filtering" : myFilter.toString();
    }

    private static final FilterItem[] ALL = new FilterItem[]{
      new FilterItem(null),
      new FilterItem(DaltonizationFilter.protanopia),
      new FilterItem(DaltonizationFilter.forProtanopia(.5)),
      new FilterItem(DaltonizationFilter.deuteranopia),
      new FilterItem(DaltonizationFilter.forDeuteranopia(.5)),
      new FilterItem(DaltonizationFilter.tritanopia),
      new FilterItem(DaltonizationFilter.forTritanopia(.5)),
      new FilterItem(SimulationFilter.protanopia),
      new FilterItem(SimulationFilter.forProtanopia(.5)),
      new FilterItem(SimulationFilter.deuteranopia),
      new FilterItem(SimulationFilter.forDeuteranopia(.5)),
      new FilterItem(SimulationFilter.tritanopia),
      new FilterItem(SimulationFilter.forTritanopia(.5)),
      new FilterItem(SimulationFilter.achromatopsia),
      new FilterItem(SimulationFilter.forAchromatopsia(.5)),
    };
  }

  private static final class ColorView extends JComponent implements ItemListener {
    private ImageFilter myFilter;
    private Image myImage;

    @Override
    protected void paintComponent(Graphics g) {
      Rectangle bounds = new Rectangle(getWidth(), getHeight());
      JBInsets.removeFrom(bounds, getInsets());
      if (bounds.isEmpty()) return;

      if (myImage == null || bounds.width != myImage.getWidth(this) || bounds.height != myImage.getHeight(this)) {
        int[] array = new int[bounds.width * bounds.height];
        float width = (float)(bounds.width - 1);
        float height = (float)(bounds.height - 1);
        for (int i = 0, h = 0; h < bounds.height; h++) {
          for (int w = 0; w < bounds.width; w++, i++) {
            float level = 2 * h / height;
            float saturation = (level > 1f) ? 1 : level;
            float brightness = (level > 1f) ? 2 - level : 1;
            array[i] = Color.HSBtoRGB(w / width, saturation, brightness);
          }
        }
        BufferedImage image = UIUtil.createImage(bounds.width, bounds.height, BufferedImage.TYPE_INT_RGB);
        image.setRGB(0, 0, bounds.width, bounds.height, array, 0, bounds.width);
        myImage = ImageUtil.filter(image, myFilter);
      }
      g.drawImage(myImage, bounds.x, bounds.y, bounds.width, bounds.height, this);
    }

    @Override
    public void itemStateChanged(ItemEvent event) {
      if (ItemEvent.SELECTED == event.getStateChange()) {
        Object object = event.getItem();
        if (object instanceof FilterItem) {
          FilterItem item = (FilterItem)object;
          myFilter = item.myFilter;
          myImage = null;
          repaint();
        }
      }
    }
  }
}
