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
package com.intellij.ide.customize;

import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.ui.VerticalFlowLayout;
import com.intellij.ui.ColorUtil;
import com.intellij.ui.JBCardLayout;
import com.intellij.ui.JBColor;
import com.intellij.ui.border.CustomLineBorder;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.labels.LinkLabel;
import com.intellij.ui.components.labels.LinkListener;

import javax.swing.*;
import javax.swing.border.CompoundBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.List;
import java.util.Map;

public class CustomizePluginsStepPanel extends AbstractCustomizeWizardStep implements LinkListener<String> {
  private static final String MAIN = "main";
  private static final String CUSTOMIZE = "customize";
  private static final int COLS = 3;
  private final Color myDescriptionForeground = ColorUtil.withAlpha(UIManager.getColor("Label.foreground"), .75);
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


  public CustomizePluginsStepPanel() {
    myCardLayout = new JBCardLayout();
    setLayout(myCardLayout);
    JPanel gridPanel = new JPanel(new GridLayout(0, COLS));
    myCustomizePanel = new IdSetPanel();
    JBScrollPane scrollPane =
      new JBScrollPane(gridPanel, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED, ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
    scrollPane.getVerticalScrollBar().setUnitIncrement(10);
    add(scrollPane, MAIN);
    add(myCustomizePanel, CUSTOMIZE);

    //PluginManager.loadDisabledPlugins(new File(PathManager.getConfigPath()).getPath(), myDisabledPluginIds);
    //for (IdeaPluginDescriptor pluginDescriptor : myAllPlugins) {
    //  if (pluginDescriptor.getPluginId().getIdString().equals("com.intellij")) {
    ////    skip 'IDEA CORE' plugin
        //continue;
      //}
    //  //PluginManager.initClassLoader(PluginGroups.class.getClassLoader(), (IdeaPluginDescriptorImpl)pluginDescriptor);
    //}
    Map<String, List<String>> groups = PluginGroups.getInstance().getTree();
    for (Map.Entry<String, List<String>> entry : groups.entrySet()) {
      final String group = entry.getKey();
      if (PluginGroups.CORE.equals(group)) continue;

      JPanel groupPanel = new JPanel(new GridBagLayout());
      GridBagConstraints gbc = new GridBagConstraints();
      gbc.insets = new Insets(0, 0, 10, 0);
      gbc.fill = GridBagConstraints.BOTH;
      gbc.gridwidth = GridBagConstraints.REMAINDER;
      gbc.weightx = 1;
      JLabel titleLabel = new JLabel("<html><body><h2 style=\"text-align:left;\">" + group + "</h2></body></html>") {
        @Override
        public boolean isEnabled() {
          return isGroupEnabled(group);
        }
      };
      groupPanel.add(titleLabel, gbc);
      JLabel descriptionLabel = new JLabel(PluginGroups.getInstance().getDescription(group)) {
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

        //@Override
        //public Color getForeground() {
        //  return isGroupEnabled(group)? myDescriptionForeground : myDescriptionDisabledForeground;
        //}

      };
      descriptionLabel.setForeground(myDescriptionForeground);
      groupPanel.add(descriptionLabel, gbc);
      gbc.weighty = 1;
      groupPanel.add(Box.createVerticalGlue(), gbc);
      gbc.weighty = 0;
      if (PluginGroups.getInstance().getSets(group).size() == 1) {
        groupPanel.add(createLink(SWITCH_COMMAND + ":" + group, getGroupSwitchTextProvider(group)), gbc);
      }
      else {
        JPanel buttonsPanel = new JPanel(new GridLayout(1, 2, 10, 5));
        LinkLabel customizeButton = createLink(CUSTOMIZE_COMMAND + ":" + group, CUSTOMIZE_TEXT_PROVIDER);
        buttonsPanel.add(customizeButton);
        LinkLabel disableAllButton = createLink(SWITCH_COMMAND + ":" + group, getGroupSwitchTextProvider(group));
        buttonsPanel.add(disableAllButton);
        groupPanel.add(buttonsPanel, gbc);
      }
      gridPanel.add(groupPanel);
    }

    int cursor = 0;
    Component[] components = gridPanel.getComponents();
    int rowCount = components.length / COLS;
    for (Component component : components) {
      ((JComponent)component).setBorder(
        new CompoundBorder(new CustomLineBorder(ColorUtil.withAlpha(JBColor.foreground(), .2), 0, 0, cursor / 3 < rowCount - 1 ? 1 : 0,
                                                cursor % COLS != COLS - 1 ? 1 : 0), BorderFactory.createEmptyBorder(GAP, GAP, GAP, GAP))
      );
      cursor++;
    }
  }

  @Override
  public void linkSelected(LinkLabel linkLabel, String command) {
    if (command == null || !command.contains(":")) return;
    int semicolonPosition = command.indexOf(":");
    String group = command.substring(semicolonPosition + 1);
    command = command.substring(0, semicolonPosition);

    if (SWITCH_COMMAND.equals(command)) {
      boolean enabled = isGroupEnabled(group);
      List<IdSet> sets = PluginGroups.getInstance().getSets(group);
      for (IdSet idSet : sets) {
        String[] ids = idSet.getIds();
        for (String id : ids) {
          PluginGroups.getInstance().setPluginEnabledWithDependencies(id, !enabled);
        }
      }
      repaint();
      return;
    }
    if (CUSTOMIZE_COMMAND.equals(command)) {
      myCustomizePanel.update(group);
      myCardLayout.show(this, CUSTOMIZE);
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
               (PluginGroups.getInstance().getSets(group).size() > 1 ? " All" : "");
      }
    };
  }

  private boolean isGroupEnabled(String group) {
    List<IdSet> sets = PluginGroups.getInstance().getSets(group);
    for (IdSet idSet : sets) {
      String[] ids = idSet.getIds();
      for (String id : ids) {
        if (PluginGroups.getInstance().isPluginEnabled(id)) return true;
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
  public String getHTMLFooter() {
    return null;
  }





  private class IdSetPanel extends JPanel {
    private JLabel myTitleLabel = new JLabel();
    private JPanel myContentPanel = new JPanel(new GridLayout(0, 3, 5, 5));
    private JButton mySaveButton = new JButton("Save Changes and Go Back");

    private IdSetPanel() {
      setLayout(new VerticalFlowLayout(VerticalFlowLayout.TOP, 0, GAP, true, false));
      add(myTitleLabel);
      add(myContentPanel);
      JPanel buttonPanel = new JPanel(new BorderLayout());
      buttonPanel.add(mySaveButton, BorderLayout.WEST);
      buttonPanel.add(Box.createHorizontalGlue(), BorderLayout.CENTER);
      add(buttonPanel);
      mySaveButton.addActionListener(new ActionListener() {
        @Override
        public void actionPerformed(ActionEvent e) {
          myCardLayout.show(CustomizePluginsStepPanel.this, MAIN);
        }
      });
    }

    void update(String group) {
      myTitleLabel.setText("<html><body><h2 style=\"text-align:left;\">" + group + "</h2></body></html>");
      myContentPanel.removeAll();
      List<IdSet> idSets = PluginGroups.getInstance().getSets(group);
      for (final IdSet set : idSets) {
        final JCheckBox checkBox = new JCheckBox(set.getTitle(), PluginGroups.getInstance().isIdSetAllEnabled(set));
        checkBox.setModel(new JToggleButton.ToggleButtonModel() {
          @Override
          public boolean isSelected() {
            return PluginGroups.getInstance().isIdSetAllEnabled(set);
          }
        });
        checkBox.addActionListener(new ActionListener() {
          @Override
          public void actionPerformed(ActionEvent e) {
            PluginGroups.getInstance().setIdSetEnabled(set, !checkBox.isSelected());
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
