// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.impl.storage;

import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootModificationTracker;
import com.intellij.openapi.util.IntRef;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.ThrowableRunnable;
import com.intellij.util.indexing.IdFilter;
import com.intellij.util.indexing.StorageException;
import com.intellij.util.io.DataExternalizer;
import com.intellij.util.io.DataInputOutputUtil;
import com.intellij.util.io.DataOutputStream;
import com.intellij.util.io.EnumeratorStringDescriptor;
import com.intellij.util.io.IOUtil;
import com.intellij.util.io.KeyDescriptor;
import com.intellij.util.io.StorageLockContext;
import com.intellij.util.io.keyStorage.AppendableObjectStorage;
import com.intellij.util.io.keyStorage.AppendableStorageBackedByResizableMappedFile;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntIterator;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.VisibleForTesting;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.Closeable;
import java.io.DataInput;
import java.io.DataInputStream;
import java.io.DataOutput;
import java.io.File;
import java.io.IOException;
import java.lang.ref.SoftReference;
import java.nio.file.DirectoryStream;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntPredicate;

import static com.intellij.util.SystemProperties.getBooleanProperty;

/**
 * A data structure to store key hashes to virtual file id mappings.
 */
@ApiStatus.Internal
public final class KeyHashLog<Key> implements Closeable {
  private static final Logger LOG = Logger.getInstance(KeyHashLog.class);

  /// Enable caching key-hashes for [IdFilter.FilterScopeType#PROJECT_AND_LIBRARIES]
  @VisibleForTesting
  public static final boolean ENABLE_CACHED_HASH_IDS = getBooleanProperty("idea.index.cashed.hashids", true);

  private static final AtomicInteger CACHE_FILE_IDS = new AtomicInteger();

  private final @NotNull KeyDescriptor<Key> myKeyDescriptor;
  private final @NotNull Path myBaseStorageFile;
  private final @Nullable StorageLockContext myStorageLockContext;
  private final @NotNull AppendableObjectStorage<int[]> myKeyHashToVirtualFileMapping;


  private final long myCacheFileId = CACHE_FILE_IDS.incrementAndGet();
  private final @NotNull PerProjectKeyHashes myPerProjectKeyHashes = new PerProjectKeyHashes();

  public KeyHashLog(@NotNull KeyDescriptor<Key> descriptor, @NotNull Path baseStorageFile) throws IOException {
    this(descriptor, baseStorageFile, null);
  }

  /** Shares the index storage lock context with key-hash mapping files opened next to the main index storage. */
  public KeyHashLog(@NotNull KeyDescriptor<Key> descriptor,
                    @NotNull Path baseStorageFile,
                    @Nullable StorageLockContext storageLockContext) throws IOException {
    this(descriptor, baseStorageFile, storageLockContext, /*compact: */true);
  }

  private KeyHashLog(@NotNull KeyDescriptor<Key> descriptor,
                     @NotNull Path baseStorageFile,
                     @Nullable StorageLockContext storageLockContext,
                     boolean compact) throws IOException {
    myKeyDescriptor = descriptor;
    myBaseStorageFile = baseStorageFile;
    myStorageLockContext = storageLockContext;
    if (compact && isRequiresCompaction()) {
      performCompaction();
    }
    myKeyHashToVirtualFileMapping = openMapping(getDataFile(), /*size: */ 4096, myStorageLockContext);
  }

  private static @NotNull AppendableStorageBackedByResizableMappedFile<int[]> openMapping(@NotNull Path dataFile,
                                                                                          int size,
                                                                                          @Nullable StorageLockContext storageLockContext)
    throws IOException {
    return new AppendableStorageBackedByResizableMappedFile<>(dataFile,
                                                              size,
                                                              storageLockContext,
                                                              /*pageSize: */ IOUtil.MiB,
                                                              /*valuesAreAligned: */ true,
                                                              IntPairAsArrayExternalizer.INSTANCE);
  }

  public void addKeyHashToVirtualFileMapping(Key key, int inputId) throws StorageException {
    appendKeyHashToVirtualFileMappingToLog(key, inputId);
  }

  public void removeKeyHashToVirtualFileMapping(Key key, int inputId) throws StorageException {
    appendKeyHashToVirtualFileMappingToLog(key, -inputId);
  }

  /// @return a predicate that accepts `key.hash` if the key is likely present in files that belong to `project` and match `filter`
  public @NotNull IntPredicate getSuitableKeyHashes(@NotNull IdFilter filter,
                                                    @NotNull Project project) throws StorageException {
    if (!ENABLE_CACHED_HASH_IDS || filter.getFilteringScopeType() != IdFilter.FilterScopeType.PROJECT_AND_LIBRARIES) {
      IntSet keyHashes = getSuitableKeyHashes(filter);
      return keyHashes::contains;
    }

    long startedAt = System.currentTimeMillis();
    IntPredicate keyHashPredicate = myPerProjectKeyHashes.getKeyHashPredicate(project, filter);

    if (LOG.isDebugEnabled()) {
      LOG.debug("Scanned keyHashToVirtualFileMapping of " + myBaseStorageFile + " for " + (System.currentTimeMillis() - startedAt));
    }

    return keyHashPredicate;
  }

  private void appendKeyHashToVirtualFileMappingToLog(Key key, int inputId) throws StorageException {
    if (inputId == 0) return;
    try {
      withLock(
        () -> myKeyHashToVirtualFileMapping.append(new int[]{myKeyDescriptor.getHashCode(key), inputId}),
        /* read: */ false
      );
    }
    catch (IOException e) {
      throw new StorageException(e);
    }
  }

  public @NotNull IntSet getSuitableKeyHashes(@NotNull IdFilter idFilter) throws StorageException {
    ProgressManager.checkCanceled();
    try {
      Int2ObjectMap<IntSet> hash2inputIds = new Int2ObjectOpenHashMap<>(1000);
      IntRef uselessRecords = new IntRef(0);

      myKeyHashToVirtualFileMapping.processAll((offset, key) -> {
        int keyHash = key[0];
        int inputId = key[1];

        //Throttle the check (probability of any 5-bit pattern in a good hash is ~1/32)
        if ((keyHash & 0b11111) == 0) ProgressManager.checkCanceled();

        int absInputId = Math.abs(inputId);
        if (!idFilter.containsFileId(absInputId)) return true;


        if (inputId > 0) {
          if (!hash2inputIds.computeIfAbsent(keyHash, __ -> new IntOpenHashSet(4)).add(inputId)) {
            uselessRecords.inc();
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
          uselessRecords.inc();
        }
        return true;
      });

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
    withLock(() -> myKeyHashToVirtualFileMapping.force(), /*read: */ false);
  }

  @Override
  public void close() throws IOException {
    withLock(
      myKeyHashToVirtualFileMapping::close,
      /*read: */ false
    );
    myPerProjectKeyHashes.clear();
  }

  private void performCompaction() throws IOException {
    Int2ObjectMap<IntSet> data = new Int2ObjectOpenHashMap<>();
    Path oldDataFile = getDataFile();

    AppendableStorageBackedByResizableMappedFile<int[]> oldMapping = openMapping(oldDataFile, 0, myStorageLockContext);
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
    AppendableStorageBackedByResizableMappedFile<int[]> newMapping = openMapping(newDataFile, 32 * 2 * data.size(), myStorageLockContext);

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

  private @NotNull Path pathForCachedKeyHashesPerProject(@NotNull Project project) {
    return getSessionDir().resolve(getDataFile().getFileName().toString() + "." + myCacheFileId + "." + project.hashCode());
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

  private static final class IntPairAsArrayExternalizer implements DataExternalizer<int[]> {
    private static final IntPairAsArrayExternalizer INSTANCE = new IntPairAsArrayExternalizer();

    @Override
    public void save(@NotNull DataOutput out, int[] value) throws IOException {
      DataInputOutputUtil.writeINT(out, value[0]);
      DataInputOutputUtil.writeINT(out, value[1]);
    }

    /// This externalizer is used _only_ privately in this class => we can be sure returned array doesn't leak
    /// from this class, and thread-local caching is ok:
    private static final ThreadLocal<int[]> PAIR = ThreadLocal.withInitial(() -> new int[2]);

    @Override
    public int[] read(@NotNull DataInput in) throws IOException {
      int[] result = PAIR.get();
      result[0] = DataInputOutputUtil.readINT(in);
      result[1] = DataInputOutputUtil.readINT(in);
      return result;
    }
  }


  /// Keeps an in-memory hash-mask , and a disk checkpoint that can restore it after the soft reference is cleared.
  /// The mask is per-project.
  /// 
  /// The actual hash-mask structure is in [InternalState] class, while this class keeps [SoftReference], and provides
  /// locking for multithreaded access;
  private final class PerProjectKeyHashes {
    private @NotNull SoftReference<InternalState> stateRef = new SoftReference<>(null);

    private synchronized @NotNull IntPredicate getKeyHashPredicate(@NotNull Project project,
                                                                   @NotNull IdFilter filter) throws StorageException {
      int currentFileLength = myKeyHashToVirtualFileMapping.getCurrentLength();
      InternalState state = stateRef.get();
      if (state == null || state.project != project) {
        Path cacheFile = pathForCachedKeyHashesPerProject(project);
        try {
          state = InternalState.load(project, cacheFile, currentFileLength);
        }
        catch (IOException ignored) {
          state = null;
        }
      }

      //IdFilter[PROJECT_AND_LIBRARIES] could change, and we need to account for its changes
      long currentRootsModificationCount = ProjectRootModificationTracker.getInstance(project).getModificationCount();
      if (state == null ||
          state.rootsModificationCount != currentRootsModificationCount ||
          state.needsFullRescan() ||
          state.scannedUpToOffsetExclusive > currentFileLength) {
        state = createState(project, filter, currentFileLength, currentRootsModificationCount);
      }
      else if (state.scannedUpToOffsetExclusive < currentFileLength) {
        try {
          state.updateFromTail(myKeyHashToVirtualFileMapping, filter, currentFileLength);
        }
        catch (IOException e) {
          throw new StorageException(e);
        }
      }
      stateRef = new SoftReference<>(state);
      InternalState selectedState = state;
      return keyHash -> contains(selectedState, keyHash);
    }

    private synchronized boolean contains(@NotNull InternalState state, int keyHash) {
      return state.keyHashes.contains(keyHash);
    }

    private @NotNull InternalState createState(@NotNull Project project,
                                               @NotNull IdFilter filter,
                                               int currentFileLength,
                                               long rootsModificationCount) throws StorageException {
      var state = new InternalState(
        project,
        pathForCachedKeyHashesPerProject(project),
        new IntOpenHashSet(KeyHashLog.this.getSuitableKeyHashes(filter)),
        currentFileLength,
        rootsModificationCount
      );
      state.save();
      return state;
    }

    private synchronized void clear() {
      stateRef.clear();
    }

    /// Keeps an in-memory hash-mask [#keyHashes], and a disk checkpoint [#scannedUpToOffsetExclusive] that can restore it after
    /// the soft reference is cleared.
    /// The mask is per-project.
    ///
    /// The hash-mask is not exact: it is exact on creation, but incremental update could only add key-hashes, never remove them,
    /// so the hash-mask only grows. Functionally it is still correct: hash-mask allows false-positives by definition -- but the
    /// rate of false-positives increases with incremental updates, so the mask needs periodical re-creation to remain reasonably
    /// effective -- [#removesUnaccountedFor] is a gauge for that.
    private static final class InternalState {
      private static final int STORE_FORMAT_VERSION = 2;

      private final @NotNull Project project;
      private final @NotNull Path cacheFile;

      private final @NotNull IntSet keyHashes;

      /// The range `[0, scannedUpToOffsetExclusive)` of the main log is already accounted for
      private long scannedUpToOffsetExclusive;

      /// `ProjectRootModificationTracker.getInstance(project).getModificationCount()` at creation
      private final long rootsModificationCount;

      /// Incremental updates keep removed hashes because another file can still use the same hash
      private int removesUnaccountedFor;

      private InternalState(@NotNull Project project,
                            @NotNull Path cacheFile,
                            @NotNull IntSet keyHashes,
                            long scannedUpToOffsetExclusive,
                            long rootsModificationCount) {
        this(project, cacheFile, keyHashes, scannedUpToOffsetExclusive, rootsModificationCount, 0);
      }

      private InternalState(@NotNull Project project,
                            @NotNull Path cacheFile,
                            @NotNull IntSet keyHashes,
                            long scannedUpToOffsetExclusive,
                            long rootsModificationCount,
                            int removesUnaccountedFor) {
        this.project = project;
        this.cacheFile = cacheFile;
        this.keyHashes = keyHashes;
        this.scannedUpToOffsetExclusive = scannedUpToOffsetExclusive;
        this.rootsModificationCount = rootsModificationCount;
        this.removesUnaccountedFor = removesUnaccountedFor;
      }

      private static @NotNull InternalState load(@NotNull Project project,
                                                 @NotNull Path cacheFile,
                                                 long currentFileLength) throws IOException {
        try (var input = new DataInputStream(new BufferedInputStream(Files.newInputStream(cacheFile)))) {
          int formatVersion = DataInputOutputUtil.readINT(input);
          if (formatVersion != STORE_FORMAT_VERSION) {
            throw new IOException("Unsupported project key hashes cache version: " + formatVersion);
          }

          long scannedUpToOffsetExclusive = DataInputOutputUtil.readLONG(input);
          if (scannedUpToOffsetExclusive < 0 || scannedUpToOffsetExclusive > currentFileLength) {
            throw new IOException("Invalid scanned offset: " + scannedUpToOffsetExclusive + ", current log length: " + currentFileLength);
          }

          long rootsModificationCount = DataInputOutputUtil.readLONG(input);
          int removesUnaccountedFor = DataInputOutputUtil.readINT(input);
          int hashesCount = DataInputOutputUtil.readINT(input);
          if (removesUnaccountedFor < 0 || removesUnaccountedFor > currentFileLength ||
              hashesCount < 0 || hashesCount > currentFileLength) {
            throw new IOException("Invalid project key hashes cache counters");
          }

          var keyHashes = new IntOpenHashSet(hashesCount);
          for (int i = 0; i < hashesCount; i++) {
            keyHashes.add(DataInputOutputUtil.readINT(input));
          }
          return new InternalState(project, cacheFile, keyHashes, scannedUpToOffsetExclusive, rootsModificationCount, removesUnaccountedFor);
        }
      }

      private void save() {
        try (var output = new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(cacheFile)))) {
          DataInputOutputUtil.writeINT(output, STORE_FORMAT_VERSION);
          DataInputOutputUtil.writeLONG(output, scannedUpToOffsetExclusive);
          DataInputOutputUtil.writeLONG(output, rootsModificationCount);
          DataInputOutputUtil.writeINT(output, removesUnaccountedFor);
          DataInputOutputUtil.writeINT(output, keyHashes.size());
          IntIterator iterator = keyHashes.iterator();
          while (iterator.hasNext()) {
            DataInputOutputUtil.writeINT(output, iterator.nextInt());
          }
        }
        catch (IOException ignored) {
          //MAYBE delete the file to clean up after the failure?
        }
      }

      private void updateFromTail(@NotNull AppendableObjectStorage<int[]> mapping,
                                  @NotNull IdFilter filter,
                                  int currentFileLength) throws IOException {
        mapping.lockRead();
        try {
          int offset = Math.toIntExact(scannedUpToOffsetExclusive);
          while (offset < currentFileLength) {
            int[] mappingEntry = mapping.read(offset, false);
            int keyHash = mappingEntry[0];
            int inputId = mappingEntry[1];
            offset += DataInputOutputUtil.sizeOfVarint(keyHash) + DataInputOutputUtil.sizeOfVarint(inputId);

            if ((keyHash & 0b11111) == 0) ProgressManager.checkCanceled();

            if (!filter.containsFileId(Math.abs(inputId))) continue;
            if (inputId > 0) {
              keyHashes.add(keyHash);
            }
            else {
              removesUnaccountedFor++;
            }
          }
          scannedUpToOffsetExclusive = currentFileLength;
        }
        finally {
          mapping.unlockRead();
        }
        save();
      }

      private boolean needsFullRescan() {
        //heuristics:
        return removesUnaccountedFor > Math.max(64, keyHashes.size() / 2);
      }
    }
  }


  @SuppressWarnings("UseOfSystemOutOrSystemErr")
  static void main(String[] args) throws Exception {
    String indexPath = args[0];
    EnumeratorStringDescriptor enumeratorStringDescriptor = EnumeratorStringDescriptor.INSTANCE;

    try (KeyHashLog<String> keyHashLog = new KeyHashLog<>(enumeratorStringDescriptor, Path.of(indexPath), null, false)) {
      IntSet allHashes = keyHashLog.getSuitableKeyHashes(IdFilter.ACCEPT_ALL);

      for (Integer hash : allHashes) {
        System.out.println("key hash = " + hash);
      }
    }
  }
}
