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
package com.intellij.ide.plugins;

import com.intellij.ide.IdeBundle;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.application.ex.ApplicationInfoEx;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.updateSettings.impl.PluginDownloader;
import com.intellij.openapi.updateSettings.impl.UpdateChecker;
import com.intellij.openapi.updateSettings.impl.UpdateSettings;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.JDOMExternalizableStringList;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.BooleanTableCellEditor;
import com.intellij.ui.BooleanTableCellRenderer;
import com.intellij.ui.SideBorder;
import com.intellij.util.Function;
import com.intellij.util.containers.hash.HashSet;
import com.intellij.util.ui.ColumnInfo;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableCellRenderer;
import java.awt.*;
import java.util.*;
import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: stathik
 * Date: Dec 26, 2003
 * Time: 3:51:58 PM
 * To change this template use Options | File Templates.
 */
public class InstalledPluginsTableModel extends PluginTableModel {
  public static Map<PluginId, Integer> NewVersions2Plugins = new HashMap<PluginId, Integer>();
  public static Set<PluginId> updatedPlugins = new HashSet<PluginId>();
  private final Map<PluginId, Boolean> myEnabled = new HashMap<PluginId, Boolean>();
  private final Map<PluginId, Set<PluginId>> myDependentToRequiredListMap = new HashMap<PluginId, Set<PluginId>>();

  private static final String ENABLED_DISABLED = "All plugins";
  private static final String ENABLED = "Enabled plugins";
  private static final String DISABLED = "Disabled plugins";
  public static final String[] ENABLED_VALUES = new String[] {ENABLED_DISABLED, ENABLED, DISABLED};
  private String myEnabledFilter = ENABLED_DISABLED;

  private static final String BUNDLED_NONBUNDLED = "All";
  private static final String BUNDLED = "Yes";
  private static final String NON_BUNDLED = "No";
  public static final String[] BUNDLED_VALUES = new String[] {BUNDLED_NONBUNDLED, BUNDLED, NON_BUNDLED};
  private String myBundledFilter = BUNDLED_NONBUNDLED;
  private boolean myBundledEnabled = false;
  private final Map<String, String> myPlugin2host = new HashMap<String, String>();


  public InstalledPluginsTableModel() {
    super.columns = new ColumnInfo[]{new EnabledPluginInfo(), new MyPluginManagerColumnInfo()};
    view = new ArrayList<IdeaPluginDescriptor>(Arrays.asList(PluginManager.getPlugins()));
    reset(view);

    ApplicationInfoEx applicationInfo = ApplicationInfoEx.getInstanceEx();
    for (Iterator<IdeaPluginDescriptor> iterator = view.iterator(); iterator.hasNext(); ) {
      @NonNls final String s = iterator.next().getPluginId().getIdString();
      if ("com.intellij".equals(s) || applicationInfo.isEssentialPlugin(s)) iterator.remove();
    }

    setSortKey(new RowSorter.SortKey(getNameColumn(), SortOrder.ASCENDING));
  }

  public static int getCheckboxColumn() {
    return 0;
  }

  public int getNameColumn() {
    return 1;
  }

  private void reset(final List<IdeaPluginDescriptor> list) {
    for (IdeaPluginDescriptor ideaPluginDescriptor : list) {
      if (ideaPluginDescriptor instanceof IdeaPluginDescriptorImpl) {
        setEnabled(ideaPluginDescriptor);
      }
      myBundledEnabled |= !ideaPluginDescriptor.isBundled();
    }

    updatePluginDependencies();

    updateRepositoryPlugins();
  }

  public void updateRepositoryPlugins() {
    myPlugin2host.clear();
    final JDOMExternalizableStringList pluginHosts = UpdateSettings.getInstance().myPluginHosts;
    for (String host : pluginHosts) {
      try {
        final ArrayList<PluginDownloader> downloaded = new ArrayList<PluginDownloader>();
        UpdateChecker.checkPluginsHost(host, downloaded, false);
        for (PluginDownloader downloader : downloaded) {
          myPlugin2host.put(downloader.getPluginId(), host);
        }
      }
      catch (Exception ignored) {
      }
    }
  }

  private void setEnabled(IdeaPluginDescriptor ideaPluginDescriptor) {
    final Collection<String> disabledPlugins = PluginManager.getDisabledPlugins();
    final boolean enabled = ((IdeaPluginDescriptorImpl)ideaPluginDescriptor).isEnabled();
    final PluginId pluginId = ideaPluginDescriptor.getPluginId();
    if (!enabled && !disabledPlugins.contains(pluginId.toString())) {
      myEnabled.put(pluginId, null);
    } else {
      myEnabled.put(pluginId, enabled);
    }
  }

  public Map<PluginId, Set<PluginId>> getDependentToRequiredListMap() {
    return myDependentToRequiredListMap;
  }

  private void updatePluginDependencies() {
    myDependentToRequiredListMap.clear();

    final int rowCount = getRowCount();
    for (int i = 0; i < rowCount; i++) {
      final IdeaPluginDescriptor descriptor = getObjectAt(i);
      final PluginId pluginId = descriptor.getPluginId();
      myDependentToRequiredListMap.remove(pluginId);
      final Boolean enabled = myEnabled.get(pluginId);
      if (enabled == null || enabled.booleanValue()) {
        PluginManager.checkDependants(descriptor, new Function<PluginId, IdeaPluginDescriptor>() {
          @Nullable
          public IdeaPluginDescriptor fun(final PluginId pluginId) {
            return PluginManager.getPlugin(pluginId);
          }
        }, new Condition<PluginId>() {
          public boolean value(final PluginId pluginId) {
            final Boolean enabled = myEnabled.get(pluginId);
            if (enabled == null || !enabled.booleanValue()) {
              Set<PluginId> required = myDependentToRequiredListMap.get(descriptor.getPluginId());
              if (required == null) {
                required = new HashSet<PluginId>();
                myDependentToRequiredListMap.put(descriptor.getPluginId(), required);
              }

              required.add(pluginId);
              //return false;
            }

            return true;
          }
        });
      }
    }
  }

  public void updatePluginsList(List<IdeaPluginDescriptor> list) {
    //  For each downloadable plugin we need to know whether its counterpart
    //  is already installed, and if yes compare the difference in versions:
    //  availability of newer versions will be indicated separately.
    for (IdeaPluginDescriptor descr : list) {
      PluginId descrId = descr.getPluginId();
      IdeaPluginDescriptor existing = PluginManager.getPlugin(descrId);
      if (existing != null) {
        if (descr instanceof PluginNode) {
          updateExistingPluginInfo(descr, existing);
        } else {
          view.add(descr);
          setEnabled(descr);
        }
      }
    }

    fireTableDataChanged();
  }

  @Override
  public void filter(final List<IdeaPluginDescriptor> filtered) {
    view.clear();
    for (IdeaPluginDescriptor descriptor : filtered) {
      if (PluginManager.getPlugin(descriptor.getPluginId()) != null) {
        view.add(descriptor);
      }
    }

    super.filter(filtered);
  }

  private static void updateExistingPluginInfo(IdeaPluginDescriptor descr, IdeaPluginDescriptor existing) {
    int state = StringUtil.compareVersionNumbers(descr.getVersion(), existing.getVersion());
    final PluginId pluginId = existing.getPluginId();
    final String idString = pluginId.getIdString();
    final JDOMExternalizableStringList installedPlugins = PluginManagerUISettings.getInstance().myInstalledPlugins;
    if (!installedPlugins.contains(idString)){
      installedPlugins.add(idString);
    }
    final PluginManagerUISettings updateSettings = PluginManagerUISettings.getInstance();
    if (state > 0 && !PluginManager.isIncompatible(descr) && !updatedPlugins.contains(descr.getPluginId())) {
      NewVersions2Plugins.put(pluginId, 1);
      if (!updateSettings.myOutdatedPlugins.contains(idString)) {
        updateSettings.myOutdatedPlugins.add(idString);
      }

      final IdeaPluginDescriptorImpl plugin = (IdeaPluginDescriptorImpl)existing;
      plugin.setDownloadsCount(descr.getDownloads());
      plugin.setVendor(descr.getVendor());
      plugin.setVendorEmail(descr.getVendorEmail());
      plugin.setVendorUrl(descr.getVendorUrl());
      plugin.setUrl(descr.getUrl());

    } else {
      updateSettings.myOutdatedPlugins.remove(idString);
      if (NewVersions2Plugins.remove(pluginId) != null) {
        updatedPlugins.add(pluginId);
      }
    }
  }

  public void enableRows(IdeaPluginDescriptor[] ideaPluginDescriptors, Boolean value) {
    for (IdeaPluginDescriptor ideaPluginDescriptor : ideaPluginDescriptors) {
      final PluginId currentPluginId = ideaPluginDescriptor.getPluginId();
      final Boolean enabled = myEnabled.get(currentPluginId) == null ? Boolean.FALSE : value;
      myEnabled.put(currentPluginId, enabled);
    }
    updatePluginDependencies();
    warnAboutMissedDependencies(value, ideaPluginDescriptors);
    if (!value && ENABLED.equals(myEnabledFilter)) {
      for (IdeaPluginDescriptor ideaPluginDescriptor : ideaPluginDescriptors) {
        view.remove(ideaPluginDescriptor);
        filtered.add(ideaPluginDescriptor);
      }
      fireTableDataChanged();
    }
  }


  public static boolean hasNewerVersion(PluginId descr) {
    return NewVersions2Plugins.containsKey(descr) ||
           PluginManagerUISettings.getInstance().myOutdatedPlugins.contains(descr.getIdString());
  }

  public static boolean wasUpdated(PluginId descr) {
    return updatedPlugins.contains(descr);
  }

  public boolean isEnabled(final PluginId pluginId) {
    final Boolean enabled = myEnabled.get(pluginId);
    return enabled != null && enabled.booleanValue();
  }

  public Map<PluginId, Boolean> getEnabledMap() {
    return myEnabled;
  }

  public String getEnabledFilter() {
    return myEnabledFilter;
  }

  public void setEnabledFilter(String enabledFilter, String filter) {
    myEnabledFilter = enabledFilter;
    filter(filter);
  }

  public String getBundledFilter() {
    return myBundledFilter;
  }

  public void setBundledFilter(String bundledFilter, String filter) {
    myBundledFilter = bundledFilter;
    filter(filter);
  }

  public boolean isBundledEnabled() {
    return myBundledEnabled;
  }

  @Override
  public boolean isPluginDescriptorAccepted(IdeaPluginDescriptor descriptor) {
    if (myEnabledFilter != ENABLED_DISABLED) {
      final boolean enabled = isEnabled(descriptor.getPluginId());
      if (enabled && myEnabledFilter == DISABLED) return false;
      if (!enabled && myEnabledFilter == ENABLED) return false;
    }
    if (myBundledFilter != BUNDLED_NONBUNDLED) {
      final boolean bundled = descriptor.isBundled();
      if (bundled && myBundledFilter == NON_BUNDLED) return false;
      if (!bundled && myBundledFilter == BUNDLED) return false;
    }
    return true;
  }

  private class EnabledPluginInfo extends ColumnInfo<IdeaPluginDescriptorImpl, Boolean> {

    public EnabledPluginInfo() {
      super(IdeBundle.message("plugin.manager.enable.column.title"));
    }

    public Boolean valueOf(IdeaPluginDescriptorImpl ideaPluginDescriptor) {
      return myEnabled.get(ideaPluginDescriptor.getPluginId());
    }

    public boolean isCellEditable(final IdeaPluginDescriptorImpl ideaPluginDescriptor) {
      return true;
    }

    public Class getColumnClass() {
      return Boolean.class;
    }

    public TableCellEditor getEditor(final IdeaPluginDescriptorImpl o) {
      return new BooleanTableCellEditor();
    }

    public TableCellRenderer getRenderer(final IdeaPluginDescriptorImpl ideaPluginDescriptor) {
      return new BooleanTableCellRenderer() {
        @Override
        public Component getTableCellRendererComponent(JTable table,
                                                       Object value,
                                                       boolean isSelected,
                                                       boolean hasFocus,
                                                       int row,
                                                       int column) {
          return super.getTableCellRendererComponent(table, value == null ? Boolean.TRUE : value, isSelected, hasFocus, row, column);
        }
      };
    }

    public void setValue(final IdeaPluginDescriptorImpl ideaPluginDescriptor, Boolean value) {
      final PluginId currentPluginId = ideaPluginDescriptor.getPluginId();
      final Boolean enabled = myEnabled.get(currentPluginId) == null ? Boolean.FALSE : value;
      myEnabled.put(currentPluginId, enabled);
      updatePluginDependencies();
      warnAboutMissedDependencies(enabled, ideaPluginDescriptor);
      if (!value && ENABLED.equals(myEnabledFilter)) {
        view.remove(ideaPluginDescriptor);
        filtered.add(ideaPluginDescriptor);
        fireTableDataChanged();
      }
    }

    public Comparator<IdeaPluginDescriptorImpl> getComparator() {
      return new Comparator<IdeaPluginDescriptorImpl>() {
        public int compare(final IdeaPluginDescriptorImpl o1, final IdeaPluginDescriptorImpl o2) {
          final Boolean enabled1 = myEnabled.get(o1.getPluginId());
          final Boolean enabled2 = myEnabled.get(o2.getPluginId());
          if (enabled1 != null && enabled1.booleanValue()) {
            if (enabled2 != null && enabled2.booleanValue()) {
              return 0;
            }

            return 1;
          }
          else {
            if (enabled2 == null || !enabled2.booleanValue()) {
              return 0;
            }
            return -1;
          }
        }
      };
    }
  }

  private void warnAboutMissedDependencies(final Boolean newVal, final IdeaPluginDescriptor... ideaPluginDescriptors) {
    final Set<PluginId> deps = new HashSet<PluginId>();
    final List<IdeaPluginDescriptor> descriptorsToCheckDependencies = new ArrayList<IdeaPluginDescriptor>();
    if (newVal) {
      Collections.addAll(descriptorsToCheckDependencies, ideaPluginDescriptors);
    } else {
      descriptorsToCheckDependencies.addAll(view);
      descriptorsToCheckDependencies.addAll(filtered);
      descriptorsToCheckDependencies.removeAll(Arrays.asList(ideaPluginDescriptors));

      for (Iterator<IdeaPluginDescriptor> iterator = descriptorsToCheckDependencies.iterator(); iterator.hasNext(); ) {
        IdeaPluginDescriptor descriptor = iterator.next();
        final Boolean enabled = myEnabled.get(descriptor.getPluginId());
        if (enabled == null || !enabled.booleanValue()) {
          iterator.remove();
        }
      }
    }

    for (final IdeaPluginDescriptor ideaPluginDescriptor : descriptorsToCheckDependencies) {
      PluginManager.checkDependants(ideaPluginDescriptor, new Function<PluginId, IdeaPluginDescriptor>() {
                                      @Nullable
                                      public IdeaPluginDescriptor fun(final PluginId pluginId) {
                                        return PluginManager.getPlugin(pluginId);
                                      }
                                    }, new Condition<PluginId>() {
        public boolean value(final PluginId pluginId) {
          Boolean enabled = myEnabled.get(pluginId);
          if (enabled == null) {
            return false;
          }
          if (newVal && !enabled.booleanValue()) {
            deps.add(pluginId);
          }

          if (!newVal) {
            final PluginId pluginDescriptorId = ideaPluginDescriptor.getPluginId();
            for (IdeaPluginDescriptor descriptor : ideaPluginDescriptors) {
              if (pluginId.equals(descriptor.getPluginId())) {
                deps.add(pluginDescriptorId);
                break;
              }
            }
          }
          return true;
        }
      }
      );
    }
    if (!deps.isEmpty()) {
      final String listOfSelectedPlugins = StringUtil.join(ideaPluginDescriptors, new Function<IdeaPluginDescriptor, String>() {
        @Override
        public String fun(IdeaPluginDescriptor pluginDescriptor) {
          return pluginDescriptor.getName();
        }
      }, ", ");
      final String listOfDependencies = StringUtil.join(deps, new Function<PluginId, String>() {
        public String fun(final PluginId pluginId) {
          final IdeaPluginDescriptor pluginDescriptor = PluginManager.getPlugin(pluginId);
          assert pluginDescriptor != null;
          return pluginDescriptor.getName();
        }
      }, "<br>");
      final String message = !newVal ? "<html>The following plugins <br>" + listOfDependencies + "<br>are enabled and depend" +(deps.size() == 1 ? "s" : "") + " on selected plugins. " +
                                       "<br>Would you like to disable them too?</html>"
                                     : "<html>The following plugins on which " + listOfSelectedPlugins + " depend" + (ideaPluginDescriptors.length == 1 ? "s" : "") +
                                       " are disabled:<br>" + listOfDependencies + "<br>Would you like to enable them?</html>";
      if (Messages.showOkCancelDialog(message, newVal ? "Enable Dependant Plugins" : "Disable Plugins with Dependency on this", Messages.getQuestionIcon()) == DialogWrapper.OK_EXIT_CODE) {
        for (PluginId pluginId : deps) {
          myEnabled.put(pluginId, newVal);
        }

        updatePluginDependencies();
      }
    }
  }

  private class InstalledPluginsTableRenderer extends DefaultTableCellRenderer {

    private JLabel myNameLabel = new JLabel();
    private JLabel myBundledLabel = new JLabel();
    private JPanel myPanel = new JPanel(new GridBagLayout());

    private final IdeaPluginDescriptor myPluginDescriptor;

    public InstalledPluginsTableRenderer(IdeaPluginDescriptor pluginDescriptor) {
      myPluginDescriptor = pluginDescriptor;

      myNameLabel.setFont(PluginManagerColumnInfo.getNameFont());
      myBundledLabel.setFont(UIUtil.getLabelFont(UIUtil.FontSize.SMALL));
      myPanel.setBorder(new SideBorder(Color.lightGray, SideBorder.BOTTOM, true));
      
      final GridBagConstraints gn =
        new GridBagConstraints(GridBagConstraints.RELATIVE, 0, 1, 1, 0, 0, GridBagConstraints.NORTHWEST, GridBagConstraints.NONE,
                               new Insets(0, 0, 0, 0), 0, 0);
      myPanel.add(myNameLabel, gn);
      gn.insets.left = 5;
      gn.anchor = GridBagConstraints.NORTHWEST;
      gn.weightx = 1;
      gn.fill = GridBagConstraints.HORIZONTAL;
      myPanel.add(Box.createHorizontalBox(), gn);
      gn.fill = GridBagConstraints.NONE;
      gn.weightx = 0;
      gn.anchor = GridBagConstraints.EAST;
      myPanel.add(myBundledLabel, gn);
    }

    @Override
    public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
      final Component orig = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
      if (myPluginDescriptor != null) {
        myNameLabel.setText(myPluginDescriptor.getName());
        final String idString = myPluginDescriptor.getPluginId().getIdString();
        if (myPluginDescriptor.isBundled()) {
          myBundledLabel.setText("Bundled");
        } else {
          final String host = myPlugin2host.get(idString);
          if (host != null) {
            myBundledLabel.setText("From: " + host);
          } else {
            if (PluginManagerUISettings.getInstance().myInstalledPlugins.contains(idString)) {
              myBundledLabel.setText("From repository");
            } else {
              myBundledLabel.setText("Custom");
            }
          }
        }
        final IdeaPluginDescriptorImpl descriptor = (IdeaPluginDescriptorImpl)myPluginDescriptor;
        if (descriptor.isDeleted()) {
          myNameLabel.setIcon(IconLoader.getIcon("/actions/clean.png"));
        }
        else if (hasNewerVersion(myPluginDescriptor.getPluginId())) {
          myNameLabel.setIcon(IconLoader.getIcon("/nodes/pluginobsolete.png"));
          myPanel.setToolTipText("Newer version of the plugin is available");
        }
        else {
          myNameLabel.setIcon(IconLoader.getIcon("/nodes/plugin.png"));
        }

        final Color fg = orig.getForeground();
        final Color bg = orig.getBackground();
        final Color grayedFg = isSelected ? fg : Color.GRAY;

        myPanel.setBackground(bg);
        myNameLabel.setBackground(bg);
        myBundledLabel.setBackground(bg);

        myNameLabel.setForeground(fg);
        myBundledLabel.setForeground(grayedFg);

        final PluginId pluginId = myPluginDescriptor.getPluginId();
        final Set<PluginId> required = myDependentToRequiredListMap.get(pluginId);
        if (required != null && required.size() > 0) {
          myNameLabel.setForeground(Color.RED);

          final StringBuilder s = new StringBuilder();
          if (myEnabled.get(pluginId) == null) {
            s.append("Plugin was not loaded.\n");
          }
          s.append("Required plugin").append(required.size() == 1 ? " \"" : "s \"");
          s.append(StringUtil.join(required, new Function<PluginId, String>() {
            @Override
            public String fun(final PluginId id) {
              final IdeaPluginDescriptor plugin = PluginManager.getPlugin(id);
              return plugin == null ? id.getIdString() : plugin.getName();
            }
          }, ","));

          s.append(required.size() == 1 ? "\" is not enabled!" : "\" are not enabled!");
          myPanel.setToolTipText(s.toString());
        }

        if (PluginManager.isIncompatible(myPluginDescriptor)) {
          myPanel.setToolTipText(
            IdeBundle.message("plugin.manager.incompatible.tooltip.warning", ApplicationNamesInfo.getInstance().getFullProductName()));
          myNameLabel.setForeground(Color.red);
        }
      }

      return myPanel;
    }
  }

  private class MyPluginManagerColumnInfo extends PluginManagerColumnInfo {
    public MyPluginManagerColumnInfo() {
      super(PluginManagerColumnInfo.COLUMN_NAME, InstalledPluginsTableModel.this);
    }

    @Override
    public TableCellRenderer getRenderer(final IdeaPluginDescriptor pluginDescriptor) {
      return new InstalledPluginsTableRenderer(pluginDescriptor);
    }

    @Override
    protected boolean isSortByName() {
      return true;
    }

    /*@Override
    public Comparator<IdeaPluginDescriptor> getComparator() {
      final Comparator<IdeaPluginDescriptor> comparator = super.getComparator();
        return new Comparator<IdeaPluginDescriptor>() {
          @Override
          public int compare(IdeaPluginDescriptor o1, IdeaPluginDescriptor o2) {
            if (o1.isBundled() && o2.isBundled()) return comparator.compare(o1, o2);
            if (o1.isBundled()) return -1;
            if (o2.isBundled()) return 1;
            final String host1 = myPlugin2host.get(o1.getPluginId().getIdString());
            final String host2 = myPlugin2host.get(o2.getPluginId().getIdString());
            if (host1 == null && host2 == null) return comparator.compare(o1, o2);
            if (host1 == null) return 1;
            if (host2 == null) return -1;
            if (host1.equals(host2)) return comparator.compare(o1, o2);
            return host1.compareToIgnoreCase(host2);
          }
        };

    }*/
  }
}
