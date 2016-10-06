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

import com.intellij.util.Function;
import com.intellij.util.SystemProperties;
import com.intellij.util.containers.ContainerUtil;
import com.sun.tools.javac.code.Symbol;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.builders.java.JavaBuilderUtil;
import org.jetbrains.jps.builders.storage.BuildDataCorruptedException;
import org.jetbrains.jps.incremental.CompileContext;
import org.jetbrains.jps.incremental.java.JavaBuilder;
import org.jetbrains.jps.incremental.storage.BuildDataManager;
import org.jetbrains.jps.javac.ast.api.JavacRefSymbol;
import org.jetbrains.jps.model.java.compiler.JavaCompilers;

import javax.tools.*;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

public class BackwardReferenceIndexWriter {
  public static final String PROP_KEY = "ref.index.builder";

  private static volatile BackwardReferenceIndexWriter ourInstance;

  private final CompilerBackwardReferenceIndex myIndex;
  private final boolean myRebuild;

  private BackwardReferenceIndexWriter(CompilerBackwardReferenceIndex index, boolean rebuild) {
    myIndex = index;
    myRebuild = rebuild;
  }

  static BackwardReferenceIndexWriter getInstance() {
    return ourInstance;
  }

  static void initialize(@NotNull final CompileContext context) {
    if (isEnabled()) {
      final BuildDataManager dataManager = context.getProjectDescriptor().dataManager;
      final File buildDir = dataManager.getDataPaths().getDataStorageRoot();
      boolean isRebuild = JavaBuilderUtil.isForcedRecompilationAllJavaModules(context);

      if (!JavaCompilers.JAVAC_ID.equals(JavaBuilder.getUsedCompilerId(context))) {
        CompilerBackwardReferenceIndex.removeIndexFiles(buildDir);
        return;
      }
      if (isRebuild) {
        CompilerBackwardReferenceIndex.removeIndexFiles(buildDir);
      }
      else if (CompilerBackwardReferenceIndex.versionDiffers(buildDir)) {
        throw new BuildDataCorruptedException(new IOException("backward reference index should be updated to actual version"));
      }

      if (CompilerBackwardReferenceIndex.exist(buildDir) || isRebuild) {
        ourInstance = new BackwardReferenceIndexWriter(new CompilerBackwardReferenceIndex(buildDir), isRebuild);
      }
    }
  }

  static boolean isEnabled() {
    return SystemProperties.getBooleanProperty(PROP_KEY, false);
  }

  void close() {
    myIndex.close();
  }

  int enumerateFile(JavaFileObject file) {
    return enumeratePath(file.getName());
  }

  synchronized LightUsage.LightClassUsage asClassUsage(Symbol name) {
    return new LightUsage.LightClassUsage(myIndex.getByteSeqEum().enumerate(LightUsage.bytes(name)));
  }

  synchronized void processDeletedFiles(Collection<String> paths) {
    for (String path : paths) {
      final int deletedFileId = enumeratePath(path);

      //remove from reference maps
      final Collection<LightUsage> refs = myIndex.getReferenceMap().get(deletedFileId);
      if (refs != null) {
        for (LightUsage ref : refs) {
          myIndex.getBackwardReferenceMap().removeFrom(ref, deletedFileId);
        }
      }
      myIndex.getReferenceMap().remove(deletedFileId);

      //remove from definition & hierarchy maps
      final Collection<LightUsage> definedClasses = myIndex.getClassDefinitionMap().get(deletedFileId);
      removeClassesFromHierarchy(deletedFileId, definedClasses);
      myIndex.getClassDefinitionMap().remove(deletedFileId);
    }
  }

  synchronized void writeClassDefinitions(int fileId, Collection<LightUsage> classes) {
    if (myRebuild) {
      directlyWriteClassDefinitions(fileId, classes);
    } else {
      updateClassDefinitions(fileId, classes);
    }
  }

  private void updateClassDefinitions(int fileId, Collection<LightUsage> classes) {
    final Collection<LightUsage> oldDefs = myIndex.getClassDefinitionMap().get(fileId);
    final Collection<LightUsage> oldDefsCopy = oldDefs == null ? null : new THashSet<LightUsage>(oldDefs);

    myIndex.getClassDefinitionMap().replace(fileId, classes);
    for (LightUsage aClass : classes) {
      if (oldDefsCopy == null || !oldDefsCopy.remove(aClass)) {
        myIndex.getBackwardClassDefinitionMap().put(aClass, fileId);
      }
    }

    removeClassesFromHierarchy(fileId, oldDefsCopy);
  }

  private void directlyWriteClassDefinitions(int fileId, Collection<LightUsage> classes) {
    myIndex.getClassDefinitionMap().put(fileId, classes);
    for (LightUsage aClass : classes) {
      myIndex.getBackwardClassDefinitionMap().put(aClass, fileId);
    }
  }

  synchronized void writeReferences(int fileId, Collection<JavacRefSymbol> refs) {
    final ByteArrayEnumerator byteSeqEum = myIndex.getByteSeqEum();
    final List<LightUsage> usages = ContainerUtil.mapNotNull(refs, new Function<JavacRefSymbol, LightUsage>() {
      @Override
      public LightUsage fun(JavacRefSymbol symbol) {
        return LightUsage.fromSymbol(symbol, byteSeqEum);
      }
    });

    if (myRebuild) {
      for (LightUsage usage : usages) {
        myIndex.getBackwardReferenceMap().put(usage, fileId);
        myIndex.getReferenceMap().put(fileId, usage);
      }
    }
    else {
      updateReferenceIndicesIncrementally(fileId, usages);
    }
  }

  private void updateReferenceIndicesIncrementally(int fileId, Collection<LightUsage> usages) {
    final Collection<LightUsage> rawOldUsages = myIndex.getReferenceMap().get(fileId);
    Collection<LightUsage> oldUsages = rawOldUsages == null ? null : new ArrayList<LightUsage>(rawOldUsages);
    for (LightUsage usage : usages) {
      if (oldUsages == null || !oldUsages.remove(usage)) {
        myIndex.getBackwardReferenceMap().put(usage, fileId);
        myIndex.getReferenceMap().put(fileId, usage);
      }
    }
    if (oldUsages != null && !oldUsages.isEmpty()) {
      myIndex.getReferenceMap().removeAll(fileId, oldUsages);
      for (LightUsage usage : oldUsages) {
        myIndex.getBackwardReferenceMap().removeFrom(usage, fileId);
      }
    }
  }

  synchronized void writeHierarchy(int fileId, LightUsage.LightClassUsage aClass, LightUsage.LightClassUsage[] supers) {
    CompilerBackwardReferenceIndex.LightDefinition def = new CompilerBackwardReferenceIndex.LightDefinition(aClass, fileId);
    if (myRebuild) {
      directlyWriteHierarchyIndices(def, supers);
    }
    else {
      updateHierarchyIndicesIncrementally(def, supers);
    }
  }

  private synchronized int enumeratePath(String file) {
    try {
      return myIndex.getFilePathEnumerator().enumerate(file);
    }
    catch (IOException e) {
      throw new BuildDataCorruptedException(e);
    }
  }

  private void directlyWriteHierarchyIndices(CompilerBackwardReferenceIndex.LightDefinition classId, LightUsage.LightClassUsage[] superIds) {
    for (LightUsage.LightClassUsage superId : superIds) {
      myIndex.getBackwardHierarchyMap().put(superId, classId);
      myIndex.getHierarchyMap().put(classId, superId);
    }
  }

  private void updateHierarchyIndicesIncrementally(final CompilerBackwardReferenceIndex.LightDefinition classId, LightUsage.LightClassUsage[] superIds) {
    final Collection<LightUsage> rawOldSupers = myIndex.getHierarchyMap().get(classId);
    Set<LightUsage> oldSuperClasses = rawOldSupers == null ? null : new THashSet<LightUsage>(rawOldSupers);
    for (LightUsage.LightClassUsage superId: superIds) {
      if (oldSuperClasses == null || !oldSuperClasses.remove(superId)) {
        myIndex.getBackwardHierarchyMap().put(superId, classId);
        myIndex.getHierarchyMap().put(classId, superId);
      }
    }
    if (oldSuperClasses != null && !oldSuperClasses.isEmpty()) {
      myIndex.getHierarchyMap().removeAll(classId, oldSuperClasses);
      for (LightUsage anOldClass : oldSuperClasses) {
        myIndex.getBackwardHierarchyMap().put(anOldClass, classId);
      }
    }
  }


  private void removeClassesFromHierarchy(int deletedFileId, Collection<LightUsage> definedClasses) {
    if (definedClasses != null) {
      for (LightUsage aClass : definedClasses) {
        myIndex.getBackwardClassDefinitionMap().removeFrom(aClass, deletedFileId);
        final CompilerBackwardReferenceIndex.LightDefinition def =
          new CompilerBackwardReferenceIndex.LightDefinition(aClass, deletedFileId);
        final Collection<LightUsage> superClasses = myIndex.getHierarchyMap().get(def);
        if (superClasses != null) {
          for (LightUsage superClass : superClasses) {
            myIndex.getBackwardHierarchyMap().removeFrom(superClass, def);
          }
        }
        myIndex.getHierarchyMap().remove(def);
      }
    }
  }
}


