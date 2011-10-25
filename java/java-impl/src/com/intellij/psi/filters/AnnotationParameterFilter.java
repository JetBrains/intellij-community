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
package com.intellij.psi.filters;

import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiAnnotationParameterList;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNameValuePair;
import com.intellij.util.ReflectionCache;
import org.jetbrains.annotations.NonNls;

/**
 * @author peter
 */
public class AnnotationParameterFilter implements ElementFilter{
  private final Class<? extends PsiElement> myClass;
  @NonNls private final String myParameterName;
  private final String myAnnotationQualifiedName;


  public AnnotationParameterFilter(final Class<? extends PsiElement> elementClass,
                                   final String annotationQualifiedName,
                                   @NonNls final String parameterName) {
    myAnnotationQualifiedName = annotationQualifiedName;
    myClass = elementClass;
    myParameterName = parameterName;
  }

  @Override
  public boolean isAcceptable(Object element, PsiElement context) {
    final PsiElement parent = ((PsiElement)element).getParent();
    if (parent instanceof PsiNameValuePair) {
      final PsiNameValuePair pair = (PsiNameValuePair)parent;
      final String name = pair.getName();
      if (myParameterName.equals(name) || name == null && "value".equals(myParameterName)) {
        final PsiElement psiElement = pair.getParent();
        if (psiElement instanceof PsiAnnotationParameterList) {
          final PsiElement grandParent = psiElement.getParent();
          if (grandParent instanceof PsiAnnotation) {
            if (myAnnotationQualifiedName.equals(((PsiAnnotation)grandParent).getQualifiedName())) {
              return true;
            }
          }
        }
      }
    }
    return false;
  }

  @Override
  public boolean isClassAcceptable(Class hintClass) {
    return ReflectionCache.isAssignable(myClass, hintClass);
  }
}
