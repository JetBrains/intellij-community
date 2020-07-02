// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.conversion.impl;

import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.util.PathUtil;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import it.unimi.dsi.fastutil.objects.Object2LongMaps;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@ApiStatus.Internal
final class CachedConversionResult {
  public final Set<String> appliedConverters;
  public final Object2LongMap<String> projectFilesTimestamps;

  private CachedConversionResult(@NotNull Set<String> appliedConverters, @NotNull Object2LongMap<String> projectFilesTimestamps) {
    this.appliedConverters = appliedConverters;
    this.projectFilesTimestamps = projectFilesTimestamps;
  }

  static @NotNull Path getConversionInfoFile(@NotNull Path projectFile) {
    String dirName = PathUtil.suggestFileName(projectFile.getFileName().toString() + Integer.toHexString(projectFile.toAbsolutePath().hashCode()));
    return Paths.get(PathManager.getSystemPath(), "conversion", dirName + ".xml");
  }

  static @NotNull CachedConversionResult load(@NotNull Path infoFile) throws JDOMException, IOException {
    Element root;
    try {
      root = JDOMUtil.load(infoFile);
    }
    catch (NoSuchFileException ignore) {
      return createEmpty();
    }

    Object2LongOpenHashMap<String> projectFilesTimestamps = new Object2LongOpenHashMap<>();
    projectFilesTimestamps.defaultReturnValue(-1);
    CachedConversionResult result = new CachedConversionResult(new HashSet<>(), projectFilesTimestamps);
    for (Element child : root.getChildren()) {
      if (child.getName().equals("applied-converters")) {
        for (Element element : child.getChildren()) {
          String id = element.getAttributeValue("id");
          if (id != null) {
            result.appliedConverters.add(id);
          }
        }
      }
      else if (child.getName().equals("project-files")) {
        List<Element> projectFiles = child.getChildren();
        for (Element element : projectFiles) {
          String path = element.getAttributeValue("path");
          String timestamp = element.getAttributeValue("timestamp");
          if (path != null && timestamp != null) {
            try {
              projectFilesTimestamps.put(path, Long.parseLong(timestamp));
            }
            catch (NumberFormatException ignore) {
            }
          }
        }
      }
    }
    return result;
  }

  static @NotNull CachedConversionResult createEmpty() {
    return new CachedConversionResult(Collections.emptySet(), Object2LongMaps.emptyMap());
  }
}
