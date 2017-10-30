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
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.SystemProperties;
import com.intellij.util.indexing.InvertedIndex;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.backwardRefs.index.CompiledFileData;
import org.jetbrains.jps.builders.java.JavaModuleBuildTargetType;
import org.jetbrains.jps.builders.storage.BuildDataCorruptedException;
import org.jetbrains.jps.incremental.CompileContext;
import org.jetbrains.jps.incremental.ModuleBuildTarget;
import org.jetbrains.jps.incremental.java.JavaBuilder;
import org.jetbrains.jps.incremental.storage.BuildDataManager;
import org.jetbrains.jps.javac.ast.api.JavacRef;
import org.jetbrains.jps.model.java.compiler.JavaCompilers;

import javax.lang.model.element.Modifier;
import java.io.File;
import java.io.IOException;
import java.util.Collection;

public class BackwardReferenceIndexWriter {
  public static final String PROP_KEY = "jps.backward.ref.index.builder";

  private static volatile BackwardReferenceIndexWriter ourInstance;

  private final CompilerBackwardReferenceIndex myIndex;

  private BackwardReferenceIndexWriter(CompilerBackwardReferenceIndex index) {
    myIndex = index;
  }

  Throwable getRebuildRequestCause() {
    return myIndex.getRebuildRequestCause();
  }

  void setRebuildCause(Throwable e) {
    myIndex.setRebuildRequestCause(e);
  }

  public static void closeIfNeed(boolean clearIndex) {
    if (ourInstance != null) {
      File dir = clearIndex ? ourInstance.myIndex.getIndicesDir() : null;
      try {
        ourInstance.close();
      } finally {
        ourInstance = null;
        if (dir != null) {
          FileUtil.delete(dir);
        }
      }
    }
  }

  static BackwardReferenceIndexWriter getInstance() {
    return ourInstance;
  }

  static void initialize(@NotNull final CompileContext context, int attempt) {
    final BuildDataManager dataManager = context.getProjectDescriptor().dataManager;
    final File buildDir = dataManager.getDataPaths().getDataStorageRoot();
    if (isEnabled()) {
      boolean isRebuild = isRebuildInAllJavaModules(context);

      if (!JavaCompilers.JAVAC_ID.equals(JavaBuilder.getUsedCompilerId(context)) || !JavaBuilder.IS_ENABLED.get(context, Boolean.TRUE)) {
        CompilerBackwardReferenceIndex.removeIndexFiles(buildDir);
        return;
      }
      if (isRebuild) {
        CompilerBackwardReferenceIndex.removeIndexFiles(buildDir);
      }
      else if (CompilerBackwardReferenceIndex.versionDiffers(buildDir)) {
        CompilerBackwardReferenceIndex.removeIndexFiles(buildDir);
        if ((attempt == 0 && areAllJavaModulesAffected(context)) ) {
          throw new BuildDataCorruptedException("backward reference index should be updated to actual version");
        } else {
          // do not request a rebuild if a project is affected incompletely and version is changed, just disable indices
        }
      }

      if (CompilerBackwardReferenceIndex.exist(buildDir) || isRebuild) {
        ourInstance = new BackwardReferenceIndexWriter(new CompilerBackwardReferenceIndex(buildDir, false));
      }
    } else {
      CompilerBackwardReferenceIndex.removeIndexFiles(buildDir);
    }
  }

  public static boolean isEnabled() {
    return SystemProperties.getBooleanProperty(PROP_KEY, false);
  }

  synchronized LightRef.JavaLightClassRef asClassUsage(JavacRef aClass) throws IOException {
    return new LightRef.JavaLightClassRef(id(aClass, myIndex.getByteSeqEum()));
  }

  void processDeletedFiles(Collection<String> files) throws IOException {
    for (String file : files) {
      writeData(enumeratePath(new File(file).getPath()), null);
    }
  }

  void writeData(int id, CompiledFileData d) {
    for (InvertedIndex<?, ?, CompiledFileData> index : myIndex.getIndices()) {
      index.update(id, d).compute();
    }
  }

  synchronized int enumeratePath(String file) throws IOException {
    return myIndex.getFilePathEnumerator().enumerate(file);
  }

  private void close() {
    myIndex.close();
  }

  @Nullable
  LightRef enumerateNames(JavacRef ref, Function<String, Integer> ownerIdReplacer) throws IOException {
    NameEnumerator nameEnumerator = myIndex.getByteSeqEum();
    if (ref instanceof JavacRef.JavacClass) {
      if (!isPrivate(ref) && !((JavacRef.JavacClass)ref).isAnonymous()) {
        return new LightRef.JavaLightClassRef(id(ref, nameEnumerator));
      }
    }
    else {
      if (isPrivate(ref)) {
        return null;
      }
      String ownerName = ref.getOwnerName();
      final Integer ownerPrecalculatedId = ownerIdReplacer.fun(ownerName);
      if (ref instanceof JavacRef.JavacField) {
        return new LightRef.JavaLightFieldRef(ownerPrecalculatedId != null ? ownerPrecalculatedId : id(ownerName, nameEnumerator), id(ref, nameEnumerator));
      }
      else if (ref instanceof JavacRef.JavacMethod) {
        int paramCount = ((JavacRef.JavacMethod) ref).getParamCount();
        return new LightRef.JavaLightMethodRef(ownerPrecalculatedId != null ? ownerPrecalculatedId : id(ownerName, nameEnumerator), id(ref, nameEnumerator), paramCount);
      }
      else {
        throw new AssertionError("unexpected symbol: " + ref + " class: " + ref.getClass());
      }
    }
    return null;
  }

  private static boolean isPrivate(JavacRef ref) {
    return ref.getModifiers().contains(Modifier.PRIVATE);
  }

  private static int id(JavacRef ref, NameEnumerator nameEnumerator) throws IOException {
    return id(ref.getName(), nameEnumerator);
  }

  private static int id(String name, NameEnumerator nameEnumerator) throws IOException {
    return nameEnumerator.enumerate(name);
  }

  private static boolean isRebuildInAllJavaModules(CompileContext context) {
    for (JavaModuleBuildTargetType type : JavaModuleBuildTargetType.ALL_TYPES) {
      for (ModuleBuildTarget target : context.getProjectDescriptor().getBuildTargetIndex().getAllTargets(type)) {
        if (!context.getScope().isBuildForced(target)) {
          return false;
        }
      }
    }
    return true;
  }

  private static boolean areAllJavaModulesAffected(CompileContext context) {
    for (JavaModuleBuildTargetType type : JavaModuleBuildTargetType.ALL_TYPES) {
      for (ModuleBuildTarget target : context.getProjectDescriptor().getBuildTargetIndex().getAllTargets(type)) {
        if (!context.getScope().isWholeTargetAffected(target)) {
          return false;
        }
      }
    }
    return true;
  }
}


