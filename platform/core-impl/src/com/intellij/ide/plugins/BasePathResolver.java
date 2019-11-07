// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins;

import com.intellij.openapi.util.JDOMUtil;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

class BasePathResolver implements PathBasedJdomXIncluder.PathResolver<Path> {
  @NotNull
  @Override
  public List<Path> createNewStack(@Nullable Path base) {
    List<Path> stack = new ArrayList<>(2);
    if (base != null) {
      stack.add(base);
    }
    return stack;
  }

  @NotNull
  @Override
  public Element resolvePath(@NotNull List<Path> bases, @NotNull String relativePath, @Nullable String base) throws
                                                                                                              IOException,
                                                                                                              JDOMException {
    Path basePath = base == null ? bases.get(bases.size() - 1) : Paths.get(base);
    Path path = basePath == null ? Paths.get(relativePath) : basePath.resolve(relativePath);
    Element element = JDOMUtil.load(path);
    if (basePath == null) {
      bases.add(path.getParent());
    }
    else {
      Path parent = path.getParent();
      if (!parent.equals(basePath)) {
        bases.add(parent);
        assert !bases.contains(path) : "Circular XInclude Reference to " + path;
      }
    }
    return element;
  }
}
