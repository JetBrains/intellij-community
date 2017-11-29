/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.codeInsight;

import com.intellij.codeInsight.intention.impl.BaseIntentionAction;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiModifierListOwner;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.psi.codeStyle.JavaCodeStyleSettings;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

public class MakeAnnotationExternal extends BaseIntentionAction {

  @Nls
  @NotNull
  @Override
  public String getFamilyName() {
    return "Annotate Externally";
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    PsiAnnotation annotation = PsiTreeUtil.getParentOfType(file.findElementAt(editor.getCaretModel().getOffset()), PsiAnnotation.class);

    if (annotation != null && annotation.getQualifiedName() != null &&
        annotation.getManager().isInProject(annotation)) {
      PsiModifierListOwner modifierListOwner = PsiTreeUtil.getParentOfType(annotation, PsiModifierListOwner.class);
      if (modifierListOwner != null) {
        VirtualFile virtualFile = PsiUtilCore.getVirtualFile(modifierListOwner);
        if (CodeStyleSettingsManager.getSettings(project).getCustomSettings(JavaCodeStyleSettings.class).USE_EXTERNAL_ANNOTATIONS ||
            virtualFile != null && ExternalAnnotationsManager.getInstance(project).hasAnnotationRootsForFile(virtualFile)) {
          setText("Annotate externally");
          return true;
        }
      }
    }
    return false;
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    final PsiElement leaf = file.findElementAt(editor.getCaretModel().getOffset());
    PsiAnnotation annotation = PsiTreeUtil.getParentOfType(leaf, PsiAnnotation.class);
    assert annotation != null;
    
    final PsiModifierListOwner owner = PsiTreeUtil.getParentOfType(annotation, PsiModifierListOwner.class);
    assert owner != null;
    
    ExternalAnnotationsManager externalAnnotationsManager = ExternalAnnotationsManager.getInstance(project);

    if (!FileModificationService.getInstance().preparePsiElementsForWrite(MakeExternalAnnotationExplicit.getFilesToWrite(file, owner, externalAnnotationsManager))) return;

    String qualifiedName = annotation.getQualifiedName();
    assert qualifiedName != null;
    try {
      externalAnnotationsManager.annotateExternally(owner, qualifiedName, file, annotation.getParameterList().getAttributes());
    }
    catch (ExternalAnnotationsManager.CanceledConfigurationException e) {
      return;
    }

    WriteAction.run(() -> annotation.delete());
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }
}
