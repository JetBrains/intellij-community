// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.externalComponents;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.updateSettings.impl.UpdateSettings;
import com.intellij.openapi.util.Pair;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;

/**
 * Interface for classes that can provide information on and updates for installed components.
 */
public interface ExternalComponentSource {
  ExtensionPointName<ExternalComponentSource> EP_NAME = ExtensionPointName.create("com.intellij.externalComponentSource");

  /**
   * Retrieve information on the updates that this source can provide.
   *
   * @param indicator      A {@link ProgressIndicator} that can be updated to show progress, or can be used to cancel the process.
   * @param updateSettings The current UpdateSettings
   * @return A Collection of {@link UpdatableExternalComponent}s representing the available updates.
   */
  @NotNull
  Collection<UpdatableExternalComponent> getAvailableVersions(@Nullable ProgressIndicator indicator,
                                                              @Nullable UpdateSettings updateSettings);

  /**
   * Retrieve information on currently installed components.
   *
   * @return A Collection of currently installed {@link UpdatableExternalComponent}s.
   */
  @NotNull
  Collection<UpdatableExternalComponent> getCurrentVersions();

  /**
   * Install updates for the given {@link UpdatableExternalComponent}s.
   *
   * @param request
   */
  void installUpdates(@NotNull Collection<UpdatableExternalComponent> request);

  /**
   * Gets a human-readable name for this source.
   *
   * @return The name.
   */
  @NotNull
  @Nls(capitalization = Nls.Capitalization.Title) String getName();

  @NotNull
  Collection<? extends Pair<@Nls String, @Nls String>> getStatuses();

  /**
   * Gets a list of all available channels for this source. The first item is the default.
   * @return A list of channel names, or {@code null} to indicate that this source does not have different update channels.
   */
  @Nullable
  List<@Nls(capitalization = Nls.Capitalization.Title) String> getAllChannels();

  default boolean isEnabled(){
    return true;
  }
}