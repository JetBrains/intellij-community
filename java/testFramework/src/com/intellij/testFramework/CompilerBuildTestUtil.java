// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework;

import com.intellij.compiler.server.BuildManager;
import com.intellij.java.testFramework.backend.CompilerTestUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.impl.JavaAwareProjectJdkTableImpl;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ModuleRootModificationUtil;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.NioFiles;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;

import java.nio.file.Path;

public final class CompilerBuildTestUtil {
  private static final Logger LOG = Logger.getInstance(CompilerBuildTestUtil.class);

  private CompilerBuildTestUtil() {
  }

  @TestOnly
  @SuppressWarnings("removal")
  public static void enableExternalCompiler() {
    final JavaAwareProjectJdkTableImpl table = JavaAwareProjectJdkTableImpl.getInstanceEx();
    WriteAction.runAndWait(() -> {
      Sdk jdk = table.getInternalJdk();
      if (table.findJdk(jdk.getName(), jdk.getSdkType().getName()) != jdk) {
        table.addJdk(jdk);
      }
    });
  }

  @TestOnly
  @SuppressWarnings("removal")
  public static void disableExternalCompiler(final @NotNull Project project) {
    EdtTestUtil.runInEdtAndWait(() -> {
      final JavaAwareProjectJdkTableImpl table = JavaAwareProjectJdkTableImpl.getInstanceEx();
      ApplicationManager.getApplication().runWriteAction(() -> {
        Sdk internalJdk = table.getInternalJdk();
        for (Module module : ModuleManager.getInstance(project).getModules()) {
          Sdk sdk = ModuleRootManager.getInstance(module).getSdk();
          if (sdk != null && sdk.equals(internalJdk)) {
            ModuleRootModificationUtil.setModuleSdk(module, null);
          }
        }
        if (table.findJdk(internalJdk.getName(), internalJdk.getSdkType().getName()) == internalJdk) {
          table.removeJdk(internalJdk);
        }
        BuildManager.getInstance().clearState(project);
      });
      CompilerTestUtil.saveApplicationSettings();
    });
  }

  @SuppressWarnings("UseOptimizedEelFunctions")
  public static void deleteBuildSystemDirectory(@NotNull Project project) {
    BuildManager buildManager = BuildManager.getInstance();
    if (buildManager == null) return;
    Path buildSystemDirectory = buildManager.getBuildSystemDirectory(project);
    try {
      NioFiles.deleteRecursively(buildSystemDirectory);
      return;
    }
    catch (Exception ignore) {
    }
    try {
      FileUtil.delete(buildSystemDirectory.toFile());
    }
    catch (Exception e) {
      LOG.warn("Unable to remove build system directory.", e);
    }
  }
}
