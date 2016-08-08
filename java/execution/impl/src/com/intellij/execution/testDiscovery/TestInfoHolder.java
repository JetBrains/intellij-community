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
package com.intellij.execution.testDiscovery;

import com.intellij.openapi.vfs.newvfs.persistent.FlushingDaemon;
import com.intellij.util.io.*;
import gnu.trove.TIntArrayList;
import gnu.trove.TIntHashSet;
import gnu.trove.TIntObjectHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ScheduledFuture;

final class TestInfoHolder {
  final PersistentHashMap<Long, TIntArrayList> myMethodQNameToTestNames;
  final PersistentHashMap<Integer, TIntObjectHashMap<TIntArrayList>> myTestNameToUsedClassesAndMethodMap;
  final PersistentHashMap<Long, TIntArrayList> myTestNameToNearestModule;
  final PersistentStringEnumerator myClassEnumerator;
  final CachingEnumerator<String> myClassEnumeratorCache;
  final PersistentStringEnumerator myMethodEnumerator;
  final CachingEnumerator<String> myMethodEnumeratorCache;
  final PersistentStringEnumerator myTestNameEnumerator;
  final PersistentStringEnumerator myModuleNameEnumerator;
  final List<PersistentEnumeratorDelegate> myConstructedDataFiles = new ArrayList<>(6);

  private ScheduledFuture<?> myFlushingFuture;
  private boolean myDisposed;
  private final Object myLock;

  private static final int VERSION = 4;

  TestInfoHolder(String basePath, boolean readOnly, Object lock) {
    myLock = lock;
    final File versionFile = getVersionFile(basePath);
    versionFile.getParentFile().mkdirs();
    final File methodQNameToTestNameFile = new File(basePath + File.separator + "methodQNameToTestName.data");
    final File testNameToUsedClassesAndMethodMapFile = new File(basePath + File.separator + "testToCalledMethodNames.data");
    final File classNameEnumeratorFile = new File(basePath + File.separator + "classNameEnumerator.data");
    final File methodNameEnumeratorFile = new File(basePath + File.separator + "methodNameEnumerator.data");
    final File testNameEnumeratorFile = new File(basePath + File.separator + "testNameEnumerator.data");
    final File moduleNameEnumeratorFile = new File(basePath + File.separator + "moduleNameEnumerator.data");
    final File testNameToNearestModuleFile = new File(basePath + File.separator + "testNameToNearestModule.data");

    try {
      int version = readVersion(versionFile);
      if (version != VERSION) {
        assert !readOnly;
        TestDiscoveryIndex.LOG.info("TestDiscoveryIndex was rewritten due to version change");
        deleteAllIndexDataFiles(methodQNameToTestNameFile,
                                testNameToUsedClassesAndMethodMapFile,
                                classNameEnumeratorFile,
                                methodNameEnumeratorFile,
                                testNameEnumeratorFile, moduleNameEnumeratorFile,
                                testNameToNearestModuleFile);

        writeVersion(versionFile);
      }

      PersistentHashMap<Long, TIntArrayList> methodQNameToTestNames;
      PersistentHashMap<Integer, TIntObjectHashMap<TIntArrayList>> testNameToUsedClassesAndMethodMap;
      PersistentHashMap<Long, TIntArrayList> testNameToNearestModule;
      PersistentStringEnumerator classNameEnumerator;
      PersistentStringEnumerator methodNameEnumerator;
      PersistentStringEnumerator testNameEnumerator;
      PersistentStringEnumerator moduleNameEnumerator;

      int iterations = 0;

      while (true) {
        ++iterations;

        try {
          methodQNameToTestNames = new PersistentHashMap<Long, TIntArrayList>(
            methodQNameToTestNameFile,
            MethodQNameSerializer.INSTANCE,
            new TestNamesExternalizer()
          ) {
            @Override
            protected boolean isReadOnly() {
              return readOnly;
            }
          };
          myConstructedDataFiles.add(methodQNameToTestNames);

          testNameToUsedClassesAndMethodMap = new PersistentHashMap<Integer, TIntObjectHashMap<TIntArrayList>>(
            testNameToUsedClassesAndMethodMapFile,
            EnumeratorIntegerDescriptor.INSTANCE,
            new ClassesAndMethodsMapDataExternalizer()
          ) {
            @Override
            protected boolean isReadOnly() {
              return readOnly;
            }
          };
          myConstructedDataFiles.add(testNameToUsedClassesAndMethodMap);

          testNameToNearestModule = new PersistentHashMap<>(testNameToNearestModuleFile,
                                                            MethodQNameSerializer.INSTANCE,
                                                            new TestNamesExternalizer());
          myConstructedDataFiles.add(testNameToNearestModule);

          classNameEnumerator = new PersistentStringEnumerator(classNameEnumeratorFile);
          myConstructedDataFiles.add(classNameEnumerator);

          methodNameEnumerator = new PersistentStringEnumerator(methodNameEnumeratorFile);
          myConstructedDataFiles.add(methodNameEnumerator);

          moduleNameEnumerator = new PersistentStringEnumerator(moduleNameEnumeratorFile);
          myConstructedDataFiles.add(moduleNameEnumerator);

          testNameEnumerator = new PersistentStringEnumerator(testNameEnumeratorFile);
          myConstructedDataFiles.add(testNameEnumerator);

          break;
        }
        catch (Throwable throwable) {
          TestDiscoveryIndex.LOG.info("TestDiscoveryIndex problem", throwable);
          closeAllConstructedFiles(true);
          myConstructedDataFiles.clear();

          deleteAllIndexDataFiles(methodQNameToTestNameFile, testNameToUsedClassesAndMethodMapFile, classNameEnumeratorFile,
                                  methodNameEnumeratorFile,
                                  testNameEnumeratorFile, moduleNameEnumeratorFile, testNameToNearestModuleFile);
          // try another time
        }

        if (iterations >= 3) {
          TestDiscoveryIndex.LOG.error("Unexpected circular initialization problem");
          assert false;
        }
      }

      myMethodQNameToTestNames = methodQNameToTestNames;
      myTestNameToUsedClassesAndMethodMap = testNameToUsedClassesAndMethodMap;
      myTestNameToNearestModule = testNameToNearestModule;
      myClassEnumerator = classNameEnumerator;
      myMethodEnumerator = methodNameEnumerator;
      myTestNameEnumerator = testNameEnumerator;
      myModuleNameEnumerator = moduleNameEnumerator;
      myMethodEnumeratorCache = new CachingEnumerator<>(methodNameEnumerator, EnumeratorStringDescriptor.INSTANCE);
      myClassEnumeratorCache = new CachingEnumerator<>(classNameEnumerator, EnumeratorStringDescriptor.INSTANCE);

      myFlushingFuture = FlushingDaemon.everyFiveSeconds(() -> {
        synchronized (myLock) {
          if (myDisposed) {
            myFlushingFuture.cancel(false);
            return;
          }
          for (PersistentEnumeratorDelegate dataFile : myConstructedDataFiles) {
            if (dataFile.isDirty()) {
              dataFile.force();
            }
          }
          myClassEnumeratorCache.clear();
          myMethodEnumeratorCache.clear();
        }
      });
    }
    catch (IOException ex) {
      throw new RuntimeException(ex);
    }
  }

  private void closeAllConstructedFiles(boolean ignoreCloseProblem) {
    for (Closeable closeable : myConstructedDataFiles) {
      try {
        closeable.close();
      }
      catch (Throwable throwable) {
        if (!ignoreCloseProblem) throw new RuntimeException(throwable);
      }
    }
  }

  private static void deleteAllIndexDataFiles(File... files) {
    for (File file : files) {
      IOUtil.deleteAllFilesStartingWith(file);
    }
  }

  private static void writeVersion(File versionFile) throws IOException {
    final java.io.DataOutputStream versionOut = new java.io.DataOutputStream(new FileOutputStream(versionFile));

    try {
      DataInputOutputUtil.writeINT(versionOut, VERSION);
    }
    finally {
      try {
        versionOut.close();
      }
      catch (IOException ignore) {
      }
    }
  }

  private static int readVersion(File versionFile) throws IOException {
    if (!versionFile.exists()) return 0;
    final DataInputStream versionInput = new DataInputStream(new FileInputStream(versionFile));
    int version;
    try {
      version = DataInputOutputUtil.readINT(versionInput);
    }
    finally {
      try {
        versionInput.close();
      }
      catch (IOException ignore) {
      }
    }
    return version;
  }

  void dispose() {
    assert Thread.holdsLock(myLock);
    try {
      closeAllConstructedFiles(false);
    }
    finally {
      myDisposed = true;
    }
  }

  private static final int REMOVED_MARKER = -1;

  void doUpdateFromDiff(final int testNameId,
                        @Nullable TIntObjectHashMap<TIntArrayList> classData,
                        @Nullable TIntObjectHashMap<TIntArrayList> previousClassData,
                        @Nullable Integer moduleId) throws IOException {
    ValueDiff valueDiff = new ValueDiff(classData, previousClassData);

    if (valueDiff.hasRemovedDelta()) {
      for (int classQName : valueDiff.myRemovedClassData.keys()) {
        for (int methodName : valueDiff.myRemovedClassData.get(classQName).toNativeArray()) {
          myMethodQNameToTestNames.appendData(createKey(classQName, methodName),
                                              dataOutput -> {
                                                DataInputOutputUtil.writeINT(dataOutput, REMOVED_MARKER);
                                                DataInputOutputUtil.writeINT(dataOutput, testNameId);
                                              }
          );
        }
      }
    }

    if (valueDiff.hasAddedDelta()) {
      for (int classQName : valueDiff.myAddedOrChangedClassData.keys()) {
        for (int methodName : valueDiff.myAddedOrChangedClassData.get(classQName).toNativeArray()) {
          myMethodQNameToTestNames.appendData(createKey(classQName, methodName),
                                              dataOutput -> DataInputOutputUtil.writeINT(dataOutput, testNameId));
          if (moduleId != null) {
            myTestNameToNearestModule.appendData(createKey(classQName, methodName),
                                                 dataOutput -> DataInputOutputUtil.writeINT(dataOutput, moduleId));
          }
        }
      }
    }

    if ((valueDiff.hasAddedDelta() || valueDiff.hasRemovedDelta())) {
      if (classData != null) {
        myTestNameToUsedClassesAndMethodMap.put(testNameId, classData);
      }
      else {
        myTestNameToUsedClassesAndMethodMap.remove(testNameId);
      }
    }
  }

  public boolean isDisposed() {
    return myDisposed;
  }

  public static boolean isValidPath(String path) {
    try {
      return readVersion(getVersionFile(path)) == VERSION;
    } catch (IOException ex) {
      return false;
    }
  }

  private static class TestNamesExternalizer implements DataExternalizer<TIntArrayList> {
    public void save(@NotNull DataOutput dataOutput, TIntArrayList testNameIds) throws IOException {
      for (int testNameId : testNameIds.toNativeArray()) DataInputOutputUtil.writeINT(dataOutput, testNameId);
    }

    public TIntArrayList read(@NotNull DataInput dataInput) throws IOException {
      TIntHashSet result = new TIntHashSet();

      while (((InputStream)dataInput).available() > 0) {
        int id = DataInputOutputUtil.readINT(dataInput);
        if (REMOVED_MARKER == id) {
          id = DataInputOutputUtil.readINT(dataInput);
          if(!result.remove(id)) {
            result.add(-id);
          }
        }
        else {
          result.add(id);
        }
      }

      return new TIntArrayList(result.toArray());
    }
  }

  private static class ClassesAndMethodsMapDataExternalizer implements DataExternalizer<TIntObjectHashMap<TIntArrayList>> {
    public void save(@NotNull final DataOutput dataOutput, TIntObjectHashMap<TIntArrayList> classAndMethodsMap)
      throws IOException {
      DataInputOutputUtil.writeINT(dataOutput, classAndMethodsMap.size());
      final int[] classNameIds = classAndMethodsMap.keys();
      Arrays.sort(classNameIds);

      int prevClassNameId = 0;
      for (int classNameId : classNameIds) {
        DataInputOutputUtil.writeINT(dataOutput, classNameId - prevClassNameId);
        TIntArrayList value = classAndMethodsMap.get(classNameId);
        DataInputOutputUtil.writeINT(dataOutput, value.size());

        final int[] methodNameIds = value.toNativeArray();
        Arrays.sort(methodNameIds);
        int prevMethodNameId = 0;
        for (int methodNameId : methodNameIds) {
          DataInputOutputUtil.writeINT(dataOutput, methodNameId - prevMethodNameId);
          prevMethodNameId = methodNameId;
        }
        prevClassNameId = classNameId;
      }
    }

    public TIntObjectHashMap<TIntArrayList> read(@NotNull DataInput dataInput) throws IOException {
      int numberOfClasses = DataInputOutputUtil.readINT(dataInput);
      TIntObjectHashMap<TIntArrayList> result = new TIntObjectHashMap<>();
      int prevClassNameId = 0;

      while (numberOfClasses-- > 0) {
        int classNameId = DataInputOutputUtil.readINT(dataInput) + prevClassNameId;
        int numberOfMethods = DataInputOutputUtil.readINT(dataInput);
        TIntArrayList methodNameIds = new TIntArrayList(numberOfMethods);

        int prevMethodNameId = 0;
        while (numberOfMethods-- > 0) {
          final int methodNameId = DataInputOutputUtil.readINT(dataInput) + prevMethodNameId;
          methodNameIds.add(methodNameId);
          prevMethodNameId = methodNameId;
        }

        result.put(classNameId, methodNameIds);
        prevClassNameId = classNameId;
      }
      return result;
    }
  }

  private static class MethodQNameSerializer implements KeyDescriptor<Long> {
    public static final MethodQNameSerializer INSTANCE = new MethodQNameSerializer();

    @Override
    public void save(@NotNull DataOutput out, Long value) throws IOException {
      out.writeLong(value);
    }

    @Override
    public Long read(@NotNull DataInput in) throws IOException {
      return in.readLong();
    }

    @Override
    public int getHashCode(Long value) {
      return value.hashCode();
    }

    @Override
    public boolean isEqual(Long val1, Long val2) {
      return val1.equals(val2);
    }
  }

  @NotNull
  static File getVersionFile(String path) {
    return new File(path + File.separator + "index.version");
  }

  static long createKey(int classQName, int methodName) {
    return ((long)classQName << 32) | methodName;
  }

  static class ValueDiff {
    final TIntObjectHashMap<TIntArrayList> myAddedOrChangedClassData;
    final TIntObjectHashMap<TIntArrayList> myRemovedClassData;

    ValueDiff(@Nullable TIntObjectHashMap<TIntArrayList> classData, @Nullable TIntObjectHashMap<TIntArrayList> previousClassData) {
      TIntObjectHashMap<TIntArrayList> addedOrChangedClassData = classData;
      TIntObjectHashMap<TIntArrayList> removedClassData = previousClassData;

      if (previousClassData != null && !previousClassData.isEmpty()) {
        removedClassData = new TIntObjectHashMap<>();
        addedOrChangedClassData = new TIntObjectHashMap<>();

        if (classData != null) {
          for (int classQName : classData.keys()) {
            TIntArrayList currentMethods = classData.get(classQName);
            TIntArrayList previousMethods = previousClassData.get(classQName);

            if (previousMethods == null) {
              addedOrChangedClassData.put(classQName, currentMethods);
              continue;
            }

            final int[] previousMethodIds = previousMethods.toNativeArray();
            TIntHashSet previousMethodsSet = new TIntHashSet(previousMethodIds);
            final int[] currentMethodIds = currentMethods.toNativeArray();
            TIntHashSet currentMethodsSet = new TIntHashSet(currentMethodIds);
            currentMethodsSet.removeAll(previousMethodIds);
            previousMethodsSet.removeAll(currentMethodIds);

            if (!currentMethodsSet.isEmpty()) {
              addedOrChangedClassData.put(classQName, new TIntArrayList(currentMethodsSet.toArray()));
            }
            if (!previousMethodsSet.isEmpty()) {
              removedClassData.put(classQName, new TIntArrayList(previousMethodsSet.toArray()));
            }
          }
        }
        if (classData != null) {
          for (int classQName : previousClassData.keys()) {
            if (classData.containsKey(classQName)) continue;

            TIntArrayList previousMethods = previousClassData.get(classQName);
            removedClassData.put(classQName, previousMethods);
          }
        }
      }

      myAddedOrChangedClassData = addedOrChangedClassData;
      myRemovedClassData = removedClassData;
    }

    public boolean hasRemovedDelta() {
      return myRemovedClassData != null && !myRemovedClassData.isEmpty();
    }

    public boolean hasAddedDelta() {
      return myAddedOrChangedClassData != null && !myAddedOrChangedClassData.isEmpty();
    }
  }
}
