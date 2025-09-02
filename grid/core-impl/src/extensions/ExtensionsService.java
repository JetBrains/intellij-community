package com.intellij.database.extensions;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.extensions.PluginId;
import kotlinx.coroutines.Job;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Path;
import java.util.function.Predicate;

public class ExtensionsService {
  public static ExtensionsService getInstance() {
    return ApplicationManager.getApplication().getService(ExtensionsService.class);
  }

  public @NotNull Job unpackPluginResources(PluginId id) {
    return null;
  }

  public @NotNull Predicate<Path> extensionsRootTypeRegularFileFilter() {
    return null;
  }

  public @Nullable String extensionsRootTypePathWithoutUnpack(PluginId id, String path) {
    return null;
  }

  public @Nullable Path extensionsRootTypeFindResource(PluginId id, String path) throws IOException {
    return null;
  }

  public @NotNull Path extensionsRootTypeFindResourceDirectory(PluginId id, String dir, boolean createIfMissing) throws IOException {
    return null;
  }
}
