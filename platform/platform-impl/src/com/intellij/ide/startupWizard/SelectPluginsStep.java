/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.ide.startupWizard;

import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.ClickListener;
import com.intellij.ui.CollectionListModel;
import com.intellij.ui.wizard.WizardNavigationState;
import com.intellij.ui.wizard.WizardStep;
import com.intellij.util.Function;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * @author yole
 */
public class SelectPluginsStep extends WizardStep<StartupWizardModel> {
  private JPanel myRootPanel;
  private JList myPluginsList;
  private JTextPane myDescriptionArea;
  private JButton myEnableAllButton;
  private JButton myDisableAllButton;
  private final List<IdeaPluginDescriptor> myPlugins = new ArrayList<>();
  private final StartupWizardModel myModel;
  private final String myRequirePlugin;

  private static final String[] ourSuffixes = new String[] { "integration", "support", "plugin" };

  public SelectPluginsStep(final String title, final StartupWizardModel model, final String requirePlugin) {
    super(title, "Select the plugins to enable. Disabling unused plugins will improve IDE startup speed and performance.\n\nTo change plugin settings later, go to " +
                 ShowSettingsUtil.getSettingsMenuName() + " | Plugins.",
          null);
    myModel = model;
    myRequirePlugin = requirePlugin;
    myPluginsList.setCellRenderer(new ListCellRenderer() {
      private final JCheckBox myCheckbox = new JCheckBox();

      public Component getListCellRendererComponent(final JList list,
                                                    final Object value,
                                                    final int index,
                                                    final boolean isSelected,
                                                    final boolean cellHasFocus) {
        IdeaPluginDescriptor descriptor = (IdeaPluginDescriptor)value;
        myCheckbox.setEnabled(!myModel.isForceEnable(descriptor));
        if (isSelected) {
          myCheckbox.setBackground(UIUtil.getListSelectionBackground());
          myCheckbox.setForeground(UIUtil.getListSelectionForeground());
        }
        else {
          myCheckbox.setBackground(UIUtil.getListBackground());
          myCheckbox.setForeground(UIUtil.getListForeground());
        }
        myCheckbox.setText(getAbbreviatedName(descriptor) + buildRequires(descriptor));
        myCheckbox.setSelected(!myModel.isDisabledPlugin(descriptor));
        return myCheckbox;
      }
    });
    myPluginsList.addListSelectionListener(new ListSelectionListener() {
      public void valueChanged(final ListSelectionEvent e) {
        final IdeaPluginDescriptor pluginDescriptor = getSelectedPlugin();
        if (pluginDescriptor != null) {
          final String description = pluginDescriptor.getDescription();
          myDescriptionArea.setText(description == null || description.startsWith("<") ? description : UIUtil.toHtml(description, 5));
          myDescriptionArea.moveCaretPosition(0);
        }
        else {
          myDescriptionArea.setText(UIUtil.toHtml("Select a plugin to see its description"));
        }
      }
    });

    final int clickableArea = new JCheckBox("").getMinimumSize().width;
    new ClickListener() {
      @Override
      public boolean onClick(@NotNull MouseEvent e, int clickCount) {
        if (e.getX() < clickableArea) {
          toggleSelection();
        }
        return true;
      }
    }.installOn(myPluginsList);

    myPluginsList.addKeyListener(new KeyAdapter() {
      public void keyTyped(final KeyEvent e) {
        if (e.getKeyChar() == ' ') {
          toggleSelection();
        }
      }
    });
    
    myEnableAllButton.addActionListener(new ActionListener() {
      public void actionPerformed(final ActionEvent e) {
        setAllPluginsEnabled(true);
      }
    });
    myDisableAllButton.addActionListener(new ActionListener() {
      public void actionPerformed(final ActionEvent e) {
        setAllPluginsEnabled(false);
      }
    });
  }

  private String buildRequires(final IdeaPluginDescriptor descriptor) {
    StringBuilder requiresBuffer = new StringBuilder();
    for (PluginId id : StartupWizardModel.getNonOptionalDependencies(descriptor)) {
      final IdeaPluginDescriptor dependent = findPlugin(id);
      if (dependent != null) {
        String name = getAbbreviatedName(dependent);
        if (requiresBuffer.length() == 0) {
          requiresBuffer.append("   (requires ");
        }
        else {
          requiresBuffer.append(", ");
        }
        requiresBuffer.append(name);
      }
    }
    List<IdeaPluginDescriptor> requiredBy = myModel.getDependentsOnEarlierPages(descriptor, false);
    if (requiredBy.size() > 0) {
      if (requiresBuffer.length() > 0) {
        requiresBuffer.append(", ");
      }
      else {
        requiresBuffer.append("   (");
      }
      requiresBuffer.append("required by ");
      requiresBuffer.append(StringUtil.join(requiredBy, ideaPluginDescriptor -> getAbbreviatedName(ideaPluginDescriptor), ", "));
    }
    if (requiresBuffer.length() > 0) {
      requiresBuffer.append(")");
    }
    return requiresBuffer.toString();
  }

  private static String getAbbreviatedName(final IdeaPluginDescriptor descriptor) {
    final String name = descriptor.getName();
    for (String suffix : ourSuffixes) {
      if (name.toLowerCase().endsWith(suffix)) {
        return name.substring(0, name.length() - suffix.length()).trim();
      }
    }
    return name;
  }

  private void toggleSelection() {
    final IdeaPluginDescriptor descriptor = getSelectedPlugin();
    if (descriptor == null || myModel.isForceEnable(descriptor)) return;
    boolean willDisable = !myModel.isDisabledPlugin(descriptor);
    final Object[] selection = myPluginsList.getSelectedValues();
    for (Object o : selection) {
      IdeaPluginDescriptor desc = (IdeaPluginDescriptor) o;
      if (!willDisable) {
        myModel.setPluginEnabledWithDependencies(desc);
      }
      else {
        myModel.setPluginDisabledWithDependents(desc);
      }
    }
    myPluginsList.repaint();
  }

  private void setAllPluginsEnabled(boolean value) {
    for(IdeaPluginDescriptor descriptor: myPlugins) {
      if (!value && myModel.isForceEnable(descriptor)) {
        continue;
      }
      myModel.setPluginEnabled(descriptor, value);
    }
    myPluginsList.repaint();
  }

  @Nullable
  private IdeaPluginDescriptor findPlugin(final PluginId id) {
    for (IdeaPluginDescriptor plugin : myPlugins) {
      if (plugin.getPluginId().equals(id)) {
        return plugin;
      }
    }
    return null;
  }

  @Nullable
  private IdeaPluginDescriptor getSelectedPlugin() {
    final int leadSelectionIndex = myPluginsList.getSelectionModel().getLeadSelectionIndex();
    return (leadSelectionIndex < 0) ? null : myPlugins.get(leadSelectionIndex);
  }

  public JComponent prepare(final WizardNavigationState state) {
    myRootPanel.revalidate();
    myPluginsList.requestFocusInWindow();
    return myRootPanel;
  }

  public void addPlugin(final IdeaPluginDescriptor pluginDescriptor) {
    myPlugins.add(pluginDescriptor);
  }

  public void fillPlugins() {
    Collections.sort(myPlugins, (o1, o2) -> StringUtil.compare(o1.getName(), o2.getName(), true));
    myPluginsList.setModel(new CollectionListModel(myPlugins));
    myPluginsList.setSelectedIndex(0);
  }

  public String getRequirePlugin() {
    return myRequirePlugin;
  }

  public List<IdeaPluginDescriptor> getPlugins() {
    return myPlugins;
  }

  @Override
  public WizardStep onNext(final StartupWizardModel model) {
    final WizardStep next = super.onNext(model);
    if (next instanceof SelectPluginsStep) {
      final SelectPluginsStep selectPluginsStep = (SelectPluginsStep)next;
      final String id = selectPluginsStep.getRequirePlugin();
      if (id != null && model.getDisabledPluginIds().contains(id) && !model.isLast(next)) {
        for (IdeaPluginDescriptor descriptor: selectPluginsStep.getPlugins()) {
          model.getDisabledPluginIds().add(descriptor.getPluginId().getIdString());
        }
        return model.getNextFor(next);
      }
    }
    return next;
  }

  @Override
  public WizardStep onPrevious(final StartupWizardModel model) {
    final WizardStep prev = super.onPrevious(model);
    if (prev instanceof SelectPluginsStep) {
      final SelectPluginsStep selectPluginsStep = (SelectPluginsStep)prev;
      final String id = selectPluginsStep.getRequirePlugin();
      if (id != null && model.getDisabledPluginIds().contains(id) && !model.isFirst(prev)) {
        return model.getPreviousFor(prev);
      }
    }
    return prev;
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myPluginsList;
  }

  @Override
  public String getHelpId() {
    return "plugin.configuration.wizard";
  }
}
