// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.util.SafeJdomFactory;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.List;

final class PluginXmlPathResolver extends BasePathResolver {
  private static final Logger LOG = Logger.getInstance(PluginXmlPathResolver.class);

  private final @NotNull List<? extends Path> pluginJarFiles;
  private final DescriptorLoadingContext context;

  PluginXmlPathResolver(@NotNull List<? extends Path> pluginJarFiles, @NotNull DescriptorLoadingContext context) {
    this.pluginJarFiles = pluginJarFiles;
    this.context = context;
  }

  @Override
  public @NotNull Element loadXIncludeReference(@NotNull List<Path> bases,
                                                @NotNull String relativePath,
                                                @Nullable String base,
                                                @NotNull SafeJdomFactory jdomFactory) throws IOException, JDOMException {
    // e.g. intellij.clouds.docker.remoteRun.xml
    if (bases.size() == 1 && relativePath.startsWith("intellij.")) {
      Path root = bases.get(0);
      if (root.getFileSystem() != FileSystems.getDefault()) {
        return resolveNewModelModuleFile(relativePath, jdomFactory, root.getRoot());
      }
    }

    try {
      return super.loadXIncludeReference(bases, relativePath, base, jdomFactory);
    }
    catch (NoSuchFileException mainError) {
      if (relativePath.charAt(0) != '/' && !bases.isEmpty()) {
        relativePath = bases.get(bases.size() - 1) + "/" + relativePath;
      }

      Element element = findInJarFiles(relativePath, jdomFactory);
      if (element != null) {
        return element;
      }

      throw mainError;
    }
  }

  @Override
  public @NotNull Element resolvePath(@NotNull Path basePath, @NotNull String relativePath, @NotNull SafeJdomFactory jdomFactory)
    throws IOException, JDOMException {
    if (relativePath.startsWith("intellij.") && basePath.getFileSystem() != FileSystems.getDefault()) {
      return resolveNewModelModuleFile(relativePath, jdomFactory, basePath);
    }

    try {
      return super.resolvePath(basePath, relativePath, jdomFactory);
    }
    catch (NoSuchFileException mainError) {
      if (relativePath.charAt(0) != '/') {
        relativePath = basePath + "/" + relativePath;
      }

      Element element = findInJarFiles(relativePath, jdomFactory);
      if (element != null) {
        return element;
      }

      throw mainError;
    }
  }

  private @NotNull Element resolveNewModelModuleFile(@NotNull String relativePath, @NotNull SafeJdomFactory jdomFactory, @NotNull Path basePath)
    throws JDOMException, IOException {
    try {
      return JDOMUtil.load(basePath.getFileSystem().getPath(relativePath), jdomFactory);
    }
    catch (NoSuchFileException ignored) {
    }

    //noinspection DuplicatedCode
    for (Path jarFile : pluginJarFiles) {
      FileSystem fileSystem;
      try {
        fileSystem = context.open(jarFile);
      }
      catch (IOException e) {
        LOG.error("Corrupted jar file: " + jarFile, e);
        continue;
      }

      try {
        return JDOMUtil.load(fileSystem.getPath(relativePath), jdomFactory);
      }
      catch (NoSuchFileException ignored) {
      }
    }

    throw new RuntimeException("Cannot find " + relativePath + " in " + pluginJarFiles);
  }

  @SuppressWarnings("DuplicatedCode")
  private @Nullable Element findInJarFiles(@NotNull @NonNls String relativePath, @NotNull SafeJdomFactory jdomFactory)
    throws JDOMException, IOException {
    for (Path jarFile : pluginJarFiles) {
      FileSystem fileSystem;
      try {
        fileSystem = context.open(jarFile);
      }
      catch (IOException e) {
        LOG.error("Corrupted jar file: " + jarFile, e);
        continue;
      }

      try {
        return JDOMUtil.load(fileSystem.getPath(relativePath), jdomFactory);
      }
      catch (NoSuchFileException ignore) {
      }
    }

    // it is allowed to reference any platform XML file using href="/META-INF/EnforcedPlainText.xml"
    if (relativePath.startsWith("/META-INF/")) {
      InputStream stream = PluginXmlPathResolver.class.getResourceAsStream(relativePath);
      if (stream != null) {
        return JDOMUtil.load(stream);
      }
    }
    return null;
  }
}
