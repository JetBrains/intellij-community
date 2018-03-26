// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.testDiscovery.indices;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.LowMemoryWatcher;
import com.intellij.util.containers.MultiMap;
import com.intellij.util.indexing.StorageException;
import com.intellij.util.indexing.ValueContainer;
import com.intellij.util.io.*;
import com.intellij.util.io.DataOutputStream;
import gnu.trove.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;

public final class DiscoveredTestDataHolder {
  private static final Logger LOG = Logger.getInstance(DiscoveredTestDataHolder.class);

  private final DiscoveredTestsIndex myDiscoveredTestsIndex;
  private final TestModuleIndex myTestModuleIndex;

  private final PersistentStringEnumerator myClassEnumerator;
  private final PersistentStringEnumerator myMethodEnumerator;
  private final PersistentEnumeratorDelegate<TestId> myTestEnumerator;
  private final PersistentObjectSeq myConstructedDataFiles = new PersistentObjectSeq();

  private boolean myDisposed;
  private final Disposable myDisposable = Disposer.newDisposable();

  static final int VERSION = 8;

  public DiscoveredTestDataHolder(@NotNull Path basePath) {
    final Path versionFile = getVersionFile(basePath);
    PathKt.createDirectories(basePath);
    final File discoveredTestsIndexFile = basePath.resolve("discoveredTests.index").toFile();

    final File classNameEnumeratorFile = basePath.resolve("className.enum").toFile();
    final File methodNameEnumeratorFile = basePath.resolve("methodName.enum").toFile();
    final File testNameEnumeratorFile = basePath.resolve("testName.enum").toFile();

    try {
      int version = readVersion(versionFile);
      if (version != VERSION) {
        LOG.info(version != -1
                 ? "TestDiscoveryIndex was rewritten due to version change"
                 : "TestDiscoveryIndex is not exist. Empty index is created");
        PathKt.delete(basePath);
        writeVersion(versionFile);
      }

      DiscoveredTestsIndex discoveredTestsIndex;
      TestModuleIndex testModuleIndex;
      PersistentStringEnumerator classNameEnumerator;
      PersistentStringEnumerator methodEnumerator;
      PersistentEnumeratorDelegate<TestId> testEnumerator;

      int iterations = 0;

      while (true) {
        ++iterations;

        try {
          discoveredTestsIndex = new DiscoveredTestsIndex(discoveredTestsIndexFile);
          myConstructedDataFiles.add(discoveredTestsIndex);

          testModuleIndex = new TestModuleIndex(basePath,  myConstructedDataFiles);

          classNameEnumerator = new PersistentStringEnumerator(classNameEnumeratorFile, true);
          myConstructedDataFiles.add(classNameEnumerator);

          methodEnumerator = new PersistentStringEnumerator(methodNameEnumeratorFile, true);
          myConstructedDataFiles.add(methodEnumerator);

          testEnumerator = new PersistentEnumeratorDelegate<>(testNameEnumeratorFile, TestId.DESCRIPTOR, 1024 * 4);
          myConstructedDataFiles.add(testEnumerator);

          break;
        }
        catch (Throwable throwable) {
          LOG.info("TestDiscoveryIndex problem", throwable);
          myConstructedDataFiles.close(true);
          myConstructedDataFiles.clear();

          PathKt.delete(basePath);
          // try another time
        }

        if (iterations >= 3) {
          LOG.error("Unexpected circular initialization problem");
          assert false;
        }
      }

      myDiscoveredTestsIndex = discoveredTestsIndex;
      myTestModuleIndex = testModuleIndex;
      myClassEnumerator = classNameEnumerator;
      myMethodEnumerator = methodEnumerator;
      myTestEnumerator = testEnumerator;

      LowMemoryWatcher.register(() -> myConstructedDataFiles.flush(), myDisposable);
    }
    catch (IOException ex) {
      throw new RuntimeException(ex);
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

  public void flush() {
    myConstructedDataFiles.flush();
  }

  public void dispose() {
    try {
      Disposer.dispose(myDisposable);
      myConstructedDataFiles.close(false);
    }
    finally {
      myDisposed = true;
    }
  }

  public boolean hasTestTrace(@NotNull String testClassName, @NotNull String testMethodName, byte frameworkId) throws IOException {
    int testId = myTestEnumerator.tryEnumerate(createTestId(testClassName, testMethodName, frameworkId));
    return testId != 0 && myDiscoveredTestsIndex.containsDataFrom(testId);
  }

  public void removeTestTrace(@NotNull String testClassName, @NotNull String testMethodName, byte frameworkId) throws IOException {
    int testId = myTestEnumerator.tryEnumerate(createTestId(testClassName, testMethodName, frameworkId));
    if (testId != 0) {
      myDiscoveredTestsIndex.update(testId, null).compute();
      myTestModuleIndex.removeTest(testId);
    }
  }

  @NotNull
  public Collection<String> getTestModulesByMethodName(@NotNull String testClassName, @NotNull String testMethodName, byte frameworkId) throws IOException {
    int testId = myTestEnumerator.tryEnumerate(createTestId(testClassName, testMethodName, frameworkId));
    if (testId != 0) {
      return myTestModuleIndex.getTestRunModules(testId);
    }
    return Collections.emptySet();
  }

  public void updateTestData(@NotNull String testClassName,
                             @NotNull String testMethodName,
                             @NotNull MultiMap<String, String> usedMethods,
                             @Nullable String moduleName,
                             byte frameworkId) throws IOException {

    final int testNameId = myTestEnumerator.enumerate(createTestId(testClassName, testMethodName, frameworkId));
    TLongHashSet result = new TLongHashSet();
    for (Map.Entry<String, Collection<String>> e : usedMethods.entrySet()) {
      int classId = myClassEnumerator.enumerate(e.getKey());
      for (String methodName : e.getValue()) {
        long key = createKey(classId, myMethodEnumerator.enumerate(methodName));
        result.add(key);
      }
    }
    myDiscoveredTestsIndex.update(testNameId, new DiscoveredTestsIndex.UsedMethods(result)).compute();
    myTestModuleIndex.appendModuleData(testNameId, moduleName);
  }

  @NotNull
  public MultiMap<String, String> getTestsByMethodName(@NotNull String classFQName, @NotNull String methodName, byte frameworkId) throws IOException {
    int methodId = myMethodEnumerator.tryEnumerate(methodName);
    if (methodId == 0) return MultiMap.empty();
    int classId = myClassEnumerator.tryEnumerate(classFQName);
    if (classId == 0) return MultiMap.empty();
    try {
      MultiMap<String, String> result = new MultiMap<>();
      IOException[] exception = {null};
      myDiscoveredTestsIndex.getData(createKey(classId, methodId)).forEach(new ValueContainer.ContainerAction<Void>() {
        @Override
        public boolean perform(int testId, Void value) {
          try {
            TestId test = myTestEnumerator.valueOf(testId);
            if (test.getFrameworkId() == frameworkId) {
              String testClassName = myClassEnumerator.valueOf(test.getClassId());
              String testMethodName = myMethodEnumerator.valueOf(test.getMethodId());
              result.putValue(testClassName, testMethodName);
            }
          }
          catch (IOException e) {
            exception[0] = e;
            return false;
          }
          return true;
        }
      });
      if (exception[0] != null) throw exception[0];
      return result;
    }
    catch (StorageException e) {
      throw new IOException(e);
    }
  }

  public boolean isDisposed() {
    return myDisposed;
  }

  @NotNull
  static Path getVersionFile(Path path) {
    return path.resolve("index.version");
  }

  public static long createKey(int classQName, int methodName) {
    return ((long)classQName << 32) | methodName;
  }

  @NotNull
  public TestId createTestId(String className, String methodName, byte frameworkPrefix) throws IOException {
    return new TestId(myClassEnumerator.enumerate(className), myMethodEnumerator.enumerate(methodName), frameworkPrefix);
  }
}
