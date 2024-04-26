// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.incremental.messages;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.builders.BuildTarget;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;

/**
 * @author Eugene Zhuravlev
 */
public final class FileGeneratedEvent extends BuildMessage {
  private static final Logger LOG = Logger.getInstance(FileGeneratedEvent.class);

  private final Collection<Pair<String, String>> myPaths = new ArrayList<>();
  private final BuildTarget<?> mySourceTarget;

  public FileGeneratedEvent(@NotNull BuildTarget<?> sourceTarget) {
    super("", Kind.INFO);
    mySourceTarget = sourceTarget;
  }

  public @NotNull BuildTarget<?> getSourceTarget() {
    return mySourceTarget;
  }

  public void add(String root, String relativePath) {
    if (root != null && relativePath != null) {
      myPaths.add(Pair.create(FileUtil.toSystemIndependentName(root), FileUtil.toSystemIndependentName(relativePath)));
    }
    else {
      LOG.info("Invalid file generation event: root=" + root + "; relativePath=" + relativePath);
    }
  }

  /**
   * Returns pairs of ({@code outputRoot}, {@code relativePath}) where {@code outputRoot} is an absolute path to the output root directory
   * (one of {@link BuildTarget#getOutputRoots}) and {@code relativePath} is a relative path from the output root to the file created or
   * modified by the build process.
   */
  public @NotNull Collection<Pair<String, String>> getPaths() {
    return Collections.unmodifiableCollection(myPaths);
  }
}
