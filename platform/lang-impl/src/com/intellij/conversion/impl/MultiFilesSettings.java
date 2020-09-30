// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.conversion.impl;

import com.intellij.conversion.ArtifactsSettings;
import com.intellij.conversion.CannotConvertException;
import com.intellij.conversion.ProjectLibrariesSettings;
import com.intellij.openapi.util.JDOMUtil;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.serialization.library.JpsLibraryTableSerializer;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

final class MultiFilesSettings implements ArtifactsSettings, ProjectLibrariesSettings {
  private final SettingsXmlFile projectFile;
  private @Nullable List<Path> settingsFiles;
  private final Path dir;
  private final ConversionContextImpl context;

  MultiFilesSettings(@Nullable SettingsXmlFile projectFile, @Nullable Path dir, @NotNull ConversionContextImpl context)
    throws CannotConvertException {
    if (projectFile == null && dir == null) {
      throw new IllegalArgumentException("Either project file or settings files should be not null");
    }

    this.dir = dir;
    this.context = context;
    this.projectFile = projectFile;
  }

  @NotNull
  private List<Path> getSettingsFiles() {
    if (settingsFiles == null) {
      if (dir == null) {
        settingsFiles = Collections.emptyList();
      }
      else {
        settingsFiles = getSettingsXmlFiles(dir);
      }
    }
    return settingsFiles;
  }

  static @NotNull List<Path> getSettingsXmlFiles(@NotNull Path dir) throws CannotConvertException {
    try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
      List<Path> result = new ArrayList<>();
      for (Path path : stream) {
        if (path.getFileName().toString().endsWith(".xml")) {
          result.add(path);
        }
      }
      return result;
    }
    catch (NoSuchFileException ignore) {
      return Collections.emptyList();
    }
    catch (IOException e) {
      throw new CannotConvertException(e);
    }
  }

  private @NotNull Collection<Element> getSettings(@NotNull String componentName, @NotNull String tagName) {
    List<Element> result = new ArrayList<>();
    if (projectFile != null) {
      result.addAll(JDOMUtil.getChildren(projectFile.findComponent(componentName), tagName));
    }

    for (Path file : getSettingsFiles()) {
      result.addAll(JDOMUtil.getChildren(context.getOrCreateFile(file).getRootElement(), tagName));
    }
    return result;
  }

  public void collectAffectedFiles(@NotNull Collection<Path> files) {
    if (projectFile != null) {
      files.add(projectFile.getFile());
    }
    files.addAll(getSettingsFiles());
  }

  @Override
  public @NotNull Collection<Element> getArtifacts() {
    return getSettings("ArtifactManager", "artifact");
  }

  @Override
  public @NotNull Collection<Element> getProjectLibraries() {
    return getSettings("libraryTable", JpsLibraryTableSerializer.LIBRARY_TAG);
  }
}
