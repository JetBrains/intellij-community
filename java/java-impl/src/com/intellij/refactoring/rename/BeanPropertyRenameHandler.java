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

package com.intellij.refactoring.rename;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.psi.impl.beanProperties.BeanProperty;
import com.intellij.psi.util.PropertyUtil;
import com.intellij.psi.util.PropertyUtilBase;
import com.intellij.refactoring.RenameRefactoring;
import com.intellij.refactoring.openapi.impl.JavaRenameRefactoringImpl;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Dmitry Avdeev
 */
public abstract class BeanPropertyRenameHandler implements RenameHandler {

  public boolean isAvailableOnDataContext(DataContext dataContext) {
    return false;
  }

  public boolean isRenaming(DataContext dataContext) {
    return getProperty(dataContext) != null;
  }

  public void invoke(@NotNull Project project, Editor editor, PsiFile file, DataContext dataContext) {
    performInvoke(editor, dataContext);
  }

  public void invoke(@NotNull Project project, @NotNull PsiElement[] elements, DataContext dataContext) {
    performInvoke(null, dataContext);
  }

  private void performInvoke(@Nullable Editor editor, DataContext dataContext) {
    final BeanProperty property = getProperty(dataContext);
    assert property != null;
    PsiNamedElement element = property.getPsiElement();

    if (ApplicationManager.getApplication().isUnitTestMode()) {
      final String newName = PsiElementRenameHandler.DEFAULT_NAME.getData(dataContext);
      assert newName != null;
      doRename(property, newName, editor, false, false);
      return;
    }

    if (PsiElementRenameHandler.canRename(element.getProject(), editor, element)) {
      new PropertyRenameDialog(property, editor).show();
    }
  }

  @Deprecated
  public static void doRename(@NotNull final BeanProperty property,
                              final String newName,
                              final boolean searchInComments,
                              boolean isPreview) {
    doRename(property, newName, null, searchInComments, isPreview);
  }

  public static void doRename(@NotNull final BeanProperty property,
                              final String newName,
                              @Nullable Editor editor,
                              final boolean searchInComments,
                              boolean isPreview) {
    final PsiElement psiElement = property.getPsiElement();
    final RenameRefactoring rename = new JavaRenameRefactoringImpl(psiElement.getProject(), psiElement, newName, searchInComments, false);
    rename.setPreviewUsages(isPreview);

    final PsiMethod setter = property.getSetter();
    final PsiElement setterSubstitutor = substituteElementToRename(setter, editor);
    if (setterSubstitutor != null) {
      if (setterSubstitutor == setter) {
        rename.addElement(setterSubstitutor, PropertyUtilBase.suggestSetterName(newName));
      }
      else {
        rename.addElement(setterSubstitutor, newName);
      }
      final PsiParameter[] setterParameters = setter.getParameterList().getParameters();
      if (setterParameters.length == 1) {
        final JavaCodeStyleManager manager = JavaCodeStyleManager.getInstance(psiElement.getProject());
        final String suggestedParameterName = manager.propertyNameToVariableName(property.getName(), VariableKind.PARAMETER);
        if (suggestedParameterName.equals(setterParameters[0].getName())) {
          rename.addElement(setterParameters[0], manager.propertyNameToVariableName(newName, VariableKind.PARAMETER));
        }
      }
    }

    final PsiMethod getter = property.getGetter();
    final PsiElement getterSubstitutor = substituteElementToRename(getter, editor);
    if (getterSubstitutor != null) {
      if (getterSubstitutor == getter) {
        rename.addElement(getterSubstitutor, PropertyUtilBase.suggestGetterName(newName, getter.getReturnType()));
      }
      else {
        rename.addElement(getterSubstitutor, newName);
      }
    }
    rename.run();
  }

  @Contract("null, _ -> null")
  private static PsiElement substituteElementToRename(@Nullable PsiElement element, @Nullable Editor editor) {
    if (element == null) return null;
    RenamePsiElementProcessor processor = RenamePsiElementProcessor.forElement(element);
    PsiElement substituted = processor.substituteElementToRename(element, editor);
    if (substituted == null || !PsiElementRenameHandler.canRename(element.getProject(), editor, substituted)) return null;
    return substituted;
  }

  @Nullable
  protected abstract BeanProperty getProperty(DataContext context);

  private static class PropertyRenameDialog extends RenameDialog {

    private final BeanProperty myProperty;
    private final Editor myEditor;

    protected PropertyRenameDialog(BeanProperty property, final Editor editor) {
      super(property.getMethod().getProject(), property.getPsiElement(), null, editor);
      myEditor = editor;
      myProperty = property;
    }

    protected void doAction() {
      final String newName = getNewName();
      final boolean searchInComments = isSearchInComments();
      doRename(myProperty, newName, myEditor, searchInComments, isPreviewUsages());
      close(DialogWrapper.OK_EXIT_CODE);
    }
  }
}
