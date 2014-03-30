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
package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.CharTailType;
import com.intellij.codeInsight.ExpectedTypeInfo;
import com.intellij.codeInsight.ExpectedTypesProvider;
import com.intellij.codeInsight.TailType;
import com.intellij.codeInsight.completion.util.ParenthesesInsertHandler;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementPresentation;
import com.intellij.codeInsight.lookup.PsiTypeLookupItem;
import com.intellij.codeInsight.lookup.TailTypeDecorator;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.Consumer;
import com.intellij.util.Function;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static com.intellij.patterns.PsiJavaPatterns.psiElement;

/**
* @author peter
*/
class TypeArgumentCompletionProvider extends CompletionProvider<CompletionParameters> {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.completion.TypeArgumentCompletionProvider");
  private final boolean mySmart;
  @Nullable private final InheritorsHolder myInheritors;

  TypeArgumentCompletionProvider(boolean smart, @Nullable InheritorsHolder inheritors) {
    mySmart = smart;
    myInheritors = inheritors;
  }

  @Override
  protected void addCompletions(@NotNull final CompletionParameters parameters, final ProcessingContext processingContext, @NotNull final CompletionResultSet resultSet) {
    final PsiElement context = parameters.getPosition();

    final Pair<PsiClass, Integer> pair = getTypeParameterInfo(context);
    if (pair == null) return;

    PsiExpression expression = PsiTreeUtil.getContextOfType(context, PsiExpression.class, true);
    if (expression != null) {
      ExpectedTypeInfo[] types = ExpectedTypesProvider.getExpectedTypes(expression, true, false, false);
      if (types.length > 0) {
        for (ExpectedTypeInfo info : types) {
          PsiType type = info.getType();
          if (type instanceof PsiClassType && !type.equals(expression.getType())) {
            fillExpectedTypeArgs(resultSet, context, pair.first, pair.second, ((PsiClassType)type).resolveGenerics(), mySmart ? info.getTailType() : TailType.NONE);
          }
        }
        return;
      }
    }

    if (mySmart) {
      addInheritors(parameters, resultSet, pair.first, pair.second);
    }
  }

  private void fillExpectedTypeArgs(CompletionResultSet resultSet,
                                           PsiElement context,
                                           final PsiClass actualClass,
                                           final int index,
                                           PsiClassType.ClassResolveResult expectedType,
                                           TailType globalTail) {
    final PsiClass expectedClass = expectedType.getElement();

    if (!InheritanceUtil.isInheritorOrSelf(actualClass, expectedClass, true)) return;
    assert expectedClass != null;

    final PsiSubstitutor currentSubstitutor = TypeConversionUtil.getClassSubstitutor(expectedClass, actualClass, PsiSubstitutor.EMPTY);
    assert currentSubstitutor != null;

    PsiTypeParameter[] params = actualClass.getTypeParameters();
    final List<PsiTypeLookupItem> typeItems = new ArrayList<PsiTypeLookupItem>();
    for (int i = index; i < params.length; i++) {
      PsiType arg = getExpectedTypeArg(context, i, expectedType, currentSubstitutor, params);
      if (arg == null) {
        arg = getExpectedTypeArg(context, index, expectedType, currentSubstitutor, params);
        if (arg != null) {
          resultSet.addElement(TailTypeDecorator.withTail(PsiTypeLookupItem.createLookupItem(arg, context), getTail(index == params.length - 1)));
        }
        return;
      }
      typeItems.add(PsiTypeLookupItem.createLookupItem(arg, context));
    }

    boolean hasParameters = ConstructorInsertHandler.hasConstructorParameters(actualClass, context);
    TypeArgsLookupElement element = new TypeArgsLookupElement(typeItems, globalTail, hasParameters);
    element.registerSingleClass(myInheritors);
    resultSet.addElement(element);
  }

  @Nullable
  private static PsiType getExpectedTypeArg(PsiElement context,
                                            int index,
                                            PsiClassType.ClassResolveResult expectedType,
                                            PsiSubstitutor currentSubstitutor, PsiTypeParameter[] params) {
    PsiClass expectedClass = expectedType.getElement();
    assert expectedClass != null;
    for (PsiTypeParameter parameter : PsiUtil.typeParametersIterable(expectedClass)) {
      final PsiType argSubstitution = expectedType.getSubstitutor().substitute(parameter);
      final PsiType paramSubstitution = currentSubstitutor.substitute(parameter);
      final PsiType substitution = JavaPsiFacade.getInstance(context.getProject()).getResolveHelper()
        .getSubstitutionForTypeParameter(params[index], paramSubstitution, argSubstitution, false, PsiUtil.getLanguageLevel(context));
      if (substitution != null && substitution != PsiType.NULL) {
        return substitution;
      }
    }
    return null;
  }

  private static void addInheritors(CompletionParameters parameters,
                                    final CompletionResultSet resultSet,
                                    final PsiClass referencedClass,
                                    final int parameterIndex) {
    final List<PsiClassType> typeList = Collections.singletonList((PsiClassType)TypeConversionUtil.typeParameterErasure(
      referencedClass.getTypeParameters()[parameterIndex]));
    JavaInheritorsGetter.processInheritors(parameters, typeList, resultSet.getPrefixMatcher(), new Consumer<PsiType>() {
      @Override
      public void consume(final PsiType type) {
        final PsiClass psiClass = PsiUtil.resolveClassInType(type);
        if (psiClass == null) return;

        resultSet.addElement(TailTypeDecorator.withTail(new JavaPsiClassReferenceElement(psiClass),
                                                        getTail(parameterIndex == referencedClass.getTypeParameters().length - 1)));
      }
    });
  }

  private static TailType getTail(boolean last) {
    return last ? new CharTailType('>') : TailType.COMMA;
  }

  @Nullable
  static Pair<PsiClass, Integer> getTypeParameterInfo(PsiElement context) {
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
    if(!(target instanceof PsiClass)) return null;

    final PsiClass referencedClass = (PsiClass)target;
    final PsiTypeParameter[] typeParameters = referencedClass.getTypeParameters();
    if(typeParameters.length <= parameterIndex) return null;

    return Pair.create(referencedClass, parameterIndex);
  }

  public static class TypeArgsLookupElement extends LookupElement {
    private String myLookupString;
    private final List<PsiTypeLookupItem> myTypeItems;
    private final TailType myGlobalTail;
    private final boolean myHasParameters;

    public TypeArgsLookupElement(List<PsiTypeLookupItem> typeItems, TailType globalTail, boolean hasParameters) {
      myTypeItems = typeItems;
      myGlobalTail = globalTail;
      myHasParameters = hasParameters;
      myLookupString = StringUtil.join(myTypeItems, new Function<PsiTypeLookupItem, String>() {
        @Override
        public String fun(PsiTypeLookupItem item) {
          return item.getLookupString();
        }
      }, ", ");
    }

    @NotNull
    @Override
    public Object getObject() {
      return myTypeItems.get(0).getObject();
    }

    public void registerSingleClass(@Nullable InheritorsHolder inheritors) {
      if (inheritors != null && myTypeItems.size() == 1) {
        PsiType type = myTypeItems.get(0).getPsiType();
        PsiClass aClass = PsiUtil.resolveClassInClassTypeOnly(type);
        if (aClass != null && !aClass.hasTypeParameters()) {
          JavaCompletionUtil.setShowFQN(myTypeItems.get(0));
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
    public void handleInsert(InsertionContext context) {
      context.getDocument().deleteString(context.getStartOffset(), context.getTailOffset());
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
