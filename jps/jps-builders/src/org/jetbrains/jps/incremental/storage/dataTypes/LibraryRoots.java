// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.incremental.storage.dataTypes;

import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.builders.storage.BuildDataPaths;
import org.jetbrains.jps.incremental.relativizer.PathRelativizerService;
import org.jetbrains.jps.incremental.storage.StorageOwner;
import org.jetbrains.jps.javac.Iterators;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@ApiStatus.Internal
public class LibraryRoots implements StorageOwner {

  private static final Logger LOG = Logger.getInstance(LibraryRoots.class);
  private static final String LIBRARY_ROOTS_FILE_NAME = "libraries.dat";
  private static final String TIMESTAMP_DELIMITER = ": ";
  @NotNull
  private final Path myFile;
  @NotNull
  private final PathRelativizerService myRelativizer;
  private Map<Path, Long> myRoots;
  private boolean myChanged = false;

  public LibraryRoots(BuildDataPaths dataPaths, @NotNull PathRelativizerService relativizer) {
    myFile = dataPaths.getDataStorageDir().resolve(LIBRARY_ROOTS_FILE_NAME);
    myRelativizer = relativizer;
  }

  public synchronized Set<Path> getRoots() {
    return new HashSet<>(getLibraryRoots().keySet());
  }

  public synchronized long remove(Path root) {
    Long oldStamp = getLibraryRoots().remove(root);
    myChanged |= (oldStamp != null);
    return oldStamp != null? oldStamp : -1L;
  }

  public synchronized long update(Path root, long stamp) {
    Long oldStamp = getLibraryRoots().put(root, stamp);
    myChanged |= (oldStamp == null || oldStamp != stamp);
    return oldStamp != null? oldStamp : -1L;
  }

  @Override
  public synchronized void clean() throws IOException {
    cleanState();
    Files.deleteIfExists(myFile);
  }

  @Override
  public synchronized void flush(boolean memoryCachesOnly) {
    //try {
    //  storeLibraryRoots(true);
    //}
    //catch (IOException ignored) {
    //}
  }

  @Override
  public synchronized void close() throws IOException {
    storeLibraryRoots(false);
  }

  private Map<Path, Long> getLibraryRoots() {
    Map<Path, Long> roots = myRoots;
    if (roots != null) {
      return roots;
    }
    myRoots = roots = new HashMap<>();
    try (Stream<String> lines = Files.lines(myFile)) {
      for (String line : lines.collect(Collectors.toList())) {
        int idx = line.indexOf(TIMESTAMP_DELIMITER);
        long stamp = idx > 0? Long.parseLong(line.substring(0, idx)) : -1L;
        String path = idx > 0? line.substring(idx + TIMESTAMP_DELIMITER.length()) : line;
        roots.put(Path.of(myRelativizer.toFull(path)), stamp);
      }
    }
    catch(NoSuchFileException ignored) {
    }
    catch(IOException ex) {
      LOG.warn("Error loading library roots data ", ex);
    }
    return roots;
  }

  private void storeLibraryRoots(boolean keepMemoryData) throws IOException {
    Map<Path, Long> roots = myRoots;
    if (roots == null) {
      return; // not initialized
    }
    try {
      if (myChanged) {
        if (roots.isEmpty()) {
          Files.deleteIfExists(myFile);
        }
        else {
          Files.createDirectories(myFile.getParent());
          Files.write(myFile, Iterators.map(roots.entrySet(), entry -> String.join(TIMESTAMP_DELIMITER, Long.toString(entry.getValue()), myRelativizer.toRelative(entry.getKey()))), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        }
      }
    }
    finally {
      if (keepMemoryData) {
        myChanged = false;
      }
      else {
        cleanState();
      }
    }
  }

  protected void cleanState() {
    Map<Path, Long> roots = myRoots;
    if (roots != null) {
      myRoots = null;
      myChanged = false;
      roots.clear();
    }
  }

}
