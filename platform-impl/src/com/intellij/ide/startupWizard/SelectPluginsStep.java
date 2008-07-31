package com.intellij.ide.startupWizard;

import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.ui.CollectionListModel;
import com.intellij.ui.wizard.WizardNavigationState;
import com.intellij.ui.wizard.WizardStep;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.*;
import java.util.List;

/**
 * @author yole
 */
public class SelectPluginsStep extends WizardStep<StartupWizardModel> {
  private JPanel myRootPanel;
  private JList myPluginsList;
  private JTextArea myDescriptionArea;
  private JButton myEnableAllButton;
  private JButton myDisableAllButton;
  private final List<IdeaPluginDescriptor> myPlugins = new ArrayList<IdeaPluginDescriptor>();
  private Set<String> myDisabledPluginIds;
  private final String myRequirePlugin;

  private static String[] ourSuffixes = new String[] { "integration", "support", "plugin" };

  public SelectPluginsStep(final String title, final Set<String> disabledPluginIds, final String requirePlugin) {
    super(title, "Select the plugins to enable. Disabling unused plugins will improve IDE startup speed and performance.\n\nTo change plugin settings later, go to Settings | Plugins.");
    myDisabledPluginIds = disabledPluginIds;
    myRequirePlugin = requirePlugin;
    myPluginsList.setCellRenderer(new ListCellRenderer() {
      private JCheckBox myCheckbox = new JCheckBox();

      public Component getListCellRendererComponent(final JList list,
                                                    final Object value,
                                                    final int index,
                                                    final boolean isSelected,
                                                    final boolean cellHasFocus) {
        if (isSelected) {
          myCheckbox.setBackground(UIUtil.getListSelectionBackground());
          myCheckbox.setForeground(UIUtil.getListSelectionForeground());
        }
        else {
          myCheckbox.setBackground(UIUtil.getListBackground());
          myCheckbox.setForeground(UIUtil.getListForeground());
        }
        IdeaPluginDescriptor descriptor = (IdeaPluginDescriptor)value;
        myCheckbox.setText(getAbbreviatedName(descriptor) + buildRequires(descriptor));
        myCheckbox.setSelected(!isDisabledPlugin(descriptor));
        return myCheckbox;
      }
    });
    myPluginsList.addListSelectionListener(new ListSelectionListener() {
      public void valueChanged(final ListSelectionEvent e) {
        final IdeaPluginDescriptor pluginDescriptor = getSelectedPlugin();
        if (pluginDescriptor != null) {
          final String description = pluginDescriptor.getDescription();
          myDescriptionArea.setText(description);
          myDescriptionArea.moveCaretPosition(0);
        }
        else {
          myDescriptionArea.setText("Select a plugin to see its description");
        }
      }
    });

    final int checkboxWidth = new JCheckBox().getPreferredSize().width;

    myPluginsList.addMouseListener(new MouseAdapter() {
      @Override
      public void mouseClicked(final MouseEvent e) {
        final int idx = myPluginsList.locationToIndex(e.getPoint());
        if (idx >= 0) {
          final Rectangle baseRect = myPluginsList.getCellBounds(idx, idx);
          baseRect.setSize(checkboxWidth, baseRect.height);
          if (baseRect.contains(e.getPoint())) {
            toggleSelection();
            e.consume();
          }
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
    StringBuffer requiresBuffer = new StringBuffer();
    for (PluginId id : getNonOptionalDependencies(descriptor)) {
      final IdeaPluginDescriptor dependent = findPlugin(id);
      if (dependent != null) {
        String name = getAbbreviatedName(dependent);
        if (requiresBuffer.length() == 0) {
          requiresBuffer.append(" (requires ");
        }
        else {
          requiresBuffer.append(", ");
        }
        requiresBuffer.append(name);
      }
    }
    if (requiresBuffer.length() > 0) {
      requiresBuffer.append(")");
    }
    return requiresBuffer.toString();
  }

  private static List<PluginId> getNonOptionalDependencies(final IdeaPluginDescriptor descriptor) {
    List<PluginId> result = new ArrayList<PluginId>();
    for (PluginId pluginId : descriptor.getDependentPluginIds()) {
      if (pluginId.getIdString().equals("com.intellij")) continue;
      if (!ArrayUtil.contains(pluginId, descriptor.getOptionalDependentPluginIds())) {
        result.add(pluginId);
      }
    }
    return result;
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

  private boolean isDisabledPlugin(final IdeaPluginDescriptor descriptor) {
    return myDisabledPluginIds.contains(descriptor.getPluginId().toString());
  }

  private void toggleSelection() {
    final IdeaPluginDescriptor descriptor = getSelectedPlugin();
    if (descriptor == null) return;
    boolean willDisable = !isDisabledPlugin(descriptor);
    final Object[] selection = myPluginsList.getSelectedValues();
    for (Object o : selection) {
      IdeaPluginDescriptor desc = (IdeaPluginDescriptor) o;
      if (!willDisable) {
        setPluginEnabledWithDependencies(desc);
      }
      else {
        setPluginDisabledWithDependents(desc);
      }
    }
    myPluginsList.repaint();
  }

  private void setPluginDisabledWithDependents(final IdeaPluginDescriptor desc) {
    setPluginEnabled(desc, false);
    for (IdeaPluginDescriptor plugin : myPlugins) {
      if (ArrayUtil.contains(desc.getPluginId(), plugin.getDependentPluginIds()) &&
          !ArrayUtil.contains(desc.getPluginId(), plugin.getOptionalDependentPluginIds())) {
        setPluginDisabledWithDependents(plugin);
      }
    }
  }

  private void setAllPluginsEnabled(boolean value) {
    for(IdeaPluginDescriptor descriptor: myPlugins) {
      setPluginEnabled(descriptor, value);
    }
    myPluginsList.repaint();
  }

  private void setPluginEnabledWithDependencies(final IdeaPluginDescriptor desc) {
    setPluginEnabled(desc, true);
    for(PluginId id: getNonOptionalDependencies(desc)) {
      final IdeaPluginDescriptor dependent = findPlugin(id);
      if (dependent != null) {
        setPluginEnabledWithDependencies(dependent);
      }
    }
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

  private void setPluginEnabled(final IdeaPluginDescriptor desc, boolean value) {
    if (value) {
      myDisabledPluginIds.remove(desc.getPluginId().toString());
    }
    else {
      myDisabledPluginIds.add(desc.getPluginId().toString());
    }
  }

  @Nullable
  private IdeaPluginDescriptor getSelectedPlugin() {
    final int leadSelectionIndex = myPluginsList.getSelectionModel().getLeadSelectionIndex();
    return (leadSelectionIndex < 0) ? null : myPlugins.get(leadSelectionIndex);
  }

  public JComponent prepare(final WizardNavigationState state) {
    myRootPanel.revalidate();
    return myRootPanel;
  }

  public void addPlugin(final IdeaPluginDescriptor pluginDescriptor) {
    myPlugins.add(pluginDescriptor);
  }

  public void fillPlugins() {
    Collections.sort(myPlugins, new Comparator<IdeaPluginDescriptor>() {
      public int compare(final IdeaPluginDescriptor o1, final IdeaPluginDescriptor o2) {
        return o1.getName().compareTo(o2.getName());
      }
    });
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
      if (id != null && model.getDisabledPluginIds().contains(id)) {
        for (IdeaPluginDescriptor descriptor: selectPluginsStep.getPlugins()) {
          model.getDisabledPluginIds().add(descriptor.getPluginId().getIdString());
        }
        return model.getNextFor(next);
      }
    }
    return next;
  }
}
