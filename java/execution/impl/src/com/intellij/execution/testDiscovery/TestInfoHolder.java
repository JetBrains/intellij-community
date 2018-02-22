// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.testDiscovery;

import com.intellij.openapi.vfs.newvfs.persistent.FlushingDaemon;
import com.intellij.util.io.*;
import com.intellij.util.io.DataOutputStream;
import gnu.trove.TIntArrayList;
import gnu.trove.TIntHashSet;
import gnu.trove.TIntObjectHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ScheduledFuture;

final class TestInfoHolder {
  final PersistentHashMap<Long, TIntArrayList> myMethodQNameToTestNames;
  final PersistentHashMap<Integer, TIntObjectHashMap<TIntArrayList>> myTestNameToUsedClassesAndMethodMap;
  final PersistentHashMap<Long, TIntArrayList> myTestNameToNearestModule;
  final PersistentStringEnumerator myClassEnumerator;
  final PersistentStringEnumerator myMethodEnumerator;
  final PersistentEnumeratorDelegate<TestId> myTestEnumerator;
  final PersistentEnumeratorDelegate<ModuleId> myModuleEnumerator;
  final List<PersistentEnumeratorDelegate> myConstructedDataFiles = new ArrayList<>(6);

  private ScheduledFuture<?> myFlushingFuture;
  private boolean myDisposed;
  private final Object myLock;

  private static final int VERSION = 7;

  TestInfoHolder(@NotNull Path basePath, boolean readOnly, Object lock) {
    myLock = lock;
    final Path versionFile = getVersionFile(basePath);
    PathKt.createDirectories(basePath);
    final File methodQNameToTestNameFile = basePath.resolve("methodQNameToTestName.data").toFile();
    final File testNameToUsedClassesAndMethodMapFile = basePath.resolve("testToCalledMethodNames.data").toFile();
    final File classNameEnumeratorFile = basePath.resolve("classNameEnumerator.data").toFile();
    final File methodNameEnumeratorFile = basePath.resolve("methodNameEnumerator.data").toFile();
    final File testNameEnumeratorFile = basePath.resolve("testNameEnumerator.data").toFile();
    final File moduleNameEnumeratorFile = basePath.resolve("moduleNameEnumerator.data").toFile();
    final File testNameToNearestModuleFile = basePath.resolve("testNameToNearestModule.data").toFile();

    try {
      int version = readVersion(versionFile);
      if (version != VERSION) {
        TestDiscoveryIndex.LOG.info(version != -1
                                    ? "TestDiscoveryIndex was rewritten due to version change"
                                    : "TestDiscoveryIndex is not exist. Empty index is created");
        assert !readOnly;
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
      PersistentStringEnumerator methodEnumerator;
      PersistentEnumeratorDelegate<TestId> testEnumerator;
      PersistentEnumeratorDelegate<ModuleId> moduleNameEnumerator;

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

          classNameEnumerator = new PersistentStringEnumerator(classNameEnumeratorFile, true);
          myConstructedDataFiles.add(classNameEnumerator);

          methodEnumerator = new PersistentStringEnumerator(methodNameEnumeratorFile, true);
          myConstructedDataFiles.add(methodEnumerator);

          moduleNameEnumerator = new PersistentEnumeratorDelegate<>(moduleNameEnumeratorFile, ModuleId.DESCRIPTOR, 64);
          myConstructedDataFiles.add(moduleNameEnumerator);

          testEnumerator = new PersistentEnumeratorDelegate<>(testNameEnumeratorFile, TestId.DESCRIPTOR, 1024 * 4);
          myConstructedDataFiles.add(testEnumerator);

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
      myMethodEnumerator = methodEnumerator;
      myTestEnumerator = testEnumerator;
      myModuleEnumerator = moduleNameEnumerator;

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

  private static void writeVersion(@NotNull Path versionFile) throws IOException {
    try (final DataOutputStream versionOut = new DataOutputStream(PathKt.outputStream(versionFile))) {
      DataInputOutputUtil.writeINT(versionOut, VERSION);
    }
  }

  private static int readVersion(@NotNull Path versionFile) throws IOException {
    InputStream inputStream = PathKt.inputStreamIfExists(versionFile);
    if (inputStream == null) {
      return -1;
    }
    try (DataInputStream versionInput = new DataInputStream(inputStream)) {
      return DataInputOutputUtil.readINT(versionInput);
    }
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

  public static boolean isValidPath(@NotNull Path path) {
    try {
      return readVersion(getVersionFile(path)) == VERSION;
    }
    catch (IOException ex) {
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
  static Path getVersionFile(Path path) {
    return path.resolve("index.version");
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

  @NotNull
  TestId createTestId(String className, String methodName, byte frameworkPrefix) throws IOException {
    return new TestId(myClassEnumerator.enumerate(className), myMethodEnumerator.enumerate(methodName), frameworkPrefix);
  }

  static class ModuleId {
    @NotNull
    private final String myModuleName;
    private final byte myFrameworkId;

    ModuleId(@NotNull String name, byte id) {
      myModuleName = name;
      myFrameworkId = id;
    }

    @NotNull
    String getModuleName() {
      return myModuleName;
    }

    byte getFrameworkId() {
      return myFrameworkId;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      ModuleId id = (ModuleId)o;
      return myFrameworkId == id.myFrameworkId &&
             Objects.equals(myModuleName, id.myModuleName);
    }

    @Override
    public int hashCode() {
      return Objects.hash(myModuleName, myFrameworkId);
    }

    private static final KeyDescriptor<ModuleId> DESCRIPTOR = new KeyDescriptor<ModuleId>() {
      @Override
      public int getHashCode(ModuleId id) {
        return id.hashCode();
      }

      @Override
      public boolean isEqual(ModuleId id1, ModuleId id2) {
        return id1.equals(id2);
      }

      @Override
      public void save(@NotNull DataOutput out, ModuleId id) throws IOException {
        IOUtil.writeUTF(out, id.getModuleName());
        out.writeByte(id.getFrameworkId());
      }

      @Override
      public ModuleId read(@NotNull DataInput in) throws IOException {
        return new ModuleId(IOUtil.readUTF(in), in.readByte());
      }
    };
  }

  static class TestId {
    private final int myClassId;
    private final int myMethodId;
    private final byte myFrameworkId;

    TestId(int classId, int methodId, byte id) {
      myClassId = classId;
      myMethodId = methodId;
      myFrameworkId = id;
    }

    int getClassId() {
      return myClassId;
    }

    int getMethodId() {
      return myMethodId;
    }

    byte getFrameworkId() {
      return myFrameworkId;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      TestId id = (TestId)o;
      return myClassId == id.myClassId &&
             myMethodId == id.myMethodId &&
             myFrameworkId == id.myFrameworkId;
    }

    @Override
    public int hashCode() {
      return Objects.hash(myClassId, myMethodId, myFrameworkId);
    }

    private static final KeyDescriptor<TestId> DESCRIPTOR = new KeyDescriptor<TestId>() {
      @Override
      public int getHashCode(TestId id) {
        return id.hashCode();
      }

      @Override
      public boolean isEqual(TestId id1, TestId id2) {
        return id1.equals(id2);
      }

      @Override
      public void save(@NotNull DataOutput out, TestId id) throws IOException {
        DataInputOutputUtil.writeINT(out, id.getClassId());
        DataInputOutputUtil.writeINT(out, id.getMethodId());
        out.writeByte(id.getFrameworkId());
      }

      @Override
      public TestId read(@NotNull DataInput in) throws IOException {
        return new TestId(DataInputOutputUtil.readINT(in), DataInputOutputUtil.readINT(in), in.readByte());
      }
    };
  }
}
