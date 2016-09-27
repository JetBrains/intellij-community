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
import gnu.trove.TIntHashSet;
import gnu.trove.TIntProcedure;
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

  static void initialize(@NotNull CompileContext context) {
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

  void closeIfNeed() {
    myIndex.close();
  }

  synchronized List<LightUsage> asLightUsages(Collection<JavacRefSymbol> symbols) {
    final ByteArrayEnumerator byteSeqEum = myIndex.getByteSeqEum();
    return ContainerUtil.mapNotNull(symbols, new Function<JavacRefSymbol, LightUsage>() {
      @Override
      public LightUsage fun(JavacRefSymbol symbol) {
        return LightUsage.fromSymbol(symbol, byteSeqEum);
      }
    });
  }

  synchronized void writeReferences(JavaFileObject file, Collection<LightUsage> usages) {
    final int fileId = enumerateFile(file);
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

  synchronized void writeHierarchy(Symbol name, Symbol[] supers) {
    if (supers.length == 0) {
      return;
    }
    final int classId = classId(name);
    final int[] superIds = new int[supers.length];
    for (int i = 0; i < supers.length; i++) {
      superIds[i] = classId(supers[i]);
    }

    if (myRebuild) {
      directlyWriteHierarchyIndices(classId, superIds);
    }
    else {
      updateHierarchyIndicesIncrementally(classId, superIds);
    }
  }

  private void directlyWriteHierarchyIndices(int classId, int[] superIds) {
    for (int superId : superIds) {
      myIndex.getBackwardHierarchyMap().put(superId, classId);
      myIndex.getHierarchyMap().put(classId, superId);
    }
  }

  private void updateHierarchyIndicesIncrementally(final int classId, int[] superIds) {
    final TIntHashSet rawOldSupers = myIndex.getHierarchyMap().get(classId);
    TIntHashSet oldSuperClasses = rawOldSupers == null ? null : new TIntHashSet(rawOldSupers);
    for (int superId: superIds) {
      if (oldSuperClasses == null || !oldSuperClasses.remove(superId)) {
        myIndex.getBackwardHierarchyMap().put(superId, classId);
        myIndex.getHierarchyMap().put(classId, superId);
      }
    }
    if (oldSuperClasses != null && !oldSuperClasses.isEmpty()) {
      myIndex.getHierarchyMap().removeAll(classId, oldSuperClasses);
      oldSuperClasses.forEach(new TIntProcedure() {
        @Override
        public boolean execute(int oldSuperId) {
          myIndex.getBackwardHierarchyMap().put(oldSuperId, classId);
          return true;
        }
      });
    }
  }

  private int classId(Symbol name) {
    return myIndex.getByteSeqEum().enumerate(LightUsage.bytes(name));
  }

  private int enumerateFile(JavaFileObject file) {
    try {
      return myIndex.getFilePathEnumerator().enumerate(file.getName());
    }
    catch (IOException e) {
      throw new BuildDataCorruptedException(e);
    }
  }
}


