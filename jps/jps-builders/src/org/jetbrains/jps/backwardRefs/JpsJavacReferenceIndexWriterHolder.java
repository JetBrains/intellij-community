/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
import com.intellij.util.SystemProperties;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.builders.java.JavaModuleBuildTargetType;
import org.jetbrains.jps.builders.storage.BuildDataCorruptedException;
import org.jetbrains.jps.incremental.CompileContext;
import org.jetbrains.jps.incremental.ModuleBuildTarget;
import org.jetbrains.jps.incremental.java.JavaBuilder;
import org.jetbrains.jps.incremental.storage.BuildDataManager;
import org.jetbrains.jps.model.java.compiler.JavaCompilers;

import java.io.File;
import java.io.IOException;

public class JpsJavacReferenceIndexWriterHolder {
  public static final String PROP_KEY = "jps.backward.ref.index.builder";

  private static volatile JavacReferenceIndexWriter ourInstance;

  public static void closeIfNeed(boolean clearIndex) {
    if (ourInstance != null) {
      File dir = clearIndex ? ourInstance.getIndicesDir() : null;
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

  static JavacReferenceIndexWriter getInstance() {
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
        ourInstance = new JavacReferenceIndexWriter(new CompilerBackwardReferenceIndex(buildDir, false) {
          @NotNull
          @Override
          protected BuildDataCorruptedException createBuildDataCorruptedException(IOException cause) {
            return new BuildDataCorruptedException(cause);
          }
        });
      }
    } else {
      CompilerBackwardReferenceIndex.removeIndexFiles(buildDir);
    }
  }

  public static boolean isEnabled() {
    return SystemProperties.getBooleanProperty(PROP_KEY, false);
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


