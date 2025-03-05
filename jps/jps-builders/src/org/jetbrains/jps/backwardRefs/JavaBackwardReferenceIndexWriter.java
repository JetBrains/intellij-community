// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.backwardRefs;

import com.intellij.openapi.util.ShutDownTracker;
import com.intellij.openapi.util.SystemInfoRt;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.util.Function;
import com.intellij.util.SystemProperties;
import org.jetbrains.annotations.ApiStatus;
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
import java.io.IOException;
import java.nio.file.Path;

public final class JavaBackwardReferenceIndexWriter extends CompilerReferenceWriter<CompiledFileData> {
  public static final String PROP_KEY = "jps.backward.ref.index.builder";
  public static final String FS_KEY = "jps.backward.ref.index.builder.fs.case.sensitive";

  private static volatile JavaBackwardReferenceIndexWriter ourInstance;
  private static int ourInitAttempt = 0;

  private JavaBackwardReferenceIndexWriter(JavaCompilerBackwardReferenceIndex index) {
    super(index);
  }

  synchronized @NotNull CompilerRef.JavaCompilerClassRef asClassUsage(JavacRef aClass) throws IOException {
    return new CompilerRef.JavaCompilerClassRef(id(aClass, myIndex.getByteSeqEum()));
  }

  @Nullable
  CompilerRef enumerateNames(JavacRef ref, Function<? super String, Integer> ownerIdReplacer) throws IOException {
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
        return new CompilerRef.JavaCompilerFieldRef(ownerPrecalculatedId != null ? ownerPrecalculatedId : id(ownerName, nameEnumerator),
                                                    id(ref, nameEnumerator));
      }
      else if (ref instanceof JavacRef.JavacMethod) {
        int paramCount = ((JavacRef.JavacMethod)ref).getParamCount();
        return new CompilerRef.JavaCompilerMethodRef(ownerPrecalculatedId != null ? ownerPrecalculatedId : id(ownerName, nameEnumerator),
                                                     id(ref, nameEnumerator), paramCount);
      }
      else {
        throw new AssertionError("unexpected symbol: " + ref + " class: " + ref.getClass());
      }
    }
    return null;
  }

  public static synchronized void closeIfNeeded(boolean clearIndex) {
    if (ourInstance != null) {
      Path dir = clearIndex ? ourInstance.myIndex.getIndexDir() : null;
      try {
        ourInstance.close();
      }
      finally {
        ourInstance = null;
        if (dir != null) {
          try {
            FileUtilRt.deleteRecursively(dir);
          }
          catch (IOException ignored) {
          }
        }
      }
    }
  }

  static JavaBackwardReferenceIndexWriter getInstance() {
    return ourInstance;
  }

  public static void initialize(final @NotNull CompileContext context) {
    if (ourInstance != null) {
      return;
    }

    final BuildDataManager dataManager = context.getProjectDescriptor().dataManager;
    Path buildDir = dataManager.getDataPaths().getDataStorageDir();
    if (!isEnabled()) {
      CompilerReferenceIndex.removeIndexFiles(buildDir);
      return;
    }
    boolean isRebuild = isRebuildInAllJavaModules(context);

    if (!JavaCompilers.JAVAC_ID.equals(JavaBuilder.getUsedCompilerId(context)) || !JavaBuilder.IS_ENABLED.get(context, Boolean.TRUE)) {
      CompilerReferenceIndex.removeIndexFiles(buildDir);
      return;
    }

    boolean cleanupOk = true;
    if (isRebuild) {
      CompilerReferenceIndex.removeIndexFiles(buildDir);
      cleanupOk = !CompilerReferenceIndex.exists(buildDir);
    }
    else if (CompilerReferenceIndex.versionDiffers(buildDir, JavaCompilerIndices.VERSION)) {
      CompilerReferenceIndex.removeIndexFiles(buildDir);
      if ((ourInitAttempt++ == 0 && areAllJavaModulesAffected(context))) {
        throw new BuildDataCorruptedException("backward reference index will be updated to actual version");
      }
      // do not request a rebuild if a project is affected incompletely and a version is changed, disable indices
      return;
    }

    if (cleanupOk) {
      JavaCompilerBackwardReferenceIndex index =
        new JavaCompilerBackwardReferenceIndex(buildDir, dataManager.getRelativizer(), false,
                                               isCompilerReferenceFSCaseSensitive());
      ourInstance = new JavaBackwardReferenceIndexWriter(index);
      ShutDownTracker.getInstance().registerShutdownTask(() -> closeIfNeeded(false));
    }
  }

  public static boolean isEnabled() {
    return SystemProperties.getBooleanProperty(PROP_KEY, false);
  }

  @ApiStatus.Experimental
  public static boolean isCompilerReferenceFSCaseSensitive() {
    String value = System.getProperty(FS_KEY);
    if (value == null) {
      return SystemInfoRt.isFileSystemCaseSensitive;
    }
    return Boolean.parseBoolean(value);
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


