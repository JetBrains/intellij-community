// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementDecorator;
import com.intellij.codeInsight.lookup.LookupElementPresentation;
import com.intellij.codeInsight.lookup.TypedLookupItem;
import com.intellij.java.syntax.parser.JavaKeywords;
import com.intellij.openapi.editor.CaretModel;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.impl.source.resolve.JavaResolveUtil;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.ig.psiutils.TypeUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

public final class JavaConstructorCallElement extends LookupElementDecorator<LookupElement> implements TypedLookupItem {
  private static final Key<JavaConstructorCallElement> WRAPPING_CONSTRUCTOR_CALL = Key.create("WRAPPING_CONSTRUCTOR_CALL");
  private final @NotNull PsiMethod myConstructor;
  private final @NotNull PsiClassType myType;
  private final @NotNull PsiSubstitutor mySubstitutor;
  private final @Nullable Arguments myArguments;
  
  private record Arguments(String canonical, String presentation) {}

  private JavaConstructorCallElement(@NotNull LookupElement classItem, @NotNull PsiMethod constructor, @NotNull PsiClassType type) {
    super(classItem);
    myConstructor = constructor;
    myType = type;
    mySubstitutor = myType.resolveGenerics().getSubstitutor();
    myArguments = computeArguments();
  }

  private Arguments computeArguments() {
    PsiParameter[] parameters = myConstructor.getParameterList().getParameters();
    if (parameters.length == 1) {
      PsiType type = myType.resolveGenerics().getSubstitutor().substitute(parameters[0].getType());
      if (type instanceof PsiClassType clsType && TypeUtils.typeEquals(CommonClassNames.JAVA_LANG_CLASS, clsType.rawType())) {
        PsiType[] typeArgs = clsType.getParameters();
        if (typeArgs.length == 1 && typeArgs[0] instanceof PsiClassType typeArg && typeArg.getParameterCount() == 0) {
          return new Arguments(typeArg.getCanonicalText() + ".class", typeArg.getPresentableText() + ".class");
        }
      }
      if (TypeUtils.typeEquals(CommonClassNames.JAVA_LANG_VOID, type)) {
        return new Arguments(JavaKeywords.NULL, JavaKeywords.NULL);
      }
    }
    return null;
  }

  private void markClassItemWrapped(@NotNull LookupElement classItem) {
    LookupElement delegate = classItem;
    while (true) {
      delegate.putUserData(WRAPPING_CONSTRUCTOR_CALL, this);
      if (!(delegate instanceof LookupElementDecorator<?> decorator)) break;
      delegate = decorator.getDelegate();
    }
  }

  @Override
  public void handleInsert(@NotNull InsertionContext context) {
    markClassItemWrapped(getDelegate());
    super.handleInsert(context);

    context.commitDocument();
    PsiCallExpression callExpression = PsiTreeUtil.findElementOfClassAtOffset(context.getFile(), context.getStartOffset(),
                                                                              PsiCallExpression.class, false);
    // make sure this is the constructor call we've just added, not the enclosing method/constructor call
    if (callExpression != null) {
      PsiExpressionList argumentList = callExpression.getArgumentList();
      if (myArguments != null && argumentList != null) {
        PsiExpressionList newList = ((PsiMethodCallExpression)JavaPsiFacade.getElementFactory(context.getProject())
          .createExpressionFromText("a(" + myArguments.canonical() + ")", argumentList)).getArgumentList();
        argumentList = (PsiExpressionList)argumentList.replace(newList);
        JavaCodeStyleManager.getInstance(context.getProject()).shortenClassReferences(argumentList);
        CaretModel model = context.getEditor().getCaretModel();
        int offset = argumentList.getTextRange().getEndOffset();
        if (model.getOffset() < offset) {
          model.moveToOffset(offset);
        }
        return;
      }
      PsiElement completedElement = callExpression instanceof PsiNewExpression newExpression ?
                                    newExpression.getClassOrAnonymousClassReference() : null;
      TextRange completedElementRange = completedElement == null ? null : completedElement.getTextRange();
      if (completedElementRange == null || completedElementRange.getStartOffset() != context.getStartOffset()) {
        callExpression = null;
      }
    }
    if (callExpression != null) {
      JavaMethodCallElement.showParameterHints(this, context, myConstructor, callExpression);
    }
  }

  @Override
  public @NotNull PsiMethod getObject() {
    return myConstructor;
  }

  @Override
  public @NotNull PsiElement getPsiElement() {
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

  @Override
  public @NotNull PsiType getType() {
    return myType;
  }

  @Override
  public boolean isValid() {
    return myConstructor.isValid() && myType.isValid();
  }

  @Override
  public void renderElement(@NotNull LookupElementPresentation presentation) {
    super.renderElement(presentation);

    String tailText = StringUtil.notNullize(presentation.getTailText());
    int genericsEnd = tailText.lastIndexOf('>') + 1;

    presentation.clearTail();
    presentation.appendTailText(tailText.substring(0, genericsEnd), false);
    if (myArguments != null) {
      presentation.appendTailText("(" + myArguments.presentation() + ")", false);
    } else {
      presentation.appendTailText(MemberLookupHelper.getMethodParameterString(myConstructor, mySubstitutor), false);
    }
    presentation.appendTailText(tailText.substring(genericsEnd), true);
  }

  public @NotNull PsiClass getConstructedClass() {
    PsiClass aClass = myConstructor.getContainingClass();
    if (aClass == null) {
      PsiUtilCore.ensureValid(myConstructor);
      throw new AssertionError(myConstructor + " of " + myConstructor.getClass() + " returns null containing class, file=" + myConstructor.getContainingFile());
    }
    return aClass;
  }

  static @Unmodifiable List<? extends LookupElement> wrap(@NotNull JavaPsiClassReferenceElement classItem, @NotNull PsiElement position) {
    PsiClass psiClass = classItem.getObject();
    return wrap(classItem, psiClass, position, () -> JavaPsiFacade.getElementFactory(psiClass.getProject()).createType(psiClass, PsiSubstitutor.EMPTY));
  }

  static @Unmodifiable List<? extends LookupElement> wrap(@NotNull LookupElement classItem, @NotNull PsiClass psiClass,
                                                          @NotNull PsiElement position, @NotNull Supplier<? extends PsiClassType> type) {
    if ((Registry.is("java.completion.show.constructors") || CodeInsightSettings.getInstance().SHOW_PARAMETER_NAME_HINTS_ON_COMPLETION) &&
        isConstructorCallPlace(position)) {
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

  static boolean isConstructorCallPlace(@NotNull PsiElement position) {
    return CachedValuesManager.getCachedValue(position, () -> {
      boolean result = JavaClassNameCompletionContributor.AFTER_NEW.accepts(position) &&
                       !JavaClassNameInsertHandler.isArrayTypeExpected(PsiTreeUtil.getParentOfType(position, PsiNewExpression.class));
      return CachedValueProvider.Result.create(result, position);
    });
  }

  static @Nullable PsiMethod extractCalledConstructor(@NotNull LookupElement element) {
    JavaConstructorCallElement callItem = element.getUserData(WRAPPING_CONSTRUCTOR_CALL);
    return callItem != null ? callItem.getObject() : null;
  }

}
