package com.intellij.database.extensions;

import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Future;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class ExtractorScripts {
  public static final String EXTRACTORS_SCRIPT_DIR = "data/extractors";
  public static final String AGGREGATORS_SCRIPT_DIR = "data/aggregators";

  private static final Pattern FILE_PATTERN = Pattern.compile("(.+?)(?:\\.(\\w+))?\\.\\w+");

  private ExtractorScripts() {
  }

  public static @Nullable File getExtractorScriptsDirectory() {
    Path result = getScriptDirectoryImpl(false);
    return result == null ? null : result.toFile();
  }

  public static @Nullable File getAggregatorScriptsDirectory() {
    Path result = getScriptDirectoryImpl(true);
    return result == null ? null : result.toFile();
  }

  public static @NotNull List<Path> getExtractorScriptFiles() {
    return getExtractorScriptFilesWithCleanupFuture().getFirst();
  }

  public static @NotNull Pair<List<Path>, Future<?>> getExtractorScriptFilesWithCleanupFuture() {
    Path dir = getScriptDirectoryImpl(false);
    if (dir == null) return new Pair<>(Collections.emptyList(), null);
    Future<?> cleanupFuture = ScriptsCleanup.startScriptsCleanup(dir, EXTRACTORS_SCRIPT_DIR);
    return new Pair<>(getScriptFiles(dir), cleanupFuture);
  }

  public static @NotNull List<Path> getAggregatorScriptFiles() {
    Path dir = getScriptDirectoryImpl(true);
    if (dir == null) return Collections.emptyList();
    ScriptsCleanup.startScriptsCleanup(dir, AGGREGATORS_SCRIPT_DIR);
    return getScriptFiles(dir);
  }

  private static @NotNull List<Path> getScriptFiles(Path directory) {
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

  public static @Nullable Path findExtractorScript(String name) {
    return findScript(name, false);
  }

  public static @Nullable Path findAggregatorScript(String name) {
    return findScript(name, true);
  }

  private static Path findScript(String name, boolean isAggregatorScript) {
    try {
      PluginId id = getPluginId();
      return id == null ? null :
             ExtensionsService.getInstance().extensionsRootTypeFindResource(
               id,
               isAggregatorScript ? AGGREGATORS_SCRIPT_DIR + "/" + name : EXTRACTORS_SCRIPT_DIR + "/" + name
             );
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

  private static @Nullable Path getScriptDirectoryImpl(boolean isAggregatorsDir) {
    try {
      PluginId id = getPluginId();
      return id == null ? null :
             ExtensionsService.getInstance().extensionsRootTypeFindResourceDirectory(
               id,
               isAggregatorsDir ? AGGREGATORS_SCRIPT_DIR : EXTRACTORS_SCRIPT_DIR,
               false
             );
    }
    catch (IOException ignore) {
    }
    return null;
  }

  public static @Nullable PluginId getPluginId() {
    return PluginId.findId("com.intellij.database");
  }
}
