// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.testDiscovery;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.util.ThrowableConvertor;
import com.intellij.util.containers.MultiMap;
import com.intellij.util.io.PathKt;
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

  private volatile TestInfoHolder myHolder;
  private final Object myLock = new Object();
  private final Path myBasePath;

  public static TestDiscoveryIndex getInstance(Project project) {
    return project.getComponent(TestDiscoveryIndex.class);
  }

  public TestDiscoveryIndex(Project project) {
    this(project, TestDiscoveryExtension.baseTestDiscoveryPathForProject(project));
  }

  public TestDiscoveryIndex(final Project project, @NotNull Path basePath) {
    myBasePath = basePath;

    if (Files.exists(basePath)) {
      StartupManager.getInstance(project).registerPostStartupActivity(() -> ApplicationManager.getApplication().executeOnPooledThread(() -> {
        getHolder(); // proactively init with maybe io costly compact
      }));
    }
  }

  public boolean hasTestTrace(@NotNull String testClassName, @NotNull String testMethodName, byte frameworkId) throws IOException {
    Boolean result = withTestDataHolder(localHolder -> {
      TestInfoHolder.TestId testId = localHolder.createTestId(testClassName, testMethodName, frameworkId);
      final int testNameId = localHolder.myTestEnumerator.tryEnumerate(testId);
      return testNameId != 0 && localHolder.myTestNameToUsedClassesAndMethodMap.get(testNameId) != null;
    });
    return result == Boolean.TRUE;
  }

  public void removeTestTrace(@NotNull String testClassName, @NotNull String testMethodName, byte frameworkId) throws IOException {
    withTestDataHolder(localHolder -> {
      TestInfoHolder.TestId testId = localHolder.createTestId(testClassName, testMethodName, frameworkId);
      final int testNameId = localHolder.myTestEnumerator.tryEnumerate(testId);
      if (testNameId != 0) {
        localHolder.doUpdateFromDiff(testNameId, null,
                                localHolder.myTestNameToUsedClassesAndMethodMap.get(testNameId),
                                null);
      }
      return null;
    });
  }

  @NotNull
  public MultiMap<String, String> getTestsByMethodName(@NotNull String classFQName, @NotNull String methodName, byte frameworkId) throws IOException {
    MultiMap<String, String> map = withTestDataHolder(new ThrowableConvertor<TestInfoHolder, MultiMap<String, String>, IOException>() {
      @Override
      public MultiMap<String, String> convert(TestInfoHolder localHolder) throws IOException {
        Collection<TestInfoHolder.TestId> ids = getTestIdsByMethod(localHolder);
        if (ids.isEmpty()) return MultiMap.empty();

        MultiMap<String, String> result = new MultiMap<>();
        for (TestInfoHolder.TestId id : ids) {
          if (id.getFrameworkId() == frameworkId) {
            result.putValue(localHolder.myClassEnumerator.valueOf(id.getClassId()),
                            localHolder.myMethodEnumerator.valueOf(id.getMethodId()));
          }
        }
        return result;
      }

      private Collection<TestInfoHolder.TestId> getTestIdsByMethod(TestInfoHolder localHolder) throws IOException {
        final TIntArrayList localList = localHolder.myMethodQNameToTestNames.get(
          TestInfoHolder.createKey(
            localHolder.myClassEnumerator.enumerate(classFQName),
            localHolder.myMethodEnumerator.enumerate(methodName)
          )
        );
        if (localList == null) return Collections.emptyList();

        final ArrayList<TestInfoHolder.TestId> result = new ArrayList<>(localList.size());
        for (int testNameId : localList.toNativeArray()) {
          if (testNameId < 0) {
            continue;
          }
          result.add(localHolder.myTestEnumerator.valueOf(testNameId));
        }
        return result;
      }
    });
    return map == null ? MultiMap.empty() : map;
  }

  @NotNull
  public Collection<String> getTestModulesByMethodName(@NotNull String classFQName, @NotNull String methodName, byte frameworkId) throws IOException {
    List<String> modules = withTestDataHolder(localHolder -> {
      final TIntArrayList list = localHolder.myTestNameToNearestModule.get(
        TestInfoHolder.createKey(
          localHolder.myClassEnumerator.enumerate(classFQName),
          localHolder.myMethodEnumerator.enumerate(methodName)
        )
      );
      if (list == null) return Collections.emptyList();
      final ArrayList<String> result = new ArrayList<>(list.size());
      for (int moduleNameId : list.toNativeArray()) {
        final TestInfoHolder.ModuleId moduleNameWithPrefix = localHolder.myModuleEnumerator.valueOf(moduleNameId);
        if (moduleNameWithPrefix != null && moduleNameWithPrefix.getFrameworkId() == frameworkId) {
          result.add(moduleNameWithPrefix.getModuleName());
        }
      }
      return result;
    });
    return modules == null ? Collections.emptySet() : modules;
  }

  @Override
  public void dispose() {
    synchronized (myLock) {
      TestInfoHolder holder = myHolder;
      if (holder != null) {
        holder.dispose();
        myHolder = null;
      }
    }
  }

  public void updateFromData(@NotNull String testClassName,
                             @NotNull String testMethodName,
                             @NotNull MultiMap<String, String> usedMethods,
                             @Nullable String moduleName,
                             byte frameworkId) throws IOException {
    withTestDataHolder(localHolder -> {
      final int testNameId = localHolder.myTestEnumerator.enumerate(localHolder.createTestId(testClassName, testMethodName, frameworkId));
      TIntObjectHashMap<TIntArrayList> result = new TIntObjectHashMap<>();
      for (Map.Entry<String, Collection<String>> e : usedMethods.entrySet()) {
        int classId = localHolder.myClassEnumerator.enumerate(e.getKey());
        TIntArrayList methodIds = new TIntArrayList();
        result.put(classId, methodIds);
        for (String methodName : e.getValue()) {
          methodIds.add(localHolder.myMethodEnumerator.enumerate(methodName));
        }
      }
      TIntObjectHashMap<TIntArrayList> previousClassData = localHolder.myTestNameToUsedClassesAndMethodMap.get(testNameId);
      localHolder.doUpdateFromDiff(testNameId, result, previousClassData, moduleName != null ? localHolder.myModuleEnumerator.enumerate(new TestInfoHolder.ModuleId(moduleName, frameworkId)) : null);
      return null;
    });
  }

  private TestInfoHolder getHolder() {
    TestInfoHolder holder = myHolder;

    if (holder == null) {
      synchronized (myLock) {
        holder = myHolder;
        if (holder == null && myBasePath != null) myHolder = holder = new TestInfoHolder(myBasePath, false, myLock);
      }
    }
    return holder;
  }

  private void thingsWentWrongLetsReinitialize(@Nullable TestInfoHolder holder, Throwable throwable) throws IOException {
    LOG.error("Unexpected problem", throwable);
    if (holder != null) holder.dispose();
    PathKt.delete(TestInfoHolder.getVersionFile(myBasePath));

    myHolder = null;
    if (throwable instanceof IOException) throw (IOException)throwable;
  }

  private <R> R withTestDataHolder(ThrowableConvertor<TestInfoHolder, R, IOException> action) throws IOException {
    synchronized (myLock) {
      TestInfoHolder holder = getHolder();
      if (holder == null || holder.isDisposed()) return null;
      try {
        return action.convert(holder);
      }
      catch (Throwable throwable) {
        thingsWentWrongLetsReinitialize(holder, throwable);
      }
      return null;
    }
  }
}
