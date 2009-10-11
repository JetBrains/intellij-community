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

import com.intellij.codeInsight.CodeInsightUtilBase;
import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.generation.OverrideImplementUtil;
import com.intellij.codeInspection.IntentionAndQuickFixAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ImplementMethodsFix extends IntentionAndQuickFixAction {
  private final PsiClass myClass;

  public ImplementMethodsFix(PsiClass aClass) {
    myClass = aClass;
  }

  @NotNull
  public String getName() {
    return QuickFixBundle.message("implement.methods.fix");
  }

  @NotNull
  public String getFamilyName() {
    return QuickFixBundle.message("implement.methods.fix");
  }

  public boolean isAvailable(@NotNull final Project project, final Editor editor, final PsiFile file) {
    return myClass.isValid() && myClass.getManager().isInProject(myClass);
  }

  public void applyFix(final Project project, final PsiFile file, @Nullable final Editor editor) {
    if (editor == null || !CodeInsightUtilBase.prepareFileForWrite(myClass.getContainingFile())) return;
    OverrideImplementUtil.chooseAndImplementMethods(project, editor, myClass);
  }

  public boolean startInWriteAction() {
    return false;
  }

}
