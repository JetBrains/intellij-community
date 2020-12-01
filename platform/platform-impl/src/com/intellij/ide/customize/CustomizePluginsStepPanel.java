// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.customize;

import com.intellij.ide.IdeBundle;
import com.intellij.ide.plugins.DisabledPluginsState;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.VerticalFlowLayout;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.text.HtmlBuilder;
import com.intellij.ui.ColorUtil;
import com.intellij.ui.JBCardLayout;
import com.intellij.ui.JBColor;
import com.intellij.ui.border.CustomLineBorder;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.labels.LinkLabel;
import com.intellij.ui.components.labels.LinkListener;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.border.CompoundBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.List;

import static com.intellij.openapi.util.text.HtmlChunk.*;

public final class CustomizePluginsStepPanel extends AbstractCustomizeWizardStep {
  private static final @NonNls String MAIN = "main";
  private static final @NonNls String CUSTOMIZE = "customize";
  private static final int COLS = 3;
  private static final TextProvider CUSTOMIZE_TEXT_PROVIDER = new TextProvider() {
    @Override
    public String getText() {
      return IdeBundle.message("link.label.wizard.step.plugin.customize");
    }
  };
  private static final @NonNls String SWITCH_COMMAND = "Switch";
  private static final @NonNls String CUSTOMIZE_COMMAND = "Customize";
  private final JBCardLayout myCardLayout;
  private final IdSetPanel myCustomizePanel;
  private final PluginGroups myPluginGroups;


  public CustomizePluginsStepPanel(@NotNull PluginGroups pluginGroups) {
    myPluginGroups = pluginGroups;
    myCardLayout = new JBCardLayout();
    setLayout(myCardLayout);
    JPanel gridPanel = new JPanel(new GridLayout(0, COLS));
    myCustomizePanel = new IdSetPanel();
    JBScrollPane scrollPane = createScrollPane(gridPanel);
    add(scrollPane, MAIN);
    add(myCustomizePanel, CUSTOMIZE);

    List<PluginGroups.Group> groups = pluginGroups.getTree();
    for (PluginGroups.Group g : groups) {
      final String groupId = g.getId();
      if (PluginGroups.CORE.equals(groupId) || myPluginGroups.getSets(groupId).isEmpty()) continue;

      JPanel groupPanel = new JPanel(new GridBagLayout()) {
        @Override
        public Color getBackground() {
          Color color = UIManager.getColor("Panel.background");
          return isGroupEnabled(groupId)? color : ColorUtil.darker(color, 1);
        }
      };
      gridPanel.setOpaque(true);
      GridBagConstraints gbc = new GridBagConstraints();
      gbc.fill = GridBagConstraints.BOTH;
      gbc.gridwidth = GridBagConstraints.REMAINDER;
      gbc.weightx = 1;
      HtmlBuilder titleHtml = new HtmlBuilder().append(
        html().child(
          body().child(
            tag("h2").attr("style", "text-align:center;").addText(g.getName()))));
      JLabel titleLabel = new JLabel(titleHtml.toString(), SwingConstants.CENTER) {
        @Override
        public boolean isEnabled() {
          return isGroupEnabled(groupId);
        }
      };
      groupPanel.add(new JLabel(g.getIcon()), gbc);
      //gbc.insets.bottom = 5;
      groupPanel.add(titleLabel, gbc);
      JLabel descriptionLabel = new JLabel(pluginGroups.getDescription(groupId), SwingConstants.CENTER) {
        @Override
        public Dimension getPreferredSize() {
          Dimension size = super.getPreferredSize();
          size.width = Math.min(size.width, 200);
          return size;
        }

        @Override
        public boolean isEnabled() {
          return isGroupEnabled(groupId);
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
      if (pluginGroups.getSets(groupId).size() != 1) {
        buttonsPanel.add(createLink(new LinkDescription(CUSTOMIZE_COMMAND, g), CUSTOMIZE_TEXT_PROVIDER));
      }
      buttonsPanel.add(createLink(new LinkDescription(SWITCH_COMMAND, g), getGroupSwitchTextProvider(groupId)));
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
    scrollPane.setBorder(JBUI.Borders.empty()); // to disallow resetting border on LaF change
    return scrollPane;
  }

  private void setButtonsVisible(boolean visible) {
    DialogWrapper window = DialogWrapper.findInstance(this);
    if (window instanceof CustomizeIDEWizardDialog) {
      ((CustomizeIDEWizardDialog)window).setButtonsVisible(visible);
    }
  }

  @NotNull
  private LinkLabel<LinkDescription> createLink(LinkDescription command, final TextProvider provider) {
    LinkListener<LinkDescription> listener = new LinkListener<>() {
      @Override
      public void linkSelected(LinkLabel<LinkDescription> aSource, @NonNls LinkDescription description) {
        String groupId = description.groupId;
        if (SWITCH_COMMAND.equals(description.command)) {
          boolean enabled = isGroupEnabled(description.groupId);
          CustomizeIDEWizardInteractions.INSTANCE.record(enabled
                                                         ? CustomizeIDEWizardInteractionType.BundledPluginGroupDisabled
                                                         : CustomizeIDEWizardInteractionType.BundledPluginGroupEnabled,
                                                         null, groupId);
          List<IdSet> sets = myPluginGroups.getSets(groupId);
          for (IdSet idSet : sets) {
            for (PluginId id : idSet.getIds()) {
              myPluginGroups.setPluginEnabledWithDependencies(id, !enabled);
            }
          }
          repaint();
          return;
        }
        if (CUSTOMIZE_COMMAND.equals(description.command)) {
          CustomizeIDEWizardInteractions.INSTANCE.record(CustomizeIDEWizardInteractionType.BundledPluginGroupCustomized, null, groupId);
          myCustomizePanel.update(description.groupId, description.groupName);
          myCardLayout.show(CustomizePluginsStepPanel.this, CUSTOMIZE);
          setButtonsVisible(false);
        }
      }
    };

    return new LinkLabel<>("", null, listener, command) {
      @Override
      public String getText() {
        return provider.getText();
      }
    };
  }

  TextProvider getGroupSwitchTextProvider(final String groupId) {
    return new TextProvider() {
      @Override
      public String getText() {
        return IdeBundle.message(
          "link.label.choice.disable.enable.choice.all",
          isGroupEnabled(groupId) ? 0 : 1,
          myPluginGroups.getSets(groupId).size() > 1 ? 0 : 1);
      }
    };
  }

  private boolean isGroupEnabled(String group) {
    List<IdSet> sets = myPluginGroups.getSets(group);
    for (IdSet idSet : sets) {
      for (PluginId id : idSet.getIds()) {
        if (myPluginGroups.isPluginEnabled(id)) {
          return true;
        }
      }
    }
    return false;
  }

  @Override
  public String getTitle() {
    return IdeBundle.message("step.title.default.plugins");
  }

  @Override
  public String getHTMLHeader() {
    return IdeBundle.message("label.tune.0.to.your.tasks", ApplicationNamesInfo.getInstance().getFullProductName(),
                             ApplicationNamesInfo.getInstance().getFullProductName());
  }

  @Override
  public boolean beforeOkAction() {
    DisabledPluginsState.trySaveDisabledPlugins(myPluginGroups.getDisabledPluginIds());
    return true;
  }

  private final class IdSetPanel extends JPanel implements LinkListener<String> {
    private final JLabel myTitleLabel = new JLabel();
    private final JPanel myContentPanel = new JPanel(new GridLayout(0, 3, 5, 5));
    private String myGroup;

    private IdSetPanel() {
      setLayout(new VerticalFlowLayout(VerticalFlowLayout.TOP, 0, GAP, true, false));
      add(myTitleLabel);
      add(myContentPanel);
      JPanel buttonPanel = new JPanel(new GridBagLayout());
      GridBagConstraints gbc = new GridBagConstraints();
      gbc.insets.right = 25;
      gbc.gridy = 0;
      JButton saveButton = new JButton(IdeBundle.message("button.save.changes.and.go.back"));
      buttonPanel.add(saveButton, gbc);
      buttonPanel.add(new LinkLabel<>(IdeBundle.message("link.enable.all"), null, this, "enable"), gbc);
      buttonPanel.add(new LinkLabel<>(IdeBundle.message("link.disable.all"), null, this, "disable"), gbc);
      gbc.weightx = 1;
      buttonPanel.add(Box.createHorizontalGlue(), gbc);
      add(buttonPanel);
      saveButton.addActionListener(new ActionListener() {
        @Override
        public void actionPerformed(ActionEvent e) {
          myCardLayout.show(CustomizePluginsStepPanel.this, MAIN);
          setButtonsVisible(true);
        }
      });
    }

    @Override
    public void linkSelected(LinkLabel<String> aSource, String command) {
      if (myGroup == null) return;
      boolean enable = "enable".equals(command);
      List<IdSet> idSets = myPluginGroups.getSets(myGroup);
      for (IdSet set : idSets) {
        myPluginGroups.setIdSetEnabled(set, enable);
      }
      CustomizePluginsStepPanel.this.repaint();
    }

    void update(@NonNls String groupId, @Nls String groupName) {
      myGroup = groupId;
      Element titleHtml = text(groupName).bold().wrapWith(html());
      myTitleLabel.setText(titleHtml.toString());
      myContentPanel.removeAll();
      List<IdSet> idSets = myPluginGroups.getSets(groupId);
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

  private static class LinkDescription {
    final @NonNls @NotNull String command;
    final @NonNls @NotNull String groupId;
    final @Nls @NotNull String groupName;

    private LinkDescription(@NonNls @NotNull String command, @NotNull PluginGroups.Group group) {
      this.command = command;
      groupId = group.getId();
      groupName = group.getName();
    }
  }

  private interface TextProvider {
    @NlsContexts.LinkLabel String getText();
  }
}
