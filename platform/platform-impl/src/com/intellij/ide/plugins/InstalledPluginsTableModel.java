// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins;

import com.intellij.externalDependencies.DependencyOnPlugin;
import com.intellij.externalDependencies.ExternalDependenciesManager;
import com.intellij.ide.IdeBundle;
import com.intellij.openapi.application.ex.ApplicationInfoEx;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.MessageDialogBuilder;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.OkCancelDialogBuilder;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.FileVisitResult;
import java.util.*;
import java.util.function.BiConsumer;

/**
 * @author stathik
 */
public class InstalledPluginsTableModel {

  private static final InstalledPluginsState ourState = InstalledPluginsState.getInstance();

  protected final List<IdeaPluginDescriptor> view = new ArrayList<>();
  private final Map<PluginId, PluginEnabledState> myEnabled = new HashMap<>();
  private final Map<PluginId, Set<PluginId>> myDependentToRequiredListMap = new HashMap<>();
  private final @Nullable Project myProject;
  private final @Nullable ProjectPluginTracker myPluginTracker;

  public InstalledPluginsTableModel(@Nullable Project project) {
    myProject = project;
    myPluginTracker = ProjectPluginTrackerManager.createPluginTrackerOrNull(myProject);

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
    return myPluginTracker;
  }

  protected @NotNull List<IdeaPluginDescriptor> getAllPlugins() {
    return new ArrayList<>(view);
  }

  @Nullable
  public Set<PluginId> getRequiredPlugins(PluginId pluginId) {
    return myDependentToRequiredListMap.get(pluginId);
  }

  public boolean isRequiredPlugin(@NotNull IdeaPluginDescriptor descriptor) {
    return myProject != null &&
           ExternalDependenciesManager.getInstance(myProject)
             .getDependencies(DependencyOnPlugin.class)
             .stream()
             .map(DependencyOnPlugin::getPluginId)
             .anyMatch(descriptor.getPluginId().getIdString()::equals);
  }

  public final boolean isLoaded(@NotNull PluginId pluginId) {
    return isLoaded(pluginId, getEnabledMap());
  }

  protected final void setEnabled(@NotNull IdeaPluginDescriptor ideaPluginDescriptor) {
    PluginId pluginId = ideaPluginDescriptor.getPluginId();

    PluginEnabledState enabled = (myPluginTracker != null && myPluginTracker.isEnabled(pluginId)) ?
                                 PluginEnabledState.ENABLED_FOR_PROJECT :
                                 myPluginTracker != null && myPluginTracker.isDisabled(pluginId) ?
                                 PluginEnabledState.DISABLED_FOR_PROJECT :
                                 PluginManagerCore.isDisabled(pluginId) ?
                                 PluginEnabledState.DISABLED :
                                 ideaPluginDescriptor.isEnabled() ?
                                 PluginEnabledState.ENABLED :
                                 null;

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
    setNewEnabled(
      ideaPluginDescriptors,
      tempEnabled,
      newState,
      (d, s) -> {
      }
    );

    Set<Pair<@Nullable ? extends IdeaPluginDescriptor, @NotNull String>> dependencies = getDependenciesToUpdateState(
      ideaPluginDescriptors,
      tempEnabled,
      newState
    );

    if (!dependencies.isEmpty() &&
        !createUpdateDependenciesDialog(
          newState.isEnabled(),
          ideaPluginDescriptors.size(),
          ContainerUtil.map(dependencies, pair -> pair.getSecond())
        ).ask(getProject())) {
      return;
    }

    setNewEnabled(
      ContainerUtil.mapNotNull(dependencies, pair -> pair.getFirst()),
      newState
    );
    setNewEnabled(
      ideaPluginDescriptors,
      newState
    );
    updatePluginDependencies();
  }

  private void setNewEnabled(@NotNull Collection<@NotNull ? extends IdeaPluginDescriptor> dependencies,
                             @NotNull PluginEnabledState newState) {
    setNewEnabled(
      dependencies,
      myEnabled,
      newState,
      this::handleBeforeChangeEnableState
    );
  }

  private static void setNewEnabled(@NotNull Collection<@NotNull ? extends IdeaPluginDescriptor> descriptors,
                                    @NotNull Map<PluginId, PluginEnabledState> enabledMap,
                                    @NotNull PluginEnabledState newState,
                                    @NotNull BiConsumer<@NotNull ? super IdeaPluginDescriptor, @NotNull PluginEnabledState> beforeHandler) {
    for (IdeaPluginDescriptor descriptor : descriptors) {
      beforeHandler.accept(descriptor, newState);

      PluginId pluginId = descriptor.getPluginId();
      enabledMap.put(
        pluginId,
        isLoaded(pluginId, enabledMap) ? newState : PluginEnabledState.DISABLED
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

  // todo to be defined static
  private @NotNull Set<@NotNull Pair<@Nullable ? extends IdeaPluginDescriptor, @NotNull String>> getDependenciesToUpdateState(@NotNull Set<? extends IdeaPluginDescriptor> descriptorsWithChangedEnabledState,
                                                                                                                              @NotNull Map<PluginId, PluginEnabledState> enabledMap,
                                                                                                                              @NotNull PluginEnabledState newState) {
    boolean enabled = newState.isEnabled();

    List<IdeaPluginDescriptor> descriptorsToCheckDependencies =
      new ArrayList<>(enabled ? descriptorsWithChangedEnabledState : getAllPlugins());
    if (!enabled) {
      descriptorsToCheckDependencies.removeAll(descriptorsWithChangedEnabledState);
      descriptorsToCheckDependencies.removeIf(descriptor -> isDisabled(descriptor.getPluginId(), enabledMap));
    }

    Set<Pair<? extends IdeaPluginDescriptor, String>> dependencies = new HashSet<>();
    Map<PluginId, IdeaPluginDescriptorImpl> pluginIdMap = PluginManagerCore.buildPluginIdMap();
    for (IdeaPluginDescriptor descriptorToCheckDependencies : descriptorsToCheckDependencies) {
      if (!(descriptorToCheckDependencies instanceof IdeaPluginDescriptorImpl)) {
        continue;
      }
      IdeaPluginDescriptorImpl pluginDescriptor = ((IdeaPluginDescriptorImpl)descriptorToCheckDependencies);

      PluginManagerCore.processAllDependencies(pluginDescriptor, false, pluginIdMap, (depId, descriptor) -> {
        if (depId == pluginDescriptor.getPluginId()) {
          return FileVisitResult.CONTINUE;
        }

        if (!isLoaded(depId, enabledMap)) {
          return FileVisitResult.TERMINATE;
        }

        if (enabled &&
            isDisabled(depId, enabledMap)) {
          String name = descriptor == null ?
                        depId.getIdString() :
                        descriptor.getName();
          dependencies.add(Pair.create(descriptor, name));
        }

        if (enabled ||
            pluginDescriptor.isDeleted() ||
            pluginDescriptor.isImplementationDetail()) {
          return FileVisitResult.CONTINUE;
        }

        for (IdeaPluginDescriptor d : descriptorsWithChangedEnabledState) {
          if (depId == d.getPluginId()) {
            dependencies.add(Pair.create(pluginDescriptor, pluginDescriptor.getName()));
            break;
          }
        }

        return FileVisitResult.CONTINUE;
      });
    }

    return dependencies;
  }

  private static @NotNull OkCancelDialogBuilder createUpdateDependenciesDialog(boolean enabled,
                                                                               int updatedDescriptorsCount,
                                                                               @NotNull List<String> dependencies) {

    return MessageDialogBuilder.okCancel(
      IdeBundle.message(enabled ? "dialog.title.enable.required.plugins" : "dialog.title.disable.dependent.plugins"),
      IdeBundle.message(
        enabled ? "dialog.message.enable.required.plugins" : "dialog.message.disable.dependent.plugins",
        enabled ? updatedDescriptorsCount : dependencies.size(),
        enabled ? dependencies.size() : updatedDescriptorsCount,
        StringUtil.join(dependencies, id -> "&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;" + id, "<br>")
      )
    ).yesText(IdeBundle.message(enabled ? "button.enable" : "button.disable"))
      .noText(Messages.getCancelButton());
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