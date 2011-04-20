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
package com.intellij.codeInsight.daemon.impl.analysis;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.JavaSdk;
import com.intellij.openapi.projectRoots.JavaSdkVersion;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.PsiFile;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author cdr
 */
public class IncreaseLanguageLevelFix implements IntentionAction {
  private static final Logger LOG = Logger.getInstance("#" + IncreaseLanguageLevelFix.class.getName());

  private final LanguageLevel myLevel;

  public IncreaseLanguageLevelFix(LanguageLevel targetLevel) {
    myLevel = targetLevel;
  }

  @NotNull
  public String getText() {
    return CodeInsightBundle.message("set.language.level.to.0", myLevel.getPresentableText());
  }

  @NotNull
  public String getFamilyName() {
    return CodeInsightBundle.message("set.language.level");
  }

  private static boolean isJdkSupportsLevel(@Nullable final Sdk jdk, final LanguageLevel level) {
    if (jdk == null) return true;
    final JavaSdk sdk = JavaSdk.getInstance();
    final JavaSdkVersion version = sdk.getVersion(jdk);
    return version != null && version.getMaxLanguageLevel().isAtLeast(level);
  }

  public boolean isAvailable(@NotNull final Project project, final Editor editor, final PsiFile file) {
    final VirtualFile virtualFile = file.getVirtualFile();
    if (virtualFile == null) return false;
    final Module module = ModuleUtil.findModuleForFile(virtualFile, project);
    return isLanguageLevelAcceptable(project, module, myLevel);
  }

  public static boolean isLanguageLevelAcceptable(final Project project, Module module, final LanguageLevel level) {
    return isJdkSupportsLevel(getRelevantJdk(project, module), level);
  }

  public void invoke(@NotNull final Project project, final Editor editor, final PsiFile file) throws IncorrectOperationException {
    final VirtualFile virtualFile = file.getVirtualFile();
    LOG.assertTrue(virtualFile != null);
    final Module module = ModuleUtil.findModuleForFile(virtualFile, project);
    final LanguageLevel moduleLevel = module == null ? null : LanguageLevelModuleExtension.getInstance(module).getLanguageLevel();
    if (moduleLevel != null && isLanguageLevelAcceptable(project, module, myLevel)) {
      final ModifiableRootModel rootModel = ModuleRootManager.getInstance(module).getModifiableModel();
      rootModel.getModuleExtension(LanguageLevelModuleExtension.class).setLanguageLevel(myLevel);
      ApplicationManager.getApplication().runWriteAction(new Runnable() {
        public void run() {
          rootModel.commit();
        }
      });
    }
    else {
      LanguageLevelProjectExtension.getInstance(project).setLanguageLevel(myLevel);
    }
  }

  @Nullable
  private static Sdk getRelevantJdk(final Project project, @Nullable Module module) {
    Sdk projectJdk = ProjectRootManager.getInstance(project).getProjectSdk();
    Sdk moduleJdk = module == null ? null : ModuleRootManager.getInstance(module).getSdk();
    return moduleJdk == null ? projectJdk : moduleJdk;
  }

  public boolean startInWriteAction() {
    return false;
  }
}
