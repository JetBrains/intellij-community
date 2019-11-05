// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins;

import com.intellij.openapi.application.ex.ApplicationInfoEx;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.nio.file.FileVisitResult;
import java.util.*;

/**
 * @author stathik
 */
public class InstalledPluginsTableModel extends PluginTableModel {
  private static final InstalledPluginsState ourState = InstalledPluginsState.getInstance();

  private final Map<PluginId, Boolean> myEnabled = new HashMap<>();
  private final Map<PluginId, Set<PluginId>> myDependentToRequiredListMap = new HashMap<>();

  public InstalledPluginsTableModel() {
    ApplicationInfoEx appInfo = ApplicationInfoEx.getInstanceEx();
    for (IdeaPluginDescriptor plugin : PluginManagerCore.getPlugins()) {
      if (appInfo.isEssentialPlugin(plugin.getPluginId())) {
        myEnabled.put(plugin.getPluginId(), true);
      }
      else {
        view.add(plugin);
      }
    }
    view.addAll(ourState.getInstalledPlugins());

    for (IdeaPluginDescriptor descriptor : view) {
      setEnabled(descriptor, descriptor.isEnabled());
    }
    updatePluginDependencies();

    setSortKey(new RowSorter.SortKey(getNameColumn(), SortOrder.ASCENDING));
  }

  public boolean hasProblematicDependencies(PluginId pluginId) {
    final Set<PluginId> ids = myDependentToRequiredListMap.get(pluginId);
    return ids != null && !ids.isEmpty();
  }

  @Nullable
  public Set<PluginId> getRequiredPlugins(PluginId pluginId) {
    return myDependentToRequiredListMap.get(pluginId);
  }

  public boolean isLoaded(PluginId pluginId) {
    return myEnabled.get(pluginId) != null;
  }

  public void appendOrUpdateDescriptor(@NotNull IdeaPluginDescriptor descriptor, boolean restartNeeded) {
    PluginId id = descriptor.getPluginId();
    if (!PluginManagerCore.isPluginInstalled(id)) {
      int i = view.indexOf(descriptor);
      if (i < 0) {
        view.add(descriptor);
      }
      else {
        view.set(i, descriptor);
      }

      setEnabled(descriptor, true);
      fireTableDataChanged();
    }
  }

  @Override
  public int getNameColumn() {
    return 0;
  }

  private void setEnabled(IdeaPluginDescriptor ideaPluginDescriptor, boolean enabled) {
    PluginId pluginId = ideaPluginDescriptor.getPluginId();
    if (!enabled && !PluginManagerCore.isDisabled(pluginId)) {
      myEnabled.put(pluginId, null);
    }
    else {
      myEnabled.put(pluginId, enabled);
    }
  }

  public Map<PluginId, Set<PluginId>> getDependentToRequiredListMap() {
    return myDependentToRequiredListMap;
  }

  protected void updatePluginDependencies() {
    myDependentToRequiredListMap.clear();

    int rowCount = getRowCount();
    Map<PluginId, IdeaPluginDescriptorImpl> pluginIdMap = null;
    for (int i = 0; i < rowCount; i++) {
      final IdeaPluginDescriptor rootDescriptor = getObjectAt(i);
      final PluginId pluginId = rootDescriptor.getPluginId();
      myDependentToRequiredListMap.remove(pluginId);
      if (rootDescriptor instanceof IdeaPluginDescriptorImpl && ((IdeaPluginDescriptorImpl)rootDescriptor).isDeleted()) {
        continue;
      }

      Boolean enabled = myEnabled.get(pluginId);
      if (enabled != null && !enabled.booleanValue()) {
        continue;
      }

      if (pluginIdMap == null) {
        pluginIdMap = PluginManagerCore.buildPluginIdMap();
      }

      PluginManagerCore.processAllDependencies(rootDescriptor, false, pluginIdMap, descriptor -> {
        PluginId depId = descriptor.getPluginId();
        if (depId.equals(pluginId)) {
          return FileVisitResult.CONTINUE;
        }

        Boolean enabled1 = myEnabled.get(depId);
        if ((enabled1 == null && !ourState.wasUpdated(depId)) ||
            (enabled1 != null && !enabled1.booleanValue())) {
          Set<PluginId> required = myDependentToRequiredListMap.get(pluginId);
          if (required == null) {
            required = new HashSet<>();
            myDependentToRequiredListMap.put(pluginId, required);
          }
          required.add(depId);
        }

        return FileVisitResult.CONTINUE;
      });

      if (enabled == null && !myDependentToRequiredListMap.containsKey(pluginId) && PluginManagerCore.isCompatible(rootDescriptor)) {
        myEnabled.put(pluginId, true);
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
  }

  public boolean isEnabled(final PluginId pluginId) {
    final Boolean enabled = myEnabled.get(pluginId);
    return enabled != null && enabled.booleanValue();
  }

  public boolean isDisabled(@NotNull final PluginId pluginId) {
    final Boolean enabled = myEnabled.get(pluginId);
    return enabled != null && !enabled.booleanValue();
  }

  public Map<PluginId, Boolean> getEnabledMap() {
    return myEnabled;
  }

  private void warnAboutMissedDependencies(final Boolean newEnabledState, final IdeaPluginDescriptor... descriptorsWithChangedEnabledState) {
    List<IdeaPluginDescriptor> descriptorsToCheckDependencies = new ArrayList<>();
    if (newEnabledState) {
      Collections.addAll(descriptorsToCheckDependencies, descriptorsWithChangedEnabledState);
    }
    else {
      descriptorsToCheckDependencies.addAll(getAllPlugins());
      descriptorsToCheckDependencies.removeAll(Arrays.asList(descriptorsWithChangedEnabledState));

      for (Iterator<IdeaPluginDescriptor> iterator = descriptorsToCheckDependencies.iterator(); iterator.hasNext(); ) {
        IdeaPluginDescriptor descriptor = iterator.next();
        final Boolean enabled = myEnabled.get(descriptor.getPluginId());
        if (enabled == null || !enabled.booleanValue()) {
          iterator.remove();
        }
      }
    }

    Set<PluginId> deps = new HashSet<>();
    Map<PluginId, IdeaPluginDescriptorImpl> pluginIdMap = PluginManagerCore.buildPluginIdMap();
    for (final IdeaPluginDescriptor descriptorToCheckDependencies : descriptorsToCheckDependencies) {
      PluginId pluginId = descriptorToCheckDependencies.getPluginId();
      PluginManagerCore.processAllDependencies(descriptorToCheckDependencies, false, pluginIdMap, descriptor -> {
        PluginId depId = descriptor.getPluginId();
        if (depId == pluginId) {
          return FileVisitResult.CONTINUE;
        }

        Boolean enabled = myEnabled.get(depId);
        if (enabled == null) {
          return FileVisitResult.TERMINATE;
        }

        if (newEnabledState && !enabled.booleanValue()) {
          deps.add(depId);
        }

        if (newEnabledState) {
          return FileVisitResult.CONTINUE;
        }

        if (descriptorToCheckDependencies instanceof IdeaPluginDescriptorImpl &&
            ((IdeaPluginDescriptorImpl)descriptorToCheckDependencies).isDeleted()) {
          return FileVisitResult.CONTINUE;
        }
        if (descriptorToCheckDependencies.isImplementationDetail()) {
          return FileVisitResult.CONTINUE;
        }

        for (IdeaPluginDescriptor d : descriptorsWithChangedEnabledState) {
          if (depId == d.getPluginId()) {
            deps.add(pluginId);
            break;
          }
        }

        return FileVisitResult.CONTINUE;
      });
    }

    if (deps.isEmpty()) {
      return;
    }

    final String listOfSelectedPlugins = StringUtil.join(descriptorsWithChangedEnabledState, pluginDescriptor -> pluginDescriptor.getName(), ", ");
    final String listOfDependencies = StringUtil.join(deps, pluginId -> {
      final IdeaPluginDescriptor pluginDescriptor = PluginManagerCore.getPlugin(pluginId);
      assert pluginDescriptor != null;
      return pluginDescriptor.getName();
    }, "<br>");
    final String message = !newEnabledState ? "<html>The following plugins <br>" + listOfDependencies + "<br>are enabled and depend" +(deps.size() == 1 ? "s" : "") + " on selected plugins. " +
                                     "<br>Would you like to disable them too?</html>"
                                   : "<html>The following plugins on which " + listOfSelectedPlugins + " depend" + (descriptorsWithChangedEnabledState.length == 1 ? "s" : "") +
                                     " are disabled:<br>" + listOfDependencies + "<br>Would you like to enable them?</html>";
    if (Messages.showOkCancelDialog(message, newEnabledState ? "Enable Dependant Plugins" : "Disable Plugins with Dependency on this", Messages.getQuestionIcon()) == Messages.OK) {
      for (PluginId pluginId : deps) {
        myEnabled.put(pluginId, newEnabledState);
      }

      updatePluginDependencies();
    }
  }
}