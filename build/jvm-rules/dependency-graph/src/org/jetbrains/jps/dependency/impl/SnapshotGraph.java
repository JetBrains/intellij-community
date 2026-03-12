// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.dependency.impl;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.dependency.CloseableExt;
import org.jetbrains.jps.dependency.DifferentiateResult;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.InflaterInputStream;

public class SnapshotGraph extends DependencyGraphImpl implements CloseableExt {
  private final Path myStorePath;
  private int myIntegrateCount;

  public SnapshotGraph(Path storePath) throws IOException {
    this(storePath, IndexFactory.mandatoryIndices());
  }
  
  public SnapshotGraph(Path storePath, IndexFactory indexFactory) throws IOException {
    super(new MemoryMapletFactory(), indexFactory);
    myStorePath = storePath;
    try (var in = new InflaterInputStream(new BufferedInputStream(Files.newInputStream(storePath)))) {
      importSnapshot(in);
    }
    catch (NoSuchFileException ignored) {
    }
  }

  @Override
  public void close(boolean saveChanges) throws IOException {
    try {
      if (saveChanges && getIntegratesCount() > 0) {
        try (var out = new DeflaterOutputStream(new BufferedOutputStream(openOutputStream(myStorePath)), new Deflater(Deflater.BEST_SPEED))) {
          exportSnapshot(out);
        }
      }
    }
    finally {
      close();
    }
  }

  public int getIntegratesCount() {
    return myIntegrateCount;
  }

  @Override
  public void integrate(@NotNull DifferentiateResult diffResult) {
    super.integrate(diffResult);
    myIntegrateCount += 1;
  }

  private static OutputStream openOutputStream(Path storePath) throws IOException {
    try {
      return Files.newOutputStream(storePath);
    }
    catch (NoSuchFileException e) {
      Files.createDirectories(storePath.getParent());
      return Files.newOutputStream(storePath);
    }
  }
}
