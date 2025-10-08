// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide;

import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.openapi.application.InitialConfigImportState;
import com.intellij.openapi.extensions.PluginId;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

@ApiStatus.Internal
public interface ConfigImportSettings {
  /**
   * Called after configuration import is finished, even when there was nothing to import from.
   * In the latter case, {@code oldConfigDir} is {@code null} and {@link InitialConfigImportState#isConfigImported()} returns {@code false}.
   */
  default void importFinished(@NotNull Path newConfigDir, @Nullable Path oldConfigDir) { }

  /**
   * Allows a product to provide an additional set of prefixes to locate directories to import from;
   * useful e.g. when a product changes a prefix after an upgrade.
   */
  default @NotNull List<String> getEditionsToImportFrom() {
    return List.of();
  }

  /**
   * If there are no configs for previous versions of this product,
   * then configs will be imported from the IDE path selector returned from this method, if they exist.
   *
   * @param programArgs arguments passed to the application's {@code main(String[])} method.
   * @return product prefixes of IDEs to import configs from,
   * or an empty list if no import should happen when there are no configs for this product.
   */
  default @NotNull List<String> getProductsToImportFrom(@NotNull List<String> programArgs) {
    return List.of();
  }

  /**
   * If the vmoptions are modified during the initial config import, normally the IDE should restart to apply these new vmoptions.
   * Returning {@code false} from this method allows overriding this behavior and preventing restart.
   */
  default boolean shouldRestartAfterVmOptionsChange() {
    return true;
  }

  /**
   * Allows editing lists of plugins that are about to be migrated or downloaded during import.
   */
  default void processPluginsToMigrate(
    @NotNull Path newConfigDir,
    @NotNull Path oldConfigDir,
    @NotNull Path oldPluginsDir,
    @NotNull ConfigImportOptions options,
    @Nullable Map<PluginId, Set<String>> brokenPluginVersions,
    @NotNull List<IdeaPluginDescriptor> pluginsToMigrate,
    @NotNull List<IdeaPluginDescriptor> pluginsToDownload
  ) { }

  /**
   * @param prefix a platform prefix of {@code configDirectory}
   * @return {@code true} if {@code configDirectory} should be seen as an import candidate while finding configuration directories.
   */
  default boolean shouldBeSeenAsImportCandidate(@NotNull Path configDirectory, @Nullable String prefix, @NotNull List<String> otherProductPrefixes) {
    return true;
  }

  /**
   * Whether a file should be skipped during configuration import.
   */
  default boolean shouldSkipPath(@NotNull Path path) {
    return false;
  }
}
