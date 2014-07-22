/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.codeInspection.bytecodeAnalysis;

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.util.io.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;

import static com.intellij.codeInspection.bytecodeAnalysis.ProjectBytecodeAnalysis.LOG;

/**
 * @author lambdamix
 */
public class BytecodeAnalysisConverterImpl extends BytecodeAnalysisConverter {
  private static final String LOGIC_VERSION_KEY = "BytecodeAnalysisConverter.Logic";
  private static final int LOGIC_VERSION = 1;
  private static final String ENUMERATORS_VERSION_KEY = "BytecodeAnalysisConverter.Enumerators";

  private PersistentStringEnumerator myNamesEnumerator;
  private PersistentEnumeratorDelegate<int[]> myCompoundKeyEnumerator;
  private int version;

  @Override
  public void initComponent() {

    final File keysDir = new File(PathManager.getIndexRoot(), "bytecodekeys");
    final File namesFile = new File(keysDir, "names");
    final File compoundKeysFile = new File(keysDir, "compound");

    final int previousLogicVersion = PropertiesComponent.getInstance().getOrInitInt(LOGIC_VERSION_KEY, 0);
    version = PropertiesComponent.getInstance().getOrInitInt(ENUMERATORS_VERSION_KEY, 0);

    if (previousLogicVersion != LOGIC_VERSION) {
      IOUtil.deleteAllFilesStartingWith(keysDir);
      version++;
    }

    try {
      IOUtil.openCleanOrResetBroken(new ThrowableComputable<Void, IOException>() {
        @Override
        public Void compute() throws IOException {
          myNamesEnumerator = new PersistentStringEnumerator(namesFile, true);
          myCompoundKeyEnumerator = new IntArrayPersistentEnumerator(compoundKeysFile, new IntArrayKeyDescriptor());
          return null;
        }
      }, new Runnable() {
        @Override
        public void run() {
          LOG.info("Error during initialization of enumerators in bytecode analysis. Re-initializing.");
          IOUtil.deleteAllFilesStartingWith(keysDir);
          version++;
        }
      });
    }
    catch (IOException e) {
      LOG.error("Re-initialization of enumerators in bytecode analysis failed.", e);
    }
    PropertiesComponent.getInstance().setValue(ENUMERATORS_VERSION_KEY, String.valueOf(version));
    PropertiesComponent.getInstance().setValue(LOGIC_VERSION_KEY, String.valueOf(LOGIC_VERSION));
  }

  @Override
  public void disposeComponent() {
    try {
      myNamesEnumerator.close();
      myCompoundKeyEnumerator.close();
    }
    catch (IOException e) {
      LOG.debug(e);
    }
  }

  public int getVersion() {
    return version;
  }

  @Override
  protected int enumerateString(@NotNull String s) throws IOException {
    return myNamesEnumerator.enumerate(s);
  }

  @Override
  protected int enumerateCompoundKey(@NotNull int[] key) throws IOException {
    return myCompoundKeyEnumerator.enumerate(key);
  }

  private static class IntArrayKeyDescriptor implements KeyDescriptor<int[]>, DifferentSerializableBytesImplyNonEqualityPolicy {

    @Override
    public void save(@NotNull DataOutput out, int[] value) throws IOException {
      DataInputOutputUtil.writeINT(out, value.length);
      for (int i : value) {
        DataInputOutputUtil.writeINT(out, i);
      }
    }

    @Override
    public int[] read(@NotNull DataInput in) throws IOException {
      int[] value = new int[DataInputOutputUtil.readINT(in)];
      for (int i = 0; i < value.length; i++) {
        value[i] = DataInputOutputUtil.readINT(in);
      }
      return value;
    }

    @Override
    public int getHashCode(int[] value) {
      return Arrays.hashCode(value);
    }

    @Override
    public boolean isEqual(int[] val1, int[] val2) {
      return Arrays.equals(val1, val2);
    }
  }

  private static class IntArrayPersistentEnumerator extends PersistentEnumeratorDelegate<int[]> {
    private final CachingEnumerator<int[]> myCache;

    public IntArrayPersistentEnumerator(File compoundKeysFile, IntArrayKeyDescriptor descriptor) throws IOException {
      super(compoundKeysFile, descriptor, 1024 * 4);
      myCache = new CachingEnumerator<int[]>(new DataEnumerator<int[]>() {
        @Override
        public int enumerate(@Nullable int[] value) throws IOException {
          return IntArrayPersistentEnumerator.super.enumerate(value);
        }

        @Nullable
        @Override
        public int[] valueOf(int idx) throws IOException {
          return IntArrayPersistentEnumerator.super.valueOf(idx);
        }
      }, descriptor);
    }

    @Override
    public int enumerate(@Nullable int[] value) throws IOException {
      return myCache.enumerate(value);
    }

    @Nullable
    @Override
    public int[] valueOf(int idx) throws IOException {
      return myCache.valueOf(idx);
    }
  }
}
