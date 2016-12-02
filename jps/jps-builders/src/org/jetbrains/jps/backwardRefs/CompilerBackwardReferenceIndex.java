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
package org.jetbrains.jps.backwardRefs;

import com.intellij.openapi.util.LowMemoryWatcher;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.CommonProcessors;
import com.intellij.util.Processor;
import com.intellij.util.indexing.*;
import com.intellij.util.indexing.impl.*;
import com.intellij.util.io.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.backwardRefs.index.CompiledFileData;
import org.jetbrains.jps.backwardRefs.index.CompilerIndices;
import org.jetbrains.jps.builders.storage.BuildDataCorruptedException;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class CompilerBackwardReferenceIndex {
  private final static String FILE_ENUM_TAB = "file.path.enum.tab";
  private final static String NAME_ENUM_TAB = "name.tab";

  private static final String VERSION_FILE = ".version";
  private final Map<ID<?, ?>, InvertedIndex<?, ?, CompiledFileData>> myIndices;
  private final ByteArrayEnumerator myNameEnumerator;
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
  private volatile boolean myRebuildRequired;

  public CompilerBackwardReferenceIndex(File buildDir) {
    myIndicesDir = getIndexDir(buildDir);
    if (!myIndicesDir.exists() && !myIndicesDir.mkdirs()) {
      throw new RuntimeException("Can't create dir: " + buildDir.getAbsolutePath());
    }
    try {
      if (versionDiffers(buildDir)) {
        FileUtil.writeToFile(new File(myIndicesDir, VERSION_FILE), String.valueOf(CompilerIndices.VERSION));
      }
      myFilePathEnumerator = new PersistentStringEnumerator(new File(myIndicesDir, FILE_ENUM_TAB)) {
        @Override
        public int enumerate(@Nullable String value) throws IOException {
          return super.enumerate(SystemInfo.isFileSystemCaseSensitive ? value : value.toLowerCase(Locale.ROOT));
        }
      };

      myIndices = new HashMap<ID<?, ?>, InvertedIndex<?, ?, CompiledFileData>>();
      for (IndexExtension<LightRef, ?, CompiledFileData> indexExtension : CompilerIndices.getIndices()) {
        //noinspection unchecked
        myIndices.put(indexExtension.getName(), new CompilerMapReduceIndex(indexExtension, myIndicesDir));
      }

      myNameEnumerator = new ByteArrayEnumerator(new File(myIndicesDir, NAME_ENUM_TAB));
    }
    catch (IOException e) {
      removeIndexFiles(myIndicesDir);
      throw new BuildDataCorruptedException(e);
    }
  }

  Collection<InvertedIndex<?, ?, CompiledFileData>> getIndices() {
    return myIndices.values();
  }

  public <K, V> InvertedIndex<K, V, CompiledFileData> get(ID<K, V> key) {
    //noinspection unchecked
    return (InvertedIndex<K, V, CompiledFileData>)myIndices.get(key);
  }

  @NotNull
  public ByteArrayEnumerator getByteSeqEum() {
    return myNameEnumerator;
  }

  @NotNull
  public PersistentStringEnumerator getFilePathEnumerator() {
    return myFilePathEnumerator;
  }

  public void close() {
    myLowMemoryWatcher.stop();
    final CommonProcessors.FindFirstProcessor<BuildDataCorruptedException> exceptionProc =
      new CommonProcessors.FindFirstProcessor<BuildDataCorruptedException>();
    close(myFilePathEnumerator, exceptionProc);
    close(myNameEnumerator, exceptionProc);
    for (InvertedIndex<?, ?, CompiledFileData> index : myIndices.values()) {
      close(index, exceptionProc);
    }
    final BuildDataCorruptedException exception = exceptionProc.getFoundValue();
    if (exception != null) {
      removeIndexFiles(myIndicesDir);
      throw exception;
    }
    if (myRebuildRequired) {
      removeIndexFiles(myIndicesDir);
    }
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
      return Integer.parseInt(FileUtil.loadFile(versionFile)) != CompilerIndices.VERSION;
    }
    catch (final IOException e) {
      return true;
    }
  }

  private static void close(InvertedIndex<?, ?, CompiledFileData> index, CommonProcessors.FindFirstProcessor<BuildDataCorruptedException> exceptionProcessor) {
    try {
      index.dispose();
    }
    catch (BuildDataCorruptedException e) {
      exceptionProcessor.process(e);
    }
  }

  private static void close(Closeable closeable, Processor<BuildDataCorruptedException> exceptionProcessor) {
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
                                  @NotNull final File indexDir)
      throws IOException {
      super(extension,
            createIndexStorage(extension.getKeyDescriptor(), extension.getValueExternalizer(), extension.getName(), indexDir),
            new MapBasedForwardIndex<Key, Value>(extension) {
              @NotNull
              @Override
              public PersistentHashMap<Integer, Collection<Key>> createMap() throws IOException {
                ID<Key, Value> id = extension.getName();
                return new PersistentHashMap<Integer, Collection<Key>>(new File(indexDir, id + ".inputs"),
                                                                       EnumeratorIntegerDescriptor.INSTANCE,
                                                                       new InputIndexDataExternalizer<Key>(extension.getKeyDescriptor(),
                                                                                                           id));
              }
            });
    }

    @Override
    public void checkCanceled() {

    }

    @Override
    protected void requestRebuild(Exception e) {
      myRebuildRequired = true;
      throw new BuildDataCorruptedException(e);
    }
  }

  private static <Key, Value> IndexStorage<Key, Value> createIndexStorage(@NotNull KeyDescriptor<Key> keyDescriptor,
                                                                          @NotNull DataExternalizer<Value> valueExternalizer,
                                                                          @NotNull ID<Key, Value> indexId,
                                                                          @NotNull File indexDir) throws IOException {
    return new MapIndexStorage<Key, Value>(new File(indexDir, indexId.toString()),
                                           keyDescriptor,
                                           valueExternalizer,
                                           16 * 1024,
                                           false) {
      @Override
      public void checkCanceled() {
        //TODO
      }
    };
  }
}
