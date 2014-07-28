/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.openapi.roots.ui.configuration;

import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.ui.GraphicsConfig;
import com.intellij.openapi.ui.popup.ListItemDescriptor;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.*;
import com.intellij.ui.components.JBList;
import com.intellij.ui.components.panels.NonOpaquePanel;
import com.intellij.ui.navigation.History;
import com.intellij.ui.navigation.Place;
import com.intellij.ui.popup.list.GroupedItemsListRenderer;
import com.intellij.util.ui.EmptyIcon;
import com.intellij.util.ui.GraphicsUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.border.EmptyBorder;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import static javax.swing.SwingConstants.CENTER;
import static javax.swing.SwingConstants.LEFT;

public class SidePanel extends JPanel {

  private final JList myList;
  private final DefaultListModel myModel;
  private final Place.Navigator myNavigator;
  private final ArrayList<Place> myPlaces = new ArrayList<Place>();

  private final Map<Integer, String> myIndex2Separator = new HashMap<Integer, String>();
  private final Map<Place, Presentation> myPlace2Presentation = new HashMap<Place, Presentation>();
  private final History myHistory;

  public SidePanel(Place.Navigator navigator, History history) {
    myHistory = history;
    myNavigator = navigator;

    setLayout(new BorderLayout());

    myModel = new DefaultListModel();
    myList = new JBList(myModel);
    if (Registry.is("ide.new.project.settings")) {
      myList.setBackground(UIUtil.getSidePanelColor());
      myList.setBorder(new EmptyBorder(5, 0, 0, 0));
    }
    final ListItemDescriptor descriptor = new ListItemDescriptor() {
      @Override
      public String getTextFor(final Object value) {
        return myPlace2Presentation.get(value).getText();
      }

      @Override
      public String getTooltipFor(final Object value) {
        return getTextFor(value);
      }

      @Override
      public Icon getIconFor(final Object value) {
        return Registry.is("ide.new.project.settings") ? EmptyIcon.create(16, 20) : null;
        //return myPlace2Presentation.get(value).getIcon();
      }

      @Override
      public boolean hasSeparatorAboveOf(final Object value) {
        final int index = myPlaces.indexOf(value);
        return myIndex2Separator.get(index) != null;
      }

      @Override
      public String getCaptionAboveOf(final Object value) {
        return myIndex2Separator.get(myPlaces.indexOf(value));
      }
    };

    myList.setCellRenderer(new GroupedItemsListRenderer(descriptor) {
      JPanel myExtraPanel;
      CountLabel myCountLabel;
      {
        mySeparatorComponent.setCaptionCentered(false);
      }

      @Override
      protected Color getForeground() {
        return Registry.is("ide.new.project.settings") ? new JBColor(Gray._60, Gray._140) : super.getForeground();
      }

      @Override
      protected SeparatorWithText createSeparator() {
        return new SeparatorWithText() {
          @Override
          protected void paintComponent(Graphics g) {
            if (Registry.is("ide.new.project.settings")) {
              g.setColor(new JBColor(POPUP_SEPARATOR_FOREGROUND, Gray._80));
              if ("--".equals(getCaption())) {
                g.drawLine(0, getHeight()/ 2, getWidth(), getHeight() /2);
                return;
              }
              Rectangle viewR = new Rectangle(0, getVgap(), getWidth() - 1, getHeight() - getVgap() - 1);
              Rectangle iconR = new Rectangle();
              Rectangle textR = new Rectangle();
              String s = SwingUtilities
                .layoutCompoundLabel(g.getFontMetrics(), getCaption(), null, CENTER,
                                     LEFT,
                                     CENTER,
                                     LEFT,
                                     viewR, iconR, textR, 0);
              GraphicsUtil.setupAAPainting(g);
              g.setColor(new JBColor(Gray._255.withAlpha(80), Gray._0.withAlpha(80)));
              g.drawString(s, textR.x + 10, textR.y + 1 + g.getFontMetrics().getAscent());
              g.setColor(new JBColor(new Color(0x5F6D7B), Gray._120));
              g.drawString(s, textR.x + 10, textR.y + g.getFontMetrics().getAscent());
            }
            else {
              super.paintComponent(g);
            }
          }
        };
      }

      @Override
      protected void layout() {
        if (Registry.is("ide.new.project.settings")) {
          myRendererComponent.add(mySeparatorComponent, BorderLayout.NORTH);
          myExtraPanel.add(myComponent, BorderLayout.CENTER);
          myExtraPanel.add(myCountLabel, BorderLayout.EAST);
          myRendererComponent.add(myExtraPanel, BorderLayout.CENTER);
        } else {
          super.layout();
        }
      }

      @Override
      public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
        myCountLabel.setText("");
        final Component component = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
        if ("Problems".equals(descriptor.getTextFor(value))) {
          final ErrorPaneConfigurable errorPane = (ErrorPaneConfigurable)((Place)value).getPath("category");
          if (errorPane != null && errorPane.getErrorsCount() > 0) {
            myCountLabel.setSelected(isSelected);
            myCountLabel.setText(String.valueOf(errorPane.getErrorsCount()));
          }
        }
        return component;
      }

      @Override
      protected JComponent createItemComponent() {
        myExtraPanel = new NonOpaquePanel(new BorderLayout());
        myCountLabel = new CountLabel();


        if (Registry.is("ide.new.project.settings")) {
          myTextLabel = new EngravedLabel();
          myTextLabel.setFont(myTextLabel.getFont().deriveFont(Font.BOLD));
          myTextLabel.setForeground(Gray._240);
          myTextLabel.setOpaque(true);
          return layoutComponent(myTextLabel);
        }
        return super.createItemComponent();
      }

      @Override
      protected Color getBackground() {
        return Registry.is("ide.new.project.settings") ? UIUtil.getSidePanelColor() : super.getBackground();
      }
    });

    add(ScrollPaneFactory.createScrollPane(myList, Registry.is("ide.new.project.settings")), BorderLayout.CENTER);
    myList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

    myList.addListSelectionListener(new ListSelectionListener() {
      @Override
      public void valueChanged(final ListSelectionEvent e) {
        if (e.getValueIsAdjusting()) return;
        final Object value = myList.getSelectedValue();
        if (value != null) {
          myNavigator.navigateTo(((Place)value), false);
        }
      }
    });
  }

  public JList getList() {
    return myList;
  }

  public void addPlace(Place place, @NotNull Presentation presentation) {
    myModel.addElement(place);
    myPlaces.add(place);
    myPlace2Presentation.put(place, presentation);
    revalidate();
    repaint();
  }

  public void addSeparator(String text) {
    myIndex2Separator.put(myPlaces.size(), text);
  }

  public Collection<Place> getPlaces() {
    return myPlaces;
  }

  public void select(final Place place) {
    myList.setSelectedValue(place, true);
  }

  private static class CountLabel extends JLabel {
    private boolean mySelected;

    public CountLabel() {
      super();
      setBorder(new Border() {
        @Override
        public void paintBorder(Component c, Graphics g, int x, int y, int width, int height) {
        }

        @Override
        public Insets getBorderInsets(Component c) {
          return StringUtil.isEmpty(getText()) ? new Insets(0,0,0,0) : new Insets(2, 6, 2, 6 + 6);
        }

        @Override
        public boolean isBorderOpaque() {
          return false;
        }
      });
      setFont(UIUtil.getListFont().deriveFont(Font.BOLD));
    }

    public boolean isSelected() {
      return mySelected;
    }

    public void setSelected(boolean selected) {
      mySelected = selected;
    }

    @Override
    protected void paintComponent(Graphics g) {
      g.setColor(isSelected() ? UIUtil.getListSelectionBackground() : UIUtil.getSidePanelColor());
      g.fillRect(0, 0, getWidth(), getHeight());
      if (StringUtil.isEmpty(getText())) return;
      final JBColor deepBlue = new JBColor(new Color(0x97A4B2), new Color(92, 98, 113));
      g.setColor(isSelected() ? Gray._255.withAlpha(UIUtil.isUnderDarcula() ? 100 : 220) : deepBlue);
      final GraphicsConfig config = GraphicsUtil.setupAAPainting(g);
      g.fillRoundRect(0, 3, getWidth() - 6 -1, getHeight()-6 , (getHeight() - 6), (getHeight() - 6));
      config.restore();
      setForeground(isSelected() ? deepBlue.darker() : UIUtil.getListForeground(true));

      super.paintComponent(g);
    }
  }
}
