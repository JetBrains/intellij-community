// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.compiler;

import com.intellij.compiler.server.BuildManager;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathMacros;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.ServiceKt;
import com.intellij.openapi.components.impl.stores.IComponentStore;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.impl.JavaAwareProjectJdkTableImpl;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ModuleRootModificationUtil;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.util.io.PathKt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;

import java.nio.file.Path;

public final class CompilerTestUtil {
  private static final Logger LOG = Logger.getInstance(CompilerTestUtil.class);

  private CompilerTestUtil() {
  }

  @TestOnly
  public static void setupJavacForTests(Project project) {
    CompilerConfigurationImpl compilerConfiguration = (CompilerConfigurationImpl)CompilerConfiguration.getInstance(project);
    compilerConfiguration.setDefaultCompiler(compilerConfiguration.getJavacCompiler());
  }

  @TestOnly
  // should be invoked in EDT
  public static void saveApplicationSettings() {
    IComponentStore store = getApplicationStore();
    store.saveComponent((PersistentStateComponent<?>)ProjectJdkTable.getInstance());
    store.saveComponent((PersistentStateComponent<?>)FileTypeManager.getInstance());
    store.saveComponent((PersistentStateComponent<?>)PathMacros.getInstance());
  }

  @NotNull
  public static IComponentStore getApplicationStore() {
    return ServiceKt.getStateStore(ApplicationManager.getApplication());
  }

  @TestOnly
  public static void saveApplicationComponent(@NotNull PersistentStateComponent<?> appComponent) {
    EdtTestUtil.runInEdtAndWait(() -> getApplicationStore().saveComponent(appComponent));
  }

  @TestOnly
  public static void enableExternalCompiler() {
    final JavaAwareProjectJdkTableImpl table = JavaAwareProjectJdkTableImpl.getInstanceEx();
    WriteAction.runAndWait(() -> table.addJdk(table.getInternalJdk()));
  }

  @TestOnly
  public static void disableExternalCompiler(@NotNull  final Project project) {
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
        table.removeJdk(internalJdk);
        BuildManager.getInstance().clearState(project);
      });
    });
  }

  public static void deleteBuildSystemDirectory(@NotNull Project project) {
    BuildManager buildManager = BuildManager.getInstance();
    if (buildManager == null) return;
    Path buildSystemDirectory = buildManager.getBuildSystemDirectory(project);
    try {
      PathKt.delete(buildSystemDirectory);
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
