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

import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.openapi.util.io.BufferExposingByteArrayOutputStream;
import com.intellij.openapi.vfs.newvfs.persistent.FlushingDaemon;
import com.intellij.util.io.*;
import gnu.trove.THashMap;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.io.DataOutputStream;
import java.util.*;
import java.util.concurrent.ScheduledFuture;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.InflaterInputStream;

/**
 * Created by Maxim.Mossienko on 7/9/2015.
 */
public class TestDiscoveryIndex implements ProjectComponent {
  private static final String REMOVED_MARKER = "-removed-";
  private final Object ourLock = new Object();
  private final Project myProject;
  private volatile Holder myHolder;

  public TestDiscoveryIndex(Project project) {
    myProject = project;
  }

  public Collection<String> getTestsByMethodName(String classFQName, String methodName) throws IOException {
    synchronized (ourLock) {
      return getHolder().myMethodQNameToTestNames.get(Pair.create(classFQName, methodName));
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

  private final class Holder {
    final PersistentHashMap<Pair<String, String>, Collection<String>> myMethodQNameToTestNames;
    final PersistentHashMap<String, Map<String, List<String>>> myTestNameToUsedClassesAndMethodMap;
    private ScheduledFuture<?> myFlushingFuture;
    private boolean myDisposed;

    Holder() {
      String path = myProject != null ?
                    TestDiscoveryExtension.baseTestDiscoveryPathForProject(myProject) :
                    "out";
      final File methodQNameToTestNameFile = new File(path + File.separator + "methodQNameToTestName.data");
      final File testNameToUsedClassesAndMethodMapFile = new File(path + File.separator + "testToCalledMethodNames");

      try {
        // todo better io recovery
        myMethodQNameToTestNames = IOUtil.openCleanOrResetBroken(
          new ThrowableComputable<PersistentHashMap<Pair<String, String>, Collection<String>>, IOException>() {
            @Override
            public PersistentHashMap<Pair<String, String>, Collection<String>> compute() throws IOException {
              return new PersistentHashMap<Pair<String, String>, Collection<String>>(
                methodQNameToTestNameFile,
                new StringPairKeyDescriptor(),
                new DataExternalizer<Collection<String>>() {
                  public void save(@NotNull DataOutput dataOutput, Collection<String> strings) throws IOException {
                    for (String string : strings) IOUtil.writeUTF(dataOutput, string);
                  }

                  public Collection<String> read(@NotNull DataInput dataInput) throws IOException {
                    Set<String> result = new THashSet<String>();

                    while (((InputStream)dataInput).available() > 0) {
                      String string = IOUtil.readUTF(dataInput);
                      if (REMOVED_MARKER.equals(string)) {
                        string = IOUtil.readUTF(dataInput);
                        result.remove(string);
                      }
                      else {
                        result.add(string);
                      }
                    }

                    return result;
                  }
                }
              );
            }
          }, methodQNameToTestNameFile);
        myTestNameToUsedClassesAndMethodMap = IOUtil.openCleanOrResetBroken(
          new ThrowableComputable<PersistentHashMap<String, Map<String, List<String>>>, IOException>() {
            @Override
            public PersistentHashMap<String, Map<String, List<String>>> compute() throws IOException {
              return new PersistentHashMap<String, Map<String, List<String>>>(
                testNameToUsedClassesAndMethodMapFile,
                EnumeratorStringDescriptor.INSTANCE,
                new DataExternalizer<Map<String, List<String>>>() {
                  public void save(@NotNull DataOutput dataOutput, Map<String, List<String>> classAndMethodsMap)
                    throws IOException {
                    BufferExposingByteArrayOutputStream out = new BufferExposingByteArrayOutputStream();
                    Deflater deflater = new Deflater(1);
                    DataOutputStream dataOutputStream =
                      new DataOutputStream(new BufferedOutputStream(new DeflaterOutputStream(out, deflater)));
                    DataInputOutputUtil.writeINT(dataOutputStream, classAndMethodsMap.size());
                    for (Map.Entry<String, List<String>> e : classAndMethodsMap.entrySet()) {
                      IOUtil.writeUTF(dataOutputStream, e.getKey());
                      DataInputOutputUtil.writeINT(dataOutputStream, e.getValue().size());
                      for (String methodName : e.getValue()) IOUtil.writeUTF(dataOutputStream, methodName);
                    }
                    dataOutputStream.close();
                    deflater.end();
                    dataOutput.write(out.getInternalBuffer(), 0, out.size());
                  }

                  public Map<String, List<String>> read(@NotNull DataInput dataInput) throws IOException {
                    byte[] buf;
                    dataInput.readFully(buf = new byte[((InputStream)dataInput).available()]);
                    DataInputStream dataInputStream =
                      new DataInputStream(new InflaterInputStream(new ByteArrayInputStream(buf)));
                    int numberOfClasses = DataInputOutputUtil.readINT(dataInputStream);
                    THashMap<String, List<String>> result = new THashMap<String, List<String>>(numberOfClasses);
                    while (numberOfClasses-- > 0) {
                      String className = IOUtil.readUTF(dataInputStream);
                      int numberOfMethods = DataInputOutputUtil.readINT(dataInputStream);
                      ArrayList<String> methods = new ArrayList<String>(numberOfMethods);
                      while (numberOfMethods-- > 0) methods.add(IOUtil.readUTF(dataInputStream));
                      result.put(className, methods);
                    }
                    dataInputStream.close();
                    return result;
                  }
                }
              );
            }
          }, testNameToUsedClassesAndMethodMapFile);
        myFlushingFuture = FlushingDaemon.everyFiveSeconds(new Runnable() {
          @Override
          public void run() {
            synchronized (ourLock) {
              if (myDisposed) {
                myFlushingFuture.cancel(false);
                return;
              }
              if (myMethodQNameToTestNames.isDirty()) {
                myMethodQNameToTestNames.force();
              }
              if (myTestNameToUsedClassesAndMethodMap.isDirty()) {
                myTestNameToUsedClassesAndMethodMap.force();
              }
            }
          }
        });
      }
      catch (IOException ex) {
        throw new RuntimeException(ex);
      }
    }

    void dispose() {
      assert Thread.holdsLock(ourLock);
      try {
        myMethodQNameToTestNames.close();
        myTestNameToUsedClassesAndMethodMap.close();
      }
      catch (IOException e) {
        throw new RuntimeException(e);
      }
      finally {
        myDisposed = true;
      }
    }

    
  }

  private static class StringPairKeyDescriptor implements KeyDescriptor<Pair<String, String>> {
    public static final StringPairKeyDescriptor INSTANCE = new StringPairKeyDescriptor();

    @Override
    public void save(@NotNull DataOutput out, Pair<String, String> value) throws IOException {
      IOUtil.writeUTF(out, value.first);
      IOUtil.writeUTF(out, value.second);
    }

    @Override
    public Pair<String, String> read(@NotNull DataInput in) throws IOException {
      return Pair.create(IOUtil.readUTF(in), IOUtil.readUTF(in));
    }

    @Override
    public int getHashCode(Pair<String, String> value) {
      return value.hashCode();
    }

    @Override
    public boolean isEqual(Pair<String, String> val1, Pair<String, String> val2) {
      return val1.equals(val2);
    }
  }
  
  public void updateFromTestTrace(File file) throws IOException {
    synchronized (ourLock) {
      int fileNameDotIndex = file.getName().lastIndexOf('.');
      final String testName = fileNameDotIndex != -1 ? file.getName().substring(0, fileNameDotIndex) : file.getName();

      Holder holder = getHolder();
      if (holder.myDisposed) return;
      Map<String, List<String>> classData = loadClassAndMethodsMap(file);
      Map<String, List<String>> previousClassData = holder.myTestNameToUsedClassesAndMethodMap.get(testName);

      ValueDiff valueDiff = new ValueDiff(classData, previousClassData);

      if (valueDiff.myRemovedClassData != null && !valueDiff.myRemovedClassData.isEmpty()) {
        for (String classQName : valueDiff.myRemovedClassData.keySet()) {
          for (String methodName : valueDiff.myRemovedClassData.get(classQName)) {
            holder.myMethodQNameToTestNames.appendData(createKey(classQName, methodName),
                                                       new PersistentHashMap.ValueDataAppender() {
                                                         @Override
                                                         public void append(DataOutput dataOutput) throws IOException {
                                                           IOUtil.writeUTF(dataOutput, REMOVED_MARKER);
                                                           IOUtil.writeUTF(dataOutput, testName);
                                                         }
                                                       }
            );
          }
        }
      }

      if (valueDiff.myAddedOrChangedClassData != null && !valueDiff.myAddedOrChangedClassData.isEmpty()) {
        for (String classQName : valueDiff.myAddedOrChangedClassData.keySet()) {
          for (String methodName : valueDiff.myAddedOrChangedClassData.get(classQName)) {
            holder.myMethodQNameToTestNames.appendData(createKey(classQName, methodName),
                                                       new PersistentHashMap.ValueDataAppender() {
                                                         @Override
                                                         public void append(DataOutput dataOutput) throws IOException {
                                                           IOUtil.writeUTF(dataOutput, testName);
                                                         }
                                                       });
          }
        }
      }

      if (valueDiff.myAddedOrChangedClassData != null && !valueDiff.myAddedOrChangedClassData.isEmpty() ||
          valueDiff.myRemovedClassData != null && !valueDiff.myRemovedClassData.isEmpty()
        ) {
        holder.myTestNameToUsedClassesAndMethodMap.put(testName, classData);
      }
    }
  }

  private static Pair<String, String> createKey(String classQName, String methodName) {
    return Pair.create(classQName, methodName);
  }

  static class ValueDiff {
    final Map<String, List<String>> myAddedOrChangedClassData;
    final Map<String, List<String>> myRemovedClassData;

    ValueDiff(Map<String, List<String>> classData, Map<String, List<String>> previousClassData) {
      Map<String, List<String>> addedOrChangedClassData = classData;
      Map<String, List<String>> removedClassData = previousClassData;

      if (previousClassData != null && !previousClassData.isEmpty()) {
        removedClassData = new THashMap<String, List<String>>();
        addedOrChangedClassData = new THashMap<String, List<String>>();

        for (String classQName : classData.keySet()) {
          List<String> currentMethods = classData.get(classQName);
          List<String> previousMethods = previousClassData.get(classQName);

          if (previousMethods == null) {
            addedOrChangedClassData.put(classQName, currentMethods);
            continue;
          }

          THashSet<String> previousMethodsSet = new THashSet<String>(previousMethods);
          THashSet<String> currentMethodsSet = new THashSet<String>(currentMethods);
          currentMethodsSet.removeAll(previousMethods);
          previousMethodsSet.removeAll(currentMethods);

          if (!currentMethodsSet.isEmpty()) {
            addedOrChangedClassData.put(classQName, new ArrayList<String>(currentMethodsSet));
          }
          if (!previousMethodsSet.isEmpty()) {
            removedClassData.put(classQName, new ArrayList<String>(previousMethodsSet));
          }
        }
        for (String classQName : previousClassData.keySet()) {
          if (classData.containsKey(classQName)) continue;

          List<String> previousMethods = previousClassData.get(classQName);
          removedClassData.put(classQName, previousMethods);
        }
      }

      myAddedOrChangedClassData = addedOrChangedClassData;
      myRemovedClassData = removedClassData;
    }
  }

  @NotNull
  private static Map<String, List<String>> loadClassAndMethodsMap(File file) throws IOException {
    DataInputStream inputStream = new DataInputStream(new InflaterInputStream(new BufferedInputStream(new FileInputStream(file))));

    try {
      int numberOfClasses = inputStream.readInt();
      Map<String, List<String>> classData = new THashMap<String, List<String>>();
      while (numberOfClasses-- > 0) {
        String classQName = inputStream.readUTF();
        int numberOfMethods = inputStream.readInt();
        List<String> methodsList;
        classData.put(classQName, methodsList = new ArrayList<String>(numberOfMethods));
        //System.out.println(classQName + "," + numberOfMethods);

        while (numberOfMethods-- > 0) {
          String methodName = inputStream.readUTF();
          methodsList.add(methodName);
          //System.out.println(methodName);
        }
      }
      return classData;
    }
    finally {
      inputStream.close();
    }
  }
}
