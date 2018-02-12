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
package com.intellij.compiler;

import com.intellij.compiler.server.BuildManager;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathMacros;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.ServiceKt;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.impl.JavaAwareProjectJdkTableImpl;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ModuleRootModificationUtil;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.util.SmartList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;

import java.util.List;

/**
 * @author nik
 */
public class CompilerTestUtil {
  private CompilerTestUtil() {
  }

  @TestOnly
  public static void setupJavacForTests(Project project) {
    CompilerConfigurationImpl compilerConfiguration = (CompilerConfigurationImpl)CompilerConfiguration.getInstance(project);
    compilerConfiguration.setDefaultCompiler(compilerConfiguration.getJavacCompiler());
  }

  @TestOnly
  public static void saveApplicationSettings() {
    EdtTestUtil.runInEdtAndWait(() -> {
      doSaveComponent((PersistentStateComponent<?>)ProjectJdkTable.getInstance());
      doSaveComponent((PersistentStateComponent<?>)FileTypeManager.getInstance());
      doSaveComponent((PersistentStateComponent<?>)PathMacros.getInstance());
    });
  }

  @TestOnly
  public static void saveApplicationComponent(@NotNull PersistentStateComponent<?> appComponent) {
    EdtTestUtil.runInEdtAndWait(() -> doSaveComponent(appComponent));
  }

  private static void doSaveComponent(@NotNull PersistentStateComponent<?> component) {
    //noinspection TestOnlyProblems
    ServiceKt.getStateStore(ApplicationManager.getApplication()).saveApplicationComponent(component);
  }

  @TestOnly
  public static void enableExternalCompiler() {
    final JavaAwareProjectJdkTableImpl table = JavaAwareProjectJdkTableImpl.getInstanceEx();
    new WriteAction() {
      @Override
      protected void run(@NotNull final Result result) {
        table.addJdk(table.getInternalJdk());
      }
    }.execute();
  }

  @TestOnly
  public static void disableExternalCompiler(@NotNull  final Project project) {
    EdtTestUtil.runInEdtAndWait(() -> {
      final JavaAwareProjectJdkTableImpl table = JavaAwareProjectJdkTableImpl.getInstanceEx();
      ApplicationManager.getApplication().runWriteAction(() -> {
        Sdk internalJdk = table.getInternalJdk();
        List<Module> modulesToRestore = new SmartList<>();
        for (Module module : ModuleManager.getInstance(project).getModules()) {
          Sdk sdk = ModuleRootManager.getInstance(module).getSdk();
          if (sdk != null && sdk.equals(internalJdk)) {
            modulesToRestore.add(module);
          }
        }
        for (Module module : modulesToRestore) {
          ModuleRootModificationUtil.setModuleSdk(module, internalJdk);
        }
        table.removeJdk(internalJdk);
        BuildManager.getInstance().clearState(project);
      });
    });
  }
}
