/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.ex.JavaSdkUtil;
import com.intellij.openapi.roots.JavaProjectModelModificationService;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.PsiFile;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

/**
 * @author cdr
 */
public class IncreaseLanguageLevelFix implements IntentionAction {
  private static final Logger LOG = Logger.getInstance("#" + IncreaseLanguageLevelFix.class.getName());

  private final LanguageLevel myLevel;

  public IncreaseLanguageLevelFix(@NotNull LanguageLevel targetLevel) {
    myLevel = targetLevel;
  }

  @Override
  @NotNull
  public String getText() {
    return CodeInsightBundle.message("set.language.level.to.0", myLevel.getPresentableText());
  }

  @Override
  @NotNull
  public String getFamilyName() {
    return CodeInsightBundle.message("set.language.level");
  }

  @Override
  public boolean isAvailable(@NotNull final Project project, final Editor editor, final PsiFile file) {
    final VirtualFile virtualFile = file.getVirtualFile();
    if (virtualFile == null) return false;
    final Module module = ModuleUtilCore.findModuleForFile(virtualFile, project);
    if (module == null) return false;
    return JavaSdkUtil.isLanguageLevelAcceptable(project, module, myLevel);
  }

  @Override
  public void invoke(@NotNull final Project project, final Editor editor, final PsiFile file) throws IncorrectOperationException {
    final VirtualFile virtualFile = file.getVirtualFile();
    LOG.assertTrue(virtualFile != null);
    final Module module = ModuleUtilCore.findModuleForFile(virtualFile, project);
    if (module == null) return;

    JavaProjectModelModificationService.getInstance(project).changeLanguageLevel(module, myLevel);
  }

  @Override
  public boolean startInWriteAction() {
    return true;
  }
}
