// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.rename;

import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.impl.beanProperties.BeanProperty;
import com.intellij.psi.util.PropertyUtilBase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class CommonEditorBeanPropertyRenameHandler extends BeanPropertyRenameHandler {
  @Override
  @Nullable
  protected BeanProperty getProperty(DataContext context) {
    return getBeanProperty(context);
  }

  @Nullable
  protected BeanProperty getBeanProperty(DataContext dataContext) {
    final Editor editor = CommonDataKeys.EDITOR.getData(dataContext);
    PsiFile file = CommonDataKeys.PSI_FILE.getData(dataContext);
    if (file == null && editor != null && ApplicationManager.getApplication().isUnitTestMode()) {
      final Project project = editor.getProject();
      if (project != null) {
        file = PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument());
      }
    }

    if (editor != null && file != null) {
      return getBeanProperty(editor, file);
    }
    else {
      return null;
    }
  }

  @Nullable
  protected abstract BeanProperty getBeanProperty(@NotNull Editor editor, @NotNull PsiFile file);

  @Nullable
  protected BeanProperty getBeanProperty(PsiElement psiElement) {
    if (psiElement instanceof PsiMethod && PropertyUtilBase.isSimplePropertyAccessor((PsiMethod)psiElement)) {
      return BeanProperty.createBeanProperty((PsiMethod)psiElement);
    }
    return null;
  }
}
