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
package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.CharTailType;
import com.intellij.codeInsight.TailType;
import com.intellij.codeInsight.lookup.*;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.filters.getters.ExpectedTypesGetter;
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

/**
* @author peter
*/
class TypeArgumentCompletionProvider extends CompletionProvider<CompletionParameters> {

  protected void addCompletions(@NotNull final CompletionParameters parameters, final ProcessingContext processingContext, @NotNull final CompletionResultSet resultSet) {
    final PsiElement context = parameters.getPosition();

    final Pair<PsiClass, Integer> pair = getTypeParameterInfo(context);
    if (pair == null) return;

    final PsiType[] psiTypes = ExpectedTypesGetter.getExpectedTypes(context, false);
    if (psiTypes.length > 0) {
      for (PsiType type : psiTypes) {
        if (type instanceof PsiClassType) {
          fillExpectedTypeArgs(resultSet, context, pair.first, pair.second, ((PsiClassType)type).resolveGenerics());
        }
      }
    } else {
      addInheritors(parameters, resultSet, pair.first, pair.second);
    }
  }

  private static void fillExpectedTypeArgs(CompletionResultSet resultSet,
                                           PsiElement context,
                                           final PsiClass actualClass,
                                           final int index,
                                           PsiClassType.ClassResolveResult expectedType) {
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

    resultSet.addElement(LookupElementBuilder.create(typeItems.get(0).getObject(), typeItems.get(0).getLookupString()).setRenderer(new LookupElementRenderer<LookupElement>() {
      @Override
      public void renderElement(LookupElement element, LookupElementPresentation presentation) {
        typeItems.get(0).renderElement(presentation);
        presentation.setItemText(StringUtil.join(typeItems, new Function<PsiTypeLookupItem, String>() {
          @Override
          public String fun(PsiTypeLookupItem item) {
            return item.getLookupString();
          }
        }, ", "));
        presentation.setTailText(null);
        presentation.setTypeText(null);
      }
    }).setInsertHandler(new InsertHandler<LookupElement>() {
      @Override
      public void handleInsert(InsertionContext context, LookupElement item) {
        context.getDocument().deleteString(context.getStartOffset(), context.getTailOffset());
        for (int i = 0; i < typeItems.size(); i++) {
          CompletionUtil.emulateInsertion(context, context.getTailOffset(), typeItems.get(i));
          getTail(i == typeItems.size() - 1).processTail(context.getEditor(), context.getTailOffset());
        }
        context.setAddCompletionChar(false);
      }
    }));
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
}
