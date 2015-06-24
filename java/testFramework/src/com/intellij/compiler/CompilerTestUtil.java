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
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.application.impl.ApplicationImpl;
import com.intellij.openapi.components.impl.stores.ComponentStoreImpl;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.impl.JavaAwareProjectJdkTableImpl;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ModuleRootModificationUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * @author nik
 */
public class CompilerTestUtil {
  private CompilerTestUtil() {
  }

  public static void setupJavacForTests(Project project) {
    CompilerConfigurationImpl compilerConfiguration = (CompilerConfigurationImpl)CompilerConfiguration.getInstance(project);
    compilerConfiguration.setDefaultCompiler(compilerConfiguration.getJavacCompiler());
  }

  /**
   * @deprecated not needed anymore
   */
  public static void scanSourceRootsToRecompile(Project project) {
  }

  public static void saveApplicationSettings() {
    ApplicationManager.getApplication().invokeAndWait(new Runnable() {
      @Override
      public void run() {
        doSaveComponent(ProjectJdkTable.getInstance());
        doSaveComponent(FileTypeManager.getInstance());
      }
    }, ModalityState.any());
  }

  public static void saveApplicationComponent(final Object appComponent) {
    ApplicationManager.getApplication().invokeAndWait(new Runnable() {
      @Override
      public void run() {
        doSaveComponent(appComponent);
      }
    }, ModalityState.any());
  }

  private static void doSaveComponent(Object appComponent) {
    //noinspection TestOnlyProblems
    ((ComponentStoreImpl)((ApplicationImpl)ApplicationManager.getApplication()).getStateStore()).saveApplicationComponent(appComponent);
  }

  public static void enableExternalCompiler() {
    new WriteAction() {
      @Override
      protected void run(final Result result) {
        ApplicationManagerEx.getApplicationEx().doNotSave(false);
        JavaAwareProjectJdkTableImpl table = JavaAwareProjectJdkTableImpl.getInstanceEx();
        table.addJdk(table.getInternalJdk());
      }
    }.execute();
  }

  public static void disableExternalCompiler(final Project project) {
    new WriteAction() {
      @Override
      protected void run(final Result result) {
        ApplicationManagerEx.getApplicationEx().doNotSave(true);
        Module[] modules = ModuleManager.getInstance(project).getModules();
        JavaAwareProjectJdkTableImpl table = JavaAwareProjectJdkTableImpl.getInstanceEx();
        Sdk internalJdk = table.getInternalJdk();
        List<Module> modulesToRestore = new ArrayList<Module>();
        for (Module module : modules) {
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
      }
    }.execute();
  }
}
