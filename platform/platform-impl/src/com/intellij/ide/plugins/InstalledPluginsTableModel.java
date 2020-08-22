// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins;

import com.intellij.ide.IdeBundle;
import com.intellij.openapi.application.ex.ApplicationInfoEx;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.FileVisitResult;
import java.util.*;

/**
 * @author stathik
 */
public class InstalledPluginsTableModel {
  private static final InstalledPluginsState ourState = InstalledPluginsState.getInstance();

  protected final List<IdeaPluginDescriptor> view = new ArrayList<>();

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
  }

  public List<IdeaPluginDescriptor> getAllPlugins() {
    return new ArrayList<>(view);
  }

  @Nullable
  public Set<PluginId> getRequiredPlugins(PluginId pluginId) {
    return myDependentToRequiredListMap.get(pluginId);
  }

  public boolean isLoaded(PluginId pluginId) {
    return myEnabled.get(pluginId) != null;
  }

  protected void setEnabled(IdeaPluginDescriptor ideaPluginDescriptor, boolean enabled) {
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

    Map<PluginId, IdeaPluginDescriptorImpl> pluginIdMap = null;
    for (final IdeaPluginDescriptor rootDescriptor : view) {
      final PluginId pluginId = rootDescriptor.getPluginId();
      myDependentToRequiredListMap.remove(pluginId);
      if (rootDescriptor instanceof IdeaPluginDescriptorImpl && ((IdeaPluginDescriptorImpl) rootDescriptor).isDeleted()) {
        continue;
      }

      Boolean enabled = myEnabled.get(pluginId);
      if (enabled != null && !enabled) {
        continue;
      }

      if (pluginIdMap == null) {
        pluginIdMap = PluginManagerCore.buildPluginIdMap();
      }

      if (rootDescriptor instanceof IdeaPluginDescriptorImpl) {
        PluginManagerCore.processAllDependencies((IdeaPluginDescriptorImpl)rootDescriptor, false, pluginIdMap, (depId, descriptor) -> {
          if (depId.equals(pluginId)) {
            return FileVisitResult.CONTINUE;
          }

          Boolean enabled1 = myEnabled.get(depId);
          if ((enabled1 == null && !ourState.wasInstalled(depId) && !ourState.wasUpdated(depId) && !ourState.wasInstalledWithoutRestart(depId)) || (enabled1 != null && !enabled1)) {
            Set<PluginId> required = myDependentToRequiredListMap.get(pluginId);
            if (required == null) {
              required = new HashSet<>();
              myDependentToRequiredListMap.put(pluginId, required);
            }
            required.add(depId);
          }

          return FileVisitResult.CONTINUE;
        });
      }

      if (enabled == null && !myDependentToRequiredListMap.containsKey(pluginId) && PluginManagerCore.isCompatible(rootDescriptor)) {
        myEnabled.put(pluginId, true);
      }
    }
  }

  public void enableRows(IdeaPluginDescriptor @NotNull [] ideaPluginDescriptors, @NotNull Boolean value) {
    Map<PluginId, Boolean> tempEnabled = new HashMap<>(myEnabled);
    setNewEnabled(ideaPluginDescriptors, tempEnabled, value);

    if (suggestToChangeDependencies(ideaPluginDescriptors, tempEnabled, value)) {
      for (IdeaPluginDescriptor descriptor : ideaPluginDescriptors) {
        handleBeforeChangeEnableState(descriptor, value);
      }
      setNewEnabled(ideaPluginDescriptors, myEnabled, value);
      updatePluginDependencies();
    }
  }

  private static void setNewEnabled(IdeaPluginDescriptor @NotNull [] ideaPluginDescriptors,
                                    @NotNull Map<PluginId, Boolean> enabledContainer,
                                    @NotNull Boolean value) {
    for (IdeaPluginDescriptor ideaPluginDescriptor : ideaPluginDescriptors) {
      PluginId currentPluginId = ideaPluginDescriptor.getPluginId();
      Boolean enabled = enabledContainer.get(currentPluginId) == null ? Boolean.FALSE : value;
      enabledContainer.put(currentPluginId, enabled);
    }
  }

  public boolean isEnabled(final PluginId pluginId) {
    final Boolean enabled = myEnabled.get(pluginId);
    return enabled != null && enabled;
  }

  public boolean isDisabled(@NotNull final PluginId pluginId) {
    final Boolean enabled = myEnabled.get(pluginId);
    return enabled != null && !enabled;
  }

  public Map<PluginId, Boolean> getEnabledMap() {
    return myEnabled;
  }

  private boolean suggestToChangeDependencies(IdeaPluginDescriptor @NotNull [] descriptorsWithChangedEnabledState,
                                              @NotNull Map<PluginId, Boolean> enabledContainer,
                                              @NotNull Boolean newEnabledState) {
    List<IdeaPluginDescriptor> descriptorsToCheckDependencies = new ArrayList<>();
    if (newEnabledState) {
      Collections.addAll(descriptorsToCheckDependencies, descriptorsWithChangedEnabledState);
    }
    else {
      descriptorsToCheckDependencies.addAll(getAllPlugins());
      descriptorsToCheckDependencies.removeAll(Arrays.asList(descriptorsWithChangedEnabledState));

      for (Iterator<IdeaPluginDescriptor> iterator = descriptorsToCheckDependencies.iterator(); iterator.hasNext(); ) {
        IdeaPluginDescriptor descriptor = iterator.next();
        final Boolean enabled = enabledContainer.get(descriptor.getPluginId());
        if (enabled == null || !enabled) {
          iterator.remove();
        }
      }
    }

    Set<PluginId> deps = new HashSet<>();
    Map<PluginId, IdeaPluginDescriptorImpl> pluginIdMap = PluginManagerCore.buildPluginIdMap();
    for (IdeaPluginDescriptor descriptorToCheckDependencies : descriptorsToCheckDependencies) {
      if (!(descriptorToCheckDependencies instanceof IdeaPluginDescriptorImpl)) {
        continue;
      }

      IdeaPluginDescriptorImpl pluginDescriptor = ((IdeaPluginDescriptorImpl)descriptorToCheckDependencies);
      PluginId pluginId = pluginDescriptor.getPluginId();
      PluginManagerCore.processAllDependencies(pluginDescriptor, false, pluginIdMap, (depId, descriptor) -> {
        if (depId == pluginId) {
          return FileVisitResult.CONTINUE;
        }

        Boolean enabled = enabledContainer.get(depId);
        if (enabled == null) {
          return FileVisitResult.TERMINATE;
        }

        if (newEnabledState && !enabled) {
          deps.add(depId);
        }

        if (newEnabledState) {
          return FileVisitResult.CONTINUE;
        }

        if (pluginDescriptor.isDeleted()) {
          return FileVisitResult.CONTINUE;
        }
        if (pluginDescriptor.isImplementationDetail()) {
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
      return true;
    }

    String listOfDependencies = StringUtil.join(deps, pluginId -> {
      IdeaPluginDescriptor pluginDescriptor = PluginManagerCore.getPlugin(pluginId);
      return "&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;" + (pluginDescriptor == null ? pluginId.getIdString() : pluginDescriptor.getName());
    }, "<br>");
    String message = newEnabledState
                     ? IdeBundle.message("dialog.message.enable.required.plugins", descriptorsWithChangedEnabledState.length, deps.size(), listOfDependencies)
                     : IdeBundle.message("dialog.message.disable.dependent.plugins", deps.size(), descriptorsWithChangedEnabledState.length, listOfDependencies);
    if (Messages.showOkCancelDialog(message, newEnabledState ? IdeBundle.message("dialog.title.enable.required.plugins")
                                                             : IdeBundle.message("dialog.title.disable.dependent.plugins"),
                                    newEnabledState ? IdeBundle.message("button.enable") : IdeBundle.message("button.disable"), Messages.getCancelButton(), Messages.getQuestionIcon()) == Messages.OK) {
      for (PluginId pluginId : deps) {
        myEnabled.put(pluginId, newEnabledState);
      }
      return true;
    }
    return false;
  }

  protected void handleBeforeChangeEnableState(@NotNull IdeaPluginDescriptor descriptor, boolean value) {
  }
}