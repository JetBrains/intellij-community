// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins;

import com.intellij.ide.IdeBundle;
import com.intellij.openapi.application.ex.ApplicationInfoEx;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.project.Project;
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
  private final Map<PluginId, PluginEnabledState> myEnabled = new HashMap<>();
  private final Map<PluginId, Set<PluginId>> myDependentToRequiredListMap = new HashMap<>();
  private final @Nullable Project myProject;

  public InstalledPluginsTableModel(@Nullable Project project) {
    myProject = project;

    ApplicationInfoEx appInfo = ApplicationInfoEx.getInstanceEx();
    for (IdeaPluginDescriptor plugin : PluginManagerCore.getPlugins()) {
      PluginId pluginId = plugin.getPluginId();
      if (appInfo.isEssentialPlugin(pluginId)) {
        myEnabled.put(pluginId, PluginEnabledState.ENABLED);
      }
      else {
        view.add(plugin);
      }
    }
    view.addAll(ourState.getInstalledPlugins());

    for (IdeaPluginDescriptor descriptor : view) {
      setEnabled(descriptor);
    }
    updatePluginDependencies();
  }

  protected final @Nullable Project getProject() {
    return myProject;
  }

  protected final @Nullable ProjectPluginTracker getPluginTracker() {
    return myProject != null ?
           ProjectPluginTracker.getInstance(myProject) :
           null;
  }

  protected @NotNull List<IdeaPluginDescriptor> getAllPlugins() {
    return new ArrayList<>(view);
  }

  @Nullable
  public Set<PluginId> getRequiredPlugins(PluginId pluginId) {
    return myDependentToRequiredListMap.get(pluginId);
  }

  public final boolean isLoaded(@NotNull PluginId pluginId) {
    return isLoaded(pluginId, getEnabledMap());
  }

  protected final void setEnabled(@NotNull IdeaPluginDescriptor ideaPluginDescriptor) {
    PluginId pluginId = ideaPluginDescriptor.getPluginId();

    final boolean descriptorEnabled = ideaPluginDescriptor.isEnabled();
    PluginEnabledState enabled;
    if (descriptorEnabled || PluginManagerCore.isDisabled(pluginId)) {
      ProjectPluginTracker pluginTracker = getPluginTracker();
      enabled = (pluginTracker != null && pluginTracker.isEnabled(ideaPluginDescriptor)) ?
                PluginEnabledState.ENABLED_FOR_PROJECT :
                (pluginTracker != null && pluginTracker.isDisabled(ideaPluginDescriptor)) ?
                PluginEnabledState.DISABLED_FOR_PROJECT :
                descriptorEnabled ?
                PluginEnabledState.ENABLED :
                PluginEnabledState.DISABLED;
    }
    else {
      enabled = null;
    }

    setEnabled(pluginId, enabled);
  }

  protected final void setEnabled(@NotNull PluginId pluginId,
                                  @Nullable PluginEnabledState enabled) {
    myEnabled.put(pluginId, enabled);
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

      if (isDisabled(pluginId)) {
        continue;
      }

      if (pluginIdMap == null) {
        pluginIdMap = PluginManagerCore.buildPluginIdMap();
      }

      boolean loaded = isLoaded(pluginId);
      if (rootDescriptor instanceof IdeaPluginDescriptorImpl) {
        PluginManagerCore.processAllDependencies((IdeaPluginDescriptorImpl)rootDescriptor, false, pluginIdMap, (depId, descriptor) -> {
          if (depId.equals(pluginId)) {
            return FileVisitResult.CONTINUE;
          }

          if ((!isLoaded(depId) &&
               !ourState.wasInstalled(depId) &&
               !ourState.wasUpdated(depId) &&
               !ourState.wasInstalledWithoutRestart(depId)) ||
              isDisabled(depId)) {
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

      if (!loaded &&
          !myDependentToRequiredListMap.containsKey(pluginId) &&
          PluginManagerCore.isCompatible(rootDescriptor)) {
        setEnabled(pluginId, PluginEnabledState.ENABLED); // todo
      }
    }
  }

  protected final void enableRows(@NotNull Set<? extends IdeaPluginDescriptor> ideaPluginDescriptors,
                                  @NotNull PluginEnabledState newState) {
    Map<PluginId, PluginEnabledState> tempEnabled = new HashMap<>(myEnabled);
    setNewEnabled(ideaPluginDescriptors, tempEnabled, newState);

    if (suggestToChangeDependencies(ideaPluginDescriptors, tempEnabled, newState)) {
      for (IdeaPluginDescriptor descriptor : ideaPluginDescriptors) {
        handleBeforeChangeEnableState(descriptor, newState);
      }
      setNewEnabled(ideaPluginDescriptors, myEnabled, newState);
      updatePluginDependencies();
    }
  }

  private static void setNewEnabled(@NotNull Set<? extends IdeaPluginDescriptor> ideaPluginDescriptors,
                                    @NotNull Map<PluginId, PluginEnabledState> enabledMap,
                                    @NotNull PluginEnabledState newState) {
    for (IdeaPluginDescriptor ideaPluginDescriptor : ideaPluginDescriptors) {
      PluginId currentPluginId = ideaPluginDescriptor.getPluginId();
      enabledMap.put(
        currentPluginId,
        isLoaded(currentPluginId, enabledMap) ? newState : PluginEnabledState.DISABLED
      );
    }
  }

  public boolean isEnabled(@NotNull PluginId pluginId) {
    return !isDisabled(pluginId, myEnabled);
  }

  public boolean isDisabled(@NotNull PluginId pluginId) {
    return !isEnabled(pluginId, myEnabled);
  }

  protected final @NotNull Map<PluginId, PluginEnabledState> getEnabledMap() {
    return myEnabled;
  }

  private boolean suggestToChangeDependencies(@NotNull Set<? extends IdeaPluginDescriptor> descriptorsWithChangedEnabledState,
                                              @NotNull Map<PluginId, PluginEnabledState> enabledMap,
                                              @NotNull PluginEnabledState newState) {
    boolean enabled = newState.isEnabled();

    List<IdeaPluginDescriptor> descriptorsToCheckDependencies =
      new ArrayList<>(enabled ? descriptorsWithChangedEnabledState : getAllPlugins());
    if (!enabled) {
      descriptorsToCheckDependencies.removeAll(descriptorsWithChangedEnabledState);
      descriptorsToCheckDependencies.removeIf(descriptor -> isDisabled(descriptor.getPluginId(), enabledMap));
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

        if (!isLoaded(depId)) {
          return FileVisitResult.TERMINATE;
        }

        if (enabled &&
            isDisabled(depId)) {
          deps.add(depId);
        }

        if (enabled ||
            pluginDescriptor.isDeleted() ||
            pluginDescriptor.isImplementationDetail()) {
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

    int descriptorsWithChangedEnabledStateCount = descriptorsWithChangedEnabledState.size();
    String message = enabled ?
                     IdeBundle.message("dialog.message.enable.required.plugins", descriptorsWithChangedEnabledStateCount, deps.size(),
                                       listOfDependencies) :
                     IdeBundle.message("dialog.message.disable.dependent.plugins", deps.size(), descriptorsWithChangedEnabledStateCount,
                                       listOfDependencies);
    int dialogMessage = Messages.showOkCancelDialog(
      message,
      enabled ? IdeBundle.message("dialog.title.enable.required.plugins") : IdeBundle.message("dialog.title.disable.dependent.plugins"),
      enabled ? IdeBundle.message("button.enable") : IdeBundle.message("button.disable"),
      Messages.getCancelButton(),
      Messages.getQuestionIcon()
    );
    if (dialogMessage == Messages.OK) {
      for (PluginId pluginId : deps) {
        setEnabled(pluginId, newState);
      }
      return true;
    }
    return false;
  }

  protected void handleBeforeChangeEnableState(@NotNull IdeaPluginDescriptor descriptor,
                                               @NotNull PluginEnabledState newState) {
  }

  protected static boolean isEnabled(@NotNull PluginId pluginId,
                                     @NotNull Map<PluginId, PluginEnabledState> enabledMap) {
    PluginEnabledState enabled = enabledMap.get(pluginId);
    return enabled == null || enabled.isEnabled();
  }

  protected static boolean isDisabled(@NotNull PluginId pluginId,
                                      @NotNull Map<PluginId, PluginEnabledState> enabledMap) {
    PluginEnabledState enabled = enabledMap.get(pluginId);
    return enabled == null || !enabled.isEnabled();
  }

  protected static boolean isLoaded(@NotNull PluginId pluginId,
                                    @NotNull Map<PluginId, PluginEnabledState> enabledMap) {
    return enabledMap.get(pluginId) != null;
  }
}