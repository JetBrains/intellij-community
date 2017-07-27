/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.backwardRefs;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.LowMemoryWatcher;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.CommonProcessors;
import com.intellij.util.Function;
import com.intellij.util.Processor;
import com.intellij.util.indexing.IndexExtension;
import com.intellij.util.indexing.IndexId;
import com.intellij.util.indexing.InvertedIndex;
import com.intellij.util.indexing.impl.*;
import com.intellij.util.io.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.backwardRefs.index.CompiledFileData;
import org.jetbrains.backwardRefs.index.CompilerIndices;

import java.io.*;
import java.io.DataOutputStream;
import java.util.Collection;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public abstract class CompilerBackwardReferenceIndex {
  private final static Logger LOG = Logger.getInstance(CompilerBackwardReferenceIndex.class);

  private final static String FILE_ENUM_TAB = "file.path.enum.tab";
  private final static String NAME_ENUM_TAB = "name.tab";

  private static final String VERSION_FILE = "version";
  private final Map<IndexId<?, ?>, InvertedIndex<?, ?, CompiledFileData>> myIndices;
  private final NameEnumerator myNameEnumerator;
  private final PersistentStringEnumerator myFilePathEnumerator;
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
  private volatile Exception myRebuildRequestCause;

  public CompilerBackwardReferenceIndex(File buildDir, boolean readOnly) {
    myIndicesDir = getIndexDir(buildDir);
    if (!myIndicesDir.exists() && !myIndicesDir.mkdirs()) {
      throw new RuntimeException("Can't create dir: " + buildDir.getAbsolutePath());
    }
    try {
      if (versionDiffers(buildDir)) {
        saveVersion(buildDir);
      }
      myFilePathEnumerator = new PersistentStringEnumerator(new File(myIndicesDir, FILE_ENUM_TAB)) {
        @Override
        public int enumerate(String value) throws IOException {
          return super.enumerate(SystemInfo.isFileSystemCaseSensitive ? value : value.toLowerCase(Locale.ROOT));
        }
      };

      myIndices = new HashMap<>();
      for (IndexExtension<?, ?, CompiledFileData> indexExtension : CompilerIndices.getIndices(
        new Function<IOException, RuntimeException>() {
          @Override
          public RuntimeException fun(IOException e) {
            return createBuildDataCorruptedException(e);
          }
        })) {
        //noinspection unchecked
        myIndices.put(indexExtension.getName(), new CompilerMapReduceIndex(indexExtension, myIndicesDir, readOnly));
      }

      myNameEnumerator = new NameEnumerator(new File(myIndicesDir, NAME_ENUM_TAB));
    }
    catch (IOException e) {
      removeIndexFiles(myIndicesDir);
      throw createBuildDataCorruptedException(e);
    }
  }

  @NotNull
  protected abstract RuntimeException createBuildDataCorruptedException(IOException cause);

  Collection<InvertedIndex<?, ?, CompiledFileData>> getIndices() {
    return myIndices.values();
  }

  public <K, V> InvertedIndex<K, V, CompiledFileData> get(IndexId<K, V> key) {
    //noinspection unchecked
    return (InvertedIndex<K, V, CompiledFileData>)myIndices.get(key);
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
      new CommonProcessors.FindFirstProcessor<>();
    close(myFilePathEnumerator, exceptionProc);
    close(myNameEnumerator, exceptionProc);
    for (InvertedIndex<?, ?, CompiledFileData> index : myIndices.values()) {
      close(index, exceptionProc);
    }
    final Exception exception = exceptionProc.getFoundValue();
    if (exception != null) {
      removeIndexFiles(myIndicesDir);
      if (myRebuildRequestCause == null) {
        throw new RuntimeException(exception);
      }
      return;
    }
    if (myRebuildRequestCause != null) {
      removeIndexFiles(myIndicesDir);
    }
  }

  Exception getRebuildRequestCause() {
    return myRebuildRequestCause;
  }

  public File getIndicesDir() {
    return myIndicesDir;
  }

  public static void removeIndexFiles(File buildDir) {
    final File indexDir = getIndexDir(buildDir);
    if (indexDir.exists()) {
      FileUtil.delete(indexDir);
    }
  }

  private static File getIndexDir(@NotNull File buildDir) {
    return new File(buildDir, "backward-refs");
  }

  public static boolean exist(@NotNull File buildDir) {
    return getIndexDir(buildDir).exists();
  }

  public static boolean versionDiffers(@NotNull File buildDir) {
    File versionFile = new File(getIndexDir(buildDir), VERSION_FILE);

    try {
      final DataInputStream is = new DataInputStream(new FileInputStream(versionFile));
      try {
        int currentIndexVersion = is.readInt();
        boolean isDiffer = currentIndexVersion != CompilerIndices.VERSION;
        if (isDiffer) {
          LOG.info("backward reference index version differ, expected = " + CompilerIndices.VERSION + ", current = " + currentIndexVersion);
        }
        return isDiffer;
      }
      finally {
        is.close();
      }
    }
    catch (IOException ignored) {
      LOG.info("backward reference index version differ due to: " + ignored.getClass());
    }
    return true;
  }

  public void saveVersion(@NotNull File buildDir) {
    File versionFile = new File(getIndexDir(buildDir), VERSION_FILE);

    try {
      FileUtil.createIfDoesntExist(versionFile);
      final DataOutputStream os = new DataOutputStream(new FileOutputStream(versionFile));
      try {
        os.writeInt(CompilerIndices.VERSION);
      }
      finally {
        os.close();
      }
    }
    catch (IOException ex) {
      LOG.error(ex);
      throw createBuildDataCorruptedException(ex);
    }
  }

  void setRebuildRequestCause(Exception e) {
    myRebuildRequestCause = e;
  }

  private static void close(InvertedIndex<?, ?, CompiledFileData> index, CommonProcessors.FindFirstProcessor<Exception> exceptionProcessor) {
    try {
      index.dispose();
    }
    catch (RuntimeException e) {
      exceptionProcessor.process(e);
    }
  }

  private void close(Closeable closeable, Processor<Exception> exceptionProcessor) {
    //noinspection SynchronizationOnLocalVariableOrMethodParameter
    synchronized (closeable) {
      try {
        closeable.close();
      }
      catch (IOException e) {
        exceptionProcessor.process(createBuildDataCorruptedException(e));
      }
    }
  }

  class CompilerMapReduceIndex<Key, Value> extends MapReduceIndex<Key, Value, CompiledFileData> {
    public CompilerMapReduceIndex(@NotNull final IndexExtension<Key, Value, CompiledFileData> extension,
                                  @NotNull final File indexDir,
                                  boolean readOnly)
      throws IOException {
      super(extension,
            createIndexStorage(extension.getKeyDescriptor(), extension.getValueExternalizer(), extension.getName(), indexDir, readOnly),
            readOnly ? null : new KeyCollectionBasedForwardIndex<Key, Value>(extension) {
              @NotNull
              @Override
              public PersistentHashMap<Integer, Collection<Key>> createMap() throws IOException {
                IndexId<Key, Value> id = getIndexExtension().getName();
                return new PersistentHashMap<>(new File(indexDir, id.getName() + ".inputs"),
                                               EnumeratorIntegerDescriptor.INSTANCE,
                                               new InputIndexDataExternalizer<>(extension.getKeyDescriptor(),
                                                                                id));
              }
            });
    }

    @Override
    public void checkCanceled() {

    }

    @Override
    protected void requestRebuild(Exception e) {
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
                                           readOnly) {
      @Override
      public void checkCanceled() {
        //TODO
      }
    };
  }
}
