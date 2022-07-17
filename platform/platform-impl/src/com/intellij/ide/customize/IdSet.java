// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.customize;

import com.intellij.openapi.extensions.PluginId;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

public final class IdSet {

  private static final List<@NonNls String> STOP_LIST = List.of("Support", "support",
                                                                "Integration", "integration");

  private final @NotNull Set<PluginId> myPluginIds;
  private final @Nls @NotNull String myTitle;

  IdSet(@NotNull Set<PluginId> pluginIds,
        @Nls @NotNull String title) {
    assert !pluginIds.isEmpty();
    myPluginIds = pluginIds;
    myTitle = cleanTitle(title);
  }

  @Override
  public String toString() {
    return myTitle + ": " + myPluginIds.size();
  }

  public @NotNull Set<PluginId> getPluginIds() {
    return Collections.unmodifiableSet(myPluginIds);
  }

  public @Nls @NotNull String getTitle() {
    return myTitle;
  }

  /**
   * @deprecated Please use {@link #getPluginIds()}.
   */
  @Deprecated(forRemoval = true)
  public @NotNull List<PluginId> getIds() {
    return new ArrayList<>(myPluginIds);
  }

  private static @Nls @NotNull String cleanTitle(@Nls @NotNull String title) {
    for (String skipWord : STOP_LIST) {
      title = title.replaceAll(skipWord, ""); //NON-NLS
    }
    return title.replaceAll(" {2}", " ").trim();
  }
}
