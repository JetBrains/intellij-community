// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.impl.storage;

import com.intellij.concurrency.ConcurrentCollectionFactory;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.SystemProperties;
import com.intellij.util.ThrowableRunnable;
import com.intellij.util.containers.ConcurrentIntObjectMap;
import com.intellij.util.indexing.IdFilter;
import com.intellij.util.indexing.StorageException;
import com.intellij.util.io.*;
import com.intellij.util.io.DataOutputStream;
import com.intellij.util.io.keyStorage.AppendableObjectStorage;
import com.intellij.util.io.keyStorage.AppendableStorageBackedByResizableMappedFile;
import it.unimi.dsi.fastutil.ints.*;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.VisibleForTesting;

import java.io.*;
import java.nio.file.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A data structure to store key hashes to virtual file id mappings.
 */
@ApiStatus.Internal
public final class KeyHashLog<Key> implements Closeable {
  private static final Logger LOG = Logger.getInstance(KeyHashLog.class);
  private static final boolean ENABLE_CACHED_HASH_IDS = SystemProperties.getBooleanProperty("idea.index.cashed.hashids", true);

  private final @NotNull KeyDescriptor<Key> myKeyDescriptor;
  private final @NotNull Path myBaseStorageFile;
  private final @NotNull AppendableObjectStorage<int[]> myKeyHashToVirtualFileMapping;
  private final @NotNull ConcurrentIntObjectMap<Boolean> myInvalidatedSessionIds = ConcurrentCollectionFactory.createConcurrentIntObjectMap();

  private volatile int myLastScannedId;

  public KeyHashLog(@NotNull KeyDescriptor<Key> descriptor, @NotNull Path baseStorageFile) throws IOException {
    this(descriptor, baseStorageFile, true);
  }

  private KeyHashLog(@NotNull KeyDescriptor<Key> descriptor, @NotNull Path baseStorageFile, boolean compact) throws IOException {
    myKeyDescriptor = descriptor;
    myBaseStorageFile = baseStorageFile;
    if (compact && isRequiresCompaction()) {
      performCompaction();
    }
    myKeyHashToVirtualFileMapping =
      openMapping(getDataFile(), 4096);
  }

  private static @NotNull AppendableStorageBackedByResizableMappedFile<int[]> openMapping(@NotNull Path dataFile, int size) throws IOException {
    return new AppendableStorageBackedByResizableMappedFile<>(dataFile,
                                                              size,
                                                              null,
                                                              IOUtil.MiB,
                                                              true,
                                                              IntPairInArrayKeyDescriptor.INSTANCE);
  }

  public void addKeyHashToVirtualFileMapping(Key key, int inputId) throws StorageException {
    appendKeyHashToVirtualFileMappingToLog(key, inputId);
  }

  public void removeKeyHashToVirtualFileMapping(Key key, int inputId) throws StorageException {
    appendKeyHashToVirtualFileMappingToLog(key, -inputId);
  }

  public @NotNull IntSet getSuitableKeyHashes(@NotNull IdFilter filter, @NotNull Project project) throws StorageException {
    IdFilter.FilterScopeType filteringScopeType = filter.getFilteringScopeType();
    IntSet hashMaskSet = null;
    long l = System.currentTimeMillis();

    @NotNull Path sessionProjectCacheFile = getSavedProjectFileValueIds(myLastScannedId,
                                                                        filteringScopeType == IdFilter.FilterScopeType.OTHER
                                                                        ? IdFilter.FilterScopeType.PROJECT_AND_LIBRARIES
                                                                        : filteringScopeType,
                                                                        project);
    int id = myKeyHashToVirtualFileMapping.getCurrentLength();

    final boolean useCachedHashIds = ENABLE_CACHED_HASH_IDS;
    if (useCachedHashIds && id == myLastScannedId && filter.getFilteringScopeType() == IdFilter.FilterScopeType.PROJECT_AND_LIBRARIES) {
      if (myInvalidatedSessionIds.remove(id) == null) {
        try {
          hashMaskSet = loadProjectHashes(sessionProjectCacheFile);
        }
        catch (IOException ignored) {
        }
      }
    }

    if (hashMaskSet == null) {
      if (useCachedHashIds && myLastScannedId != 0) {
        try {
          FileUtil.delete(sessionProjectCacheFile);
        }
        catch (NoSuchFileException ignored) {

        }
        catch (IOException e) {
          LOG.error(e);
        }
      }

      hashMaskSet = getSuitableKeyHashes(filter);

      if (useCachedHashIds && filteringScopeType != IdFilter.FilterScopeType.OTHER) {
        saveHashedIds(hashMaskSet, id, filteringScopeType, project);
      }
    }

    if (LOG.isDebugEnabled()) {
      LOG.debug("Scanned keyHashToVirtualFileMapping of " + myBaseStorageFile + " for " + (System.currentTimeMillis() - l));
    }

    return hashMaskSet;
  }

  private void appendKeyHashToVirtualFileMappingToLog(Key key, int inputId) throws StorageException {
    if (inputId == 0) return;
    try {
      withLock(() -> myKeyHashToVirtualFileMapping.append(new int[]{myKeyDescriptor.getHashCode(key), inputId}), false);
    }
    catch (IOException e) {
      throw new StorageException(e);
    }
    invalidateKeyHashToVirtualFileMappingCache();
  }

  public @NotNull IntSet getSuitableKeyHashes(@NotNull IdFilter idFilter) throws StorageException {
    try {
      doForce();

      Int2ObjectMap<IntSet> hash2inputIds = new Int2ObjectOpenHashMap<>(1000);
      AtomicInteger uselessRecords = new AtomicInteger();

      withLock(() -> {
        ProgressManager.checkCanceled();

        myKeyHashToVirtualFileMapping.processAll((offset, key) -> {
          ProgressManager.checkCanceled();
          int inputId = key[1];
          int absInputId = Math.abs(inputId);
          if (!idFilter.containsFileId(absInputId)) return true;
          int keyHash = key[0];
          if (inputId > 0) {
            if (!hash2inputIds.computeIfAbsent(keyHash, __ -> new IntOpenHashSet()).add(inputId)) {
              uselessRecords.incrementAndGet();
            }
          }
          else {
            IntSet inputIds = hash2inputIds.get(keyHash);
            if (inputIds != null) {
              inputIds.remove(absInputId);
              if (inputIds.isEmpty()) {
                hash2inputIds.remove(keyHash);
              }
            }
            uselessRecords.incrementAndGet();
          }
          return true;
        });
      }, true);

      if (uselessRecords.get() >= hash2inputIds.size()) {
        setRequiresCompaction();
      }

      return hash2inputIds.keySet();
    }
    catch (IOException e) {
      throw new StorageException(e);
    }
  }

  void force() throws IOException {
    if (myKeyHashToVirtualFileMapping.isDirty()) {
      doForce();
    }
  }

  private void doForce() throws IOException {
    withLock(() -> myKeyHashToVirtualFileMapping.force(), false);
  }

  @Override
  public void close() throws IOException {
    withLock(() -> {
      myKeyHashToVirtualFileMapping.close();
    }, false);
  }

  private void performCompaction() throws IOException {
    Int2ObjectMap<IntSet> data = new Int2ObjectOpenHashMap<>();
    Path oldDataFile = getDataFile();

    AppendableStorageBackedByResizableMappedFile<int[]> oldMapping = openMapping(oldDataFile, 0);
    try {
      oldMapping.processAll((offset, key) -> {
        int inputId = key[1];
        int keyHash = key[0];
        int absInputId = Math.abs(inputId);

        if (inputId > 0) {
          data.computeIfAbsent(keyHash, __ -> new IntOpenHashSet()).add(absInputId);
        }
        else {
          IntSet associatedInputIds = data.get(keyHash);
          if (associatedInputIds != null) {
            associatedInputIds.remove(absInputId);
          }
        }
        return true;
      });
    }
    finally {
      oldMapping.lockRead();
      try {
        oldMapping.close();
      }
      finally {
        oldMapping.unlockRead();
      }
    }

    String dataFileName = oldDataFile.getFileName().toString();
    String newDataFileName = "new." + dataFileName;
    Path newDataFile = oldDataFile.resolveSibling(newDataFileName);
    AppendableStorageBackedByResizableMappedFile<int[]> newMapping = openMapping(newDataFile, 32 * 2 * data.size());

    newMapping.lockWrite();
    try {
      try {
        for (Int2ObjectMap.Entry<IntSet> entry : data.int2ObjectEntrySet()) {
          int keyHash = entry.getIntKey();
          IntIterator inputIdIterator = entry.getValue().iterator();
          while (inputIdIterator.hasNext()) {
            int inputId = inputIdIterator.nextInt();
            newMapping.append(new int[]{keyHash, inputId});
          }
        }
      }
      finally {
        newMapping.close();
      }
    }
    finally {
      newMapping.unlockWrite();
    }

    IOUtil.deleteAllFilesStartingWith(oldDataFile);

    try (DirectoryStream<Path> paths = Files.newDirectoryStream(newDataFile.getParent())) {
      for (Path path : paths) {
        String name = path.getFileName().toString();
        if (name.startsWith(newDataFileName)) {
          FileUtil.rename(path.toFile(), dataFileName + name.substring(newDataFileName.length()));
        }
      }
    }

    try {
      Files.delete(getCompactionMarker());
    }
    catch (IOException ignored) {
    }
  }


  private static @NotNull IntSet loadProjectHashes(@NotNull Path fileWithCaches) throws IOException {
    try (DataInputStream inputStream = new DataInputStream(new BufferedInputStream(Files.newInputStream(fileWithCaches)))) {
      int capacity = DataInputOutputUtil.readINT(inputStream);
      IntSet hashMaskSet = new IntOpenHashSet(capacity);
      while (capacity > 0) {
        hashMaskSet.add(DataInputOutputUtil.readINT(inputStream));
        --capacity;
      }
      return hashMaskSet;
    }
  }

  private void saveHashedIds(@NotNull IntSet hashMaskSet, int largestId, @NotNull IdFilter.FilterScopeType scopeType, @NotNull Project project) {
    @NotNull Path newFileWithCaches = getSavedProjectFileValueIds(largestId, scopeType, project);

    boolean savedSuccessfully = true;
    try (com.intellij.util.io.DataOutputStream stream = new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(newFileWithCaches)))) {
      DataInputOutputUtil.writeINT(stream, hashMaskSet.size());
      IntIterator iterator = hashMaskSet.iterator();
      while (iterator.hasNext()) {
        DataInputOutputUtil.writeINT(stream, iterator.nextInt());
      }
    }
    catch (IOException ignored) {
      savedSuccessfully = false;
    }
    if (savedSuccessfully) {
      myLastScannedId = largestId;
    }
  }

  private static volatile Path mySessionDirectory;
  private static final Object mySessionDirectoryLock = new Object();

  private static Path getSessionDir() {
    Path sessionDirectory = mySessionDirectory;
    if (sessionDirectory == null) {
      synchronized (mySessionDirectoryLock) {
        sessionDirectory = mySessionDirectory;
        if (sessionDirectory == null) {
          try {
            mySessionDirectory = sessionDirectory = FileUtil
              .createTempDirectory(new File(PathManager.getTempPath()), Long.toString(System.currentTimeMillis()), "", true).toPath();
          }
          catch (IOException ex) {
            throw new RuntimeException("Can not create temp directory", ex);
          }
        }
      }
    }
    return sessionDirectory;
  }

  private @NotNull Path getSavedProjectFileValueIds(int id, @NotNull IdFilter.FilterScopeType scopeType, @NotNull Project project) {
    return getSessionDir().resolve(getDataFile().getFileName().toString() + "." + project.hashCode() + "." + id + "." + scopeType.getId());
  }

  private void invalidateKeyHashToVirtualFileMappingCache() {
    int lastScannedId = myLastScannedId;
    if (lastScannedId != 0) { // we have write lock
      myInvalidatedSessionIds.putIfAbsent(lastScannedId, Boolean.TRUE);
      myLastScannedId = 0;
    }
  }

  private <T extends Throwable> void withLock(ThrowableRunnable<T> r, boolean read) throws T {
    if (read) {
      myKeyHashToVirtualFileMapping.lockRead();
    }
    else {
      myKeyHashToVirtualFileMapping.lockWrite();
    }
    try {
      r.run();
    }
    finally {
      if (read) {
        myKeyHashToVirtualFileMapping.unlockRead();
      }
      else {
        myKeyHashToVirtualFileMapping.unlockWrite();
      }
    }
  }

  private void setRequiresCompaction() {
    Path marker = getCompactionMarker();
    if (Files.exists(marker)) {
      return;
    }
    try {
      Files.createDirectories(marker.getParent());
      Files.createFile(marker);
    }
    catch (FileAlreadyExistsException ignored) {
    }
    catch (IOException e) {
      LOG.error(e);
    }
  }

  @VisibleForTesting
  public boolean isRequiresCompaction() {
    return Files.exists(getCompactionMarker());
  }

  private @NotNull Path getCompactionMarker() {
    Path dataFile = getDataFile();
    return dataFile.resolveSibling(dataFile.getFileName().toString() + ".require.compaction");
  }

  private @NotNull Path getDataFile() {
    return myBaseStorageFile.resolveSibling(myBaseStorageFile.getFileName() + ".project");
  }

  private static final class IntPairInArrayKeyDescriptor implements DataExternalizer<int[]> {
    private static final IntPairInArrayKeyDescriptor INSTANCE = new IntPairInArrayKeyDescriptor();

    @Override
    public void save(@NotNull DataOutput out, int[] value) throws IOException {
      DataInputOutputUtil.writeINT(out, value[0]);
      DataInputOutputUtil.writeINT(out, value[1]);
    }

    @Override
    public int[] read(@NotNull DataInput in) throws IOException {
      return new int[]{DataInputOutputUtil.readINT(in), DataInputOutputUtil.readINT(in)};
    }
  }

  @SuppressWarnings("UseOfSystemOutOrSystemErr")
  public static void main(String[] args) throws Exception {
    String indexPath = args[0];
    EnumeratorStringDescriptor enumeratorStringDescriptor = EnumeratorStringDescriptor.INSTANCE;

    try (KeyHashLog<String> keyHashLog = new KeyHashLog<>(enumeratorStringDescriptor, Path.of(indexPath), false)) {
      IntSet allHashes = keyHashLog.getSuitableKeyHashes(new IdFilter() {
        @Override
        public boolean containsFileId(int id) {
          return true;
        }
      });

      for (Integer hash : allHashes) {
        System.out.println("key hash = " + hash);
      }
    }
  }
}
