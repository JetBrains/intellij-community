/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

package com.intellij.psi.scope.processor;

import com.intellij.openapi.util.Key;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.ResolveState;
import com.intellij.psi.scope.ElementClassHint;
import com.intellij.psi.scope.NameHint;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Dmitry Avdeev
 */
public class MethodResolveProcessor implements PsiScopeProcessor, ElementClassHint, NameHint {

  private final String myNameHint;
  private final List<PsiMethod> myMethods = new ArrayList<PsiMethod>();

  public MethodResolveProcessor() {
    myNameHint = null;
  }

  public MethodResolveProcessor(final String name) {
    myNameHint = name;
  }

  public PsiMethod[] getMethods() {
    return myMethods.toArray(new PsiMethod[myMethods.size()]);
  }

  @Override
  public boolean execute(@NotNull PsiElement element, @NotNull ResolveState state) {
    if (element instanceof PsiMethod) {
      ContainerUtil.addIfNotNull(myMethods, (PsiMethod)element);
    }
    return true;
  }

  @Override
  public <T> T getHint(@NotNull Key<T> hintKey) {
    if (hintKey == ElementClassHint.KEY) {
      return (T)this;
    }
    if (hintKey == NameHint.KEY && myNameHint != null) {
      return (T)this;
    }
    return null;
  }

  @Override
  public void handleEvent(@NotNull Event event, Object associated) {
  }

  @Override
  public boolean shouldProcess(DeclarationKind kind) {
    return kind == DeclarationKind.METHOD;
  }

  public static PsiMethod[] findMethod(PsiClass psiClass, String methodName) {
    MethodResolveProcessor processor = new MethodResolveProcessor(methodName);
    psiClass.processDeclarations(processor, ResolveState.initial(), null, psiClass);
    return processor.getMethods();
  }

  public static PsiMethod[] getAllMethods(PsiClass psiClass) {
    MethodResolveProcessor processor = new MethodResolveProcessor();
    psiClass.processDeclarations(processor, ResolveState.initial(), null, psiClass);
    return processor.getMethods();
  }


  @Nullable
  @Override
  public String getName(@NotNull ResolveState state) {
    return myNameHint;
  }
}
