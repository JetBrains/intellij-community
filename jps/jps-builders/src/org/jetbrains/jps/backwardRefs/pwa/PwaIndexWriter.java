// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.jps.backwardRefs.pwa;

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.SystemProperties;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.backwardRefs.CompilerReferenceWriter;
import org.jetbrains.jps.backwardRefs.index.CompilerReferenceIndex;
import org.jetbrains.jps.backwardRefs.index.JavaCompilerIndices;
import org.jetbrains.jps.builders.storage.BuildDataCorruptedException;
import org.jetbrains.jps.incremental.CompileContext;
import org.jetbrains.jps.incremental.java.JavaBuilder;
import org.jetbrains.jps.incremental.storage.BuildDataManager;
import org.jetbrains.jps.model.java.compiler.JavaCompilers;

import java.io.File;

import static org.jetbrains.jps.backwardRefs.JavaBackwardReferenceIndexWriter.areAllJavaModulesAffected;
import static org.jetbrains.jps.backwardRefs.JavaBackwardReferenceIndexWriter.isRebuildInAllJavaModules;

public class PwaIndexWriter extends CompilerReferenceWriter<ClassFileData> {
  private static volatile PwaIndexWriter ourInstance;

  public PwaIndexWriter(PwaIndex index) {
    super(index);
  }

  public static PwaIndexWriter getInstance() {
    return ourInstance;
  }

  public static boolean isEnabled() {
    return SystemProperties.getBooleanProperty("pwa.indices", false);
  }

  static void initialize(@NotNull final CompileContext context) {
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
        if ((areAllJavaModulesAffected(context))) {
          throw new BuildDataCorruptedException("backward reference index should be updated to actual version");
        } else {
          // do not request a rebuild if a project is affected incompletely and version is changed, just disable indices
        }
      }

      if (CompilerReferenceIndex.exists(buildDir) || isRebuild) {
        ourInstance = new PwaIndexWriter(new PwaIndex(buildDir, false));
      }
    } else {
      CompilerReferenceIndex.removeIndexFiles(buildDir);
    }
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
}
