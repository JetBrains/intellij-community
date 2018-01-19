// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.jps.backwardRefs.index;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.LowMemoryWatcher;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.CommonProcessors;
import com.intellij.util.Processor;
import com.intellij.util.indexing.IndexExtension;
import com.intellij.util.indexing.IndexId;
import com.intellij.util.indexing.InvertedIndex;
import com.intellij.util.indexing.impl.*;
import com.intellij.util.io.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.backwardRefs.NameEnumerator;
import org.jetbrains.jps.builders.storage.BuildDataCorruptedException;

import java.io.*;
import java.io.DataOutputStream;
import java.util.Collection;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class CompilerReferenceIndex<Input> {
  private final static Logger LOG = Logger.getInstance(CompilerReferenceIndex.class);

  private final static String FILE_ENUM_TAB = "file.path.enum.tab";
  private final static String NAME_ENUM_TAB = "name.tab";

  private final Map<IndexId<?, ?>, InvertedIndex<?, ?, Input>> myIndices;
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
  private volatile Throwable myRebuildRequestCause;
  private final CompilerIndexDescriptor<? extends Input> myDescriptor;

  public CompilerReferenceIndex(CompilerIndexDescriptor<? extends Input> descriptor,
                                File buildDir, boolean readOnly) {
    myDescriptor = descriptor;
    myIndicesDir = descriptor.getIndicesDir(buildDir);
    if (!myIndicesDir.exists() && !myIndicesDir.mkdirs()) {
      throw new RuntimeException("Can't create dir: " + buildDir.getAbsolutePath());
    }
    try {
      if (CompilerReferenceIndexUtil.indexVersionDiffers(buildDir, myDescriptor)) {
        saveVersion(buildDir);
      }
      myFilePathEnumerator = new PersistentStringEnumerator(new File(myIndicesDir, FILE_ENUM_TAB)) {
        @Override
        public int enumerate(String value) throws IOException {
          return super.enumerate(SystemInfo.isFileSystemCaseSensitive ? value : value.toLowerCase(Locale.ROOT));
        }
      };

      myIndices = new HashMap<>();
      for (IndexExtension<?, ?, ? extends Input> indexExtension : descriptor.getIndices()) {
        //noinspection unchecked
        myIndices.put(indexExtension.getName(), new CompilerMapReduceIndex(indexExtension, myIndicesDir, readOnly));
      }

      myNameEnumerator = new NameEnumerator(new File(myIndicesDir, NAME_ENUM_TAB));
    }
    catch (IOException e) {
      CompilerReferenceIndexUtil.removeIndexFiles(myIndicesDir, myDescriptor);
      throw new BuildDataCorruptedException(e);
    }
  }

  public Collection<InvertedIndex<?, ?, Input>> getIndices() {
    return myIndices.values();
  }

  public <K, V> InvertedIndex<K, V, ? extends Input> get(IndexId<K, V> key) {
    //noinspection unchecked
    return (InvertedIndex<K, V, ? extends Input>)myIndices.get(key);
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
    for (InvertedIndex<?, ?, ?> index : myIndices.values()) {
      close(index, exceptionProc);
    }
    final Exception exception = exceptionProc.getFoundValue();
    if (exception != null) {
      CompilerReferenceIndexUtil.removeIndexFiles(myIndicesDir, myDescriptor);
      if (myRebuildRequestCause == null) {
        throw new RuntimeException(exception);
      }
      return;
    }
    if (myRebuildRequestCause != null) {
      CompilerReferenceIndexUtil.removeIndexFiles(myIndicesDir, myDescriptor);
    }
  }

  public Throwable getRebuildRequestCause() {
    return myRebuildRequestCause;
  }

  public File getIndicesDir() {
    return myIndicesDir;
  }

  public void saveVersion(@NotNull File buildDir) {
    File versionFile = myDescriptor.getVersionFile(buildDir);

    try {
      FileUtil.createIfDoesntExist(versionFile);
      final DataOutputStream os = new DataOutputStream(new FileOutputStream(versionFile));
      try {
        os.writeInt(myDescriptor.getVersion());
      }
      finally {
        os.close();
      }
    }
    catch (IOException ex) {
      LOG.error(ex);
      throw new BuildDataCorruptedException(ex);
    }
  }

  public void setRebuildRequestCause(Throwable e) {
    myRebuildRequestCause = e;
  }

  public CompilerIndexDescriptor<? extends Input> getDescriptor() {
    return myDescriptor;
  }

  private static void close(InvertedIndex<?, ?, ?> index, CommonProcessors.FindFirstProcessor<Exception> exceptionProcessor) {
    try {
      index.dispose();
    }
    catch (RuntimeException e) {
      exceptionProcessor.process(e);
    }
  }

  private static void close(Closeable closeable, Processor<Exception> exceptionProcessor) {
    //noinspection SynchronizationOnLocalVariableOrMethodParameter
    synchronized (closeable) {
      try {
        closeable.close();
      }
      catch (IOException e) {
        exceptionProcessor.process(new BuildDataCorruptedException(e));
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
    protected void requestRebuild(Throwable e) {
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
