// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs;

import com.intellij.openapi.editor.colors.ColorKey;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.*;
import org.jetbrains.annotations.ApiStatus.Internal;

import java.awt.*;
import java.util.function.Supplier;

public final class FileStatusFactory {
  private static final FileStatusFactory ourInstance = new FileStatusFactory();

  public static @NotNull FileStatusFactory getInstance() {
    return ourInstance;
  }

  @Internal
  public static String getFilestatusColorKeyPrefix() {
    return "FILESTATUS_";
  }

  /**
   * @deprecated this method is not locale-friendly or plugin unloading-friendly
   */
  @Deprecated(forRemoval = true)
  public FileStatus createFileStatus(@NonNls @NotNull String id,
                                     @Nls(capitalization = Nls.Capitalization.Sentence) @NotNull String description) {
    return createFileStatus(id, () -> description, null, null);
  }

  /**
   * @deprecated this method is not locale-friendly or plugin unloading-friendly
   */
  @Deprecated
  public FileStatus createFileStatus(@NonNls @NotNull String id,
                                     @Nls(capitalization = Nls.Capitalization.Sentence) @NotNull String description,
                                     @Nullable Color color) {
    return createFileStatus(id, () -> description, color, null);
  }

  /**
   * @param pluginId if specified, returned status will be removed from global file status list on plugin unloading (to avoid unloading
   *                 being blocked by plugin classes referenced via description supplier)
   */
  public @NotNull FileStatus createFileStatus(
    @NonNls @NotNull String id,
    @NotNull Supplier<@Nls(capitalization = Nls.Capitalization.Sentence) @NotNull String> description,
    @Nullable PluginId pluginId
  ) {
    return createFileStatus(id, description, null, pluginId);
  }

  /**
   * @param pluginId if specified, returned status will be removed from global file status list on plugin unloading (to avoid unloading
   *                 being blocked by plugin classes referenced via description supplier)
   */
  public synchronized @NotNull FileStatus createFileStatus(
    @NonNls @NotNull String id,
    @NotNull Supplier<@Nls(capitalization = Nls.Capitalization.Sentence) @NotNull String> description,
    @Nullable Color color,
    @Nullable PluginId pluginId
  ) {
    ColorKey colorKey = ColorKey.createColorKey(getFilestatusColorKeyPrefix() + id, color);
    FileStatusImpl result = new FileStatusImpl(id, colorKey, description);
    myStatuses.putValue(pluginId, result);
    return result;
  }

  private final MultiMap<@Nullable PluginId, FileStatus> myStatuses = new MultiMap<>();

  @Internal
  public @NotNull FileStatus @NotNull [] getAllFileStatuses() {
    return myStatuses.values().toArray(new FileStatus[0]);
  }

  synchronized void onPluginUnload(@NotNull PluginId pluginId) {
    myStatuses.remove(pluginId);
  }

  private FileStatusFactory() {
  }
}
