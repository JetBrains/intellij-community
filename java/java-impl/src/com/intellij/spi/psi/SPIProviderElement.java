/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.spi.psi;

import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.extapi.psi.ASTWrapperPsiElement;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.search.searches.ClassInheritorsSearch;
import com.intellij.psi.util.ClassUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.spi.SPIFileType;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.Processor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * User: anna
 */
public class SPIProviderElement extends ASTWrapperPsiElement implements PsiReference {
  public SPIProviderElement(ASTNode node) {
    super(node);
  }

  @Override
  public PsiElement getElement() {
    return this;
  }

  @Override
  public TextRange getRangeInElement() {
    final PsiElement last = PsiTreeUtil.getDeepestLast(this);
    return new TextRange(last.getStartOffsetInParent(), getTextLength());
  }

  @Nullable
  @Override
  public PsiElement resolve() {
    return ClassUtil.findPsiClass(getManager(), getText(), null, true, getResolveScope());
  }

  @NotNull
  @Override
  public String getCanonicalText() {
    return getText();
  }

  @Override
  public PsiElement handleElementRename(String newElementName) throws IncorrectOperationException {
    final SPIProvidersElementList firstChild =
      (SPIProvidersElementList)PsiFileFactory.getInstance(getProject())
        .createFileFromText("spi_dummy", SPIFileType.INSTANCE, newElementName).getFirstChild();
    return replace(firstChild.getElements().get(0));
  }

  @Override
  public PsiElement bindToElement(@NotNull PsiElement element) throws IncorrectOperationException {
    if (element instanceof PsiClass) {
      final String className = ClassUtil.getJVMClassName((PsiClass)element);
      if (className != null) {
        return handleElementRename(className);
      }
    }
    return null;
  }

  @Override
  public boolean isReferenceTo(PsiElement element) {
    if (element instanceof PsiClass) {
      return getText().equals(ClassUtil.getJVMClassName((PsiClass)element));
    }
    return false;
  }

  @NotNull
  @Override
  public Object[] getVariants() {
    final String name = getContainingFile().getName();
    final PsiClass superProvider = JavaPsiFacade.getInstance(getProject()).findClass(name, getResolveScope());
    if (superProvider != null) {
      final List<Object> result = new ArrayList<Object>();
      ClassInheritorsSearch.search(superProvider).forEach(new Processor<PsiClass>() {
        @Override
        public boolean process(PsiClass psiClass) {
          final String jvmClassName = ClassUtil.getJVMClassName(psiClass);
          if (jvmClassName != null) {
            result.add(LookupElementBuilder.create(psiClass, jvmClassName));
          }
          return false;
        }
      });
      return result.toArray(new Object[result.size()]);
    }
    return ArrayUtil.EMPTY_OBJECT_ARRAY;
  }

  @Override
  public PsiReference getReference() {
    return this;
  }

  @Override
  public boolean isSoft() {
    return false;
  }
  
  public boolean isDestination() {
    return true;
  }
}
