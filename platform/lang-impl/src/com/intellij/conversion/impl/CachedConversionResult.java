// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.conversion.impl;

import com.intellij.conversion.CannotConvertException;
import com.intellij.conversion.ConverterProvider;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.util.PathUtil;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import it.unimi.dsi.fastutil.objects.Object2LongMaps;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

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

  static void saveConversionResult(@NotNull Object2LongMap<String> projectFilesMap, @NotNull Path outFile, @NotNull Path baseDir)
    throws CannotConvertException, IOException {
    Element root = new Element("conversion");
    Element appliedConverters = new Element("applied-converters");
    root.addContent(appliedConverters);
    for (ConverterProvider provider : ConverterProvider.EP_NAME.getExtensionList()) {
      appliedConverters.addContent(new Element("converter").setAttribute("id", provider.getId()));
    }

    Element projectFiles = new Element("project-files");
    root.addContent(projectFiles);

    String basePathWithSlash = baseDir.toString() + File.separator;
    for (ObjectIterator<Object2LongMap.Entry<String>> iterator = Object2LongMaps.fastIterator(projectFilesMap); iterator.hasNext(); ) {
      Object2LongMap.Entry<String> entry = iterator.next();
      Element element = new Element("f");
      String path = entry.getKey();
      element.setAttribute("p", path.startsWith(basePathWithSlash) ? "./" + path.substring(basePathWithSlash.length()) : path);
      element.setAttribute("t", Long.toString(entry.getLongValue()));
      projectFiles.addContent(element);
    }

    JDOMUtil.write(root, outFile);
  }

  static @NotNull CachedConversionResult load(@NotNull Path infoFile, @NotNull Path baseDir) throws JDOMException, IOException {
    Element root;
    try {
      root = JDOMUtil.load(infoFile);
    }
    catch (NoSuchFileException ignore) {
      return createEmpty();
    }

    Object2LongMap<String> projectFilesTimestamps = createPathToLastModifiedMap();
    CachedConversionResult result = new CachedConversionResult(new HashSet<>(), projectFilesTimestamps);
    String basePathWithSlash = baseDir.toString() + File.separator;
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
          String path = element.getAttributeValue("p");
          if (path == null) {
            path = element.getAttributeValue("path");
          }
          else if (path.startsWith("./")) {
            path = basePathWithSlash + path;
          }
          if (path == null || path.isEmpty()) {
            continue;
          }

          try {
            String timestamp = element.getAttributeValue("t");
            if (timestamp == null) {
              timestamp = element.getAttributeValue("timestamp");
              if (timestamp != null) {
                projectFilesTimestamps.put(path, TimeUnit.MILLISECONDS.toSeconds(Long.parseLong(timestamp)));
              }
            }
            else {
              projectFilesTimestamps.put(path, Long.parseLong(timestamp));
            }
          }
          catch (NumberFormatException ignore) {
          }
        }
      }
    }
    return result;
  }

  @NotNull static Object2LongMap<String> createPathToLastModifiedMap() {
    Object2LongMap<String> result = new Object2LongOpenHashMap<>();
    result.defaultReturnValue(-1);
    return result;
  }

  static @NotNull CachedConversionResult createEmpty() {
    return new CachedConversionResult(Collections.emptySet(), Object2LongMaps.emptyMap());
  }
}
