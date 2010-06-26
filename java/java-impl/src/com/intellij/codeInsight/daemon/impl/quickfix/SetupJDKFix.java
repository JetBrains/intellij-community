/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

/*
 * Created by IntelliJ IDEA.
 * User: mike
 * Date: Aug 20, 2002
 * Time: 8:49:24 PM
 * To change template for new class use
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.intention.HighPriorityAction;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ui.configuration.ProjectSettingsService;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

public class SetupJDKFix implements IntentionAction, HighPriorityAction {
  private static final SetupJDKFix ourInstance = new SetupJDKFix();
  public static SetupJDKFix getInstnace() {
    return ourInstance;
  }

  private SetupJDKFix() {
  }

  @NotNull
  public String getText() {
    return QuickFixBundle.message("setup.jdk.location.text");
  }

  @NotNull
  public String getFamilyName() {
    return QuickFixBundle.message("setup.jdk.location.family");
  }

  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    return JavaPsiFacade.getInstance(project).findClass("java.lang.Object", file.getResolveScope()) == null;
  }

  public void invoke(@NotNull Project project, Editor editor, final PsiFile file) {
    Sdk projectJdk = ProjectSettingsService.getInstance(project).chooseAndSetSdk();
    if (projectJdk == null) return;
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        Module module = ModuleUtil.findModuleForPsiElement(file);
        if (module != null) {
          ModifiableRootModel modifiableModel = ModuleRootManager.getInstance(module).getModifiableModel();
          modifiableModel.inheritSdk();
          modifiableModel.commit();
        }
      }
    });
  }

  public boolean startInWriteAction() {
    return false;
  }
}
