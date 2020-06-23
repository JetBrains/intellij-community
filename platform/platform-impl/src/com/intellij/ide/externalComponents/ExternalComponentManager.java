// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.externalComponents;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.updateSettings.impl.UpdateChecker;
import com.intellij.openapi.updateSettings.impl.UpdateSettings;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * Registry for {@link ExternalComponentSource}s, used for integrating with the {@link UpdateChecker}.
 * Keeps track of the external components and component sources that can be updated.
 */
public class ExternalComponentManager {
  @NotNull
  public static ExternalComponentManager getInstance() {
    return ServiceManager.getService(ExternalComponentManager.class);
  }

  @NotNull
  public Iterable<ExternalComponentSource> getEnabledComponentSources(@NotNull UpdateSettings updateSettings) {
    refreshKnownSources(updateSettings);
    Set<String> enabledSources = new HashSet<>(updateSettings.getEnabledExternalUpdateSources());

    List<ExternalComponentSource> res = new LinkedList<>(ExternalComponentSource.EP_NAME.getExtensionList());
    res.removeIf(source -> !enabledSources.contains(source.getName()));
    return res;
  }

  private void refreshKnownSources(@NotNull UpdateSettings updateSettings) {
    List<ExternalComponentSource> unknownSources = new LinkedList<>(ExternalComponentSource.EP_NAME.getExtensionList());
    Set<String> knownSources = new HashSet<>(updateSettings.getKnownExternalUpdateSources());
    unknownSources.removeIf(source -> knownSources.contains(source.getName()));

    for (ExternalComponentSource source : unknownSources) {
      updateSettings.getKnownExternalUpdateSources().add(source.getName());
      updateSettings.getEnabledExternalUpdateSources().add(source.getName());
      List<String> channels = source.getAllChannels();
      if (channels != null) {
        updateSettings.getExternalUpdateChannels().put(source.getName(), channels.get(0));
      }
    }
  }

  /**
   * Finds an installed component that could be updated by the given component.
   *
   * @param update The potential update.
   * @param source The source for the update.
   * @return A component from the same source for which the given component is an update, or null if no such component is found.
   */
  @Nullable
  public UpdatableExternalComponent findExistingComponentMatching(@NotNull UpdatableExternalComponent update,
                                                                  @NotNull ExternalComponentSource source) {
    Collection<UpdatableExternalComponent> existing = source.getCurrentVersions();
    for (UpdatableExternalComponent c : existing) {
      if (update.isUpdateFor(c)) {
        return c;
      }
    }
    return null;
  }
}