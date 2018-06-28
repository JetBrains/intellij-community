// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.jps.backwardRefs;

import com.intellij.util.Function;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.SystemProperties;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.backwardRefs.index.CompiledFileData;
import org.jetbrains.jps.backwardRefs.index.CompilerReferenceIndex;
import org.jetbrains.jps.backwardRefs.index.JavaCompilerIndices;
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

public class JavaBackwardReferenceIndexWriter extends CompilerReferenceWriter<CompiledFileData> {
  public static final String PROP_KEY = "jps.backward.ref.index.builder";

  private static volatile JavaBackwardReferenceIndexWriter ourInstance;

  private JavaBackwardReferenceIndexWriter(JavaCompilerBackwardReferenceIndex index) {
    super(index);
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

  static JavaBackwardReferenceIndexWriter getInstance() {
    return ourInstance;
  }

  static void initialize(@NotNull final CompileContext context, int attempt) {
    if (true) {
      return;
    }
    final BuildDataManager dataManager = context.getProjectDescriptor().dataManager;
    final File buildDir = dataManager.getDataPaths().getDataStorageRoot();
    if (isEnabled()) {
      boolean isRebuild = isRebuildInAllJavaModules(context);

      if (!JavaCompilers.JAVAC_ID.equals(JavaBuilder.getUsedCompilerId(context)) || !JavaBuilder.IS_ENABLED.get(context, Boolean.TRUE)) {
        CompilerReferenceIndex.removeIndexFiles(buildDir);
        return;
      }
      if (isRebuild) {
        CompilerReferenceIndex.removeIndexFiles(buildDir);
      } else if (CompilerReferenceIndex.versionDiffers(buildDir, JavaCompilerIndices.VERSION)) {
        CompilerReferenceIndex.removeIndexFiles(buildDir);
        if ((attempt == 0 && areAllJavaModulesAffected(context))) {
          throw new BuildDataCorruptedException("backward reference index should be updated to actual version");
        } else {
          // do not request a rebuild if a project is affected incompletely and version is changed, just disable indices
        }
      }

      if (CompilerReferenceIndex.exists(buildDir) || isRebuild) {
        ourInstance = new JavaBackwardReferenceIndexWriter(new JavaCompilerBackwardReferenceIndex(buildDir, false));
      }
    } else {
      CompilerReferenceIndex.removeIndexFiles(buildDir);
    }
  }

  public static boolean isEnabled() {
    return SystemProperties.getBooleanProperty(PROP_KEY, false);
  }

  synchronized CompilerRef.JavaCompilerClassRef asClassUsage(JavacRef aClass) throws IOException {
    return new CompilerRef.JavaCompilerClassRef(id(aClass, myIndex.getByteSeqEum()));
  }

  @Nullable
  CompilerRef enumerateNames(JavacRef ref, Function<String, Integer> ownerIdReplacer) throws IOException {
    NameEnumerator nameEnumerator = myIndex.getByteSeqEum();
    if (ref instanceof JavacRef.JavacClass) {
      if (!isPrivate(ref) && !((JavacRef.JavacClass)ref).isAnonymous()) {
        return new CompilerRef.JavaCompilerClassRef(id(ref, nameEnumerator));
      }
    }
    else {
      if (isPrivate(ref)) {
        return null;
      }
      String ownerName = ref.getOwnerName();
      final Integer ownerPrecalculatedId = ownerIdReplacer.fun(ownerName);
      if (ref instanceof JavacRef.JavacField) {
        return new CompilerRef.JavaCompilerFieldRef(ownerPrecalculatedId != null ? ownerPrecalculatedId : id(ownerName, nameEnumerator), id(ref, nameEnumerator));
      }
      else if (ref instanceof JavacRef.JavacMethod) {
        int paramCount = ((JavacRef.JavacMethod) ref).getParamCount();
        return new CompilerRef.JavaCompilerMethodRef(ownerPrecalculatedId != null ? ownerPrecalculatedId : id(ownerName, nameEnumerator), id(ref, nameEnumerator), paramCount);
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

  public static boolean isRebuildInAllJavaModules(CompileContext context) {
    for (JavaModuleBuildTargetType type : JavaModuleBuildTargetType.ALL_TYPES) {
      for (ModuleBuildTarget target : context.getProjectDescriptor().getBuildTargetIndex().getAllTargets(type)) {
        if (!context.getScope().isBuildForced(target)) {
          return false;
        }
      }
    }
    return true;
  }

  public static boolean areAllJavaModulesAffected(CompileContext context) {
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


