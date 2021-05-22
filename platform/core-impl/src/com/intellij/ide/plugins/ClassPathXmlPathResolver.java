// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins;

import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.util.SafeJdomFactory;
import com.intellij.util.PathUtil;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

final class ClassPathXmlPathResolver implements PathBasedJdomXIncluder.PathResolver<String> {
  private final ClassLoader classLoader;

  ClassPathXmlPathResolver(@NotNull ClassLoader classLoader) {
    this.classLoader = classLoader;
  }

  @Override
  public boolean isFlat() {
    return true;
  }

  @Override
  public @NotNull List<String> createNewStack(@Nullable Path base) {
    List<@NonNls String> stack = new ArrayList<>(2);
    stack.add("META-INF");
    return stack;
  }

  @Override
  public @NotNull Element loadXIncludeReference(@NotNull List<String> bases,
                                                @NotNull String relativePath,
                                                @Nullable String base,
                                                @NotNull SafeJdomFactory jdomFactory) throws IOException, JDOMException {
    String path;
    if (relativePath.charAt(0) != '/') {
      if (base == null) {
        base = bases.get(bases.size() - 1);
      }

      if (relativePath.startsWith("./")) {
        PluginManagerCore.getLogger().error("Do not use prefix ./: " + relativePath);
        relativePath = relativePath.substring(2);
      }

      path = base + "/" + relativePath;
      if (relativePath.indexOf('/', 1) > 0) {
        bases.add(PathUtil.getParentPath(path));
      }
    }
    else {
      path = relativePath.substring(1);
    }

    InputStream stream = classLoader.getResourceAsStream(path);
    if (stream == null) {
      throw new NoSuchFileException(relativePath);
    }
    else {
      return JDOMUtil.load(stream, jdomFactory);
    }
  }

  @Override
  public @NotNull Element resolvePath(@NotNull Path basePath, @NotNull String relativePath, @NotNull SafeJdomFactory jdomFactory)
    throws IOException, JDOMException {
    String path;
    if (relativePath.charAt(0) == '/') {
      path = relativePath.substring(1);
    }
    else {
      if (relativePath.startsWith("./")) {
        PluginManagerCore.getLogger().error("Do not use prefix ./: " + relativePath);
        relativePath = relativePath.substring(2);
      }

      path = relativePath.startsWith("intellij.") ? relativePath : "META-INF/" + relativePath;
    }

    InputStream stream = classLoader.getResourceAsStream(path);
    if (stream == null) {
      throw new NoSuchFileException(relativePath);
    }
    else {
      return JDOMUtil.load(stream, jdomFactory);
    }
  }
}
