/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
import com.intellij.openapi.application.Result;
import com.intellij.openapi.application.WriteAction;
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
import com.intellij.util.ThrowableRunnable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;

import java.util.List;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

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

  /**
   * @deprecated not needed anymore
   */
  public static void scanSourceRootsToRecompile(Project project) {
  }

  @TestOnly
  public static void saveApplicationSettings() {
    EdtTestUtil.runInEdtAndWait(new Runnable() {
      @Override
      public void run() {
        doSaveComponent(ProjectJdkTable.getInstance());
        doSaveComponent(FileTypeManager.getInstance());
      }
    });
  }

  @TestOnly
  public static void saveApplicationComponent(final Object appComponent) {
    EdtTestUtil.runInEdtAndWait(new Runnable() {
      @Override
      public void run() {
        doSaveComponent(appComponent);
      }
    });
  }

  private static void doSaveComponent(Object appComponent) {
    //noinspection TestOnlyProblems
    ServiceKt.getStateStore(ApplicationManager.getApplication()).saveApplicationComponent(appComponent);
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
    EdtTestUtil.runInEdtAndWait(new ThrowableRunnable<Throwable>() {
      @Override
      public void run() throws Throwable {
        final JavaAwareProjectJdkTableImpl table = JavaAwareProjectJdkTableImpl.getInstanceEx();
        ApplicationManager.getApplication().runWriteAction(new Runnable() {
          @Override
          public void run() {
            Sdk internalJdk = table.getInternalJdk();
            List<Module> modulesToRestore = new SmartList<Module>();
            for (Module module : ModuleManager.getInstance(project).getModules()) {
              Sdk sdk = ModuleRootManager.getInstance(module).getSdk();
              if (sdk != null && sdk.equals(internalJdk)) {
                modulesToRestore.add(module);
              }
            }
            table.removeJdk(internalJdk);
            for (Module module : modulesToRestore) {
              ModuleRootModificationUtil.setModuleSdk(module, internalJdk);
            }
            BuildManager.getInstance().clearState(project);
            Future<?> future = BuildManager.getInstance().stopListening();
            try {
              future.get(100, TimeUnit.SECONDS);
            }
            catch (Exception e) {
              throw new RuntimeException(e);
            }
          }
        });
      }
    });
  }
}
