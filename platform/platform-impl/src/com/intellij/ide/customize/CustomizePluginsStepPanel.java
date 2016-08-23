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
package com.intellij.ide.customize;

import com.intellij.ide.plugins.PluginManager;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.VerticalFlowLayout;
import com.intellij.openapi.util.Pair;
import com.intellij.ui.ColorUtil;
import com.intellij.ui.JBCardLayout;
import com.intellij.ui.JBColor;
import com.intellij.ui.border.CustomLineBorder;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.labels.LinkLabel;
import com.intellij.ui.components.labels.LinkListener;
import com.intellij.util.ui.JBUI;

import javax.swing.*;
import javax.swing.border.CompoundBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.IOException;
import java.util.List;
import java.util.Map;

public class CustomizePluginsStepPanel extends AbstractCustomizeWizardStep implements LinkListener<String> {
  private static final String MAIN = "main";
  private static final String CUSTOMIZE = "customize";
  private static final int COLS = 3;
  private static final TextProvider CUSTOMIZE_TEXT_PROVIDER = new TextProvider() {
    @Override
    public String getText() {
      return "Customize...";
    }
  };
  private static final String SWITCH_COMMAND = "Switch";
  private static final String CUSTOMIZE_COMMAND = "Customize";
  private final JBCardLayout myCardLayout;
  private final IdSetPanel myCustomizePanel;
  private final PluginGroups myPluginGroups;


  public CustomizePluginsStepPanel(PluginGroups pluginGroups) {
    myPluginGroups = pluginGroups;
    myCardLayout = new JBCardLayout();
    setLayout(myCardLayout);
    JPanel gridPanel = new JPanel(new GridLayout(0, COLS));
    myCustomizePanel = new IdSetPanel();
    JBScrollPane scrollPane = createScrollPane(gridPanel);
    add(scrollPane, MAIN);
    add(myCustomizePanel, CUSTOMIZE);

    Map<String, Pair<Icon, List<String>>> groups = pluginGroups.getTree();
    for (final Map.Entry<String, Pair<Icon, List<String>>> entry : groups.entrySet()) {
      final String group = entry.getKey();
      if (PluginGroups.CORE.equals(group) || myPluginGroups.getSets(group).isEmpty()) continue;

      JPanel groupPanel = new JPanel(new GridBagLayout()) {
        @Override
        public Color getBackground() {
          Color color = UIManager.getColor("Panel.background");
          return isGroupEnabled(group)? color : ColorUtil.darker(color, 1);
        }
      };
      gridPanel.setOpaque(true);
      GridBagConstraints gbc = new GridBagConstraints();
      gbc.fill = GridBagConstraints.BOTH;
      gbc.gridwidth = GridBagConstraints.REMAINDER;
      gbc.weightx = 1;
      JLabel titleLabel = new JLabel("<html><body><h2 style=\"text-align:left;\">" + group + "</h2></body></html>", SwingConstants.CENTER) {
        @Override
        public boolean isEnabled() {
          return isGroupEnabled(group);
        }
      };
      groupPanel.add(new JLabel(entry.getValue().getFirst()), gbc);
      //gbc.insets.bottom = 5;
      groupPanel.add(titleLabel, gbc);
      JLabel descriptionLabel = new JLabel(pluginGroups.getDescription(group), SwingConstants.CENTER) {
        @Override
        public Dimension getPreferredSize() {
          Dimension size = super.getPreferredSize();
          size.width = Math.min(size.width, 200);
          return size;
        }

        @Override
        public boolean isEnabled() {
          return isGroupEnabled(group);
        }

        @Override
        public Color getForeground() {
          return ColorUtil.withAlpha(UIManager.getColor("Label.foreground"), .75);
        }
      };
      groupPanel.add(descriptionLabel, gbc);
      gbc.weighty = 1;
      groupPanel.add(Box.createVerticalGlue(), gbc);
      gbc.weighty = 0;
      JPanel buttonsPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, SMALL_GAP, SMALL_GAP / 2));
      buttonsPanel.setOpaque(false);
      if (pluginGroups.getSets(group).size() == 1) {
        buttonsPanel.add(createLink(SWITCH_COMMAND + ":" + group, getGroupSwitchTextProvider(group)));
      }
      else {
        buttonsPanel.add(createLink(CUSTOMIZE_COMMAND + ":" + group, CUSTOMIZE_TEXT_PROVIDER));
        buttonsPanel.add(createLink(SWITCH_COMMAND + ":" + group, getGroupSwitchTextProvider(group)));
      }
      groupPanel.add(buttonsPanel, gbc);
      gridPanel.add(groupPanel);
    }

    int cursor = 0;
    Component[] components = gridPanel.getComponents();
    int rowCount = components.length / COLS;
    if (components.length % COLS == 0) rowCount--;
    for (Component component : components) {
      ((JComponent)component).setBorder(
        new CompoundBorder(new CustomLineBorder(ColorUtil.withAlpha(JBColor.foreground(), .2), 0, 0, cursor / 3 <= rowCount - 1 ? 1 : 0,
                                                cursor % COLS != COLS - 1 ? 1 : 0) {
          @Override
          protected Color getColor() {
            return ColorUtil.withAlpha(JBColor.foreground(), .2);
          }
        }, BorderFactory.createEmptyBorder(SMALL_GAP, GAP, SMALL_GAP, GAP)));
      cursor++;
    }
  }

  static JBScrollPane createScrollPane(JPanel gridPanel) {
    JBScrollPane scrollPane =
      new JBScrollPane(gridPanel, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED, ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
    scrollPane.getVerticalScrollBar().setUnitIncrement(10);
    scrollPane.setBorder(JBUI.Borders.empty()); // to disallow resetting border on LaF change
    return scrollPane;
  }

  @Override
  public void linkSelected(LinkLabel linkLabel, String command) {
    if (command == null || !command.contains(":")) return;
    int semicolonPosition = command.indexOf(":");
    String group = command.substring(semicolonPosition + 1);
    command = command.substring(0, semicolonPosition);

    if (SWITCH_COMMAND.equals(command)) {
      boolean enabled = isGroupEnabled(group);
      List<IdSet> sets = myPluginGroups.getSets(group);
      for (IdSet idSet : sets) {
        String[] ids = idSet.getIds();
        for (String id : ids) {
          myPluginGroups.setPluginEnabledWithDependencies(id, !enabled);
        }
      }
      repaint();
      return;
    }
    if (CUSTOMIZE_COMMAND.equals(command)) {
      myCustomizePanel.update(group);
      myCardLayout.show(this, CUSTOMIZE);
      setButtonsVisible(false);
    }
  }

  private void setButtonsVisible(boolean visible) {
    DialogWrapper window = DialogWrapper.findInstance(this);
    if (window instanceof CustomizeIDEWizardDialog) {
      ((CustomizeIDEWizardDialog)window).setButtonsVisible(visible);
    }
  }

  private LinkLabel createLink(String command, final TextProvider provider) {
    return new LinkLabel<String>("", null, this, command) {
      @Override
      public String getText() {
        return provider.getText();
      }
    };
  }

  TextProvider getGroupSwitchTextProvider(final String group) {
    return new TextProvider() {
      @Override
      public String getText() {
        return (isGroupEnabled(group) ? "Disable" : "Enable") +
               (myPluginGroups.getSets(group).size() > 1 ? " All" : "");
      }
    };
  }

  private boolean isGroupEnabled(String group) {
    List<IdSet> sets = myPluginGroups.getSets(group);
    for (IdSet idSet : sets) {
      String[] ids = idSet.getIds();
      for (String id : ids) {
        if (myPluginGroups.isPluginEnabled(id)) return true;
      }
    }
    return false;
  }

  @Override
  public String getTitle() {
    return "Default plugins";
  }

  @Override
  public String getHTMLHeader() {
    return "<html><body><h2>Tune " +
           ApplicationNamesInfo.getInstance().getProductName() +
           " to your tasks</h2>" +
           ApplicationNamesInfo.getInstance().getProductName() +
           " has a lot of tools enabled by default. You can set only ones you need or leave them all." +
           "</body></html>";
  }

  @Override
  public boolean beforeOkAction() {
    try {
      PluginManager.saveDisabledPlugins(myPluginGroups.getDisabledPluginIds(), false);
    }
    catch (IOException ignored) {
    }
    return true;
  }

  private class IdSetPanel extends JPanel implements LinkListener<String> {
    private JLabel myTitleLabel = new JLabel();
    private JPanel myContentPanel = new JPanel(new GridLayout(0, 3, 5, 5));
    private JButton mySaveButton = new JButton("Save Changes and Go Back");
    private String myGroup;

    private IdSetPanel() {
      setLayout(new VerticalFlowLayout(VerticalFlowLayout.TOP, 0, GAP, true, false));
      add(myTitleLabel);
      add(myContentPanel);
      JPanel buttonPanel = new JPanel(new GridBagLayout());
      GridBagConstraints gbc = new GridBagConstraints();
      gbc.insets.right = 25;
      gbc.gridy = 0;
      buttonPanel.add(mySaveButton, gbc);
      buttonPanel.add(new LinkLabel<>("Enable All", null, this, "enable"), gbc);
      buttonPanel.add(new LinkLabel<>("Disable All", null, this, "disable"), gbc);
      gbc.weightx = 1;
      buttonPanel.add(Box.createHorizontalGlue(), gbc);
      add(buttonPanel);
      mySaveButton.addActionListener(new ActionListener() {
        @Override
        public void actionPerformed(ActionEvent e) {
          myCardLayout.show(CustomizePluginsStepPanel.this, MAIN);
          setButtonsVisible(true);
        }
      });
    }

    @Override
    public void linkSelected(LinkLabel aSource, String command) {
      if (myGroup == null) return;
      boolean enable = "enable".equals(command);
      List<IdSet> idSets = myPluginGroups.getSets(myGroup);
      for (IdSet set : idSets) {
        myPluginGroups.setIdSetEnabled(set, enable);
      }
      CustomizePluginsStepPanel.this.repaint();
    }

    void update(String group) {
      myGroup = group;
      myTitleLabel.setText("<html><body><h2 style=\"text-align:left;\">" + group + "</h2></body></html>");
      myContentPanel.removeAll();
      List<IdSet> idSets = myPluginGroups.getSets(group);
      for (final IdSet set : idSets) {
        final JCheckBox checkBox = new JCheckBox(set.getTitle(), myPluginGroups.isIdSetAllEnabled(set));
        checkBox.setModel(new JToggleButton.ToggleButtonModel() {
          @Override
          public boolean isSelected() {
            return myPluginGroups.isIdSetAllEnabled(set);
          }
        });
        checkBox.addActionListener(new ActionListener() {
          @Override
          public void actionPerformed(ActionEvent e) {
            myPluginGroups.setIdSetEnabled(set, !checkBox.isSelected());
            CustomizePluginsStepPanel.this.repaint();
          }
        });
        myContentPanel.add(checkBox);
      }
    }
  }

  private interface TextProvider {
    String getText();
  }
}
