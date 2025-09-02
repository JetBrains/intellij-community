package com.intellij.database.extensions;

import com.intellij.ide.extensionResources.ExtensionsRootType;
import com.intellij.ide.scratch.ScratchFileService;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.util.text.Strings;
import kotlinx.coroutines.Job;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Path;
import java.util.function.Predicate;

final class ExtensionsServiceImpl extends ExtensionsService {
  @Override
  public @NotNull Job unpackPluginResources(PluginId id) {
    return ExtensionsRootType.getInstance().updateBundledResources(id);
  }

  @Override
  public @Nullable Path extensionsRootTypeFindResource(PluginId id, String path) throws IOException {
    return ExtensionsRootType.getInstance().findResource(id, path);
  }

  @Override
  public @NotNull Path extensionsRootTypeFindResourceDirectory(PluginId id, String dir, boolean createIfMissing) throws IOException {
    return ExtensionsRootType.getInstance().findResourceDirectory(id, dir, createIfMissing);
  }

  @Override
  public @NotNull Predicate<Path> extensionsRootTypeRegularFileFilter() {
    return ExtensionsRootType.regularFileFilter();
  }

  @Override
  public @NotNull String extensionsRootTypePathWithoutUnpack(PluginId id, String path) {
    return ScratchFileService.getInstance().getRootPath(ExtensionsRootType.getInstance()) + "/" + id.getIdString() + (Strings.isEmpty(path) ? "" : '/' + path);
  }
}
