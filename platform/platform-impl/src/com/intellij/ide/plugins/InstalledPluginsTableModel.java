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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.FileVisitResult;
import java.util.*;
import java.util.function.BiConsumer;

public class InstalledPluginsTableModel {

  protected static final boolean HIDE_IMPLEMENTATION_DETAILS = !Boolean.getBoolean("startup.performance.framework");
  private static final InstalledPluginsState ourState = InstalledPluginsState.getInstance();

  protected final List<IdeaPluginDescriptor> view = new ArrayList<>();
  private final Map<PluginId, PluginEnabledState> myEnabled = new HashMap<>();
  private final Map<PluginId, Set<PluginId>> myDependentToRequiredListMap = new HashMap<>();
  private final @Nullable Project myProject;
  private final @Nullable ProjectPluginTracker myPluginTracker;

  public InstalledPluginsTableModel(@Nullable Project project) {
    myProject = project;
    myPluginTracker = myProject == null ?
                      null :
                      ProjectPluginTrackerManager.getInstance().getPluginTracker(myProject);

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

  protected final @NotNull List<IdeaPluginDescriptor> getAllPlugins() {
    return new ArrayList<>(view);
  }

  protected final @NotNull Set<PluginId> getRequiredPluginIds(@NotNull PluginId pluginId) {
    return myDependentToRequiredListMap.getOrDefault(pluginId, Set.of());
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

  protected final @NotNull Map<PluginId, ? extends IdeaPluginDescriptor> getDependentToRequiredListMap() {
    HashMap<PluginId, IdeaPluginDescriptor> result = new HashMap<>();
    for (Map.Entry<PluginId, Set<PluginId>> entry : myDependentToRequiredListMap.entrySet()) {
      PluginId pluginId = entry.getKey();

      if (!isLoaded(pluginId)) {
        continue;
      }

      for (PluginId dependencyPluginId : entry.getValue()) {
        if (PluginManagerCore.isModuleDependency(dependencyPluginId)) {
          continue;
        }

        IdeaPluginDescriptor descriptor = PluginManagerCore.getPlugin(pluginId);
        if (descriptor != null && !isHidden(descriptor)) {
          result.put(pluginId, descriptor);
        }
        break;
      }
    }

    return Collections.unmodifiableMap(result);
  }

  protected void updatePluginDependencies() {
    myDependentToRequiredListMap.clear();

    Map<PluginId, IdeaPluginDescriptorImpl> pluginIdMap = null;
    for (final IdeaPluginDescriptor rootDescriptor : view) {
      final PluginId pluginId = rootDescriptor.getPluginId();
      myDependentToRequiredListMap.remove(pluginId);
      if (isDeleted(rootDescriptor) ||
          isDisabled(pluginId)) {
        continue;
      }

      if (pluginIdMap == null) {
        pluginIdMap = PluginManagerCore.buildPluginIdMap();
      }

      boolean loaded = isLoaded(pluginId);
      if (rootDescriptor instanceof IdeaPluginDescriptorImpl) {
        PluginManagerCore.processAllNonOptionalDependencies((IdeaPluginDescriptorImpl)rootDescriptor, pluginIdMap, (depId, __) -> {
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

  protected final void enableRows(@NotNull Collection<? extends IdeaPluginDescriptor> descriptors,
                                  @NotNull PluginEnableDisableAction action) {
    Map<PluginId, PluginEnabledState> tempEnabled = new HashMap<>(myEnabled);

    setNewEnabled(descriptors,
                  tempEnabled,
                  action,
                  (descriptor, pair) -> {
                  });

    boolean enabled = action.isEnable();
    Set<Pair<? extends IdeaPluginDescriptor, @NotNull String>> dependencies =
      getDependenciesToUpdateState(descriptors,
                                   tempEnabled,
                                   enabled);

    if (HIDE_IMPLEMENTATION_DETAILS &&
        !createUpdateDependenciesDialog(ContainerUtil.map(dependencies, pair -> pair.getSecond()), action)) {
      return;
    }

    setNewEnabled(ContainerUtil.mapNotNull(dependencies, pair -> pair.getFirst()),
                  action);
    setNewEnabled(descriptors,
                  action);
    updatePluginDependencies();
  }

  private void setNewEnabled(@NotNull Collection<? extends IdeaPluginDescriptor> descriptors,
                             @NotNull PluginEnableDisableAction action) {
    setNewEnabled(descriptors,
                  myEnabled,
                  action,
                  this::handleBeforeChangeEnableState);
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

  // todo to be defined static
  private @NotNull Set<@NotNull Pair<? extends IdeaPluginDescriptor, @NotNull String>> getDependenciesToUpdateState(@NotNull Collection<? extends IdeaPluginDescriptor> descriptorsWithChangedEnabledState,
                                                                                                                    @NotNull Map<PluginId, PluginEnabledState> enabledMap,
                                                                                                                    boolean enabled) {
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

      PluginManagerCore.processAllNonOptionalDependencies(pluginDescriptor, pluginIdMap, (depId, descriptor) -> {
        if (depId.equals(pluginDescriptor.getPluginId())) {
          return FileVisitResult.CONTINUE;
        }

        if (!isLoaded(depId, enabledMap)) {
          return FileVisitResult.TERMINATE;
        }

        if (enabled && isDisabled(depId, enabledMap)) {
          String name = descriptor == null ? depId.getIdString() : descriptor.getName();
          dependencies.add(new Pair<>(descriptor, name));
        }

        if (enabled ||
            isHidden(pluginDescriptor)) {
          return FileVisitResult.CONTINUE;
        }

        for (IdeaPluginDescriptor d : descriptorsWithChangedEnabledState) {
          if (depId.equals(d.getPluginId())) {
            dependencies.add(Pair.create(pluginDescriptor, pluginDescriptor.getName()));
            break;
          }
        }

        return FileVisitResult.CONTINUE;
      });
    }

    return dependencies;
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
      .yesText(IdeBundle.message(enabled ? "button.enable" : "button.disable"))
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
}