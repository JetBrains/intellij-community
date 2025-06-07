// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.rename;

import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.impl.beanProperties.BeanProperty;
import com.intellij.psi.util.PropertyUtilBase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class CommonEditorReferenceBeanPropertyRenameHandler extends BeanPropertyRenameHandler {

  private final Class<? extends PsiReference> acceptableReferenceClass;

  //maybe extract it to a separate EP if there will be too many subclasses just calling this constructor?
  protected CommonEditorReferenceBeanPropertyRenameHandler(Class<? extends PsiReference> acceptableReference) {
    this.acceptableReferenceClass = acceptableReference;
  }

  protected CommonEditorReferenceBeanPropertyRenameHandler() {
    this.acceptableReferenceClass = PsiReference.class;
  }

  @Override
  protected @Nullable BeanProperty getProperty(DataContext context) {
    return getBeanProperty(context);
  }

  protected @Nullable BeanProperty getBeanProperty(DataContext dataContext) {
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

  protected @Nullable BeanProperty getBeanProperty(final @NotNull Editor editor, final @NotNull PsiFile file) {
    final int offset = editor.getCaretModel().getOffset();
    final PsiReference reference = file.findReferenceAt(offset);
    if (reference == null) return null;
    return getBeanProperty(reference);
  }

  protected @Nullable BeanProperty getBeanProperty(@Nullable PsiElement psiElement) {
    if (psiElement instanceof PsiMethod && PropertyUtilBase.isSimplePropertyAccessor((PsiMethod)psiElement)) {
      return BeanProperty.createBeanProperty((PsiMethod)psiElement);
    }
    return null;
  }

  protected @Nullable BeanProperty getBeanProperty(@NotNull PsiReference reference) {
    if (acceptableReferenceClass.isAssignableFrom(reference.getClass())) {
      final PsiElement psiElement = reference.resolve();
      return getBeanProperty(psiElement);
    }
    return null;
  }
}
