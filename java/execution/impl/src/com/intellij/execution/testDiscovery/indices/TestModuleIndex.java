// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.testDiscovery.indices;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.io.*;
import gnu.trove.TIntArrayList;
import gnu.trove.TIntHashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class TestModuleIndex {
  private static final Logger LOG = Logger.getInstance(TestModuleIndex.class);

  private final PersistentHashMap<Integer, TIntArrayList> myTestNameToRunModule;
  private final PersistentEnumeratorDelegate<String> myModuleNameEnumerator;

  public TestModuleIndex(@NotNull Path basePath, @NotNull PersistentObjectSeq persistentObjectSeq) throws IOException {
    File moduleNameEnumeratorFile = basePath.resolve("moduleName.enum").toFile();
    myModuleNameEnumerator = new PersistentEnumeratorDelegate<>(moduleNameEnumeratorFile, EnumeratorStringDescriptor.INSTANCE, 64);
    persistentObjectSeq.add(myModuleNameEnumerator);

    File testModuleIndexFile = basePath.resolve("testModule.index").toFile();
    myTestNameToRunModule = new PersistentHashMap<>(testModuleIndexFile, EnumeratorIntegerDescriptor.INSTANCE,
                                                    new IntSeqExternalizer());
  }

  void appendModuleData(int testId, @Nullable String moduleName) throws IOException {
    if (moduleName != null) {
      int moduleId = myModuleNameEnumerator.enumerate(moduleName);
      TIntArrayList previousRunModules = myTestNameToRunModule.get(moduleId);
      if (previousRunModules != null && previousRunModules.contains(moduleId)) {
        return;
      }
      myTestNameToRunModule.appendData(testId, out -> DataInputOutputUtil.writeINT(out, moduleId));
    }
  }

  @NotNull
  Collection<String> getTestRunModules(int testId) throws IOException {
    TIntArrayList moduleIds = myTestNameToRunModule.get(testId);
    if (moduleIds == null) return Collections.emptySet();
    List<String> result = new ArrayList<>(moduleIds.size());
    for (int i = 0; i < moduleIds.size(); i++) {
      int moduleId = moduleIds.get(i);
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

  private static class IntSeqExternalizer implements DataExternalizer<TIntArrayList> {
    public void save(@NotNull DataOutput dataOutput, TIntArrayList testNameIds) throws IOException {
      for (int testNameId : testNameIds.toNativeArray()) DataInputOutputUtil.writeINT(dataOutput, testNameId);
    }

    public TIntArrayList read(@NotNull DataInput dataInput) throws IOException {
      TIntHashSet result = new TIntHashSet();

      while (((InputStream)dataInput).available() > 0) {
        int id = DataInputOutputUtil.readINT(dataInput);
        result.add(id);
      }

      return new TIntArrayList(result.toArray());
    }
  }
}
