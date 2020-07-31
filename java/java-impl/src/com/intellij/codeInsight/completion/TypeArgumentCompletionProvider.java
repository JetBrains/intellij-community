// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.*;
import com.intellij.codeInsight.completion.util.ParenthesesInsertHandler;
import com.intellij.codeInsight.lookup.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.patterns.ElementPattern;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.Consumer;
import com.intellij.util.ThreeState;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.JBIterable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

import static com.intellij.patterns.PsiJavaPatterns.psiElement;

/**
* @author peter
*/
class TypeArgumentCompletionProvider {
  static final ElementPattern<PsiElement> IN_TYPE_ARGS = psiElement().inside(PsiReferenceParameterList.class);
  private static final Logger LOG = Logger.getInstance(TypeArgumentCompletionProvider.class);
  private final boolean mySmart;
  @Nullable private final JavaCompletionSession mySession;

  TypeArgumentCompletionProvider(boolean smart, @Nullable JavaCompletionSession session) {
    mySmart = smart;
    mySession = session;
  }

  void addTypeArgumentVariants(CompletionParameters parameters, Consumer<? super LookupElement> result, PrefixMatcher matcher) {
    final Pair<PsiTypeParameterListOwner, Integer> pair = getTypeParameterInfo(parameters.getPosition());
    if (pair == null) return;

    PsiTypeParameterListOwner paramOwner = pair.first;
    if (suggestByExpectedType(result, parameters.getPosition(), paramOwner, pair.second)) return;

    if (mySmart && paramOwner instanceof PsiClass) {
      addInheritors(parameters, result, (PsiClass)paramOwner, pair.second, matcher);
    }
  }

  private boolean suggestByExpectedType(Consumer<? super LookupElement> result,
                                        PsiElement context,
                                        PsiTypeParameterListOwner paramOwner, int index) {
    PsiExpression expression = PsiTreeUtil.getContextOfType(context, PsiExpression.class, true);
    ExpectedTypeInfo[] types = ExpectedTypesProvider.getExpectedTypes(expression, true, false, false);
    if (expression == null || types.length == 0) return false;

    for (ExpectedTypeInfo info : types) {
      PsiType type = info.getType();
      if (type instanceof PsiClassType && !type.equals(expression.getType())) {
        JBIterable<PsiTypeParameter> remainingParams = JBIterable.of(paramOwner.getTypeParameters()).skip(index);
        List<PsiType> expectedArgs = CodeInsightUtil.getExpectedTypeArgs(context, paramOwner, remainingParams, (PsiClassType)type);
        createLookupItems(result, context, info, expectedArgs, paramOwner);
      }
    }
    return true;
  }

  private void createLookupItems(Consumer<? super LookupElement> result,
                                 PsiElement context,
                                 ExpectedTypeInfo info,
                                 List<? extends PsiType> expectedArgs, PsiTypeParameterListOwner paramOwner) {
    if (expectedArgs.contains(null)) {
      PsiType arg = expectedArgs.get(0);
      if (arg != null) {
        result.consume(TailTypeDecorator.withTail(PsiTypeLookupItem.createLookupItem(arg, context), getTail(expectedArgs.size() == 1)));
      }
    } else {
      fillAllArgs(result, context, info, expectedArgs, paramOwner);
    }
  }

  private void fillAllArgs(Consumer<? super LookupElement> resultSet,
                           PsiElement context,
                           ExpectedTypeInfo info,
                           List<? extends PsiType> expectedArgs,
                           PsiTypeParameterListOwner paramOwner) {
    List<PsiTypeLookupItem> typeItems = ContainerUtil.map(expectedArgs, arg -> PsiTypeLookupItem.createLookupItem(arg, context));
    TailType globalTail = mySmart ? info.getTailType() : TailType.NONE;
    TypeArgsLookupElement element = new TypeArgsLookupElement(typeItems, globalTail, hasParameters(paramOwner, context));
    element.registerSingleClass(mySession);
    resultSet.consume(element);
  }

  private static boolean hasParameters(PsiTypeParameterListOwner paramOwner, PsiElement context) {
    return paramOwner instanceof PsiClass && ConstructorInsertHandler.hasConstructorParameters((PsiClass)paramOwner, context) != ThreeState.NO;
  }

  private static void addInheritors(CompletionParameters parameters,
                                    Consumer<? super LookupElement> resultSet,
                                    PsiClass referencedClass,
                                    int parameterIndex,
                                    PrefixMatcher matcher) {
    final List<PsiClassType> typeList = Collections.singletonList((PsiClassType)TypeConversionUtil.typeParameterErasure(
      referencedClass.getTypeParameters()[parameterIndex]));
    JavaInheritorsGetter.processInheritors(parameters, typeList, matcher, type -> {
      final PsiClass psiClass = PsiUtil.resolveClassInType(type);
      if (psiClass == null) return;

      resultSet.consume(TailTypeDecorator.withTail(new JavaPsiClassReferenceElement(psiClass),
                                                      getTail(parameterIndex == referencedClass.getTypeParameters().length - 1)));
    });
  }

  private static TailType getTail(boolean last) {
    return last ? new CharTailType('>') : CommaTailType.INSTANCE;
  }

  @Nullable
  static Pair<PsiTypeParameterListOwner, Integer> getTypeParameterInfo(PsiElement context) {
    final PsiReferenceParameterList parameterList = PsiTreeUtil.getContextOfType(context, PsiReferenceParameterList.class, true);
    if (parameterList == null) return null;

    PsiElement parent = parameterList.getParent();
    if (!(parent instanceof PsiJavaCodeReferenceElement)) return null;

    final PsiJavaCodeReferenceElement referenceElement = (PsiJavaCodeReferenceElement)parent;
    final int parameterIndex;

    int index = 0;
    final PsiTypeElement typeElement = PsiTreeUtil.getContextOfType(context, PsiTypeElement.class, true);
    if(typeElement != null){
      final PsiTypeElement[] elements = referenceElement.getParameterList().getTypeParameterElements();
      while (index < elements.length) {
        final PsiTypeElement element = elements[index++];
        if(element == typeElement) break;
      }
    }
    parameterIndex = index - 1;

    if(parameterIndex < 0) return null;
    final PsiElement target = referenceElement.resolve();
    if(!(target instanceof PsiClass) && !(target instanceof PsiMethod)) return null;

    final PsiTypeParameterListOwner referencedClass = (PsiTypeParameterListOwner)target;
    final PsiTypeParameter[] typeParameters = referencedClass.getTypeParameters();
    if(typeParameters.length <= parameterIndex) return null;

    return Pair.create(referencedClass, parameterIndex);
  }

  public static class TypeArgsLookupElement extends LookupElement {
    private final String myLookupString;
    private final List<? extends PsiTypeLookupItem> myTypeItems;
    private final TailType myGlobalTail;
    private final boolean myHasParameters;

    public TypeArgsLookupElement(List<? extends PsiTypeLookupItem> typeItems, TailType globalTail, boolean hasParameters) {
      myTypeItems = typeItems;
      myGlobalTail = globalTail;
      myHasParameters = hasParameters;
      myLookupString = StringUtil.join(myTypeItems, item -> item.getType().getPresentableText(), ", ");
    }

    @NotNull
    @Override
    public Object getObject() {
      return myTypeItems.get(0).getObject();
    }

    public void registerSingleClass(@Nullable JavaCompletionSession inheritors) {
      if (inheritors != null && myTypeItems.size() == 1) {
        PsiType type = myTypeItems.get(0).getType();
        PsiClass aClass = PsiUtil.resolveClassInClassTypeOnly(type);
        if (aClass != null && !aClass.hasTypeParameters()) {
          myTypeItems.get(0).setShowPackage();
          inheritors.registerClass(aClass);
        }
      }
    }

    @NotNull
    @Override
    public String getLookupString() {
      return myLookupString;
    }

    @Override
    public void renderElement(LookupElementPresentation presentation) {
      myTypeItems.get(0).renderElement(presentation);
      presentation.setItemText(getLookupString());
      if (myTypeItems.size() > 1) {
        presentation.setTailText(null);
        presentation.setTypeText(null);
      }
    }

    @Override
    public void handleInsert(@NotNull InsertionContext context) {
      context.commitDocument();
      PsiReferenceParameterList list = PsiTreeUtil.findElementOfClassAtOffset(context.getFile(), context.getStartOffset(), PsiReferenceParameterList.class, false);
      PsiTypeElement[] typeElements = list != null ? list.getTypeParameterElements() : PsiTypeElement.EMPTY_ARRAY;
      if (typeElements.length == 0) {
        return;
      }
      int listEnd = typeElements[typeElements.length - 1].getTextRange().getEndOffset();
      context.setTailOffset(listEnd);
      context.getDocument().deleteString(context.getStartOffset(), listEnd);
      for (int i = 0; i < myTypeItems.size(); i++) {
        PsiTypeLookupItem typeItem = myTypeItems.get(i);
        CompletionUtil.emulateInsertion(context, context.getTailOffset(), typeItem);
        if (context.getTailOffset() < 0) {
          LOG.error("tail offset spoiled by " + typeItem);
          return;
        }
        context.setTailOffset(getTail(i == myTypeItems.size() - 1).processTail(context.getEditor(), context.getTailOffset()));
      }
      context.setAddCompletionChar(false);

      context.commitDocument();

      PsiElement leaf = context.getFile().findElementAt(context.getTailOffset() - 1);
      if (psiElement().withParents(PsiReferenceParameterList.class, PsiJavaCodeReferenceElement.class, PsiNewExpression.class)
        .accepts(leaf)) {
        ParenthesesInsertHandler.getInstance(myHasParameters).handleInsert(context, this);
        myGlobalTail.processTail(context.getEditor(), context.getTailOffset());
      }
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      TypeArgsLookupElement element = (TypeArgsLookupElement)o;

      if (!myTypeItems.equals(element.myTypeItems)) return false;

      return true;
    }

    @Override
    public int hashCode() {
      return myTypeItems.hashCode();
    }
  }
}
