// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.impl.storage;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootModificationTracker;
import com.intellij.openapi.util.IntRef;
import com.intellij.util.containers.hash.EqualityPolicy;
import com.intellij.util.indexing.IdFilter;
import com.intellij.util.indexing.StorageException;
import com.intellij.util.io.EnumeratorStringDescriptor;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntIterator;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.VisibleForTesting;

import java.io.Closeable;
import java.io.Flushable;
import java.io.IOException;
import java.lang.ref.SoftReference;
import java.nio.file.Path;
import java.util.function.IntPredicate;

import static com.intellij.util.SystemProperties.getBooleanProperty;

/** A data structure to store key hashes to virtual file id mappings */
@ApiStatus.Internal
public final class KeyHashLog<Key> implements Closeable, Flushable {
  private static final Logger LOG = Logger.getInstance(KeyHashLog.class);

  /// Enable caching key-hashes for [IdFilter.FilterScopeType#PROJECT_AND_LIBRARIES]
  @VisibleForTesting
  public static final boolean ENABLE_CACHED_HASH_IDS = getBooleanProperty("idea.index.cashed.hashids", true);

  private final @NotNull EqualityPolicy<Key> myKeyEqualityPolicy;

  private final @NotNull KeyHashLogStorage myStorage;
  private final @NotNull KeyHashLogCheckpointStore myCheckpointStore;

  private final @NotNull PerProjectKeyHashes myPerProjectKeyHashes = new PerProjectKeyHashes();

  /// Takes ownership of the supplied storage and closes it with the log
  public KeyHashLog(@NotNull EqualityPolicy<Key> keyEqualityPolicy,
                    @NotNull KeyHashLogStorage storage,
                    @NotNull KeyHashLogCheckpointStore checkpointStore) throws IOException {
    this(keyEqualityPolicy, storage, checkpointStore, /*compact: */ true);
  }

  private KeyHashLog(@NotNull EqualityPolicy<Key> keyEqualityPolicy,
                     @NotNull KeyHashLogStorage storage,
                     @NotNull KeyHashLogCheckpointStore checkpointStore,
                     boolean compact) throws IOException {
    myKeyEqualityPolicy = keyEqualityPolicy;
    myStorage = storage;
    myCheckpointStore = checkpointStore;
    try {
      if (compact && storage.isCompactionRequired()) {
        performCompaction();
      }
    }
    catch (IOException | RuntimeException | Error e) {
      try {
        storage.close();
      }
      catch (IOException closeError) {
        e.addSuppressed(closeError);
      }
      throw e;
    }
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
      LOG.debug("Scanned keyHashToVirtualFileMapping of " + myStorage + " for " + (System.currentTimeMillis() - startedAt));
    }

    return keyHashPredicate;
  }

  private void appendKeyHashToVirtualFileMappingToLog(Key key, int inputId) throws StorageException {
    if (inputId == 0) return;
    try {
      int keyHash = myKeyEqualityPolicy.getHashCode(key);
      myStorage.append(keyHash, inputId);
    }
    catch (IOException e) {
      throw new StorageException(e);
    }
  }

  public @NotNull IntSet getSuitableKeyHashes(@NotNull IdFilter idFilter) throws StorageException {
    try {
      return getSuitableKeyHashes(idFilter, myStorage.endPosition());
    }
    catch (IOException e) {
      throw new StorageException(e);
    }
  }

  private @NotNull IntSet getSuitableKeyHashes(@NotNull IdFilter idFilter,
                                               @NotNull KeyHashLogStorage.Position snapshot) throws StorageException {
    ProgressManager.checkCanceled();
    try {
      Int2ObjectMap<IntSet> hash2inputIds = new Int2ObjectOpenHashMap<>(1000);
      IntRef uselessRecords = new IntRef(0);

      boolean completed = myStorage.scan(KeyHashLogStorage.Position.START, snapshot, (keyHash, inputId) -> {
        //Throttle the check (probability of any 5-bit pattern in a good hash is ~1/32)
        if ((keyHash & 0b11111) == 0) ProgressManager.checkCanceled();

        int absInputId = Math.abs(inputId);
        if (!idFilter.containsFileId(absInputId)) return true;


        if (inputId > 0) {
          if (!hash2inputIds.computeIfAbsent(keyHash, _ -> new IntOpenHashSet(4)).add(inputId)) {
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

      if (!completed) throw new IOException("The key hash log scan did not complete");
      if (uselessRecords.get() >= hash2inputIds.size()) {
        myStorage.setCompactionRequired();
      }

      return hash2inputIds.keySet();
    }
    catch (IOException e) {
      throw new StorageException(e);
    }
  }

  @Override
  public void flush() throws IOException {
    myStorage.flush();
  }

  @Override
  public void close() throws IOException {
    myStorage.close();
    myPerProjectKeyHashes.clear();
  }

  private void performCompaction() throws IOException {
    Int2ObjectMap<IntSet> data = new Int2ObjectOpenHashMap<>();
    boolean completed = myStorage.scan(KeyHashLogStorage.Position.START, myStorage.endPosition(), (keyHash, inputId) -> {
      int absInputId = Math.abs(inputId);
      if (inputId > 0) {
        data.computeIfAbsent(keyHash, _ -> new IntOpenHashSet()).add(absInputId);
      }
      else {
        IntSet associatedInputIds = data.get(keyHash);
        if (associatedInputIds != null) {
          associatedInputIds.remove(absInputId);
        }
      }
      return true;
    });
    if (!completed) throw new IOException("The key hash log compaction scan did not complete");
    myStorage.clear();
    for (Int2ObjectMap.Entry<IntSet> entry : data.int2ObjectEntrySet()) {
      int keyHash = entry.getIntKey();
      IntIterator inputIdIterator = entry.getValue().iterator();
      while (inputIdIterator.hasNext()) {
        myStorage.append(keyHash, inputIdIterator.nextInt());
      }
    }
    myStorage.flush();
  }

  @VisibleForTesting
  public boolean isRequiresCompaction() {
    return myStorage.isCompactionRequired();
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
      KeyHashLogStorage.Position snapshot;
      try {
        snapshot = myStorage.endPosition();
      }
      catch (IOException e) {
        throw new StorageException(e);
      }
      InternalState state = stateRef.get();
      if (state == null || state.project != project) {
        int checkpointId = project.hashCode();
        try {
          var checkpoint = myCheckpointStore.load(checkpointId);
          state = new InternalState(project, checkpointId, checkpoint.keyHashes(), checkpoint.scannedUpTo(),
                                    checkpoint.modificationCount(), checkpoint.removesUnaccountedFor());
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
          !myStorage.isPositionInRange(state.scannedUpTo, snapshot)) {
        state = createState(project, filter, snapshot, currentRootsModificationCount);
      }
      else if (!state.scannedUpTo.equals(snapshot)) {
        try {
          state.updateFromTail(myStorage, myCheckpointStore, filter, snapshot);
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
                                               @NotNull KeyHashLogStorage.Position snapshot,
                                               long rootsModificationCount) throws StorageException {
      var state = new InternalState(
        project,
        project.hashCode(),
        new IntOpenHashSet(getSuitableKeyHashes(filter, snapshot)),
        snapshot,
        rootsModificationCount,
        /*removesUnaccountedFor: */ 0
      );
      state.save(myCheckpointStore);
      return state;
    }

    private synchronized void clear() {
      stateRef.clear();
    }

    /// Keeps an in-memory hash-mask [#keyHashes], and a disk checkpoint [#scannedUpTo] that can restore it after
    /// the soft reference is cleared.
    /// The mask is per-project.
    ///
    /// The hash-mask is not exact: it is exact on creation, but incremental update could only add key-hashes, never remove them,
    /// so the hash-mask only grows. Functionally it is still correct: hash-mask allows false-positives by definition -- but the
    /// rate of false-positives increases with incremental updates, so the mask needs periodical re-creation to remain reasonably
    /// effective -- [#removesUnaccountedFor] is a gauge for that.
    private static final class InternalState {
      private final @NotNull Project project;
      private final int checkpointId;

      private final @NotNull IntSet keyHashes;

      /// The prefix identified by this position is already accounted for
      private @NotNull KeyHashLogStorage.Position scannedUpTo;

      /// `ProjectRootModificationTracker.getInstance(project).getModificationCount()` at creation
      private final long rootsModificationCount;

      /// Incremental updates keep removed hashes because another file can still use the same hash
      private int removesUnaccountedFor;

      private InternalState(@NotNull Project project,
                            int checkpointId,
                            @NotNull IntSet keyHashes,
                            @NotNull KeyHashLogStorage.Position scannedUpTo,
                            long rootsModificationCount,
                            int removesUnaccountedFor) {
        this.project = project;
        this.checkpointId = checkpointId;
        this.keyHashes = keyHashes;
        this.scannedUpTo = scannedUpTo;
        this.rootsModificationCount = rootsModificationCount;
        this.removesUnaccountedFor = removesUnaccountedFor;
      }

      private void save(@NotNull KeyHashLogCheckpointStore checkpointStore) {
        try {
          checkpointStore.save(checkpointId, new KeyHashLogCheckpointStore.Checkpoint(
            scannedUpTo, rootsModificationCount, removesUnaccountedFor, keyHashes
          ));
        }
        catch (IOException ignored) {
          //MAYBE delete the file to clean up after the failure?
        }
      }

      private void updateFromTail(@NotNull KeyHashLogStorage storage,
                                  @NotNull KeyHashLogCheckpointStore checkpointStore,
                                  @NotNull IdFilter filter,
                                  @NotNull KeyHashLogStorage.Position snapshot) throws IOException {
        var addedHashes = new IntOpenHashSet();
        var removed = new IntRef(0);
        boolean completed = storage.scan(scannedUpTo, snapshot, (keyHash, inputId) -> {
          if ((keyHash & 0b11111) == 0) ProgressManager.checkCanceled();
          if (!filter.containsFileId(Math.abs(inputId))) return true;
          if (inputId > 0) {
            addedHashes.add(keyHash);
          }
          else {
            removed.inc();
          }
          return true;
        });
        if (!completed) throw new IOException("The key hash log tail scan did not complete");
        // Publish the state only after the scan succeeds, so a retry cannot count removals twice.
        keyHashes.addAll(addedHashes);
        removesUnaccountedFor += removed.get();
        scannedUpTo = snapshot;
        save(checkpointStore);
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

    Path baseStorageFile = Path.of(indexPath);
    try (KeyHashLog<String> keyHashLog = new KeyHashLog<>(
      enumeratorStringDescriptor,
      new FileKeyHashLogStorage(baseStorageFile, null),
      new FileKeyHashLogCheckpointStore(baseStorageFile),
      /* compact: */ false
    )) {
      IntSet allHashes = keyHashLog.getSuitableKeyHashes(IdFilter.ACCEPT_ALL);

      for (Integer hash : allHashes) {
        System.out.println("key hash = " + hash);
      }
    }
  }
}
