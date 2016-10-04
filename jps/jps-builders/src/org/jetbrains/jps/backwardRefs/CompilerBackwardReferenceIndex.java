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
package org.jetbrains.jps.backwardRefs;

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.CommonProcessors;
import com.intellij.util.Processor;
import com.intellij.util.io.EnumeratorIntegerDescriptor;
import com.intellij.util.io.PersistentStringEnumerator;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.builders.java.dependencyView.*;
import org.jetbrains.jps.builders.storage.BuildDataCorruptedException;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.util.Collection;

public class CompilerBackwardReferenceIndex {
  private static final int VERSION = 0;
  private final static String FILE_ENUM_TAB = "file.path.enum.tab";
  private final static String INCOMPLETE_FILES_TAB = "incomplete.files.tab";
  private final static String USAGES_TAB = "refs.tab";
  private final static String BACK_USAGES_TAB = "back.refs.tab";
  private final static String HIERARCHY_TAB = "hierarchy.tab";
  private final static String BACK_HIERARCHY_TAB = "back.hierarchy.tab";
  public static final String VERSION_FILE = ".version";

  private final IntObjectPersistentMultiMaplet<LightUsage> myReferenceMap;
  private final IntIntPersistentMultiMaplet myHierarchyMap;

  private final ObjectObjectPersistentMultiMaplet<LightUsage, Integer> myBackwardReferenceMap;
  private final IntIntPersistentMultiMaplet myBackwardHierarchyMap;

  private final ByteArrayEnumerator myNameEnumerator;
  private final PersistentStringEnumerator myFilePathEnumerator;

  private final File myIndicesDir;


  public CompilerBackwardReferenceIndex(File buildDir) {
    myIndicesDir = getIndexDir(buildDir);
    if (!myIndicesDir.exists() && !myIndicesDir.mkdirs()) {
      throw new RuntimeException("Can't create dir: " + buildDir.getAbsolutePath());
    }
    try {
      if (versionDiffers(buildDir)) {
        FileUtil.writeToFile(new File(myIndicesDir, VERSION_FILE), String.valueOf(VERSION));
      }
      myFilePathEnumerator = new PersistentStringEnumerator(new File(myIndicesDir, FILE_ENUM_TAB));

      myBackwardReferenceMap = new ObjectObjectPersistentMultiMaplet<LightUsage, Integer>(new File(myIndicesDir, BACK_USAGES_TAB),
                                                                                          LightUsage.createDescriptor(),
                                                                                          EnumeratorIntegerDescriptor.INSTANCE,
                                                                                          new CollectionFactory<Integer>() {
                                                                                        @Override
                                                                                        public Collection<Integer> create() {
                                                                                          return new THashSet<Integer>();
                                                                                        }
                                                                                      });
      myBackwardHierarchyMap = new IntIntPersistentMultiMaplet(new File(myIndicesDir, BACK_HIERARCHY_TAB),
                                                               EnumeratorIntegerDescriptor.INSTANCE);

      myReferenceMap = new IntObjectPersistentMultiMaplet<LightUsage>(new File(myIndicesDir, USAGES_TAB),
                                                                      EnumeratorIntegerDescriptor.INSTANCE,
                                                                      LightUsage.createDescriptor(), new CollectionFactory<LightUsage>() {
        @Override
        public Collection<LightUsage> create() {
          return new THashSet<LightUsage>();
        }
      });
      myHierarchyMap = new IntIntPersistentMultiMaplet(new File(myIndicesDir, HIERARCHY_TAB), EnumeratorIntegerDescriptor.INSTANCE);

      myNameEnumerator = new ByteArrayEnumerator(new File(myIndicesDir, INCOMPLETE_FILES_TAB));
    }
    catch (IOException e) {
      removeIndexFiles(myIndicesDir);
      throw new BuildDataCorruptedException(e);
    }
  }

  @NotNull
  public ObjectObjectPersistentMultiMaplet<LightUsage, Integer> getBackwardReferenceMap() {
    return myBackwardReferenceMap;
  }

  @NotNull
  public IntIntPersistentMultiMaplet getBackwardHierarchyMap() {
    return myBackwardHierarchyMap;
  }

  @NotNull
  public IntObjectPersistentMultiMaplet<LightUsage> getReferenceMap() {
    return myReferenceMap;
  }

  @NotNull
  public IntIntPersistentMultiMaplet getHierarchyMap() {
    return myHierarchyMap;
  }

  @NotNull
  public ByteArrayEnumerator getByteSeqEum() {
    return myNameEnumerator;
  }

  @NotNull
  public PersistentStringEnumerator getFilePathEnumerator() {
    return myFilePathEnumerator;
  }

  public void close() {
    final CommonProcessors.FindFirstAndOnlyProcessor<BuildDataCorruptedException> exceptionProc =
      new CommonProcessors.FindFirstAndOnlyProcessor<BuildDataCorruptedException>();
    close(myFilePathEnumerator, exceptionProc);
    close(myBackwardHierarchyMap, exceptionProc);
    close(myBackwardReferenceMap, exceptionProc);
    close(myHierarchyMap, exceptionProc);
    close(myReferenceMap, exceptionProc);
    close(myNameEnumerator, exceptionProc);
    final BuildDataCorruptedException exception = exceptionProc.getFoundValue();
    if (exception != null) {
      removeIndexFiles(myIndicesDir);
      throw exception;
    }
  }

  public static void removeIndexFiles(File buildDir) {
    final File indexDir = getIndexDir(buildDir);
    if (indexDir.exists()) {
      FileUtil.delete(indexDir);
    }
  }

  private static File getIndexDir(@NotNull File buildDir) {
    return new File(buildDir, "backward-refs");
  }

  public static boolean exist(@NotNull File buildDir) {
    return getIndexDir(buildDir).exists();
  }

  public static boolean versionDiffers(@NotNull File buildDir) {
    File versionFile = new File(getIndexDir(buildDir), VERSION_FILE);
    try {
      return Integer.parseInt(FileUtil.loadFile(versionFile)) != VERSION;
    }
    catch (final IOException e) {
      return true;
    }
  }

  private static void close(Closeable closeable, Processor<BuildDataCorruptedException> exceptionProcessor) {
    try {
      closeable.close();
    }
    catch (IOException e) {
      exceptionProcessor.process(new BuildDataCorruptedException(e));
    }
  }

  private static void close(CloseableMaplet closeable, Processor<BuildDataCorruptedException> exceptionProcessor) {
    try {
      closeable.close();
    }
    catch (BuildDataCorruptedException e) {
      exceptionProcessor.process(e);
    }
  }
}
