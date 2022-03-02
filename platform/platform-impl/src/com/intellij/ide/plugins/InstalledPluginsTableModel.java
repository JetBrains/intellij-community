// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins;

import com.intellij.ide.IdeBundle;
import com.intellij.openapi.application.ex.ApplicationInfoEx;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.MessageDialogBuilder;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Pair;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.FileVisitResult;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

public class InstalledPluginsTableModel {

  protected static final boolean HIDE_IMPLEMENTATION_DETAILS = !Boolean.getBoolean("startup.performance.framework");

  protected final List<IdeaPluginDescriptor> view = new ArrayList<>();
  private final Map<PluginId, PluginEnabledState> myEnabled = new HashMap<>();
  private final @Nullable Project myProject;

  public InstalledPluginsTableModel(@Nullable Project project) {
    myProject = project;

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

    ProjectPluginTracker pluginTracker = myProject != null ? DynamicPluginEnabler.findPluginTracker(myProject) : null;
    for (IdeaPluginDescriptor descriptor : view) {
      setEnabled(descriptor, pluginTracker);
    }
  }

  protected final @Nullable Project getProject() {
    return myProject;
  }

  public final boolean isLoaded(@NotNull PluginId pluginId) {
    return isLoaded(pluginId, getEnabledMap());
  }

  private void setEnabled(@NotNull IdeaPluginDescriptor ideaPluginDescriptor,
                          @Nullable ProjectPluginTracker pluginTracker) {
    PluginId pluginId = ideaPluginDescriptor.getPluginId();

    PluginEnabledState enabled = pluginTracker != null && pluginTracker.isEnabled(pluginId) ?
                                 PluginEnabledState.ENABLED_FOR_PROJECT :
                                 pluginTracker != null && pluginTracker.isDisabled(pluginId) ?
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

    List<IdeaPluginDescriptorImpl> impls = descriptors.stream()
      .filter(IdeaPluginDescriptorImpl.class::isInstance)
      .map(IdeaPluginDescriptorImpl.class::cast)
      .collect(Collectors.toCollection(ArrayList::new));

    Map<PluginId, IdeaPluginDescriptorImpl> pluginIdMap = PluginManagerCore.buildPluginIdMap();
    List<IdeaPluginDescriptorImpl> descriptorsToUpdate = action.isEnable() ?
                                                         getDependenciesToEnable(impls, tempEnabled, pluginIdMap) :
                                                         getDependentsToDisable(impls, tempEnabled, pluginIdMap);

    Set<String> pluginNamesToUpdate = descriptorsToUpdate.stream()
      .filter(descriptor -> !isHiddenImplementationDetail(descriptor))
      .map(IdeaPluginDescriptorImpl::getName)
      .collect(Collectors.toCollection(TreeSet::new));
    if (HIDE_IMPLEMENTATION_DETAILS &&
        !createUpdateDependenciesDialog(pluginNamesToUpdate, action)) {
      return;
    }

    impls.addAll(descriptorsToUpdate);
    setNewEnabled(impls,
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

  protected final @NotNull Map<PluginId, PluginEnabledState> getEnabledMap() {
    return myEnabled;
  }

  private static @NotNull List<IdeaPluginDescriptorImpl> getDependenciesToEnable(@NotNull Collection<IdeaPluginDescriptorImpl> descriptors,
                                                                                 @NotNull Map<PluginId, PluginEnabledState> enabledMap,
                                                                                 @NotNull Map<PluginId, IdeaPluginDescriptorImpl> pluginIdMap) {
    ArrayList<IdeaPluginDescriptorImpl> result = new ArrayList<>();
    for (IdeaPluginDescriptorImpl descriptor : descriptors) {
      PluginManagerCore.processAllNonOptionalDependencies(descriptor, pluginIdMap, dependency -> {
        PluginId dependencyId = dependency.getPluginId();
        PluginEnabledState state = enabledMap.get(dependencyId);

        if (!dependencyId.equals(descriptor.getPluginId()) &&
            !(state != null && state.isEnabled())) {
          result.add(dependency);
        }
        return FileVisitResult.CONTINUE;
      });
    }

    return Collections.unmodifiableList(result);
  }

  private static @NotNull List<IdeaPluginDescriptorImpl> getDependentsToDisable(@NotNull Collection<IdeaPluginDescriptorImpl> descriptors,
                                                                                @NotNull Map<PluginId, PluginEnabledState> enabledMap,
                                                                                @NotNull Map<PluginId, IdeaPluginDescriptorImpl> pluginIdMap) {
    ArrayList<IdeaPluginDescriptorImpl> result = new ArrayList<>();
    Set<PluginId> pluginIds = descriptors.stream()
      .map(IdeaPluginDescriptorImpl::getPluginId)
      .collect(Collectors.toUnmodifiableSet());

    for (IdeaPluginDescriptorImpl descriptor : PluginManagerCore.getPluginSet().allPlugins) {
      PluginId pluginId = descriptor.getPluginId();
      if (pluginIds.contains(pluginId) ||
          isDisabled(pluginId, enabledMap)) {
        continue;
      }

      PluginManagerCore.processAllNonOptionalDependencies(descriptor, pluginIdMap, dependency -> {
        PluginId dependencyId = dependency.getPluginId();
        if (!isLoaded(dependencyId, enabledMap)) {
          return FileVisitResult.TERMINATE;
        }

        if (!dependencyId.equals(pluginId) &&
            pluginIds.contains(dependencyId)) {
          result.add(descriptor);
        }
        return FileVisitResult.CONTINUE;
      });
    }

    return Collections.unmodifiableList(result);
  }

  private boolean createUpdateDependenciesDialog(@NotNull Collection<String> dependencies,
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
                              dependencies.iterator().next() :
                              dependencies.stream()
                                .map("&nbsp;".repeat(5)::concat)
                                .collect(Collectors.joining("<br>"));

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