// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.jps.javac;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.NotDirectoryException;
import java.nio.file.Path;
import java.util.stream.Collectors;
import java.util.stream.Stream;

// experimental alternative implementation for directory contents listing
final class NIOFileOperations extends DefaultFileOperations {

  @Override
  protected Iterable<File> listFiles(File file) throws IOException {
    try (Stream<Path> stream = Files.list(file.toPath())) {
      return stream.map(path -> path.toFile()).collect(Collectors.toList());
    }
    catch (NotDirectoryException | NoSuchFileException e) {
      return null;
    }
  }
}
