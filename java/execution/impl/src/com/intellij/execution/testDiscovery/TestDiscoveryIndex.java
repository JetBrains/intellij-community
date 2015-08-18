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

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.newvfs.persistent.FlushingDaemon;
import com.intellij.util.io.*;
import gnu.trove.TIntArrayList;
import gnu.trove.TIntHashSet;
import gnu.trove.TIntObjectHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.io.DataOutputStream;
import java.util.*;
import java.util.concurrent.ScheduledFuture;

/**
 * @author Maxim.Mossienko on 7/9/2015.
 */
public class TestDiscoveryIndex implements ProjectComponent {
  static final Logger LOG = Logger.getInstance(TestDiscoveryIndex.class);

  private static final int REMOVED_MARKER = -1;
  private final Object ourLock = new Object();
  private final Project myProject;
  private volatile Holder myHolder;

  public TestDiscoveryIndex(Project project) {
    myProject = project;

    String path = TestDiscoveryExtension.baseTestDiscoveryPathForProject(myProject);

    if (new File(path).exists()) {
      StartupManager.getInstance(project).registerPostStartupActivity(new Runnable() {
        @Override
        public void run() {
          ApplicationManager.getApplication().executeOnPooledThread(new Runnable() {
            @Override
            public void run() {
              getHolder(); // proactively init with maybe io costly compact
            }
          });
        }
      });
    }
  }

  public boolean hasTestTrace(@NotNull String testName) throws IOException {
    synchronized (ourLock) {
      Holder holder = null;
      try {
        holder = getHolder();
        final int testNameId = holder.myTestNameEnumerator.tryEnumerate(testName);
        if (testNameId == 0) return false;
        return holder.myTestNameToUsedClassesAndMethodMap.get(testNameId) != null;
      } catch (Throwable throwable) {
        thingsWentWrongLetsReinitialize(holder, throwable);
        return false;
      }
    }
  }

  public void removeTestTrace(@NotNull String testName) throws IOException {
    synchronized (ourLock) {
      Holder holder = null;
      try {
        holder = getHolder();

        final int testNameId = holder.myTestNameEnumerator.tryEnumerate(testName);
        if (testNameId == 0) return;
        doUpdateFromDiff(holder, testNameId, null, holder.myTestNameToUsedClassesAndMethodMap.get(testNameId));
      } catch (Throwable throwable) {
        thingsWentWrongLetsReinitialize(holder, throwable);
      }
    }
  }

  public Collection<String> getTestsByMethodName(@NotNull String classFQName, @NotNull String methodName) throws IOException {
    synchronized (ourLock) {
      Holder holder = null;
      try {
        holder = getHolder();
        final TIntArrayList list = holder.myMethodQNameToTestNames.get(
          createKey(
            holder.myClassEnumerator.enumerate(classFQName),
            holder.myMethodEnumerator.enumerate(methodName)
          )
        );
        if (list == null) return Collections.emptyList();
        final ArrayList<String> result = new ArrayList<String>(list.size());
        for (int testNameId : list.toNativeArray()) result.add(holder.myTestNameEnumerator.valueOf(testNameId));
        return result;
      } catch (Throwable throwable) {
        thingsWentWrongLetsReinitialize(holder, throwable);
        return Collections.emptyList();
      }
    }
  }

  private Holder getHolder() {
    Holder holder = myHolder;

    if (holder == null) {
      synchronized (ourLock) {
        holder = myHolder;
        if (holder == null) holder = myHolder = new Holder();
      }
    }
    return holder;
  }

  public static TestDiscoveryIndex getInstance(Project project) {
    return project.getComponent(TestDiscoveryIndex.class);
  }

  @Override
  public void initComponent() {
  }

  @Override
  public void disposeComponent() {
    synchronized (ourLock) {
      Holder holder = myHolder;
      if (holder != null) {
        holder.dispose();
        myHolder = null;
      }
    }
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

  private static final int VERSION = 2;

  private final class Holder {
    final PersistentHashMap<Long, TIntArrayList> myMethodQNameToTestNames;
    final PersistentHashMap<Integer, TIntObjectHashMap<TIntArrayList>> myTestNameToUsedClassesAndMethodMap;
    final PersistentStringEnumerator myClassEnumerator;
    final CachingEnumerator<String> myClassEnumeratorCache;
    final PersistentStringEnumerator myMethodEnumerator;
    final CachingEnumerator<String> myMethodEnumeratorCache;
    final PersistentStringEnumerator myTestNameEnumerator;
    final List<PersistentEnumeratorDelegate> myConstructedDataFiles = new ArrayList<PersistentEnumeratorDelegate>(4);

    private ScheduledFuture<?> myFlushingFuture;
    private boolean myDisposed;

    Holder() {
      String path = TestDiscoveryExtension.baseTestDiscoveryPathForProject(myProject);
      final File versionFile = getVersionFile(path);
      versionFile.getParentFile().mkdirs();
      final File methodQNameToTestNameFile = new File(path + File.separator + "methodQNameToTestName.data");
      final File testNameToUsedClassesAndMethodMapFile = new File(path + File.separator + "testToCalledMethodNames.data");
      final File classNameEnumeratorFile = new File(path + File.separator + "classNameEnumerator.data");
      final File methodNameEnumeratorFile = new File(path + File.separator + "methodNameEnumerator.data");
      final File testNameEnumeratorFile = new File(path + File.separator + "testNameEnumerator.data");

      try {
        int version = readVersion(versionFile);
        if (version != VERSION) {
          LOG.info("TestDiscoveryIndex was rewritten due to version change");
          deleteAllIndexDataFiles(methodQNameToTestNameFile, testNameToUsedClassesAndMethodMapFile, classNameEnumeratorFile, methodNameEnumeratorFile, testNameEnumeratorFile);

          writeVersion(versionFile);
        }

        PersistentHashMap<Long, TIntArrayList> methodQNameToTestNames;
        PersistentHashMap<Integer, TIntObjectHashMap<TIntArrayList>> testNameToUsedClassesAndMethodMap;
        PersistentStringEnumerator classNameEnumerator;
        PersistentStringEnumerator methodNameEnumerator;
        PersistentStringEnumerator testNameEnumerator;

        int iterations = 0;

        while(true) {
          ++iterations;

          try {
            methodQNameToTestNames = new PersistentHashMap<Long, TIntArrayList>(
              methodQNameToTestNameFile,
              new MethodQNameSerializer(),
              new TestNamesExternalizer()
            );
            myConstructedDataFiles.add(methodQNameToTestNames);

            testNameToUsedClassesAndMethodMap = new PersistentHashMap<Integer, TIntObjectHashMap<TIntArrayList>>(
              testNameToUsedClassesAndMethodMapFile,
              EnumeratorIntegerDescriptor.INSTANCE,
              new ClassesAndMethodsMapDataExternalizer()
            );
            myConstructedDataFiles.add(testNameToUsedClassesAndMethodMap);

            classNameEnumerator = new PersistentStringEnumerator(classNameEnumeratorFile);
            myConstructedDataFiles.add(classNameEnumerator);

            methodNameEnumerator = new PersistentStringEnumerator(methodNameEnumeratorFile);
            myConstructedDataFiles.add(methodNameEnumerator);

            testNameEnumerator = new PersistentStringEnumerator(testNameEnumeratorFile);
            myConstructedDataFiles.add(testNameEnumerator);

            break;
          } catch (Throwable throwable) {
            LOG.info("TestDiscoveryIndex problem", throwable);
            closeAllConstructedFiles(true);
            myConstructedDataFiles.clear();

            deleteAllIndexDataFiles(methodQNameToTestNameFile, testNameToUsedClassesAndMethodMapFile, classNameEnumeratorFile, methodNameEnumeratorFile,
                                    testNameEnumeratorFile);
            // try another time
          }

          if (iterations >= 3) {
            LOG.error("Unexpected circular initialization problem");
            assert false;
          }
        }

        myMethodQNameToTestNames = methodQNameToTestNames;
        myTestNameToUsedClassesAndMethodMap = testNameToUsedClassesAndMethodMap;
        myClassEnumerator = classNameEnumerator;
        myMethodEnumerator = methodNameEnumerator;
        myTestNameEnumerator = testNameEnumerator;
        myMethodEnumeratorCache = new CachingEnumerator<String>(methodNameEnumerator, EnumeratorStringDescriptor.INSTANCE);
        myClassEnumeratorCache = new CachingEnumerator<String>(classNameEnumerator, EnumeratorStringDescriptor.INSTANCE);

        myFlushingFuture = FlushingDaemon.everyFiveSeconds(new Runnable() {
          @Override
          public void run() {
            synchronized (ourLock) {
              if (myDisposed) {
                myFlushingFuture.cancel(false);
                return;
              }
              for(PersistentEnumeratorDelegate dataFile:myConstructedDataFiles) {
                if (dataFile.isDirty()) {
                  dataFile.force();
                }
              }
              myClassEnumeratorCache.clear();
              myMethodEnumeratorCache.clear();
            }
          }
        });
      }
      catch (IOException ex) {
        throw new RuntimeException(ex);
      }
    }

    private void closeAllConstructedFiles(boolean ignoreCloseProblem) {
      for(Closeable closeable:myConstructedDataFiles) {
        try {
          closeable.close();
        } catch (Throwable throwable) {
          if (!ignoreCloseProblem) throw new RuntimeException(throwable);
        }
      }
    }

    private void deleteAllIndexDataFiles(File methodQNameToTestNameFile,
                                         File testNameToUsedClassesAndMethodMapFile,
                                         File classNameEnumeratorFile, File methodNameEnumeratorFile, File testNameEnumeratorFile) {
      IOUtil.deleteAllFilesStartingWith(methodQNameToTestNameFile);
      IOUtil.deleteAllFilesStartingWith(testNameToUsedClassesAndMethodMapFile);
      IOUtil.deleteAllFilesStartingWith(classNameEnumeratorFile);
      IOUtil.deleteAllFilesStartingWith(methodNameEnumeratorFile);
      IOUtil.deleteAllFilesStartingWith(testNameEnumeratorFile);
    }

    private void writeVersion(File versionFile) throws IOException {
      final DataOutputStream versionOut = new DataOutputStream(new FileOutputStream(versionFile));

      try {
        DataInputOutputUtil.writeINT(versionOut, VERSION);
      } finally {
        try { versionOut.close(); } catch (IOException ignore) {}
      }
    }

    private int readVersion(File versionFile) throws IOException {
      if (!versionFile.exists()) return 0;
      final DataInputStream versionInput = new DataInputStream(new FileInputStream(versionFile));
      int version;
      try {
        version = DataInputOutputUtil.readINT(versionInput);
      } finally {
        try { versionInput.close(); } catch (IOException ignore) {}
      }
      return version;
    }

    void dispose() {
      assert Thread.holdsLock(ourLock);
      try {
        closeAllConstructedFiles(false);
      }
      finally {
        myDisposed = true;
      }
    }

    private class TestNamesExternalizer implements DataExternalizer<TIntArrayList> {
      public void save(@NotNull DataOutput dataOutput, TIntArrayList testNameIds) throws IOException {
        for (int testNameId : testNameIds.toNativeArray()) DataInputOutputUtil.writeINT(dataOutput, testNameId);
      }

      public TIntArrayList read(@NotNull DataInput dataInput) throws IOException {
        TIntHashSet result = new TIntHashSet();

        while (((InputStream)dataInput).available() > 0) {
          int id = DataInputOutputUtil.readINT(dataInput);
          if (REMOVED_MARKER == id) {
            id = DataInputOutputUtil.readINT(dataInput);
            result.remove(id);
          }
          else {
            result.add(id);
          }
        }

        return new TIntArrayList(result.toArray());
      }
    }

    private class ClassesAndMethodsMapDataExternalizer implements DataExternalizer<TIntObjectHashMap<TIntArrayList>> {
      public void save(@NotNull final DataOutput dataOutput, TIntObjectHashMap<TIntArrayList> classAndMethodsMap)
        throws IOException {
        DataInputOutputUtil.writeINT(dataOutput, classAndMethodsMap.size());
        final int[] classNameIds = classAndMethodsMap.keys();
        Arrays.sort(classNameIds);

        int prevClassNameId = 0;
        for(int classNameId:classNameIds) {
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
        TIntObjectHashMap<TIntArrayList> result = new TIntObjectHashMap<TIntArrayList>();
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
  
  public void updateFromTestTrace(@NotNull File file) throws IOException {
    int fileNameDotIndex = file.getName().lastIndexOf('.');
    final String testName = fileNameDotIndex != -1 ? file.getName().substring(0, fileNameDotIndex) : file.getName();
    doUpdateFromTestTrace(file, testName);
  }

  private void doUpdateFromTestTrace(File file, final String testName) throws IOException {
    synchronized (ourLock) {
      Holder holder = getHolder();
      if (holder.myDisposed) return;
      try {
        final int testNameId = holder.myTestNameEnumerator.enumerate(testName);
        TIntObjectHashMap<TIntArrayList> classData = loadClassAndMethodsMap(file, holder);
        TIntObjectHashMap<TIntArrayList> previousClassData = holder.myTestNameToUsedClassesAndMethodMap.get(testNameId);

        doUpdateFromDiff(holder, testNameId, classData, previousClassData);
      } catch (Throwable throwable) {
        thingsWentWrongLetsReinitialize(holder, throwable);
      }
    }
  }

  private void doUpdateFromDiff(Holder holder,
                                final int testNameId,
                                @Nullable TIntObjectHashMap<TIntArrayList> classData,
                                @Nullable TIntObjectHashMap<TIntArrayList> previousClassData) throws IOException {
    ValueDiff valueDiff = new ValueDiff(classData, previousClassData);

    if (valueDiff.hasRemovedDelta()) {
      for (int classQName : valueDiff.myRemovedClassData.keys()) {
        for (int methodName : valueDiff.myRemovedClassData.get(classQName).toNativeArray()) {
          holder.myMethodQNameToTestNames.appendData(createKey(classQName, methodName),
                                                     new PersistentHashMap.ValueDataAppender() {
                                                       @Override
                                                       public void append(DataOutput dataOutput) throws IOException {
                                                         DataInputOutputUtil.writeINT(dataOutput, REMOVED_MARKER);
                                                         DataInputOutputUtil.writeINT(dataOutput, testNameId);
                                                       }
                                                     }
          );
        }
      }
    }

    if (valueDiff.hasAddedDelta()) {
      for (int classQName : valueDiff.myAddedOrChangedClassData.keys()) {
        for (int methodName : valueDiff.myAddedOrChangedClassData.get(classQName).toNativeArray()) {
          holder.myMethodQNameToTestNames.appendData(createKey(classQName, methodName),
                                                     new PersistentHashMap.ValueDataAppender() {
                                                       @Override
                                                       public void append(DataOutput dataOutput) throws IOException {
                                                         DataInputOutputUtil.writeINT(dataOutput, testNameId);
                                                       }
                                                     });
        }
      }
    }

    if ((valueDiff.hasAddedDelta() || valueDiff.hasRemovedDelta())) {
      if(classData != null) {
        holder.myTestNameToUsedClassesAndMethodMap.put(testNameId, classData);
      } else {
        holder.myTestNameToUsedClassesAndMethodMap.remove(testNameId);
      }
    }
  }

  @NotNull
  private static File getVersionFile(String path) {
    return new File(path + File.separator + "index.version");
  }

  private void thingsWentWrongLetsReinitialize(@Nullable Holder holder, Throwable throwable) throws IOException {
    LOG.error("Unexpected problem", throwable);
    if (holder != null) holder.dispose();
    String path = TestDiscoveryExtension.baseTestDiscoveryPathForProject(myProject);
    final File versionFile = getVersionFile(path);
    FileUtil.delete(versionFile);

    myHolder = null;
    if (throwable instanceof IOException) throw (IOException) throwable;
  }

  private static long createKey(int classQName, int methodName) {
    return ((long)classQName << 32) | methodName;
  }

  static class ValueDiff {
    final TIntObjectHashMap<TIntArrayList> myAddedOrChangedClassData;
    final TIntObjectHashMap<TIntArrayList> myRemovedClassData;

    ValueDiff(@Nullable TIntObjectHashMap<TIntArrayList> classData, @Nullable TIntObjectHashMap<TIntArrayList> previousClassData) {
      TIntObjectHashMap<TIntArrayList> addedOrChangedClassData = classData;
      TIntObjectHashMap<TIntArrayList> removedClassData = previousClassData;

      if (previousClassData != null && !previousClassData.isEmpty()) {
        removedClassData = new TIntObjectHashMap<TIntArrayList>();
        addedOrChangedClassData = new TIntObjectHashMap<TIntArrayList>();

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
  private static TIntObjectHashMap<TIntArrayList> loadClassAndMethodsMap(File file, Holder holder) throws IOException {
    DataInputStream inputStream = new DataInputStream(new BufferedInputStream(new FileInputStream(file), 64 * 1024));
    byte[] buffer = IOUtil.allocReadWriteUTFBuffer();

    try {
      int numberOfClasses = DataInputOutputUtil.readINT(inputStream);
      TIntObjectHashMap<TIntArrayList> classData = new TIntObjectHashMap<TIntArrayList>(numberOfClasses);
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
