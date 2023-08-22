// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.extensions.PluginId;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.function.Supplier;

public abstract class FileStatusFactory {
  public static final String FILESTATUS_COLOR_KEY_PREFIX = "FILESTATUS_";

  public final FileStatus createFileStatus(@NonNls @NotNull String id,
                                     @Nls(capitalization = Nls.Capitalization.Sentence) @NotNull String description) {
    return createFileStatus(id, () -> description, null, null);
  }

  public final FileStatus createFileStatus(@NonNls @NotNull String id,
                                     @Nls(capitalization = Nls.Capitalization.Sentence) @NotNull String description,
                                     @Nullable Color color) {
    return createFileStatus(id, () -> description, color, null);
  }

  /**
   * @param pluginId if specified, returned status will be removed from global file status list on plugin unloading (to avoid unloading
   *                 being blocked by plugin classes referenced via description supplier)
   */
  public final FileStatus createFileStatus(@NonNls @NotNull String id,
                                     @NotNull Supplier<@Nls(capitalization = Nls.Capitalization.Sentence) @NotNull String> description,
                                     @Nullable PluginId pluginId) {
    return createFileStatus(id, description, null, pluginId);
  }

  /**
   * @param pluginId if specified, returned status will be removed from global file status list on plugin unloading (to avoid unloading
   *                 being blocked by plugin classes referenced via description supplier)
   */
  public abstract FileStatus createFileStatus(@NonNls @NotNull String id,
                                              @NotNull Supplier<@Nls(capitalization = Nls.Capitalization.Sentence) @NotNull String> description,
                                              @Nullable Color color,
                                              @Nullable PluginId pluginId);

  public abstract FileStatus[] getAllFileStatuses();

  public static FileStatusFactory getInstance() {
    return ApplicationManager.getApplication().getService(FileStatusFactory.class);
  }
}
