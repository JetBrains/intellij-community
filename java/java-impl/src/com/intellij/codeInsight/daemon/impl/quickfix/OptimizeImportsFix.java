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
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.FileModificationService;
import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

public class OptimizeImportsFix implements IntentionAction{
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.daemon.impl.quickfix.OptimizeImportsFix");

  @Override
  @NotNull
  public String getText() {
    return QuickFixBundle.message("optimize.imports.fix");
  }

  @Override
  @NotNull
  public String getFamilyName() {
    return QuickFixBundle.message("optimize.imports.fix");
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    return file.getManager().isInProject(file) && file instanceof PsiJavaFile;
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, PsiFile file) {
    if (!(file instanceof PsiJavaFile)) return;
    if (!FileModificationService.getInstance().prepareFileForWrite(file)) return;

    try{
      JavaCodeStyleManager.getInstance(project).optimizeImports(file);
    }
    catch(IncorrectOperationException e){
      LOG.error(e);
    }
  }

  @Override
  public boolean startInWriteAction() {
    return true;
  }
}
