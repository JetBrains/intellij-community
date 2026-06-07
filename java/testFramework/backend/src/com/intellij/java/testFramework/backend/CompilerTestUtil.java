// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.testFramework.backend;

import com.intellij.compiler.CompilerConfiguration;
import com.intellij.compiler.CompilerConfigurationImpl;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathMacros;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.impl.stores.IComponentStore;
import com.intellij.openapi.components.impl.stores.IComponentStoreKt;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.testFramework.EdtTestUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;

public final class CompilerTestUtil {
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
    ProjectJdkTable.getInstance().saveOnDisk();
    store.saveComponent((PersistentStateComponent<?>)FileTypeManager.getInstance());
    store.saveComponent((PersistentStateComponent<?>)PathMacros.getInstance());
  }

  public static @NotNull IComponentStore getApplicationStore() {
    return IComponentStoreKt.getStateStore(ApplicationManager.getApplication());
  }

  @TestOnly
  public static void saveApplicationComponent(@NotNull PersistentStateComponent<?> appComponent) {
    EdtTestUtil.runInEdtAndWait(() -> getApplicationStore().saveComponent(appComponent));
  }
}
