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

/*
 * Created by IntelliJ IDEA.
 * User: mike
 * Date: Aug 29, 2002
 * Time: 4:34:37 PM
 * To change template for new class use
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.codeInsight.intention.impl;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.lang.StdLanguages;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.search.PsiElementProcessor;
import com.intellij.psi.search.PsiElementProcessorAdapter;
import com.intellij.psi.search.searches.ClassInheritorsSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ImplementAbstractMethodAction extends BaseIntentionAction {
  @NotNull
  public String getFamilyName() {
    return CodeInsightBundle.message("intention.implement.abstract.method.family");
  }

  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    int offset = editor.getCaretModel().getOffset();
    final PsiMethod method = findMethod(file, offset);

    if (method == null || !method.isValid()) return false;
    setText(getIntentionName(method));

    if (!method.getManager().isInProject(method)) return false;

    PsiClass containingClass = method.getContainingClass();
    if (containingClass == null) return false;
    if (method.hasModifierProperty(PsiModifier.ABSTRACT)) {
      MyElementProcessor processor = new MyElementProcessor(method);
      ClassInheritorsSearch.search(containingClass, containingClass.getUseScope(), false).forEach(new PsiElementProcessorAdapter<PsiClass>(
        processor));
      return isAvailable(processor);
    }

    return false;
  }

  protected String getIntentionName(final PsiMethod method) {
    return CodeInsightBundle.message("intention.implement.abstract.method.text", method.getName());
  }

  static class MyElementProcessor implements PsiElementProcessor {
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

    public boolean execute(PsiElement element) {
      if (element instanceof PsiClass && StdLanguages.JAVA.equals(element.getLanguage())) {
        PsiClass aClass = (PsiClass) element;
        final PsiMethod existingImplementation = findExistingImplementation(aClass, myMethod);
        if (existingImplementation != null && !existingImplementation.hasModifierProperty(PsiModifier.ABSTRACT)) {
          myHasExistingImplementations = true;
        }
        else if (existingImplementation == null) {
          myHasMissingImplementations = true;
        }
        if (myHasMissingImplementations && myHasExistingImplementations) return false;
      }
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
      final PsiMethod[] superMethods = candidate.findSuperMethods(aClass);
      for(PsiMethod superMethod: superMethods) {
        if (superMethod.equals(method)) {
          return candidate;
        }
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

  public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    PsiMethod method = findMethod(file, editor.getCaretModel().getOffset());
    if (method == null) return;
    if (!ApplicationManager.getApplication().isUnitTestMode() && !editor.getContentComponent().isShowing()) return;
    invokeHandler(project, editor, method);
  }

  protected void invokeHandler(final Project project, final Editor editor, final PsiMethod method) {
    new ImplementAbstractMethodHandler(project, editor, method).invoke();
  }

  public boolean startInWriteAction() {
    return false;
  }
}
