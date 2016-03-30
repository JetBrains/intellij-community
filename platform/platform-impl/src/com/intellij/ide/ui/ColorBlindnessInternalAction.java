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
import com.intellij.ui.components.panels.VerticalLayout;
import com.intellij.util.Matrix;
import com.intellij.util.ui.ImageUtil;
import com.intellij.util.ui.JBDimension;
import com.intellij.util.ui.JBInsets;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.event.ChangeListener;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.*;
import java.io.File;

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
    private final JComboBox myCombo = new ComboBox<>(FilterItem.ALL);
    private final JSlider myFirstSlider = createSlider();
    private final JSlider mySecondSlider = createSlider();

    private static JSlider createSlider() {
      JSlider slider = new JSlider(0, 100);
      slider.setBorder(BorderFactory.createEmptyBorder(10, 0, 0, 0));
      slider.setMajorTickSpacing(10);
      slider.setMinorTickSpacing(1);
      slider.setPaintTicks(true);
      slider.setPaintLabels(true);
      slider.setVisible(false);
      return slider;
    }

    private static void showSlider(JSlider slider, ChangeListener listener) {
      slider.setValue(70);
      slider.setVisible(true);
      slider.addChangeListener(listener);
    }

    private static void hideSlider(JSlider slider, ChangeListener listener) {
      slider.removeChangeListener(listener);
      slider.setVisible(false);
    }

    private void updateFilter(MutableFilter filter) {
      filter.update(myFirstSlider.getValue() / 100.0, mySecondSlider.getValue() / 100.0);
      myView.setFilter(filter);
    }

    private ColorDialog(AnActionEvent event) {
      super(event.getProject());
      init();
      setTitle("ColorBlindness");
    }

    @Nullable
    @Override
    public JComponent getPreferredFocusedComponent() {
      return myCombo;
    }

    @Override
    protected JComponent createCenterPanel() {
      myView.setBorder(BorderFactory.createEtchedBorder());
      myView.setMinimumSize(new JBDimension(360, 200));
      myView.setPreferredSize(new JBDimension(720, 400));
      myView.addMouseListener(new MouseAdapter() {
        private JFileChooser myFileChooser;

        @Override
        public void mousePressed(MouseEvent event) {
          if (SwingUtilities.isLeftMouseButton(event)) {
            if (myFileChooser == null) {
              myFileChooser = new JFileChooser();
              myFileChooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
              myFileChooser.setMultiSelectionEnabled(false);
            }
            File file = JFileChooser.APPROVE_OPTION != myFileChooser.showOpenDialog(myView) ? null : myFileChooser.getSelectedFile();
            if (file != null) {
              try {
                myView.setImage(ImageIO.read(file));
              }
              catch (Exception exception) {
                myView.setImage(null);
              }
            }
          }
        }
      });

      ChangeListener listener = event -> {
        if (myView.myFilter instanceof MutableFilter) {
          updateFilter((MutableFilter)myView.myFilter);
        }
      };

      myCombo.addItemListener(event -> {
        if (ItemEvent.SELECTED == event.getStateChange()) {
          Object object = event.getItem();
          if (object instanceof FilterItem) {
            FilterItem item = (FilterItem)object;
            if (item.myFilter instanceof MutableFilter) {
              showSlider(myFirstSlider, listener);
              showSlider(mySecondSlider, listener);
              updateFilter((MutableFilter)item.myFilter);
            }
            else {
              hideSlider(myFirstSlider, listener);
              hideSlider(mySecondSlider, listener);
              myView.setFilter(item.myFilter);
            }
          }
        }
      });
      JPanel control = new JPanel(new VerticalLayout(10));
      control.add(VerticalLayout.TOP, myCombo);
      control.add(VerticalLayout.TOP, myFirstSlider);
      control.add(VerticalLayout.TOP, mySecondSlider);

      JPanel panel = new JPanel(new BorderLayout(10, 10));
      panel.add(BorderLayout.CENTER, myView);
      panel.add(BorderLayout.SOUTH, control);
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
      new FilterItem(MatrixFilter.protanopia),
      new FilterItem(new MutableFilter("Protanopia (mutable)") {
        @Override
        ColorConverter getConverter(double first, double second) {
          Matrix matrix = Matrix.create(3, 1, first, second, 0, 1, 0, 0, 0, 1);
          return new MatrixConverter(ColorBlindnessMatrix.Protanopia.calculate(matrix));
        }
      }),
      new FilterItem(DaltonizationFilter.deuteranopia),
      new FilterItem(MatrixFilter.deuteranopia),
      new FilterItem(new MutableFilter("Deuteranopia (mutable)") {
        @Override
        ColorConverter getConverter(double first, double second) {
          Matrix matrix = Matrix.create(3, 1, 0, 0, first, 1, second, 0, 0, 1);
          return new MatrixConverter(ColorBlindnessMatrix.Deuteranopia.calculate(matrix));
        }
      }),
      new FilterItem(DaltonizationFilter.tritanopia),
      new FilterItem(MatrixFilter.tritanopia),
      new FilterItem(new MutableFilter("Tritanopia (mutable)") {
        @Override
        ColorConverter getConverter(double first, double second) {
          Matrix matrix = Matrix.create(3, 1, 0, 0, 0, 1, 0, first, second, 1);
          return new MatrixConverter(ColorBlindnessMatrix.Tritanopia.calculate(matrix));
        }
      }),
      new FilterItem(SimulationFilter.protanopia),
      new FilterItem(SimulationFilter.deuteranopia),
      new FilterItem(SimulationFilter.tritanopia),
      new FilterItem(SimulationFilter.achromatopsia),
    };
  }

  private static final class ColorView extends JComponent {
    private BufferedImage myBackground;
    private ImageFilter myFilter;
    private Image myImage;

    @Override
    protected void paintComponent(Graphics g) {
      Rectangle bounds = new Rectangle(getWidth(), getHeight());
      JBInsets.removeFrom(bounds, getInsets());
      if (bounds.isEmpty()) return;

      if (myBackground != null) {
        if (myImage == null) myImage = ImageUtil.filter(myBackground, myFilter);
      }
      else if (myImage == null || bounds.width != myImage.getWidth(this) || bounds.height != myImage.getHeight(this)) {
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

    void setImage(BufferedImage image) {
      myImage = null;
      myBackground = image;
      repaint();
    }

    void setFilter(ImageFilter filter) {
      myImage = null;
      myFilter = filter;
      repaint();
    }
  }

  private abstract static class MutableFilter extends RGBImageFilter {
    private final String myName;
    private ColorConverter myConverter;

    private MutableFilter(String name) {
      myName = name;
      update(.7, .7);
    }

    private void update(double first, double second) {
      myConverter = getConverter(first, second);
    }

    abstract ColorConverter getConverter(double first, double second);

    @Override
    public String toString() {
      return myName;
    }

    @Override
    public int filterRGB(int x, int y, int rgb) {
      return myConverter.convert(rgb);
    }
  }
}
