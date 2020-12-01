// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.externalComponents;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.updateSettings.impl.UpdateChecker;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Registry for {@link ExternalComponentSource}s, used for integrating with the {@link UpdateChecker}.
 * Keeps track of the external components and component sources that can be updated.
 */
@Service
public final class ExternalComponentManager {
  public static @NotNull ExternalComponentManager getInstance() {
    return ApplicationManager.getApplication().getService(ExternalComponentManager.class);
  }

  public static @NotNull Iterable<ExternalComponentSource> getComponentSources() {
    return ExternalComponentSource.EP_NAME.getExtensionList();
  }

  /**
   * Finds an installed component that could be updated by the given component.
   *
   * @param update The potential update.
   * @param source The source for the update.
   * @return A component from the same source for which the given component is an update, or null if no such component is found.
   */
  public @Nullable UpdatableExternalComponent findExistingComponentMatching(@NotNull UpdatableExternalComponent update,
                                                                            @NotNull ExternalComponentSource source) {
    for (UpdatableExternalComponent c : source.getCurrentVersions()) {
      if (update.isUpdateFor(c)) {
        return c;
      }
    }
    return null;
  }
}
