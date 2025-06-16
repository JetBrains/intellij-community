package com.intellij.database.extensions;

import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.util.text.StringUtil;
import kotlinx.coroutines.Job;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.intellij.database.extensions.ExtractorScripts.getPluginId;

public final class LoaderScripts {
  private static final String SCRIPT_DIR = "data/loaders";

  private static final Pattern FILE_PATTERN = Pattern.compile("(.+?)(?:\\.(\\w+))?\\.\\w+");

  private LoaderScripts() {
  }

  public static Job unpackPluginResources() {
    return ExtensionsService.getInstance().unpackPluginResources(getPluginId());
  }

  public static @Nullable String getScriptsDirectoryWithoutUnpack() {
    PluginId id = getPluginId();
    return id == null ? null : ExtensionsService.getInstance().extensionsRootTypePathWithoutUnpack(id, SCRIPT_DIR);
  }

  public static @Nullable File getScriptsDirectory() {
    Path result = getScriptDirectoryImpl(true);
    return result == null ? null : result.toFile();
  }

  public static @NotNull List<Path> getScriptFiles() {
    Path directory = getScriptDirectoryImpl(false);
    if (directory == null) {
      return Collections.emptyList();
    }

    try (Stream<Path> stream = java.nio.file.Files.list(directory)) {
      return stream
        .filter(ExtensionsService.getInstance().extensionsRootTypeRegularFileFilter())
        .filter(o -> FILE_PATTERN.matcher(o.getFileName().toString()).matches())
        .collect(Collectors.toList());
    }
    catch (IOException ignore) {
      return Collections.emptyList();
    }
  }

  public static @Nullable Path findScript(String name) {
    try {
      return ExtensionsService.getInstance().extensionsRootTypeFindResource(getPluginId(), SCRIPT_DIR + "/" + name);
    }
    catch (IOException e) {
      return null;
    }
  }

  public static @NotNull String getOutputFileExtension(@NotNull String scriptFileName) {
    return getNamePart(scriptFileName, 2, "txt");
  }

  private static @NotNull String getNamePart(String name, int namePart, String def) {
    Matcher matcher = FILE_PATTERN.matcher(name);
    try {
      return matcher.matches() ? StringUtil.notNullize(matcher.group(namePart), def) : def;
    }
    catch (Exception ignore) {
      return def;
    }
  }

  private static @Nullable Path getScriptDirectoryImpl(boolean createIfMissing) {
    PluginId pluginId = getPluginId();
    if (pluginId == null) return null;

    try {
      return ExtensionsService.getInstance().extensionsRootTypeFindResourceDirectory(pluginId, SCRIPT_DIR, createIfMissing);
    }
    catch (IOException ignore) {
    }
    return null;
  }
}
