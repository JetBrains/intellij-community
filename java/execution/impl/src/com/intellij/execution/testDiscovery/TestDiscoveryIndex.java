/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

import com.intellij.execution.JavaTestConfigurationBase;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.ThrowableConvertor;
import com.intellij.util.io.DataInputOutputUtil;
import com.intellij.util.io.IOUtil;
import gnu.trove.THashSet;
import gnu.trove.TIntArrayList;
import gnu.trove.TIntObjectHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * @author Maxim.Mossienko on 7/9/2015.
 */
public class TestDiscoveryIndex implements ProjectComponent {
  static final Logger LOG = Logger.getInstance(TestDiscoveryIndex.class);

  private final Project myProject;

  //private volatile TestInfoHolder mySystemHolder;
  private final TestDataController myLocalTestRunDataController;
  private final TestDataController myRemoteTestRunDataController;

  public TestDiscoveryIndex(Project project) {
    this(project, TestDiscoveryExtension.baseTestDiscoveryPathForProject(project));
  }

  public TestDiscoveryIndex(final Project project, final String basePath) {
    myProject = project;

    myLocalTestRunDataController = new TestDataController(basePath, false);
    myRemoteTestRunDataController = new TestDataController(null, true);

    if (new File(basePath).exists()) {
      StartupManager.getInstance(project).registerPostStartupActivity(() -> ApplicationManager.getApplication().executeOnPooledThread(() -> {
        myLocalTestRunDataController.getHolder(); // proactively init with maybe io costly compact
      }));
    }

    //{
    //  setRemoteTestRunDataPath("C:\\ultimate\\system\\testDiscovery\\145.save");
    //}
  }

  public boolean hasTestTrace(@NotNull String testName) throws IOException {
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

  public void removeTestTrace(@NotNull String testName) throws IOException {
    myLocalTestRunDataController.withTestDataHolder(new ThrowableConvertor<TestInfoHolder, Void, IOException>() {
      @Override
      public Void convert(TestInfoHolder localHolder) throws IOException {
        final int testNameId = localHolder.myTestNameEnumerator.tryEnumerate(testName);  // todo remove remote data isn't possible
        if (testNameId != 0) {
          localHolder.doUpdateFromDiff(testNameId, null,
                                  localHolder.myTestNameToUsedClassesAndMethodMap.get(testNameId),
                                  null);
        }
        return null;
      }
    });
  }

  public void setRemoteTestRunDataPath(String path) {
    if(!TestInfoHolder.isValidPath(path)) {
      path = null;
    }
    myRemoteTestRunDataController.init(path);
    // todo: should we remove our local run data ?
  }

  public Collection<String> getTestsByMethodName(@NotNull String classFQName, @NotNull String methodName) throws IOException {
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
            int a = 1;
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
    private String myBasePath;
    private final boolean myReadOnly;
    private volatile TestInfoHolder myHolder;

    TestDataController(String basePath, boolean readonly) {
      myReadOnly = readonly;
      init(basePath);
    }

    void init(String basePath) {
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
          if (holder == null && myBasePath != null) holder = myHolder = new TestInfoHolder(myBasePath, myReadOnly, myLock);
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
      final File versionFile = TestInfoHolder.getVersionFile(myBasePath);
      FileUtil.delete(versionFile);

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
  public void initComponent() {
  }

  @Override
  public void disposeComponent() {
    myLocalTestRunDataController.dispose();
    myRemoteTestRunDataController.dispose();
  }

  @NotNull
  @Override
  public String getComponentName() {
    return getClass().getName();
  }

  @Override
  public void projectOpened() {
  }

  @Override
  public void projectClosed() {
  }

  public void updateFromTestTrace(@NotNull File file,
                                  @Nullable final String moduleName,
                                  @NotNull final String frameworkPrefix) throws IOException {
    int fileNameDotIndex = file.getName().lastIndexOf('.');
    final String testName = fileNameDotIndex != -1 ? file.getName().substring(0, fileNameDotIndex) : file.getName();
    doUpdateFromTestTrace(file, testName, moduleName != null ? frameworkPrefix + moduleName : null);
  }

  private void doUpdateFromTestTrace(File file, final String testName, @Nullable final String moduleName) throws IOException {
    myLocalTestRunDataController.withTestDataHolder(new ThrowableConvertor<TestInfoHolder, Void, IOException>() {
      @Override
      public Void convert(TestInfoHolder localHolder) throws IOException {
        final int testNameId = localHolder.myTestNameEnumerator.enumerate(testName);
        TIntObjectHashMap<TIntArrayList> classData = loadClassAndMethodsMap(file, localHolder);
        TIntObjectHashMap<TIntArrayList> previousClassData = localHolder.myTestNameToUsedClassesAndMethodMap.get(testNameId);
        if (previousClassData == null) {
          previousClassData = myRemoteTestRunDataController.withTestDataHolder(
            remoteDataHolder -> {
              TIntObjectHashMap<TIntArrayList> remoteClassData = remoteDataHolder.myTestNameToUsedClassesAndMethodMap.get(testNameId);
              if (remoteClassData == null) return null;
              TIntObjectHashMap<TIntArrayList> result = new TIntObjectHashMap<>(remoteClassData.size());
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
                  result.put(localClassKey, localClassIds);
                  return true;
                } catch (IOException ex) {
                  exceptionRef.set(ex);
                  return false;
                }
              });
              if (!processingResult) throw exceptionRef.get();
              return result;
            });
        }

        localHolder.doUpdateFromDiff(testNameId, classData, previousClassData, moduleName != null ? localHolder.myModuleNameEnumerator.enumerate(moduleName) : null);
        return null;
      }
    });
  }

  @NotNull
  private static TIntObjectHashMap<TIntArrayList> loadClassAndMethodsMap(File file, TestInfoHolder holder) throws IOException {
    DataInputStream inputStream = new DataInputStream(new BufferedInputStream(new FileInputStream(file), 64 * 1024));
    byte[] buffer = IOUtil.allocReadWriteUTFBuffer();

    try {
      int numberOfClasses = DataInputOutputUtil.readINT(inputStream);
      TIntObjectHashMap<TIntArrayList> classData = new TIntObjectHashMap<>(numberOfClasses);
      while (numberOfClasses-- > 0) {
        String classQName = IOUtil.readUTFFast(buffer, inputStream);
        int classId = holder.myClassEnumeratorCache.enumerate(classQName);
        int numberOfMethods = DataInputOutputUtil.readINT(inputStream);
        TIntArrayList methodsList = new TIntArrayList(numberOfMethods);

        while (numberOfMethods-- > 0) {
          String methodName = IOUtil.readUTFFast(buffer, inputStream);
          methodsList.add(holder.myMethodEnumeratorCache.enumerate(methodName));
        }

        classData.put(classId, methodsList);
      }
      return classData;
    }
    finally {
      inputStream.close();
    }
  }
}
