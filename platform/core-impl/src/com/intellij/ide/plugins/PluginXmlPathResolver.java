// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.JDOMUtil;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.List;

/**
 * @author nik
 */
final class PluginXmlPathResolver extends BasePathResolver {
  private static final Logger LOG = Logger.getInstance(PluginXmlPathResolver.class);

  private final List<Path> pluginJarFiles;
  private final DescriptorLoadingContext context;

  PluginXmlPathResolver(@NotNull List<Path> pluginJarFiles, @NotNull DescriptorLoadingContext context) {
    this.pluginJarFiles = pluginJarFiles;
    this.context = context;
  }

  @NotNull
  @Override
  public Element resolvePath(@NotNull List<Path> bases, @NotNull String relativePath, @Nullable String base) throws
                                                                                                             IOException,
                                                                                                             JDOMException {
    try {
      return super.resolvePath(bases, relativePath, base);
    }
    catch (NoSuchFileException mainError) {
      for (Path jarFile : pluginJarFiles) {
        FileSystem fileSystem;
        try {
          fileSystem = context.open(jarFile);
        }
        catch (IOException e) {
          LOG.error("Corrupted jar file: " + jarFile, e);
          continue;
        }

        Path path = fileSystem.getPath(relativePath);
        if (Files.exists(path)) {
          return JDOMUtil.load(path);
        }
      }

      // it is allowed to reference any platform XML file using href="/META-INF/EnforcedPlainText.xml"
      if (relativePath.startsWith("/META-INF/")) {
        InputStream stream = PluginXmlPathResolver.class.getResourceAsStream(relativePath);
        if (stream != null) {
          return JDOMUtil.load(stream);
        }
      }

      throw mainError;
    }
  }
}
