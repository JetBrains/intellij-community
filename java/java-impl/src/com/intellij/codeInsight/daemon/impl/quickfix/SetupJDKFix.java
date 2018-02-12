/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.intention.HighPriorityAction;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ModuleRootModificationUtil;
import com.intellij.openapi.roots.ui.configuration.ProjectSettingsService;
import com.intellij.psi.CommonClassNames;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

/**
 * @author mike
 */
public class SetupJDKFix implements IntentionAction, HighPriorityAction {
  private static final SetupJDKFix ourInstance = new SetupJDKFix();

  public static SetupJDKFix getInstance() {
    return ourInstance;
  }

  private SetupJDKFix() {
  }

  @Override
  @NotNull
  public String getText() {
    return QuickFixBundle.message("setup.jdk.location.text");
  }

  @Override
  @NotNull
  public String getFamilyName() {
    return QuickFixBundle.message("setup.jdk.location.family");
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    return JavaPsiFacade.getInstance(project).findClass(CommonClassNames.JAVA_LANG_OBJECT, file.getResolveScope()) == null;
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, final PsiFile file) {
    Sdk projectJdk = ProjectSettingsService.getInstance(project).chooseAndSetSdk();
    if (projectJdk == null) return;
    ApplicationManager.getApplication().runWriteAction(() -> {
      Module module = ModuleUtilCore.findModuleForPsiElement(file);
      if (module != null) {
        ModuleRootModificationUtil.setSdkInherited(module);
      }
    });
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }
}
