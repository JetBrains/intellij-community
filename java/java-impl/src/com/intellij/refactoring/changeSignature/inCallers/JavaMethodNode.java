/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.refactoring.changeSignature.inCallers;

import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.presentation.java.ClassPresentationUtil;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.MethodReferencesSearch;
import com.intellij.psi.util.PsiFormatUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.changeSignature.MethodNodeBase;
import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.JBColor;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class JavaMethodNode extends MethodNodeBase<PsiMethod> {

  protected JavaMethodNode(final PsiMethod method, Set<PsiMethod> called, Project project, Runnable cancelCallback) {
    super(method, called, project, cancelCallback);
  }

  @Override
  protected MethodNodeBase<PsiMethod> createNode(PsiMethod caller, HashSet<PsiMethod> called) {
    return new JavaMethodNode(caller, called, myProject, myCancelCallback);
  }

  @Override
  protected List<PsiMethod> computeCallers() {
    final PsiReference[] refs =
      MethodReferencesSearch.search(myMethod, GlobalSearchScope.allScope(myProject), true).toArray(PsiReference.EMPTY_ARRAY);

    List<PsiMethod> result = new ArrayList<>();
    for (PsiReference ref : refs) {
      final PsiElement element = ref.getElement();
      if (!(element instanceof PsiReferenceExpression) ||
          !(((PsiReferenceExpression)element).getQualifierExpression() instanceof PsiSuperExpression)) {
        final PsiElement enclosingContext = PsiTreeUtil.getParentOfType(element, PsiMethod.class, PsiClass.class);
        if (enclosingContext instanceof PsiMethod && !result.contains(enclosingContext) &&
            !myMethod.equals(enclosingContext) && !myCalled.contains(myMethod)) { //do not add recursive methods
          result.add((PsiMethod)enclosingContext);
        }
        else if (element instanceof PsiClass) {
          final PsiClass aClass = (PsiClass)element;
          final PsiMethod method = JavaPsiFacade.getElementFactory(myProject).createMethodFromText(aClass.getName() + "(){}", aClass);
          if (!result.contains(method)) {
            result.add(method);
          }
        }
      }
    }
    return result;
  }

  @Override
  protected void customizeRendererText(ColoredTreeCellRenderer renderer) {
    final StringBuilder buffer = new StringBuilder(128);
    final PsiClass containingClass = myMethod.getContainingClass();
    if (containingClass != null) {
      buffer.append(ClassPresentationUtil.getNameForClass(containingClass, false));
      buffer.append('.');
    }
    final String methodText = PsiFormatUtil.formatMethod(
      myMethod,
      PsiSubstitutor.EMPTY, PsiFormatUtil.SHOW_NAME | PsiFormatUtil.SHOW_PARAMETERS,
      PsiFormatUtil.SHOW_TYPE
    );
    buffer.append(methodText);

    final SimpleTextAttributes attributes = isEnabled() ?
                                            new SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN, UIUtil.getTreeForeground()) :
                                            SimpleTextAttributes.EXCLUDED_ATTRIBUTES;
    renderer.append(buffer.toString(), attributes);

    if (containingClass != null) {
      final String packageName = getPackageName(containingClass);
      renderer.append("  (" + packageName + ")", new SimpleTextAttributes(SimpleTextAttributes.STYLE_ITALIC, JBColor.GRAY));
    }
  }

  @Nullable
  private static String getPackageName(final PsiClass aClass) {
    final PsiFile file = aClass.getContainingFile();
    if (file instanceof PsiJavaFile) {
      return ((PsiJavaFile)file).getPackageName();
    }
    return null;
  }
}
