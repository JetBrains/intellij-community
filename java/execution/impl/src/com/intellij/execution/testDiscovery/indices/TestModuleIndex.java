// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.testDiscovery.indices;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.io.*;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public final class TestModuleIndex {
  private static final Logger LOG = Logger.getInstance(TestModuleIndex.class);

  private final PersistentHashMap<Integer, IntList> myTestNameToRunModule;
  private final PersistentEnumerator<String> myModuleNameEnumerator;

  public TestModuleIndex(@NotNull Path basePath, @NotNull PersistentObjectSeq persistentObjectSeq) throws IOException {
    Path moduleNameEnumeratorFile = basePath.resolve("moduleName.enum");
    myModuleNameEnumerator = new PersistentEnumerator<>(moduleNameEnumeratorFile, EnumeratorStringDescriptor.INSTANCE, 64);
    persistentObjectSeq.add(myModuleNameEnumerator);

    Path testModuleIndexFile = basePath.resolve("testModule.index");
    myTestNameToRunModule = new PersistentHashMap<>(testModuleIndexFile, EnumeratorIntegerDescriptor.INSTANCE,
                                                    new IntSeqExternalizer());
  }

  void appendModuleData(int testId, @Nullable String moduleName) throws IOException {
    if (moduleName != null) {
      int moduleId = myModuleNameEnumerator.enumerate(moduleName);
      IntList previousRunModules = myTestNameToRunModule.get(moduleId);
      if (previousRunModules != null && previousRunModules.contains(moduleId)) {
        return;
      }
      myTestNameToRunModule.appendData(testId, out -> DataInputOutputUtil.writeINT(out, moduleId));
    }
  }

  @NotNull
  Collection<String> getTestRunModules(int testId) throws IOException {
    IntList moduleIds = myTestNameToRunModule.get(testId);
    if (moduleIds == null) {
      return Collections.emptySet();
    }
    List<String> result = new ArrayList<>(moduleIds.size());
    for (int i = 0; i < moduleIds.size(); i++) {
      int moduleId = moduleIds.getInt(i);
      String moduleName = myModuleNameEnumerator.valueOf(moduleId);
      if (LOG.assertTrue(moduleName != null)) {
        result.add(moduleName);
      }
    }
    return result;
  }

  void removeTest(int testId) throws IOException {
    myTestNameToRunModule.remove(testId);
  }

  private static final class IntSeqExternalizer implements DataExternalizer<IntList> {
    @Override
    public void save(@NotNull DataOutput dataOutput, IntList testNameIds) throws IOException {
      for (int testNameId : testNameIds) {
        DataInputOutputUtil.writeINT(dataOutput, testNameId);
      }
    }

    @Override
    public IntList read(@NotNull DataInput dataInput) throws IOException {
      IntSet result = new IntOpenHashSet();
      while (((InputStream)dataInput).available() > 0) {
        result.add(DataInputOutputUtil.readINT(dataInput));
      }
      return new IntArrayList(result);
    }
  }
}
