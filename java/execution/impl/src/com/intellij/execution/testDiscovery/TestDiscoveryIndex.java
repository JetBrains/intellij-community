// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.testDiscovery;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.ThrowableNotNullFunction;
import com.intellij.util.ThrowableConvertor;
import com.intellij.util.containers.MultiMap;
import com.intellij.util.io.PathKt;
import gnu.trove.THashSet;
import gnu.trove.TIntArrayList;
import gnu.trove.TIntObjectHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * @author Maxim.Mossienko on 7/9/2015.
 */
public class TestDiscoveryIndex implements Disposable {
  static final Logger LOG = Logger.getInstance(TestDiscoveryIndex.class);

  private final TestDataController myLocalTestRunDataController;
  private final TestDataController myRemoteTestRunDataController;

  public TestDiscoveryIndex(Project project) {
    this(project, TestDiscoveryExtension.baseTestDiscoveryPathForProject(project));
  }

  public TestDiscoveryIndex(final Project project, @NotNull Path basePath) {
    myLocalTestRunDataController = new TestDataController(basePath, false);
    myRemoteTestRunDataController = new TestDataController(null, true);

    if (Files.exists(basePath)) {
      StartupManager.getInstance(project).registerPostStartupActivity(() -> ApplicationManager.getApplication().executeOnPooledThread(() -> {
        myLocalTestRunDataController.getHolder(); // proactively init with maybe io costly compact
      }));
    }
  }

  public boolean hasTestTrace(@NotNull String testClassName, @NotNull String testMethodName, @NotNull String frameworkPrefix) throws IOException {
    String testName = testClassName + "-" + testMethodName;
    Boolean result = myLocalTestRunDataController.withTestDataHolder(localHolder -> {          // todo: remote run data
      final int testNameId = localHolder.myTestNameEnumerator.tryEnumerate(testName);
      if (testNameId == 0) {
        return myRemoteTestRunDataController.withTestDataHolder(remoteHolder -> {
          final int testNameId1 = remoteHolder.myTestNameEnumerator.tryEnumerate(testName);
          return testNameId1 != 0 && remoteHolder.myTestNameToUsedClassesAndMethodMap.get(testNameId1) != null;
        }) != null;
      }
      return localHolder.myTestNameToUsedClassesAndMethodMap.get(testNameId) != null;
    });
    return result == Boolean.TRUE;
  }

  public void removeTestTrace(@NotNull String testClassName, @NotNull String testMethodName, @NotNull String frameworkPrefix) throws IOException {
    String testName = testClassName + "-" + testMethodName;
    myLocalTestRunDataController.withTestDataHolder((ThrowableConvertor<TestInfoHolder, Void, IOException>)localHolder -> {
      final int testNameId = localHolder.myTestNameEnumerator.tryEnumerate(testName);  // todo remove remote data isn't possible
      if (testNameId != 0) {
        localHolder.doUpdateFromDiff(testNameId, null,
                                localHolder.myTestNameToUsedClassesAndMethodMap.get(testNameId),
                                null);
      }
      return null;
    });
  }

  public void setRemoteTestRunDataPath(@NotNull Path path) {
    if (!TestInfoHolder.isValidPath(path)) {
      path = null;
    }
    myRemoteTestRunDataController.init(path);
    // todo: should we remove our local run data ?
  }

  public Collection<String> getTestsByMethodName(@NotNull String classFQName, @NotNull String methodName, @NotNull String frameworkPrefix) throws IOException {
    //TODO support framework prefix again
    return myLocalTestRunDataController.withTestDataHolder(new ThrowableConvertor<TestInfoHolder, Collection<String>, IOException>() {
      @Override
      public Collection<String> convert(TestInfoHolder localHolder) throws IOException {
        TIntArrayList remoteList = myRemoteTestRunDataController.withTestDataHolder(
          remoteHolder -> remoteHolder.myMethodQNameToTestNames.get(
            TestInfoHolder.createKey(
              remoteHolder.myClassEnumerator.enumerate(classFQName),
              remoteHolder.myMethodEnumerator.enumerate(methodName)
            )
          )
        );

        final TIntArrayList localList = localHolder.myMethodQNameToTestNames.get(
          TestInfoHolder.createKey(
            localHolder.myClassEnumerator.enumerate(classFQName),
            localHolder.myMethodEnumerator.enumerate(methodName)
          )
        );

        if (remoteList == null) {
          return testIdsToTestNames(localList, localHolder);
        }

        Collection<String> testsFromRemote =
          myRemoteTestRunDataController.withTestDataHolder(
            remoteHolder -> testIdsToTestNames(remoteList, remoteHolder)
          );

        if (localList == null) return testsFromRemote;
        THashSet<String> setOfStrings = new THashSet<>(testsFromRemote);

        for (int testNameId : localList.toNativeArray()) {
          if (testNameId < 0) {
            setOfStrings.remove(localHolder.myTestNameEnumerator.valueOf(-testNameId));
            continue;
          }
          setOfStrings.add(localHolder.myTestNameEnumerator.valueOf(testNameId));
        }

        return setOfStrings;
      }

      private Collection<String> testIdsToTestNames(TIntArrayList localList, TestInfoHolder localHolder) throws IOException {
        if (localList == null) return Collections.emptyList();

        final ArrayList<String> result = new ArrayList<>(localList.size());
        for (int testNameId : localList.toNativeArray()) {
          if (testNameId < 0) {
            continue;
          }
          result.add(localHolder.myTestNameEnumerator.valueOf(testNameId));
        }
        return result;
      }
    });
  }

  public Collection<String> getTestModulesByMethodName(@NotNull String classFQName, @NotNull String methodName, String prefix) throws IOException {
    return myLocalTestRunDataController.withTestDataHolder(new ThrowableConvertor<TestInfoHolder, Collection<String>, IOException>() {
      @Override
      public Collection<String> convert(TestInfoHolder localHolder) throws IOException {
        List<String> modules = getTestModules(localHolder);
        List<String> modulesFromRemote = myRemoteTestRunDataController.withTestDataHolder(
          this::getTestModules);
        THashSet<String> modulesSet = new THashSet<>(modules);
        if (modulesFromRemote != null) modulesSet.addAll(modulesFromRemote);
        return modulesSet;
      }

      private List<String> getTestModules(TestInfoHolder holder) throws IOException {
        // todo merging with remote
        final TIntArrayList list = holder.myTestNameToNearestModule.get(
          TestInfoHolder.createKey(
            holder.myClassEnumerator.enumerate(classFQName),
            holder.myMethodEnumerator.enumerate(methodName)
          )
        );
        if (list == null) return Collections.emptyList();
        final ArrayList<String> result = new ArrayList<>(list.size());
        for (int moduleNameId : list.toNativeArray()) {
          final String moduleNameWithPrefix = holder.myModuleNameEnumerator.valueOf(moduleNameId);
          if (moduleNameWithPrefix != null && moduleNameWithPrefix.startsWith(prefix)) {
            result.add(moduleNameWithPrefix.substring(prefix.length()));
          }
        }
        return result;
      }
    });
  }

  static class TestDataController {
    private final Object myLock = new Object();
    private Path myBasePath;
    private final boolean myReadOnly;
    private volatile TestInfoHolder myHolder;

    TestDataController(Path basePath, boolean readonly) {
      myReadOnly = readonly;
      init(basePath);
    }

    void init(Path basePath) {
      if (myHolder != null) dispose();

      synchronized (myLock) {
        myBasePath = basePath;
      }
    }

    private TestInfoHolder getHolder() {
      TestInfoHolder holder = myHolder;

      if (holder == null) {
        synchronized (myLock) {
          holder = myHolder;
          if (holder == null && myBasePath != null) myHolder = holder = new TestInfoHolder(myBasePath, myReadOnly, myLock);
        }
      }
      return holder;
    }

    private void dispose() {
      synchronized (myLock) {
        TestInfoHolder holder = myHolder;
        if (holder != null) {
          holder.dispose();
          myHolder = null;
        }
      }
    }

    private void thingsWentWrongLetsReinitialize(@Nullable TestInfoHolder holder, Throwable throwable) throws IOException {
      LOG.error("Unexpected problem", throwable);
      if (holder != null) holder.dispose();
      PathKt.delete(TestInfoHolder.getVersionFile(myBasePath));

      myHolder = null;
      if (throwable instanceof IOException) throw (IOException) throwable;
    }

    public <R> R withTestDataHolder(ThrowableConvertor<TestInfoHolder, R, IOException> action) throws IOException {
      synchronized (myLock) {
        TestInfoHolder holder = getHolder();
        if (holder == null || holder.isDisposed()) return null;
        try {
          return action.convert(holder);
        } catch (Throwable throwable) {
          if (!myReadOnly) thingsWentWrongLetsReinitialize(holder, throwable);
          else LOG.error(throwable);
        }
        return null;
      }
    }
  }

  public static TestDiscoveryIndex getInstance(Project project) {
    return project.getComponent(TestDiscoveryIndex.class);
  }

  @Override
  public void dispose() {
    myLocalTestRunDataController.dispose();
    myRemoteTestRunDataController.dispose();
  }

  public void updateFromData(@NotNull String testClassName,
                             @NotNull String testMethodName,
                             @NotNull MultiMap<String, String> usedMethods,
                             @Nullable String moduleName,
                             @NotNull String frameworkPrefix) throws IOException {
    String testName = testClassName + "-" + testMethodName;
    myLocalTestRunDataController.withTestDataHolder(localHolder -> {
      final int testNameId = localHolder.myTestNameEnumerator.enumerate(testName);
      TIntObjectHashMap<TIntArrayList> result = new TIntObjectHashMap<>();
      for (Map.Entry<String, Collection<String>> e : usedMethods.entrySet()) {
        int classId = localHolder.myClassEnumeratorCache.enumerate(e.getKey());
        TIntArrayList methodIds = new TIntArrayList();
        result.put(classId, methodIds);
        for (String methodName : e.getValue()) {
          methodIds.add(localHolder.myMethodEnumeratorCache.enumerate(methodName));
        }
      }
      TIntObjectHashMap<TIntArrayList> previousClassData = localHolder.myTestNameToUsedClassesAndMethodMap.get(testNameId);
      if (previousClassData == null) {
        previousClassData = myRemoteTestRunDataController.withTestDataHolder(
          remoteDataHolder -> {
            TIntObjectHashMap<TIntArrayList> remoteClassData = remoteDataHolder.myTestNameToUsedClassesAndMethodMap.get(testNameId);
            if (remoteClassData == null) return null;
            TIntObjectHashMap<TIntArrayList> result1 = new TIntObjectHashMap<>(remoteClassData.size());
            Ref<IOException> exceptionRef = new Ref<>();
            boolean processingResult = remoteClassData.forEachEntry((remoteClassKey, remoteClassMethodIds) -> {
              try {
                int localClassKey =
                  localHolder.myClassEnumeratorCache.enumerate(remoteDataHolder.myClassEnumeratorCache.valueOf(remoteClassKey));
                TIntArrayList localClassIds = new TIntArrayList(remoteClassMethodIds.size());
                for (int methodId : remoteClassMethodIds.toNativeArray()) {
                  localClassIds
                    .add(localHolder.myMethodEnumeratorCache.enumerate(remoteDataHolder.myMethodEnumeratorCache.valueOf(methodId)));
                }
                result1.put(localClassKey, localClassIds);
                return true;
              } catch (IOException ex) {
                exceptionRef.set(ex);
                return false;
              }
            });
            if (!processingResult) throw exceptionRef.get();
            return result1;
          });
      }

      String moduleId = moduleName == null ? null : frameworkPrefix + moduleName;
      localHolder.doUpdateFromDiff(testNameId, result, previousClassData, moduleId != null ? localHolder.myModuleNameEnumerator.enumerate(moduleId) : null);
      return null;
    });
  }
}
