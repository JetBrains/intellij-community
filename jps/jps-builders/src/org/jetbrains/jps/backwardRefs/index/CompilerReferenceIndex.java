// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.backwardRefs.index;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.LowMemoryWatcher;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.util.io.NioFiles;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.CommonProcessors;
import com.intellij.util.ExceptionUtil;
import com.intellij.util.Processor;
import com.intellij.util.indexing.IndexExtension;
import com.intellij.util.indexing.IndexId;
import com.intellij.util.indexing.InvertedIndex;
import com.intellij.util.indexing.StorageException;
import com.intellij.util.indexing.impl.IndexStorage;
import com.intellij.util.indexing.impl.MapIndexStorage;
import com.intellij.util.indexing.impl.MapReduceIndex;
import com.intellij.util.indexing.impl.forward.ForwardIndex;
import com.intellij.util.indexing.impl.forward.ForwardIndexAccessor;
import com.intellij.util.indexing.impl.forward.KeyCollectionForwardIndexAccessor;
import com.intellij.util.indexing.impl.forward.PersistentMapBasedForwardIndex;
import com.intellij.util.io.DataExternalizer;
import com.intellij.util.io.KeyDescriptor;
import com.intellij.util.io.PersistentStringEnumerator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.backwardRefs.NameEnumerator;
import org.jetbrains.jps.builders.storage.BuildDataCorruptedException;
import org.jetbrains.jps.incremental.relativizer.PathRelativizerService;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class CompilerReferenceIndex<Input> {
  private static final Logger LOG = Logger.getInstance(CompilerReferenceIndex.class);

  private static final String FILE_ENUM_TAB = "file.path.enum.tab";
  private static final String NAME_ENUM_TAB = "name.tab";

  private static final String VERSION_FILE = "version";

  private final ConcurrentMap<IndexId<?, ?>, InvertedIndex<?, ?, Input>> myIndices;
  private final NameEnumerator myNameEnumerator;
  private final PersistentStringEnumerator myFilePathEnumerator;
  private final Path myBuildDir;
  private final Path indexDir;
  private final LowMemoryWatcher myLowMemoryWatcher = LowMemoryWatcher.register(() -> force());

  private volatile Throwable myRebuildRequestCause;

  public CompilerReferenceIndex(Collection<? extends IndexExtension<?, ?, ? super Input>> indices,
                                Path buildDir,
                                boolean readOnly,
                                int version) {
    this(indices, buildDir, null, readOnly, version);
  }

  /**
   * @deprecated Use {@link #CompilerReferenceIndex(Collection, Path, PathRelativizerService, boolean, int)}
   */
  @SuppressWarnings("IO_FILE_USAGE")
  @Deprecated
  public CompilerReferenceIndex(Collection<? extends IndexExtension<?, ?, ? super Input>> indices,
                                File buildDir,
                                boolean readOnly,
                                int version) {
    this(indices, buildDir.toPath(), null, readOnly, version);
  }

  public CompilerReferenceIndex(Collection<? extends IndexExtension<?, ?, ? super Input>> indices,
                                Path buildDir,
                                @Nullable PathRelativizerService relativizer,
                                boolean readOnly,
                                int version) {
    this(indices, buildDir, relativizer, readOnly, version, SystemInfo.isFileSystemCaseSensitive);
  }

  public CompilerReferenceIndex(Collection<? extends IndexExtension<?, ?, ? super Input>> indices,
                                Path buildDir,
                                @Nullable PathRelativizerService relativizer,
                                boolean readOnly,
                                int version,
                                boolean isCaseSensitive) {
    myBuildDir = buildDir;
    indexDir = getIndexDir(buildDir);

    try {
      Files.createDirectories(indexDir);
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }

    try {
      if (versionDiffers(buildDir, version)) {
        saveVersion(buildDir, version);
      }
      myFilePathEnumerator = new PersistentStringEnumerator(indexDir.resolve(FILE_ENUM_TAB)) {

        @Override
        public int enumerate(String path) throws IOException {
          String caseAwarePath = convertToCaseAwarePath(path, isCaseSensitive);
          if (relativizer != null) {
            return super.enumerate(relativizer.toRelative(caseAwarePath));
          }
          return super.enumerate(caseAwarePath);
        }

        @Override
        public @Nullable String valueOf(int idx) throws IOException {
          String path = super.valueOf(idx);
          if (relativizer != null && path != null) {
            return convertToCaseAwarePath(relativizer.toFull(path), isCaseSensitive);
          }
          return path;
        }

        private @NotNull String convertToCaseAwarePath(@NotNull String path, boolean isCaseSensitive) {
          return isCaseSensitive ? path : StringUtil.toLowerCase(path);
        }
      };

      myIndices = new ConcurrentHashMap<>();
      for (IndexExtension<?, ?, ? super Input> indexExtension : indices) {
        myIndices.put(indexExtension.getName(), createCompilerIndex(indexExtension, readOnly));
      }

      myNameEnumerator = new NameEnumerator(indexDir.resolve(NAME_ENUM_TAB).toFile());
    }
    catch (IOException e) {
      //IJPL-2855: must close all storages opened
      Exception closeException = ExceptionUtil.runAndCatch(
        this::close
      );
      if (closeException != null) {
        e.addSuppressed(closeException);
      }

      removeIndexFiles(myBuildDir, e);
      throw new BuildDataCorruptedException(e);
    }
    catch (Throwable t) {
      //IJPL-2855: must always close all storages opened
      Exception closeException = ExceptionUtil.runAndCatch(
        this::close
      );
      if (closeException != null) {
        t.addSuppressed(closeException);
      }

      removeIndexFiles(myBuildDir, t);
      throw t;
    }
  }

  public void force() {
    synchronized (myNameEnumerator) {
      if (!myNameEnumerator.isClosed()) {
        myNameEnumerator.force();
      }
    }
    synchronized (myFilePathEnumerator) {
      if (!myFilePathEnumerator.isClosed()) {
        myFilePathEnumerator.force();
      }
    }

    for (InvertedIndex<?, ?, Input> index : myIndices.values()) {
      try {
        index.flush();
      }
      catch (StorageException e) {
        LOG.error(e);
      }
    }
  }

  public Collection<InvertedIndex<?, ?, Input>> getIndices() {
    return myIndices.values();
  }

  public <K, V> InvertedIndex<K, V, Input> get(IndexId<K, V> key) {
    //noinspection unchecked
    return (InvertedIndex<K, V, Input>)myIndices.get(key);
  }

  public @NotNull NameEnumerator getByteSeqEum() {
    return myNameEnumerator;
  }

  public @NotNull PersistentStringEnumerator getFilePathEnumerator() {
    return myFilePathEnumerator;
  }

  public void close() {
    myLowMemoryWatcher.stop();
    CommonProcessors.FindFirstProcessor<Exception> exceptionProc = new CommonProcessors.FindFirstProcessor<>() {
      @Override
      public boolean process(Exception e) {
        LOG.error(e);
        return super.process(e);
      }
    };
    close(myFilePathEnumerator, exceptionProc);
    close(myNameEnumerator, exceptionProc);
    for (Iterator<Map.Entry<IndexId<?, ?>, InvertedIndex<?, ?, Input>>> iterator = myIndices.entrySet().iterator(); iterator.hasNext(); ) {
      Map.Entry<IndexId<?, ?>, InvertedIndex<?, ?, Input>> index = iterator.next();
      close(index.getValue(), exceptionProc);
      iterator.remove();
    }
    final Exception exception = exceptionProc.getFoundValue();
    if (exception != null) {
      removeIndexFiles(myBuildDir, exception);
      if (myRebuildRequestCause == null) {
        throw new RuntimeException(exception);
      }
      return;
    }
    if (myRebuildRequestCause != null) {
      removeIndexFiles(myBuildDir, myRebuildRequestCause);
    }
  }

  public static void removeIndexFiles(@NotNull Path buildDir) {
    removeIndexFiles(buildDir, null);
  }

  /**
   * @deprecated Use {@link #removeIndexFiles(Path)}
   */
  @SuppressWarnings("IO_FILE_USAGE")
  @Deprecated
  public static void removeIndexFiles(@NotNull File buildDir) {
    removeIndexFiles(buildDir.toPath(), null);
  }

  public void saveVersion(@NotNull Path buildDir, int version) {
    Path versionFile = getIndexDir(buildDir).resolve(VERSION_FILE);
    try {
      NioFiles.createParentDirectories(versionFile);
    }
    catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    try (DataOutputStream os = new DataOutputStream(Files.newOutputStream(versionFile))) {
      os.writeInt(version);
    }
    catch (IOException ex) {
      LOG.error(ex);
      throw new BuildDataCorruptedException(ex);
    }
  }

  /**
   * @deprecated Use {@link #getIndexDir()}
   */
  @SuppressWarnings("IO_FILE_USAGE")
  @Deprecated
  public @NotNull File getIndicesDir() {
    return indexDir.toFile();
  }

  public @NotNull Path getIndexDir() {
    return indexDir;
  }

  private <Key, Value> @NotNull CompilerMapReduceIndex<Key, Value> createCompilerIndex(@NotNull IndexExtension<Key, Value, ? super Input> indexExtension,
                                                                                       boolean readOnly) throws IOException {
    IndexStorage<Key, Value> indexStorage = createIndexStorage(
      indexExtension.getKeyDescriptor(),
      indexExtension.getValueExternalizer(),
      indexExtension.getName(),
      indexDir,
      readOnly
    );
    try {
      if (readOnly) {
        //noinspection unchecked,rawtypes
        return new CompilerMapReduceIndex(indexExtension, indexStorage, /* forwardIndex: */ null, /* forwardIndexAccessor: */ null);
      }
      else {
        Path storagePath = indexDir.resolve(indexExtension.getName().getName() + ".inputs");
        ForwardIndex forwardIndex = new PersistentMapBasedForwardIndex(storagePath, /* readOnly: */ false);
        try {
          ForwardIndexAccessor<Key, Value> forwardIndexAccessor = new KeyCollectionForwardIndexAccessor<>(indexExtension);
          //noinspection unchecked,rawtypes
          return new CompilerMapReduceIndex(indexExtension, indexStorage, forwardIndex, forwardIndexAccessor);
        }
        catch (Throwable t) {//IJPL-2855: must close all storages opened
          forwardIndex.close();
          throw t;
        }
      }
    }
    catch (Throwable t) {//IJPL-2855: must close all storages opened
      indexStorage.close();
      throw t;
    }
  }

  public static void removeIndexFiles(Path buildDir, Throwable cause) {
    Path indexDir = getIndexDir(buildDir);
    if (Files.exists(indexDir)) {
      try {
        FileUtilRt.deleteRecursively(indexDir);
        LOG.info("backward reference index deleted", cause != null ? cause : new Exception());
      }
      catch (Throwable e) {
        LOG.info("failed to delete backward reference index", e);
      }
    }
  }

  private static @NotNull Path getIndexDir(@NotNull Path buildDir) {
    return buildDir.resolve("backward-refs");
  }

  public Throwable getRebuildRequestCause() {
    return myRebuildRequestCause;
  }

  public static boolean exists(@NotNull Path buildDir) {
    return Files.exists(getIndexDir(buildDir));
  }

  /**
   * @deprecated Use {@link #exists(Path)}
   */
  @SuppressWarnings("IO_FILE_USAGE")
  @Deprecated
  public static boolean exists(@NotNull File buildDir) {
    return exists(buildDir.toPath());
  }

  public void setRebuildRequestCause(Throwable e) {
    myRebuildRequestCause = e;
    LOG.error(e);
  }

  private static void close(@NotNull InvertedIndex<?, ?, ?> index,
                            @NotNull CommonProcessors.FindFirstProcessor<? super Exception> exceptionProcessor) {
    try {
      index.dispose();
    }
    catch (Exception e) {
      exceptionProcessor.process(e);
    }
  }

  private static void close(@Nullable Closeable closeable,
                            @NotNull Processor<? super Exception> exceptionProcessor) {
    if (closeable == null) return;
    //noinspection SynchronizationOnLocalVariableOrMethodParameter
    synchronized (closeable) {
      try {
        closeable.close();
      }
      catch (IOException e) {
        exceptionProcessor.process(new BuildDataCorruptedException(e));
      }
      catch (Exception e) {
        exceptionProcessor.process(e);
      }
    }
  }

  final class CompilerMapReduceIndex<Key, Value> extends MapReduceIndex<Key, Value, Input> {
    CompilerMapReduceIndex(@NotNull IndexExtension<Key, Value, Input> extension,
                           @NotNull IndexStorage<Key, Value> storage,
                           @Nullable ForwardIndex index,
                           @Nullable ForwardIndexAccessor<Key, Value> accessor) throws IOException {
      super(extension, storage, index, accessor);
    }

    @Override
    public void checkCanceled() {

    }

    @Override
    protected void requestRebuild(@NotNull Throwable e) {
      setRebuildRequestCause(e);
    }
  }

  /**
   * @deprecated Use {@link #versionDiffers(Path, int)}
   */
  @SuppressWarnings("IO_FILE_USAGE")
  @Deprecated
  public static boolean versionDiffers(@NotNull File buildDir, int expectedVersion) {
    return versionDiffers(buildDir.toPath(), expectedVersion);
  }

  public static boolean versionDiffers(@NotNull Path buildDir, int expectedVersion) {
    Path versionFile = getIndexDir(buildDir).resolve(VERSION_FILE);
    try (DataInputStream is = new DataInputStream(Files.newInputStream(versionFile))) {
      int currentIndexVersion = is.readInt();
      boolean isDiffer = currentIndexVersion != expectedVersion;
      if (isDiffer) {
        LOG.info("backward reference index version differ, expected = " + expectedVersion + ", current = " + currentIndexVersion);
      }
      return isDiffer;
    }
    catch (NoSuchFileException ignore) {
      LOG.info("backward reference index version doesn't exist");
    }
    catch (IOException e) {
      LOG.info("backward reference index version differ due to: " + e.getClass());
    }
    return true;
  }

  private static <Key, Value> IndexStorage<Key, Value> createIndexStorage(@NotNull KeyDescriptor<Key> keyDescriptor,
                                                                          @NotNull DataExternalizer<Value> valueExternalizer,
                                                                          @NotNull IndexId<Key, Value> indexId,
                                                                          @NotNull Path indexDir,
                                                                          boolean readOnly) throws IOException {
    return new MapIndexStorage<>(indexDir.resolve(indexId.getName()),
                                 keyDescriptor,
                                 valueExternalizer,
                                 16 * 1024,
                                 false,
                                 true,
                                 false,
                                 readOnly,
                                 null);
  }
}
