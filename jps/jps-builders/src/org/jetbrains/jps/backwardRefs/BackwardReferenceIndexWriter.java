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

import com.intellij.openapi.util.LowMemoryWatcher;
import com.intellij.util.Function;
import com.intellij.util.SystemProperties;
import com.intellij.util.containers.ContainerUtil;
import com.sun.source.tree.Tree;
import com.sun.tools.javac.code.Flags;
import com.sun.tools.javac.code.Symbol;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.builders.java.JavaBuilderUtil;
import org.jetbrains.jps.builders.storage.BuildDataCorruptedException;
import org.jetbrains.jps.incremental.CompileContext;
import org.jetbrains.jps.incremental.java.JavaBuilder;
import org.jetbrains.jps.incremental.storage.BuildDataManager;
import org.jetbrains.jps.javac.ast.api.JavacRefSymbol;
import org.jetbrains.jps.model.java.compiler.JavaCompilers;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import static com.sun.tools.javac.code.Flags.PRIVATE;

public class BackwardReferenceIndexWriter {
  public static final String PROP_KEY = "jps.backward.ref.index.builder";

  private static volatile BackwardReferenceIndexWriter ourInstance;

  private final CompilerBackwardReferenceIndex myIndex;
  private final boolean myRebuild;
  private final LowMemoryWatcher myMemWatcher;
  private final Object myCloseLock = new Object();
  private boolean myClosed;

  private BackwardReferenceIndexWriter(CompilerBackwardReferenceIndex index, boolean rebuild) {
    myIndex = index;
    myRebuild = rebuild;
    myMemWatcher = LowMemoryWatcher.register(new Runnable() {
      @Override
      public void run() {
        synchronized (myCloseLock) {
          if (!myClosed) {
            myIndex.flush();
          }
        }
      }
    });
  }

  public static void closeIfNeed() {
    if (ourInstance != null) {
      try {
        ourInstance.close();
      } finally {
        ourInstance = null;
      }
    }
  }

  static BackwardReferenceIndexWriter getInstance() {
    return ourInstance;
  }

  static void initialize(@NotNull final CompileContext context) {
    final BuildDataManager dataManager = context.getProjectDescriptor().dataManager;
    final File buildDir = dataManager.getDataPaths().getDataStorageRoot();
    if (isEnabled()) {
      boolean isRebuild = JavaBuilderUtil.isForcedRecompilationAllJavaModules(context);

      if (!JavaCompilers.JAVAC_ID.equals(JavaBuilder.getUsedCompilerId(context))) {
        CompilerBackwardReferenceIndex.removeIndexFiles(buildDir);
        return;
      }
      if (isRebuild) {
        CompilerBackwardReferenceIndex.removeIndexFiles(buildDir);
      }
      else if (CompilerBackwardReferenceIndex.versionDiffers(buildDir)) {
        throw new BuildDataCorruptedException("backward reference index should be updated to actual version");
      }

      if (CompilerBackwardReferenceIndex.exist(buildDir) || isRebuild) {
        ourInstance = new BackwardReferenceIndexWriter(new CompilerBackwardReferenceIndex(buildDir), isRebuild);
      }
    } else {
      CompilerBackwardReferenceIndex.removeIndexFiles(buildDir);
    }
  }

  public static boolean isEnabled() {
    return SystemProperties.getBooleanProperty(PROP_KEY, false);
  }

  synchronized LightRef.JavaLightClassRef asClassUsage(Symbol name) {
    return new LightRef.JavaLightClassRef(myIndex.getByteSeqEum().enumerate(bytes(name)));
  }

  synchronized void processDeletedFiles(Collection<String> paths) {
    for (String path : paths) {
      final int deletedFileId = enumeratePath(new File(path).getPath());

      //remove from reference maps
      final Collection<LightRef> refs = myIndex.getReferenceMap().get(deletedFileId);
      if (refs != null) {
        for (LightRef ref : refs) {
          myIndex.getBackwardReferenceMap().removeFrom(ref, deletedFileId);
        }
      }
      myIndex.getReferenceMap().remove(deletedFileId);

      //remove from definition & hierarchy maps
      final Collection<LightRef> definedClasses = myIndex.getClassDefinitionMap().get(deletedFileId);
      removeClassesFromHierarchy(deletedFileId, definedClasses);
      myIndex.getClassDefinitionMap().remove(deletedFileId);
    }
  }

  synchronized void writeClassDefinitions(int fileId, Collection<LightRef> classes) {
    if (myRebuild) {
      directlyWriteClassDefinitions(fileId, classes);
    } else {
      updateClassDefinitions(fileId, classes);
    }
  }

  private void updateClassDefinitions(int fileId, Collection<LightRef> classes) {
    final Collection<LightRef> oldDefs = myIndex.getClassDefinitionMap().get(fileId);
    final Collection<LightRef> oldDefsCopy = oldDefs == null ? null : new THashSet<LightRef>(oldDefs);

    myIndex.getClassDefinitionMap().replace(fileId, classes);
    for (LightRef aClass : classes) {
      if (oldDefsCopy == null || !oldDefsCopy.remove(aClass)) {
        myIndex.getBackwardClassDefinitionMap().put(aClass, fileId);
      }
    }

    removeClassesFromHierarchy(fileId, oldDefsCopy);
  }

  private void directlyWriteClassDefinitions(int fileId, Collection<LightRef> classes) {
    myIndex.getClassDefinitionMap().put(fileId, classes);
    for (LightRef aClass : classes) {
      myIndex.getBackwardClassDefinitionMap().put(aClass, fileId);
    }
  }

  synchronized void writeReferences(int fileId, Collection<JavacRefSymbol> refs) {
    final ByteArrayEnumerator byteSeqEum = myIndex.getByteSeqEum();
    final List<LightRef> usages = ContainerUtil.mapNotNull(refs, new Function<JavacRefSymbol, LightRef>() {
      @Override
      public LightRef fun(JavacRefSymbol symbol) {
        return fromSymbol(symbol, byteSeqEum);
      }
    });

    if (myRebuild) {
      for (LightRef usage : usages) {
        myIndex.getBackwardReferenceMap().put(usage, fileId);
        myIndex.getReferenceMap().put(fileId, usage);
      }
    }
    else {
      updateReferenceIndicesIncrementally(fileId, usages);
    }
  }

  private void updateReferenceIndicesIncrementally(int fileId, Collection<LightRef> usages) {
    final Collection<LightRef> rawOldUsages = myIndex.getReferenceMap().get(fileId);
    Collection<LightRef> oldUsages = rawOldUsages == null ? null : new ArrayList<LightRef>(rawOldUsages);
    for (LightRef usage : usages) {
      if (oldUsages == null || !oldUsages.remove(usage)) {
        myIndex.getBackwardReferenceMap().put(usage, fileId);
        myIndex.getReferenceMap().put(fileId, usage);
      }
    }
    if (oldUsages != null && !oldUsages.isEmpty()) {
      myIndex.getReferenceMap().removeAll(fileId, oldUsages);
      for (LightRef usage : oldUsages) {
        myIndex.getBackwardReferenceMap().removeFrom(usage, fileId);
      }
    }
  }

  synchronized void writeHierarchy(int fileId, LightRef aClass, LightRef.JavaLightClassRef... supers) {
    CompilerBackwardReferenceIndex.LightDefinition def = new CompilerBackwardReferenceIndex.LightDefinition(aClass, fileId);
    if (myRebuild) {
      directlyWriteHierarchyIndices(def, supers);
    }
    else {
      updateHierarchyIndicesIncrementally(def, supers);
    }
  }

  synchronized int enumeratePath(String file) {
    try {
      return myIndex.getFilePathEnumerator().enumerate(file);
    }
    catch (IOException e) {
      throw new BuildDataCorruptedException(e);
    }
  }

  private void close() {
    synchronized (myCloseLock) {
      myClosed = true;
      myMemWatcher.stop();
      myIndex.close();
    }
  }

  private void directlyWriteHierarchyIndices(CompilerBackwardReferenceIndex.LightDefinition classId, LightRef.JavaLightClassRef[] superIds) {
    for (LightRef.JavaLightClassRef superId : superIds) {
      myIndex.getBackwardHierarchyMap().put(superId, classId);
      myIndex.getHierarchyMap().put(classId, superId);
    }
  }

  private void updateHierarchyIndicesIncrementally(final CompilerBackwardReferenceIndex.LightDefinition classId, LightRef.JavaLightClassRef[] superIds) {
    final Collection<LightRef> rawOldSupers = myIndex.getHierarchyMap().get(classId);
    Set<LightRef> oldSuperClasses;
    if (rawOldSupers == null) {
      oldSuperClasses = null;
    }
    else {
      if (superIds.length == rawOldSupers.size()) {
        boolean needUpdate = false;
        for (LightRef.JavaLightClassRef id : superIds) {
          if (!rawOldSupers.contains(id)) {
            needUpdate = true;
            break;
          }
        }
        if (!needUpdate) return;
      }
      oldSuperClasses = new THashSet<LightRef>(rawOldSupers);
    }
    for (LightRef.JavaLightClassRef superId: superIds) {
      if (oldSuperClasses == null || !oldSuperClasses.remove(superId)) {
        myIndex.getBackwardHierarchyMap().put(superId, classId);
        myIndex.getHierarchyMap().put(classId, superId);
      }
    }
    if (oldSuperClasses != null && !oldSuperClasses.isEmpty()) {
      myIndex.getHierarchyMap().removeAll(classId, oldSuperClasses);
      for (LightRef anOldClass : oldSuperClasses) {
        myIndex.getBackwardHierarchyMap().put(anOldClass, classId);
      }
    }
  }


  private void removeClassesFromHierarchy(int deletedFileId, Collection<LightRef> definedClasses) {
    if (definedClasses != null) {
      for (LightRef aClass : definedClasses) {
        myIndex.getBackwardClassDefinitionMap().removeFrom(aClass, deletedFileId);
        final CompilerBackwardReferenceIndex.LightDefinition def =
          new CompilerBackwardReferenceIndex.LightDefinition(aClass, deletedFileId);
        final Collection<LightRef> superClasses = myIndex.getHierarchyMap().get(def);
        if (superClasses != null) {
          for (LightRef superClass : superClasses) {
            myIndex.getBackwardHierarchyMap().removeFrom(superClass, def);
          }
        }
        myIndex.getHierarchyMap().remove(def);
      }
    }
  }

  private static byte[] bytes(Symbol symbol) {
    return symbol.flatName().toUtf();
  }

  @Nullable
  private static LightRef fromSymbol(JavacRefSymbol refSymbol, ByteArrayEnumerator byteArrayEnumerator) {
    Symbol symbol = refSymbol.getSymbol();
    final Tree.Kind kind = refSymbol.getPlaceKind();
    if (symbol instanceof Symbol.ClassSymbol) {
      if (!isPrivate(symbol) && !isAnonymous(symbol)) {
        return new LightRef.JavaLightClassRef(id(symbol, byteArrayEnumerator));
      }
    }
    else {
      Symbol owner = symbol.owner;
      if (isPrivate(symbol)) {
        return null;
      }
      if (symbol instanceof Symbol.VarSymbol) {
        return new LightRef.JavaLightFieldRef(id(owner, byteArrayEnumerator), id(symbol, byteArrayEnumerator));
      }
      else if (symbol instanceof Symbol.MethodSymbol) {
        int paramCount = ((Symbol.MethodSymbol)symbol).type.getParameterTypes().size();
        return new LightRef.JavaLightMethodRef(id(owner, byteArrayEnumerator), id(symbol, byteArrayEnumerator), paramCount);
      }
      else {
        throw new AssertionError("unexpected symbol: " + symbol + " class: " + symbol.getClass() + " kind: " + kind);
      }
    }
    return null;
  }

  // JDK-6 has no Symbol.isPrivate() method
  private static boolean isPrivate(Symbol symbol) {
    return (symbol.flags() & Flags.AccessFlags) == PRIVATE;
  }

  // JDK-6 has no Symbol.isAnonymous() method
  private static boolean isAnonymous(Symbol symbol) {
    return symbol.name.isEmpty();
  }

  private static int id(Symbol symbol, ByteArrayEnumerator byteArrayEnumerator) {
    return byteArrayEnumerator.enumerate(bytes(symbol));
  }
}


