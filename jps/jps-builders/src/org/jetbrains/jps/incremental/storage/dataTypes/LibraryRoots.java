// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.incremental.storage.dataTypes;

import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.builders.storage.BuildDataPaths;
import org.jetbrains.jps.incremental.relativizer.PathRelativizerService;
import org.jetbrains.jps.incremental.storage.StorageOwner;
import org.jetbrains.jps.util.Iterators;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.HashMap;
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
  private Map<Path, RootData> myRoots;
  private boolean myChanged = false;

  public LibraryRoots(BuildDataPaths dataPaths, @NotNull PathRelativizerService relativizer) {
    myFile = dataPaths.getDataStorageDir().resolve(LIBRARY_ROOTS_FILE_NAME);
    myRelativizer = relativizer;
  }

  public synchronized Set<Path> getRoots(Set<Path> acc) {
    acc.addAll(getLibraryRoots().keySet());
    return acc;
  }

  /**
   * @return true, if root data has been changed after the update, otherwise false
   */
  public synchronized boolean remove(Path root) {
    boolean changed = getLibraryRoots().remove(root) != null;
    myChanged |= changed;
    return changed;
  }

  /**
   * @return true, if root data has been changed after the update, otherwise false
   */
  public synchronized boolean update(Path root, String namespace, long stamp) {
    RootData update = RootData.create(namespace, stamp);
    boolean changed = !update.equals(getLibraryRoots().put(root, update));
    myChanged |= changed;
    return changed;
  }

  @Nullable
  public synchronized String getNamespace(Path root) {
    RootData rootData = getLibraryRoots().get(root);
    return rootData != null? rootData.namespace : null;
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

  private Map<Path, RootData> getLibraryRoots() {
    Map<Path, RootData> roots = myRoots;
    if (roots != null) {
      return roots;
    }
    myRoots = roots = new HashMap<>();
    try (Stream<String> lines = Files.lines(myFile)) {
      for (String line : lines.collect(Collectors.toList())) {
        int idx = line.indexOf(TIMESTAMP_DELIMITER);
        if (idx > 0) {
          long stamp = Long.parseLong(line.substring(0, idx));
          int idx2 = line.indexOf(TIMESTAMP_DELIMITER, idx + TIMESTAMP_DELIMITER.length());
          if (idx2 > 0) {
            String libName = line.substring(idx + TIMESTAMP_DELIMITER.length(), idx2);
            String path = line.substring(idx2 + TIMESTAMP_DELIMITER.length());
            roots.put(Path.of(myRelativizer.toFull(path)), RootData.create(libName, stamp));
          }
        }
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
    Map<Path, RootData> roots = myRoots;
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
          Files.write(myFile, Iterators.map(roots.entrySet(), entry -> String.join(TIMESTAMP_DELIMITER, Long.toString(entry.getValue().stamp), entry.getValue().namespace, myRelativizer.toRelative(entry.getKey()))), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
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

  private void cleanState() {
    Map<Path, RootData> roots = myRoots;
    if (roots != null) {
      myRoots = null;
      myChanged = false;
      roots.clear();
    }
  }

  private static final class RootData {
    @NotNull
    final String namespace;
    final long stamp;

    private RootData(@NotNull String namespace, long stamp) {
      this.namespace = namespace;
      this.stamp = stamp;
    }

    static RootData create(@NotNull String name, long stamp) {
      return new RootData(name, stamp);
    }

    @Override
    public boolean equals(Object o) {
      if (!(o instanceof RootData)) {
        return false;
      }

      final RootData rootData = (RootData)o;
      return stamp == rootData.stamp && namespace.equals(rootData.namespace);
    }

    @Override
    public int hashCode() {
      int result = namespace.hashCode();
      result = 31 * result + Long.hashCode(stamp);
      return result;
    }
  }

}
