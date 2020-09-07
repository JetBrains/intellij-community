// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.diagnostic.errordialog;

import com.intellij.diagnostic.DiagnosticBundle;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.text.HtmlBuilder;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBRadioButton;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.JBEmptyBorder;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class PluginConflictDialog extends DialogWrapper {
  public static final int WIDTH = 450;

  @NotNull
  private final List<PluginId> myConflictingPlugins;

  private final boolean myIsConflictWithPlatform;
  @Nullable
  private final List<JBRadioButton> myRadioButtons;

  private JPanel myContentPane;

  private JBLabel myTopMessageLabel;

  private JPanel myConflictingPluginsListPanel;

  public PluginConflictDialog(@NotNull List<PluginId> conflictingPlugins,
                              boolean isConflictWithPlatform) {
    super(false);

    myConflictingPlugins = conflictingPlugins;
    myIsConflictWithPlatform = isConflictWithPlatform;

    if (myIsConflictWithPlatform) {
      myRadioButtons = null;
    }
    else {
      myRadioButtons = new ArrayList<>();
    }

    $$$setupUI$$$();
    setTitle(DiagnosticBundle.message("error.dialog.conflict.plugin.title"));
    init();
    setCrossClosesWindow(false);

    getOKAction().updateText();
    myTopMessageLabel.setText(getTopMessageText(conflictingPlugins, isConflictWithPlatform));

    myTopMessageLabel.setPreferredSize(JBUI.size(WIDTH, (int)myTopMessageLabel.getPreferredSize().getHeight()));
    myContentPane.setPreferredSize(JBUI.size(WIDTH, (int)myContentPane.getMinimumSize().getHeight()));
  }

  private static @NlsContexts.Label String getTopMessageText(@NotNull List<PluginId> conflictingPlugins, boolean isConflictWithPlatform) {
    final int pluginsNumber = conflictingPlugins.size();
    if (isConflictWithPlatform) {
      return DiagnosticBundle.message("error.dialog.conflict.plugin.header.platform", pluginsNumber);
    }
    else {
      final List<String> names = conflictingPlugins.stream()
        .map(PluginConflictDialog::getPluginNameOrId)
        .map(s -> "<b>" + s + "</b>")
        .collect(Collectors.toList());
      return DiagnosticBundle.message("error.dialog.conflict.plugin.header.each.other",
                                      StringUtil.join(names.subList(0, pluginsNumber - 1), ", "),
                                      names.get(pluginsNumber - 1));
    }
  }

  private void $$$setupUI$$$() {
  }

  @Nullable
  @Override
  protected JComponent createCenterPanel() {
    return myContentPane;
  }

  private void createUIComponents() {
    final ButtonGroup buttonGroup = new ButtonGroup();

    myConflictingPluginsListPanel = new JPanel(new GridLayout(0, 1));
    final List<JPanel> pluginDescriptions =
      ContainerUtil.map(myConflictingPlugins, plugin -> getChooserPanelForPlugin(buttonGroup, plugin));
    pluginDescriptions.forEach(myConflictingPluginsListPanel::add);

    if (!myIsConflictWithPlatform) {
      JPanel chooserPanelForPlugin = getChooserPanelForPlugin(buttonGroup, null);
      myConflictingPluginsListPanel.add(chooserPanelForPlugin);
    }

    setUpDefaultSelection();
  }

  @NotNull
  private JPanel getChooserPanelForPlugin(@NotNull ButtonGroup buttonGroup, @Nullable PluginId plugin) {
    final JPanel panel = new JPanel(new BorderLayout());
    if (!myIsConflictWithPlatform) {
      assert myRadioButtons != null;

      final JBRadioButton radioButton = new JBRadioButton();
      myRadioButtons.add(radioButton);
      buttonGroup.add(radioButton);

      radioButton.addChangeListener(e -> getOKAction().updateText());
      panel.add(radioButton, BorderLayout.WEST);
      panel.addMouseListener(new MouseAdapter() {
        @Override
        public void mouseClicked(MouseEvent e) {
          radioButton.setSelected(true);
        }
      });
    }

    final JPanel descriptionPanel;
    if (plugin != null) {
      descriptionPanel = getPluginDescriptionPanel(plugin, !myIsConflictWithPlatform);
    }
    else {
      descriptionPanel = getDisableAllPanel();
    }
    descriptionPanel.setBorder(new JBEmptyBorder(10, myIsConflictWithPlatform ? 10 : 0, 10, 20));

    panel.add(descriptionPanel, BorderLayout.CENTER);
    return panel;
  }

  private void setUpDefaultSelection() {
    if (myIsConflictWithPlatform) {
      return;
    }
    assert myRadioButtons != null && myRadioButtons.size() == myConflictingPlugins.size() + 1;

    for (int i = 0; i < myConflictingPlugins.size(); i++) {
      final IdeaPluginDescriptor pluginDescriptor = PluginManagerCore.getPlugin(myConflictingPlugins.get(i));
      if (pluginDescriptor != null
          && (pluginDescriptor.isBundled() || StringUtil.equalsIgnoreCase(pluginDescriptor.getVendor(), "JetBrains"))) {
        myRadioButtons.get(i).setSelected(true);
        return;
      }
    }
    myRadioButtons.get(myRadioButtons.size() - 1).setSelected(true);
  }

  @NotNull
  private static JPanel getPluginDescriptionPanel(@NotNull PluginId plugin, boolean addUseWord) {
    final JPanel panel = new JPanel(new BorderLayout());

    final IdeaPluginDescriptor pluginDescriptor = PluginManagerCore.getPlugin(plugin);
    if (pluginDescriptor == null) {
      //noinspection HardCodedStringLiteral
      panel.add(new JBLabel(plugin.getIdString()), BorderLayout.CENTER);
      return panel;
    }

    HtmlBuilder message = new HtmlBuilder();
    String vendor = pluginDescriptor.getVendor();
    message.append(DiagnosticBundle.message("plugin.conflict.use.by.vendor.label",
                                            addUseWord ? 0 : 1,
                                            pluginDescriptor.getName(),
                                            vendor != null ? 0 : 1,
                                            vendor));
    panel.add(new JBLabel(message.wrapWithHtmlBody().toString()));
    return panel;
  }

  @NotNull
  private static String getPluginNameOrId(@NotNull PluginId pluginId) {
    final IdeaPluginDescriptor pluginDescriptor = PluginManagerCore.getPlugin(pluginId);
    if (pluginDescriptor == null) {
      return pluginId.getIdString();
    }
    else {
      return pluginDescriptor.getName();
    }
  }

  @NotNull
  private static JPanel getDisableAllPanel() {
    final JPanel panel = new JPanel(new BorderLayout());
    panel.add(new JBLabel(DiagnosticBundle.message("error.dialog.conflict.plugin.disable.all")));
    return panel;
  }

  @Override
  protected Action @NotNull [] createActions() {
    return new Action[]{getOKAction()};
  }

  @Override
  protected void createDefaultActions() {
    super.createDefaultActions();
    myOKAction = new DisableAction();
  }

  @NotNull
  @Override
  protected DisableAction getOKAction() {
    return ((DisableAction)myOKAction);
  }

  private class DisableAction extends DialogWrapperAction {
    protected DisableAction() {
      super(IdeBundle.message("button.disable"));
      putValue(DEFAULT_ACTION, Boolean.TRUE);
    }

    public void updateText() {
      putValue(NAME, getButtonText());
      repaint();
    }

    @NotNull
    private @NlsContexts.Button String getButtonText() {
      if (myIsConflictWithPlatform) {
        return DiagnosticBundle.message("error.dialog.disable.plugin.action.disableAndRestart");
      }

      assert myRadioButtons != null;
      for (int i = 0; i < myConflictingPlugins.size(); ++i) {
        if (myRadioButtons.get(i).isSelected()) {
          return DiagnosticBundle.message("error.dialog.conflict.plugin.button.enable.and.restart");
        }
      }
      return DiagnosticBundle.message("error.dialog.conflict.plugin.button.disable.all");
    }

    @Override
    protected void doAction(ActionEvent e) {
      for (int i = 0; i < myConflictingPlugins.size(); ++i) {
        if (myRadioButtons == null || !myRadioButtons.get(i).isSelected()) {
          PluginManagerCore.disablePlugin(myConflictingPlugins.get(i));
        }
      }
      close(OK_EXIT_CODE);
      ApplicationManagerEx.getApplicationEx().restart(true);
    }
  }
}
