// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins;

import com.intellij.ide.IdeBundle;
import com.intellij.openapi.application.ex.ApplicationInfoEx;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.MessageDialogBuilder;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.FileVisitResult;
import java.util.*;
import java.util.function.BiConsumer;

public class InstalledPluginsTableModel {

  protected static final boolean HIDE_IMPLEMENTATION_DETAILS = !Boolean.getBoolean("startup.performance.framework");

  protected final List<IdeaPluginDescriptor> view = new ArrayList<>();
  private final Map<PluginId, PluginEnabledState> myEnabled = new HashMap<>();
  private final @Nullable Project myProject;
  private final @Nullable ProjectPluginTracker myPluginTracker;

  public InstalledPluginsTableModel(@Nullable Project project) {
    myProject = project;
    myPluginTracker = myProject != null ?
                      ProjectPluginTrackerManager.getInstance().getPluginTracker(myProject) :
                      null;

    ApplicationInfoEx appInfo = ApplicationInfoEx.getInstanceEx();
    for (IdeaPluginDescriptor plugin : PluginManagerCore.getPlugins()) {
      PluginId pluginId = plugin.getPluginId();
      if (appInfo.isEssentialPlugin(pluginId)) {
        setEnabled(pluginId, PluginEnabledState.ENABLED);
      }
      else {
        view.add(plugin);
      }
    }
    view.addAll(InstalledPluginsState.getInstance().getInstalledPlugins());

    for (IdeaPluginDescriptor descriptor : view) {
      setEnabled(descriptor);
    }
  }

  protected final @Nullable Project getProject() {
    return myProject;
  }

  public final boolean isLoaded(@NotNull PluginId pluginId) {
    return isLoaded(pluginId, getEnabledMap());
  }

  protected final void setEnabled(@NotNull IdeaPluginDescriptor ideaPluginDescriptor) {
    PluginId pluginId = ideaPluginDescriptor.getPluginId();

    PluginEnabledState enabled = myPluginTracker != null && myPluginTracker.isEnabled(pluginId) ?
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

  protected void updatePluginDependencies(@Nullable Map<PluginId, IdeaPluginDescriptorImpl> pluginIdMap) {
  }

  protected final void enableRows(@NotNull Collection<? extends IdeaPluginDescriptor> descriptors,
                                  @NotNull PluginEnableDisableAction action) {
    Map<PluginId, PluginEnabledState> tempEnabled = new HashMap<>(myEnabled);

    setNewEnabled(descriptors,
                  tempEnabled,
                  action,
                  (descriptor, pair) -> {
                  });

    boolean enabled = action.isEnable();
    Map<PluginId, IdeaPluginDescriptorImpl> pluginIdMap = PluginManagerCore.buildPluginIdMap();

    Set<PluginId> pluginIdsToUpdate = enabled ?
                                      getDependenciesToEnable(descriptors, tempEnabled, pluginIdMap) :
                                      getDependentsToDisable(descriptors, tempEnabled, pluginIdMap);

    List<IdeaPluginDescriptor> descriptorsToUpdate = new ArrayList<>();
    List<String> pluginNames = new ArrayList<>();
    for (PluginId pluginId : pluginIdsToUpdate) {
      IdeaPluginDescriptorImpl descriptor = pluginIdMap.get(pluginId);
      descriptorsToUpdate.add(descriptor);
      pluginNames.add(getPluginNameOrId(pluginId, descriptor));
    }

    if (HIDE_IMPLEMENTATION_DETAILS &&
        !createUpdateDependenciesDialog(pluginNames, action)) {
      return;
    }

    descriptorsToUpdate.addAll(descriptors);
    setNewEnabled(descriptorsToUpdate,
                  myEnabled,
                  action,
                  this::handleBeforeChangeEnableState);
    updatePluginDependencies(pluginIdMap);
  }

  private static void setNewEnabled(@NotNull Collection<? extends IdeaPluginDescriptor> descriptors,
                                    @NotNull Map<PluginId, PluginEnabledState> enabledMap,
                                    @NotNull PluginEnableDisableAction action,
                                    @NotNull BiConsumer<? super IdeaPluginDescriptor, @NotNull Pair<PluginEnableDisableAction, PluginEnabledState>> beforeHandler) {
    for (IdeaPluginDescriptor descriptor : descriptors) {
      PluginId pluginId = descriptor.getPluginId();
      PluginEnabledState oldState = enabledMap.get(pluginId);

      PluginEnabledState newState = oldState == null ?
                                    PluginEnabledState.DISABLED :
                                    action.apply(oldState);
      if (newState != null) {
        beforeHandler.accept(descriptor, Pair.create(action, newState));
        enabledMap.put(pluginId, newState);
      }
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

  private static @NotNull Set<PluginId> getDependenciesToEnable(@NotNull Collection<? extends IdeaPluginDescriptor> descriptors,
                                                                @NotNull Map<PluginId, PluginEnabledState> enabledMap,
                                                                @NotNull Map<PluginId, IdeaPluginDescriptorImpl> pluginIdMap) {
    Set<PluginId> result = new LinkedHashSet<>();
    for (IdeaPluginDescriptor descriptor : descriptors) {
      if (!(descriptor instanceof IdeaPluginDescriptorImpl)) {
        continue;
      }

      PluginManagerCore.processAllNonOptionalDependencies(((IdeaPluginDescriptorImpl)descriptor), pluginIdMap, (dependencyId, __) -> {
        PluginEnabledState state = enabledMap.get(dependencyId);
        if (state == null) {
          return FileVisitResult.TERMINATE;
        }

        if (!dependencyId.equals(descriptor.getPluginId()) &&
            state.isDisabled()) {
          result.add(dependencyId);
        }
        return FileVisitResult.CONTINUE;
      });
    }

    return Collections.unmodifiableSet(result);
  }

  private static @NotNull Set<PluginId> getDependentsToDisable(@NotNull Collection<? extends IdeaPluginDescriptor> descriptors,
                                                               @NotNull Map<PluginId, PluginEnabledState> enabledMap,
                                                               @NotNull Map<PluginId, IdeaPluginDescriptorImpl> pluginIdMap) {
    Set<PluginId> result = new LinkedHashSet<>();
    Set<PluginId> pluginIds = ContainerUtil.map2Set(descriptors,
                                                    IdeaPluginDescriptor::getPluginId);

    for (Map.Entry<PluginId, IdeaPluginDescriptorImpl> entry : pluginIdMap.entrySet()) {
      PluginId pluginId = entry.getKey();
      IdeaPluginDescriptorImpl descriptor = entry.getValue();

      if (descriptor == null ||
          pluginIds.contains(pluginId) ||
          isDisabled(pluginId, enabledMap)) {
        continue;
      }

      PluginManagerCore.processAllNonOptionalDependencies(descriptor, pluginIdMap, (dependencyId, __) -> {
        if (!isLoaded(dependencyId, enabledMap)) {
          return FileVisitResult.TERMINATE;
        }

        if (!dependencyId.equals(pluginId) &&
            !isHidden(descriptor) &&
            pluginIds.contains(dependencyId)) {
          result.add(pluginId);
        }
        return FileVisitResult.CONTINUE;
      });
    }

    return Collections.unmodifiableSet(result);
  }

  private boolean createUpdateDependenciesDialog(@NotNull List<String> dependencies,
                                                 @NotNull PluginEnableDisableAction action) {
    int size = dependencies.size();
    if (size == 0) {
      return true;
    }
    boolean hasOnlyOneDependency = size == 1;

    String key;
    switch (action) {
      case ENABLE_GLOBALLY:
        key = hasOnlyOneDependency ?
              "dialog.message.enable.required.plugin" :
              "dialog.message.enable.required.plugins";
        break;
      case ENABLE_FOR_PROJECT:
        key = hasOnlyOneDependency ?
              "dialog.message.enable.required.plugin.for.current.project" :
              "dialog.message.enable.required.plugins.for.current.project";
        break;
      case ENABLE_FOR_PROJECT_DISABLE_GLOBALLY:
        key = hasOnlyOneDependency ?
              "dialog.message.enable.dependent.plugin.for.current.project.only" :
              "dialog.message.enable.dependent.plugins.for.current.project.only";
        break;
      case DISABLE_GLOBALLY:
        key = hasOnlyOneDependency ?
              "dialog.message.disable.dependent.plugin" :
              "dialog.message.disable.dependent.plugins";
        break;
      case DISABLE_FOR_PROJECT:
        key = hasOnlyOneDependency ?
              "dialog.message.disable.dependent.plugin.for.current.project" :
              "dialog.message.disable.dependent.plugins.for.current.project";
        break;
      case DISABLE_FOR_PROJECT_ENABLE_GLOBALLY:
        key = hasOnlyOneDependency ?
              "dialog.message.disable.required.plugin.for.current.project.only" :
              "dialog.message.disable.required.plugins.for.current.project.only";
        break;
      default:
        throw new IllegalStateException("Unexpected value: " + action);
    }

    String dependenciesText = hasOnlyOneDependency ?
                              dependencies.get(0) :
                              StringUtil.join(dependencies,
                                              StringUtil.repeat("&nbsp;", 5)::concat,
                                              "<br>");

    boolean enabled = action.isEnable();
    return MessageDialogBuilder
      .okCancel(IdeBundle.message(enabled ? "dialog.title.enable.required.plugins" : "dialog.title.disable.dependent.plugins"),
                IdeBundle.message(key, dependenciesText))
      .yesText(IdeBundle.message(enabled ? "plugins.configurable.enable" : "plugins.configurable.disable"))
      .noText(Messages.getCancelButton())
      .ask(getProject());
  }

  protected void handleBeforeChangeEnableState(@NotNull IdeaPluginDescriptor descriptor,
                                               @NotNull Pair<PluginEnableDisableAction, PluginEnabledState> pair) {
  }

  protected static boolean isEnabled(@NotNull PluginId pluginId,
                                     @NotNull Map<PluginId, PluginEnabledState> enabledMap) {
    PluginEnabledState state = enabledMap.get(pluginId);
    return state == null || state.isEnabled();
  }

  protected static boolean isDisabled(@NotNull PluginId pluginId,
                                      @NotNull Map<PluginId, PluginEnabledState> enabledMap) {
    PluginEnabledState state = enabledMap.get(pluginId);
    return state == null || state.isDisabled();
  }

  protected static boolean isLoaded(@NotNull PluginId pluginId,
                                    @NotNull Map<PluginId, PluginEnabledState> enabledMap) {
    return enabledMap.get(pluginId) != null;
  }

  protected static boolean isDeleted(@NotNull IdeaPluginDescriptor descriptor) {
    return descriptor instanceof IdeaPluginDescriptorImpl && ((IdeaPluginDescriptorImpl)descriptor).isDeleted();
  }

  protected static boolean isHiddenImplementationDetail(@NotNull IdeaPluginDescriptor descriptor) {
    return HIDE_IMPLEMENTATION_DETAILS && descriptor.isImplementationDetail();
  }

  protected static boolean isHidden(@NotNull IdeaPluginDescriptor descriptor) {
    return isDeleted(descriptor) ||
           isHiddenImplementationDetail(descriptor);
  }

  protected static @NotNull @NonNls String getPluginNameOrId(@NotNull PluginId pluginId,
                                                             @Nullable IdeaPluginDescriptor descriptor) {
    return descriptor != null ? descriptor.getName() : pluginId.getIdString();
  }
}