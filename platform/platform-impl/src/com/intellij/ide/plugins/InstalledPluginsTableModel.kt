// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins;

import com.intellij.ide.plugins.marketplace.InitSessionResult;
import com.intellij.ide.plugins.newui.PluginUiModel;
import com.intellij.ide.plugins.newui.UiPluginManager;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.BiConsumer;

public class InstalledPluginsTableModel {

  @ApiStatus.Internal
  public static final boolean HIDE_IMPLEMENTATION_DETAILS = !ApplicationManagerEx.isInIntegrationTest();

  protected final List<PluginUiModel> view = new ArrayList<>();
  private final Map<PluginId, PluginEnabledState> myEnabled = new HashMap<>();
  private final @Nullable Project myProject;
  protected final UUID mySessionId;

  public InstalledPluginsTableModel(@Nullable Project project) {
    this(project, null, UUID.randomUUID());
  }

  @ApiStatus.Internal
  public InstalledPluginsTableModel(@Nullable Project project, @Nullable InitSessionResult initSessionResult, UUID sessionId) {
    myProject = project;
    mySessionId = sessionId;
    InitSessionResult session = initSessionResult == null ? UiPluginManager.getInstance().initSession(mySessionId) : initSessionResult;
    view.addAll(session.getVisiblePluginsList());
    session.getPluginStates().forEach((pluginId, pluginState) -> {
      myEnabled.put(pluginId, pluginState != null ? (pluginState ? PluginEnabledState.ENABLED : PluginEnabledState.DISABLED) : null);
    });
  }

  protected final @Nullable Project getProject() {
    return myProject;
  }

  public final boolean isLoaded(@NotNull PluginId pluginId) {
    return isLoaded(pluginId, getEnabledMap());
  }

  private void setEnabled(@NotNull PluginUiModel ideaPluginDescriptor) {
    PluginId pluginId = ideaPluginDescriptor.getPluginId();
    PluginEnabledState enabled = ideaPluginDescriptor.isEnabled() ? PluginEnabledState.ENABLED : PluginEnabledState.DISABLED;

    setEnabled(pluginId, enabled);
  }

  @ApiStatus.NonExtendable
  protected void setEnabled(@NotNull PluginId pluginId,
                            @Nullable PluginEnabledState enabled) {
    myEnabled.put(pluginId, enabled);
  }

  protected void updatePluginDependencies(@Nullable Map<PluginId, IdeaPluginDescriptorImpl> pluginIdMap) {
  }

  private static @NotNull IdeaPluginDescriptorImpl findByPluginId(@NotNull PluginId pluginId,
                                                                  @NotNull Map<PluginId, IdeaPluginDescriptorImpl> pluginIdMap) {
    return Objects.requireNonNull(pluginIdMap.get(pluginId),
                                  "'" + pluginId + "' not found");
  }

  private static void setNewEnabled(@NotNull Collection<PluginUiModel> descriptors,
                                    @NotNull Map<PluginId, PluginEnabledState> enabledMap,
                                    @NotNull PluginEnableDisableAction action,
                                    @NotNull BiConsumer<PluginUiModel, ? super @NotNull Pair<PluginEnableDisableAction, PluginEnabledState>> beforeHandler) {
    for (PluginUiModel descriptor : descriptors) {
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


  protected void handleBeforeChangeEnableState(@NotNull IdeaPluginDescriptor descriptor,
                                               @NotNull Pair<PluginEnableDisableAction, PluginEnabledState> pair) {
  }

  protected static boolean isEnabled(@NotNull PluginId pluginId,
                                     @NotNull Map<PluginId, PluginEnabledState> enabledMap) {
    PluginEnabledState state = enabledMap.get(pluginId);
    return state == null || state.isEnabled();
  }

  @ApiStatus.Internal
  public static boolean isDisabled(@NotNull PluginId pluginId,
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

  @ApiStatus.Internal
  public static boolean isHiddenImplementationDetail(@NotNull IdeaPluginDescriptor descriptor) {
    return HIDE_IMPLEMENTATION_DETAILS && descriptor.isImplementationDetail();
  }

  @ApiStatus.Internal
  public static boolean isHidden(@NotNull IdeaPluginDescriptor descriptor) {
    return isDeleted(descriptor) ||
           isHiddenImplementationDetail(descriptor);
  }

  @ApiStatus.Internal
  public static @NotNull @NonNls String getPluginNameOrId(@NotNull PluginId pluginId,
                                                          @Nullable IdeaPluginDescriptor descriptor) {
    return descriptor != null ? descriptor.getName() : pluginId.getIdString();
  }
}