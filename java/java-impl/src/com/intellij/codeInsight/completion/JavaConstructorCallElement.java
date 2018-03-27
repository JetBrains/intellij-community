/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementDecorator;
import com.intellij.codeInsight.lookup.LookupElementPresentation;
import com.intellij.codeInsight.lookup.TypedLookupItem;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.resolve.JavaResolveUtil;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

/**
 * @author peter
 */
public class JavaConstructorCallElement extends LookupElementDecorator<LookupElement> implements TypedLookupItem {
  private static final Key<JavaConstructorCallElement> WRAPPING_CONSTRUCTOR_CALL = Key.create("WRAPPING_CONSTRUCTOR_CALL");
  @NotNull private final PsiMethod myConstructor;
  @NotNull private final PsiClassType myType;
  @NotNull private final PsiSubstitutor mySubstitutor;

  private JavaConstructorCallElement(@NotNull LookupElement classItem, @NotNull PsiMethod constructor, @NotNull PsiClassType type) {
    super(classItem);
    myConstructor = constructor;
    myType = type;
    mySubstitutor = myType.resolveGenerics().getSubstitutor();
  }

  private void markClassItemWrapped(@NotNull LookupElement classItem) {
    LookupElement delegate = classItem;
    while (true) {
      delegate.putUserData(WRAPPING_CONSTRUCTOR_CALL, this);
      if (!(delegate instanceof LookupElementDecorator)) break;
      delegate = ((LookupElementDecorator)delegate).getDelegate();
    }
  }

  @Override
  public void handleInsert(InsertionContext context) {
    markClassItemWrapped(getDelegate());
    super.handleInsert(context);

    context.commitDocument();
    PsiCallExpression callExpression = PsiTreeUtil.findElementOfClassAtOffset(context.getFile(), context.getStartOffset(), 
                                                                              PsiCallExpression.class, false);
    // make sure this is the constructor call we've just added, not the enclosing method/constructor call
    if (callExpression != null) {
      PsiElement completedElement = callExpression instanceof PsiNewExpression ?
                                    ((PsiNewExpression)callExpression).getClassOrAnonymousClassReference() : null;
      TextRange completedElementRange = completedElement == null ? null : completedElement.getTextRange();
      if (completedElementRange == null || completedElementRange.getStartOffset() != context.getStartOffset()) {
        callExpression = null;
      }
    }
    if (callExpression != null) {
      JavaMethodCallElement.showParameterHints(context, myConstructor, callExpression);
    }
  }

  @NotNull
  @Override
  public PsiMethod getObject() {
    return myConstructor;
  }

  @Override
  public boolean equals(Object o) {
    return this == o || super.equals(o) && myConstructor.equals(((JavaConstructorCallElement)o).myConstructor);
  }

  @Override
  public int hashCode() {
    return 31 * super.hashCode() + myConstructor.hashCode();
  }

  @NotNull
  @Override
  public PsiType getType() {
    return myType;
  }

  @Override
  public void renderElement(LookupElementPresentation presentation) {
    super.renderElement(presentation);

    String tailText = StringUtil.notNullize(presentation.getTailText());
    int genericsEnd = tailText.lastIndexOf('>') + 1;

    presentation.clearTail();
    presentation.appendTailText(tailText.substring(0, genericsEnd), false);
    presentation.appendTailText(MemberLookupHelper.getMethodParameterString(myConstructor, mySubstitutor), false);
    presentation.appendTailText(tailText.substring(genericsEnd), true);
  }

  static List<? extends LookupElement> wrap(@NotNull JavaPsiClassReferenceElement classItem, @NotNull PsiElement position) {
    PsiClass psiClass = classItem.getObject();
    return wrap(classItem, psiClass, position, () -> JavaPsiFacade.getElementFactory(psiClass.getProject()).createType(psiClass, PsiSubstitutor.EMPTY));
  }

  static List<? extends LookupElement> wrap(@NotNull LookupElement classItem, @NotNull PsiClass psiClass,
                                            @NotNull PsiElement position, @NotNull Supplier<PsiClassType> type) {
    if (Registry.is("java.completion.show.constructors") && isConstructorCallPlace(position)) {
      List<PsiMethod> constructors = ContainerUtil.filter(psiClass.getConstructors(), c -> shouldSuggestConstructor(psiClass, position, c));
      if (!constructors.isEmpty()) {
        return ContainerUtil.map(constructors, c -> new JavaConstructorCallElement(classItem, c, type.get()));
      }
    }
    return Collections.singletonList(classItem);
  }

  private static boolean shouldSuggestConstructor(@NotNull PsiClass psiClass, @NotNull PsiElement position, PsiMethod constructor) {
    return JavaResolveUtil.isAccessible(constructor, psiClass, constructor.getModifierList(), position, null, null) ||
           willBeAccessibleInAnonymous(psiClass, constructor);
  }

  private static boolean willBeAccessibleInAnonymous(@NotNull PsiClass psiClass, PsiMethod constructor) {
    return !constructor.hasModifierProperty(PsiModifier.PRIVATE) && psiClass.hasModifierProperty(PsiModifier.ABSTRACT);
  }

  private static boolean isConstructorCallPlace(@NotNull PsiElement position) {
    return CachedValuesManager.getCachedValue(position, () -> {
      boolean result = JavaClassNameCompletionContributor.AFTER_NEW.accepts(position) &&
                       !JavaClassNameInsertHandler.isArrayTypeExpected(PsiTreeUtil.getParentOfType(position, PsiNewExpression.class));
      return CachedValueProvider.Result.create(result, position);
    });
  }

  @Nullable
  static PsiMethod extractCalledConstructor(@NotNull LookupElement element) {
    JavaConstructorCallElement callItem = element.getUserData(WRAPPING_CONSTRUCTOR_CALL);
    return callItem != null ? callItem.getObject() : null;
  }

}
