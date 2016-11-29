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

import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.CommonProcessors;
import com.intellij.util.Processor;
import com.intellij.util.io.*;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.builders.java.dependencyView.CloseableMaplet;
import org.jetbrains.jps.builders.java.dependencyView.CollectionFactory;
import org.jetbrains.jps.builders.java.dependencyView.IntObjectPersistentMultiMaplet;
import org.jetbrains.jps.builders.java.dependencyView.ObjectObjectPersistentMultiMaplet;
import org.jetbrains.jps.builders.storage.BuildDataCorruptedException;

import java.io.*;
import java.util.Collection;
import java.util.Locale;

public class CompilerBackwardReferenceIndex {
  private static final int VERSION = 0;
  private final static String FILE_ENUM_TAB = "file.path.enum.tab";
  private final static String INCOMPLETE_FILES_TAB = "incomplete.files.tab";
  private final static String USAGES_TAB = "refs.tab";
  private final static String BACK_USAGES_TAB = "back.refs.tab";
  private final static String HIERARCHY_TAB = "hierarchy.tab";
  private final static String BACK_HIERARCHY_TAB = "back.hierarchy.tab";
  private final static String CLASS_DEF_TAB = "class.def.tab";
  private final static String BACK_CLASS_DEF_TAB = "back.class.def.tab";
  public static final String VERSION_FILE = ".version";

  private final IntObjectPersistentMultiMaplet<LightRef> myReferenceMap;
  private final ObjectObjectPersistentMultiMaplet<LightDefinition, LightRef> myHierarchyMap;

  private final ObjectObjectPersistentMultiMaplet<LightRef, Integer> myBackwardReferenceMap;
  private final ObjectObjectPersistentMultiMaplet<LightRef, LightDefinition> myBackwardHierarchyMap;

  private final ObjectObjectPersistentMultiMaplet<LightRef, Integer> myBackwardClassDefinitionMap;
  private final IntObjectPersistentMultiMaplet<LightRef> myClassDefinitionMap;

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
      myFilePathEnumerator = new PersistentStringEnumerator(new File(myIndicesDir, FILE_ENUM_TAB)) {
        @Override
        public int enumerate(@Nullable String value) throws IOException {
          return super.enumerate(SystemInfo.isFileSystemCaseSensitive ? value : value.toLowerCase(Locale.ROOT));
        }
      };

      final KeyDescriptor<LightRef> lightUsageDescriptor = new LightRefDescriptor();
      final KeyDescriptor<LightDefinition> defDescriptor = LightDefinition.createDescriptor(lightUsageDescriptor);

      myBackwardReferenceMap = new ObjectObjectPersistentMultiMaplet<LightRef, Integer>(new File(myIndicesDir, BACK_USAGES_TAB),
                                                                                        lightUsageDescriptor,
                                                                                        EnumeratorIntegerDescriptor.INSTANCE,
                                                                                        new CollectionFactory<Integer>() {
                                                                                        @Override
                                                                                        public Collection<Integer> create() {
                                                                                          return new THashSet<Integer>();
                                                                                        }
                                                                                      });
      myBackwardHierarchyMap = new ObjectObjectPersistentMultiMaplet<LightRef, LightDefinition>(new File(myIndicesDir, BACK_HIERARCHY_TAB),
                                                                                                lightUsageDescriptor,
                                                                                                defDescriptor,
                                                                                                new CollectionFactory<LightDefinition>() {
                                                                       @Override
                                                                       public Collection<LightDefinition> create() {
                                                                         return new THashSet<LightDefinition>();
                                                                       }
                                                                     });


      myReferenceMap = new IntObjectPersistentMultiMaplet<LightRef>(new File(myIndicesDir, USAGES_TAB),
                                                                    EnumeratorIntegerDescriptor.INSTANCE,
                                                                    lightUsageDescriptor, new CollectionFactory<LightRef>() {
        @Override
        public Collection<LightRef> create() {
          return new THashSet<LightRef>();
        }
      });
      myHierarchyMap = new ObjectObjectPersistentMultiMaplet<LightDefinition, LightRef>(new File(myIndicesDir, HIERARCHY_TAB),
                                                                                        defDescriptor,
                                                                                        lightUsageDescriptor,
                                                                                        new CollectionFactory<LightRef>() {
                                                                                       @Override
                                                                                       public Collection<LightRef> create() {
                                                                                         return new THashSet<LightRef>();
                                                                                       }
                                                                                     });

      myClassDefinitionMap = new IntObjectPersistentMultiMaplet<LightRef>(new File(myIndicesDir, CLASS_DEF_TAB),
                                                                          EnumeratorIntegerDescriptor.INSTANCE,
                                                                          lightUsageDescriptor,
                                                                          new CollectionFactory<LightRef>() {
                                                                                    @Override
                                                                                    public Collection<LightRef> create() {
                                                                                      return new THashSet<LightRef>();
                                                                                    }
                                                                                  });

      myBackwardClassDefinitionMap = new ObjectObjectPersistentMultiMaplet<LightRef, Integer>(new File(myIndicesDir, BACK_CLASS_DEF_TAB),
                                                                                              lightUsageDescriptor,
                                                                                              EnumeratorIntegerDescriptor.INSTANCE,
                                                                                              new CollectionFactory<Integer>() {
                                                                                                        @Override
                                                                                                        public Collection<Integer> create() {
                                                                                                          return new THashSet<Integer>();
                                                                                                        }
                                                                                                      });

      myNameEnumerator = new ByteArrayEnumerator(new File(myIndicesDir, INCOMPLETE_FILES_TAB));
    }
    catch (IOException e) {
      removeIndexFiles(myIndicesDir);
      throw new BuildDataCorruptedException(e);
    }
  }

  @NotNull
  public ObjectObjectPersistentMultiMaplet<LightRef, Integer> getBackwardReferenceMap() {
    return myBackwardReferenceMap;
  }

  @NotNull
  public ObjectObjectPersistentMultiMaplet<LightRef, LightDefinition> getBackwardHierarchyMap() {
    return myBackwardHierarchyMap;
  }

  @NotNull
  public IntObjectPersistentMultiMaplet<LightRef> getReferenceMap() {
    return myReferenceMap;
  }

  @NotNull
  public ObjectObjectPersistentMultiMaplet<LightDefinition, LightRef> getHierarchyMap() {
    return myHierarchyMap;
  }

  @NotNull
  public ObjectObjectPersistentMultiMaplet<LightRef, Integer> getBackwardClassDefinitionMap() {
    return myBackwardClassDefinitionMap;
  }

  @NotNull
  public IntObjectPersistentMultiMaplet<LightRef> getClassDefinitionMap() {
    return myClassDefinitionMap;
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
    final CommonProcessors.FindFirstProcessor<BuildDataCorruptedException> exceptionProc =
      new CommonProcessors.FindFirstProcessor<BuildDataCorruptedException>();
    close(myFilePathEnumerator, exceptionProc);
    close(myBackwardHierarchyMap, exceptionProc);
    close(myBackwardReferenceMap, exceptionProc);
    close(myBackwardClassDefinitionMap, exceptionProc);
    close(myHierarchyMap, exceptionProc);
    close(myReferenceMap, exceptionProc);
    close(myClassDefinitionMap, exceptionProc);
    close(myNameEnumerator, exceptionProc);
    final BuildDataCorruptedException exception = exceptionProc.getFoundValue();
    if (exception != null) {
      removeIndexFiles(myIndicesDir);
      throw exception;
    }
  }

  void flush() {
    myBackwardHierarchyMap.flush(false);
    myBackwardReferenceMap.flush(false);
    myBackwardClassDefinitionMap.flush(false);
    myHierarchyMap.flush(false);
    myReferenceMap.flush(false);
    myClassDefinitionMap.flush(false);
    myNameEnumerator.force();
    myFilePathEnumerator.force();
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

  public static class LightDefinition {
    private final LightRef myUsage;
    private final int myFileId;

    LightDefinition(LightRef usage, int id) {
      myUsage = usage;
      myFileId = id;
    }

    public LightRef getRef() {
      return myUsage;
    }

    public int getFileId() {
      return myFileId;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      LightDefinition that = (LightDefinition)o;

      if (!myUsage.equals(that.myUsage)) return false;
      if (myFileId != that.myFileId) return false;

      return true;
    }

    @Override
    public int hashCode() {
      return myUsage.hashCode();
    }

    private static KeyDescriptor<LightDefinition> createDescriptor(final DataExternalizer<LightRef> usageDataExternalizer) {
      return new KeyDescriptor<LightDefinition>() {
        @Override
        public int getHashCode(LightDefinition value) {
          return value.hashCode();
        }

        @Override
        public boolean isEqual(LightDefinition val1, LightDefinition val2) {
          return val1.equals(val2);
        }

        @Override
        public void save(@NotNull DataOutput out, LightDefinition value) throws IOException {
          usageDataExternalizer.save(out, value.getRef());
          EnumeratorIntegerDescriptor.INSTANCE.save(out, value.getFileId());
        }

        @Override
        public LightDefinition read(@NotNull DataInput in) throws IOException {
          return new LightDefinition(usageDataExternalizer.read(in), EnumeratorIntegerDescriptor.INSTANCE.read(in));
        }
      };
    }
  }
}
