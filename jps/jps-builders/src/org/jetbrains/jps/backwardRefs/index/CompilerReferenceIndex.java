// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.jps.backwardRefs.index;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.LowMemoryWatcher;
import com.intellij.openapi.util.ShutDownTracker;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.CommonProcessors;
import com.intellij.util.Processor;
import com.intellij.util.indexing.IndexExtension;
import com.intellij.util.indexing.IndexId;
import com.intellij.util.indexing.InvertedIndex;
import com.intellij.util.indexing.impl.IndexStorage;
import com.intellij.util.indexing.impl.MapIndexStorage;
import com.intellij.util.indexing.impl.MapReduceIndex;
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
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class CompilerReferenceIndex<Input> {
  private final static Logger LOG = Logger.getInstance(CompilerReferenceIndex.class);

  private final static String FILE_ENUM_TAB = "file.path.enum.tab";
  private final static String NAME_ENUM_TAB = "name.tab";

  private static final String VERSION_FILE = "version";
  private final ConcurrentMap<IndexId<?, ?>, InvertedIndex<?, ?, Input>> myIndices;
  private final NameEnumerator myNameEnumerator;
  private final PersistentStringEnumerator myFilePathEnumerator;
  private final File myBuildDir;
  private final File myIndicesDir;
  private final LowMemoryWatcher myLowMemoryWatcher = LowMemoryWatcher.register(new Runnable() {
    @Override
    public void run() {
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
    }
  });
  private volatile Throwable myRebuildRequestCause;

  public CompilerReferenceIndex(Collection<? extends IndexExtension<?, ?, ? super Input>> indices, File buildDir, boolean readOnly,
                                int version) {
    this(indices, buildDir, null, readOnly, version);
  }

  public CompilerReferenceIndex(Collection<? extends IndexExtension<?, ?, ? super Input>> indices, File buildDir,
                                @Nullable PathRelativizerService relativizer, boolean readOnly, int version) {
    myBuildDir = buildDir;
    myIndicesDir = getIndexDir(buildDir);
    if (!myIndicesDir.exists() && !myIndicesDir.mkdirs()) {
      throw new RuntimeException("Can't create dir: " + buildDir.getAbsolutePath());
    }
    try {
      if (versionDiffers(buildDir, version)) {
        saveVersion(buildDir, version);
      }
      myFilePathEnumerator = new PersistentStringEnumerator(new File(myIndicesDir, FILE_ENUM_TAB)) {
        @Override
        public int enumerate(String path) throws IOException {
          String caseAwarePath = convertToCaseAwarePath(path);
          if (relativizer != null) {
            return super.enumerate(relativizer.toRelative(caseAwarePath));
          }
          return super.enumerate(caseAwarePath);
        }

        @Nullable
        @Override
        public String valueOf(int idx) throws IOException {
          String path = super.valueOf(idx);
          if (relativizer != null && path != null) {
            return convertToCaseAwarePath(relativizer.toFull(path));
          }
          return path;
        }

        @NotNull
        private String convertToCaseAwarePath(@NotNull String path) {
          return SystemInfo.isFileSystemCaseSensitive ? path : StringUtil.toLowerCase(path);
        }
      };

      myIndices = new ConcurrentHashMap<>();
      for (IndexExtension<?, ?, ? super Input> indexExtension : indices) {
        //noinspection unchecked
        myIndices.put(indexExtension.getName(), new CompilerMapReduceIndex(indexExtension, myIndicesDir, readOnly));
      }

      ShutDownTracker.getInstance().registerShutdownTask(() -> {
        for (IndexId<?, ?> id : myIndices.keySet()) {
          //noinspection UseOfSystemOutOrSystemErr
          System.err.println("Leaked javac compiler index \"" + id.getName() + "\"");
        }
      });

      myNameEnumerator = new NameEnumerator(new File(myIndicesDir, NAME_ENUM_TAB));
    }
    catch (IOException e) {
      removeIndexFiles(myBuildDir, e);
      throw new BuildDataCorruptedException(e);
    }
  }

  public Collection<InvertedIndex<?, ?, Input>> getIndices() {
    return myIndices.values();
  }

  public <K, V> InvertedIndex<K, V, Input> get(IndexId<K, V> key) {
    //noinspection unchecked
    return (InvertedIndex<K, V, Input>)myIndices.get(key);
  }

  @NotNull
  public NameEnumerator getByteSeqEum() {
    return myNameEnumerator;
  }

  @NotNull
  public PersistentStringEnumerator getFilePathEnumerator() {
    return myFilePathEnumerator;
  }

  public void close() {
    myLowMemoryWatcher.stop();
    final CommonProcessors.FindFirstProcessor<Exception> exceptionProc =
      new CommonProcessors.FindFirstProcessor<Exception>() {
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

  public static void removeIndexFiles(File buildDir) {
    removeIndexFiles(buildDir, null);
  }

  public static void removeIndexFiles(File buildDir, Throwable cause) {
    final File indexDir = getIndexDir(buildDir);
    if (indexDir.exists()) {
      FileUtil.delete(indexDir);
      LOG.info("backward reference index deleted", cause != null ? cause : new Exception());
    }
  }

  private static File getIndexDir(@NotNull File buildDir) {
    return new File(buildDir, "backward-refs");
  }

  public static boolean exists(@NotNull File buildDir) {
    return getIndexDir(buildDir).exists();
  }

  public static boolean versionDiffers(@NotNull File buildDir, int expectedVersion) {
    File versionFile = new File(getIndexDir(buildDir), VERSION_FILE);

    if (!versionFile.exists()) {
      LOG.info("backward reference index version doesn't exist");
      return true;
    }

    try (DataInputStream is = new DataInputStream(new FileInputStream(versionFile))) {
      int currentIndexVersion = is.readInt();
      boolean isDiffer = currentIndexVersion != expectedVersion;
      if (isDiffer) {
        LOG.info("backward reference index version differ, expected = " + expectedVersion + ", current = " + currentIndexVersion);
      }
      return isDiffer;
    }
    catch (IOException e) {
      LOG.info("backward reference index version differ due to: " + e.getClass());
    }
    return true;
  }

  public void saveVersion(@NotNull File buildDir, int version) {
    File versionFile = new File(getIndexDir(buildDir), VERSION_FILE);

    FileUtil.createIfDoesntExist(versionFile);
    try (DataOutputStream os = new DataOutputStream(new FileOutputStream(versionFile))) {
      os.writeInt(version);
    }
    catch (IOException ex) {
      LOG.error(ex);
      throw new BuildDataCorruptedException(ex);
    }
  }

  public Throwable getRebuildRequestCause() {
    return myRebuildRequestCause;
  }

  public File getIndicesDir() {
    return myIndicesDir;
  }

  public void setRebuildRequestCause(Throwable e) {
    myRebuildRequestCause = e;
    LOG.error(e);
  }

  private static void close(InvertedIndex<?, ?, ?> index, CommonProcessors.FindFirstProcessor<? super Exception> exceptionProcessor) {
    try {
      index.dispose();
    }
    catch (Exception e) {
      exceptionProcessor.process(e);
    }
  }

  private static void close(Closeable closeable, Processor<? super Exception> exceptionProcessor) {
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

  class CompilerMapReduceIndex<Key, Value> extends MapReduceIndex<Key, Value, Input> {
    CompilerMapReduceIndex(@NotNull final IndexExtension<Key, Value, Input> extension,
                           @NotNull final File indexDir,
                           boolean readOnly)
      throws IOException {
      super(extension,
            createIndexStorage(extension.getKeyDescriptor(), extension.getValueExternalizer(), extension.getName(), indexDir, readOnly),
            readOnly ? null : new PersistentMapBasedForwardIndex(new File(indexDir, extension.getName().getName() + ".inputs")),
            readOnly ? null : new KeyCollectionForwardIndexAccessor<>(extension));
    }

    @Override
    public void checkCanceled() {

    }

    @Override
    protected void requestRebuild(@NotNull Throwable e) {
      setRebuildRequestCause(e);
    }
  }

  private static <Key, Value> IndexStorage<Key, Value> createIndexStorage(@NotNull KeyDescriptor<Key> keyDescriptor,
                                                                          @NotNull DataExternalizer<Value> valueExternalizer,
                                                                          @NotNull IndexId<Key, Value> indexId,
                                                                          @NotNull File indexDir,
                                                                          boolean readOnly) throws IOException {
    return new MapIndexStorage<Key, Value>(new File(indexDir, indexId.getName()),
                                           keyDescriptor,
                                           valueExternalizer,
                                           16 * 1024,
                                           false,
                                           true,
                                           readOnly,
                                           null) {
      @Override
      public void checkCanceled() {
        //TODO
      }
    };
  }
}
