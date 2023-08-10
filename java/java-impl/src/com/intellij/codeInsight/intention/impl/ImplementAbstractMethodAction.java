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

package com.intellij.codeInsight.intention.impl;

import com.intellij.codeInspection.util.IntentionName;
import com.intellij.java.JavaBundle;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.search.PsiElementProcessor;
import com.intellij.psi.search.PsiElementProcessorAdapter;
import com.intellij.psi.search.searches.ClassInheritorsSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ImplementAbstractMethodAction extends BaseIntentionAction {
  @Override
  @NotNull
  public String getFamilyName() {
    return JavaBundle.message("intention.implement.abstract.method.family");
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    int offset = editor.getCaretModel().getOffset();
    final PsiMethod method = findMethod(file, offset);

    if (method == null || !method.isValid() || method.isConstructor()) return false;
    setText(getIntentionName(method));

    if (!canModify(method)) return false;

    PsiClass containingClass = method.getContainingClass();
    if (containingClass == null) return false;
    final boolean isAbstract = method.hasModifierProperty(PsiModifier.ABSTRACT);
    if (isAbstract || !method.hasModifierProperty(PsiModifier.PRIVATE) && !method.hasModifierProperty(PsiModifier.STATIC)) {
      if (!isAbstract && !isOnIdentifier(file, offset)) return false;
      MyElementProcessor processor = new MyElementProcessor(method);
      if (containingClass.isEnum()) {
        for (PsiField field : containingClass.getFields()) {
          if (field instanceof PsiEnumConstant) {
            final PsiEnumConstantInitializer initializingClass = ((PsiEnumConstant)field).getInitializingClass();
            if (initializingClass == null) {
              processor.myHasMissingImplementations = true;
            } else {
              if (!processor.execute(initializingClass)){
                break;
              }
            }
          }
        }
      }
      ClassInheritorsSearch.search(containingClass, false).forEach(new PsiElementProcessorAdapter<>(processor));
      return isAvailable(processor);
    }

    return false;
  }

  private static boolean isOnIdentifier(PsiFile file, int offset) {
    final PsiElement psiElement = file.findElementAt(offset);
    if (psiElement instanceof PsiIdentifier){
      if (psiElement.getParent() instanceof PsiMethod) {
        return true;
      }
    }
    return false;
  }

  protected @IntentionName String getIntentionName(final PsiMethod method) {
    return method.hasModifierProperty(PsiModifier.ABSTRACT) ?
           JavaBundle.message("intention.implement.abstract.method.text", method.getName()) :
           JavaBundle.message("intention.override.method.text", method.getName());
  }

  static class MyElementProcessor implements PsiElementProcessor<PsiClass> {
    private boolean myHasMissingImplementations;
    private boolean myHasExistingImplementations;
    private final PsiMethod myMethod;

    MyElementProcessor(final PsiMethod method) {
      myMethod = method;
    }

    public boolean hasMissingImplementations() {
      return myHasMissingImplementations;
    }

    public boolean hasExistingImplementations() {
      return myHasExistingImplementations;
    }

    @Override
    public boolean execute(@NotNull PsiClass element) {
      final PsiMethod existingImplementation = findExistingImplementation(element, myMethod);
      if (existingImplementation != null && !existingImplementation.hasModifierProperty(PsiModifier.ABSTRACT)) {
        myHasExistingImplementations = true;
      }
      else if (existingImplementation == null) {
        myHasMissingImplementations = true;
      }
      if (myHasMissingImplementations && myHasExistingImplementations) return false;
      return true;
    }
  }

  protected boolean isAvailable(final MyElementProcessor processor) {
    return processor.hasMissingImplementations();
  }

  @Nullable
  static PsiMethod findExistingImplementation(final PsiClass aClass, PsiMethod method) {
    final PsiMethod[] methods = aClass.findMethodsByName(method.getName(), false);
    for(PsiMethod candidate: methods) {
      final PsiMethod[] superMethods = candidate.findSuperMethods(false);
      if (ArrayUtil.contains(method, superMethods)) {
        return candidate;
      }
    }
    return null;
  }

  private static PsiMethod findMethod(PsiFile file, int offset) {
    PsiMethod method = _findMethod(file, offset);
    if (method == null) {
      method = _findMethod(file, offset - 1);
    }
    return method;
  }

  private static PsiMethod _findMethod(PsiFile file, int offset) {
    return PsiTreeUtil.getParentOfType(file.findElementAt(offset), PsiMethod.class);
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    PsiMethod method = findMethod(file, editor.getCaretModel().getOffset());
    if (method == null) return;
    if (UIUtil.isShowing(editor.getContentComponent())) {
      invokeHandler(project, editor, method);
    }
  }

  protected void invokeHandler(final Project project, final Editor editor, final PsiMethod method) {
    new ImplementAbstractMethodHandler(project, editor, method).invoke();
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }
}
