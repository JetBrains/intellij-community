// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.jps.backwardRefs;

import com.intellij.util.Function;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.SystemProperties;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.backwardRefs.index.CompiledFileData;
import org.jetbrains.jps.backwardRefs.index.CompilerReferenceIndexUtil;
import org.jetbrains.jps.backwardRefs.index.CompilerIndexDescriptor;
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

public class BackwardReferenceIndexWriter extends CompilerReferenceWriter<CompiledFileData> {
  public static final String PROP_KEY = "jps.backward.ref.index.builder";
  private static final CompilerIndexDescriptor<?> DESCRIPTOR = BackwardReferenceIndexDescriptor.INSTANCE;

  private static volatile BackwardReferenceIndexWriter ourInstance;

  private BackwardReferenceIndexWriter(CompilerBackwardReferenceIndex index) {
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

  static BackwardReferenceIndexWriter getInstance() {
    return ourInstance;
  }

  static void initialize(@NotNull final CompileContext context, int attempt) {
    final BuildDataManager dataManager = context.getProjectDescriptor().dataManager;
    final File buildDir = dataManager.getDataPaths().getDataStorageRoot();
    if (isEnabled()) {
      boolean isRebuild = isRebuildInAllJavaModules(context);

      if (!JavaCompilers.JAVAC_ID.equals(JavaBuilder.getUsedCompilerId(context)) || !JavaBuilder.IS_ENABLED.get(context, Boolean.TRUE)) {
        CompilerReferenceIndexUtil.removeIndexFiles(buildDir, DESCRIPTOR);
        return;
      }
      if (isRebuild) {
        CompilerReferenceIndexUtil.removeIndexFiles(buildDir, DESCRIPTOR);
      } else if (CompilerReferenceIndexUtil.versionDiffers(buildDir, DESCRIPTOR)) {
        CompilerReferenceIndexUtil.removeIndexFiles(buildDir, DESCRIPTOR);
        if ((attempt == 0 && areAllJavaModulesAffected(context))) {
          throw new BuildDataCorruptedException("backward reference index should be updated to actual version");
        } else {
          // do not request a rebuild if a project is affected incompletely and version is changed, just disable indices
        }
      }

      if (CompilerReferenceIndexUtil.exists(buildDir, DESCRIPTOR) || isRebuild) {
        ourInstance = new BackwardReferenceIndexWriter(new CompilerBackwardReferenceIndex(buildDir, false));
      }
    } else {
      CompilerReferenceIndexUtil.removeIndexFiles(buildDir, DESCRIPTOR);
    }
  }

  public static boolean isEnabled() {
    return SystemProperties.getBooleanProperty(PROP_KEY, false);
  }

  synchronized LightRef.JavaLightClassRef asClassUsage(JavacRef aClass) throws IOException {
    return new LightRef.JavaLightClassRef(id(aClass, myIndex.getByteSeqEum()));
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


