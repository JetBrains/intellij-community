/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementDecorator;
import com.intellij.codeInsight.lookup.LookupElementPresentation;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.psi.*;
import com.intellij.util.containers.JBIterable;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

/**
 * @author peter
 */
public class JavaConstructorCallElement extends JavaMethodCallElement {
  private static final Key<JavaConstructorCallElement> WRAPPING_CONSTRUCTOR_CALL = Key.create("WRAPPING_CONSTRUCTOR_CALL");
  @NotNull private final LookupElement myClassItem;
  @NotNull private final Supplier<PsiType> myType;

  private JavaConstructorCallElement(@NotNull LookupElement classItem, @NotNull PsiMethod constructor, @NotNull Supplier<PsiType> type) {
    super(constructor);
    myClassItem = classItem;
    myType = type;
    setQualifierSubstitutor(((PsiClassType) type.get()).resolveGenerics().getSubstitutor());

    LookupElement delegate = classItem;
    while (true) {
      delegate.putUserData(WRAPPING_CONSTRUCTOR_CALL, this);
      if (!(delegate instanceof LookupElementDecorator)) break;
      delegate = ((LookupElementDecorator)delegate).getDelegate();
    }
  }

  @NotNull
  @Override
  public PsiType getType() {
    return myType.get();
  }

  @Override
  public void handleInsert(InsertionContext context) {
    myClassItem.handleInsert(context);
    super.handleInsert(context);
  }

  @Override
  public void renderElement(LookupElementPresentation presentation) {
    myClassItem.renderElement(presentation);
    String itemText = presentation.getItemText();

    super.renderElement(presentation);
    presentation.setItemText(itemText);
  }

  static List<? extends LookupElement> wrap(JavaPsiClassReferenceElement classItem, PsiElement position) {
    PsiClass psiClass = classItem.getObject();
    return wrap(classItem, psiClass, position, () -> JavaPsiFacade.getElementFactory(psiClass.getProject()).createType(psiClass, PsiSubstitutor.EMPTY));
  }

  static List<? extends LookupElement> wrap(LookupElement classItem, PsiClass psiClass, PsiElement position, Supplier<PsiType> type) {
    if (Registry.is("java.completion.show.constructors") && JavaClassNameCompletionContributor.AFTER_NEW.accepts(position)) {
      return JBIterable.of(psiClass.getConstructors()).
        filter(JavaCompletionUtil::isConstructorCompletable).
        map(c -> new JavaConstructorCallElement(classItem, c, type)).
        toList();
    }
    return Collections.singletonList(classItem);
  }

  static boolean isWrapped(LookupElement element) {
    return element.getUserData(WRAPPING_CONSTRUCTOR_CALL) != null;
  }

}
