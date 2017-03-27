/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
import com.intellij.patterns.ElementPattern;
import com.intellij.psi.*;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.ProcessingContext;
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
class TypeArgumentCompletionProvider extends CompletionProvider<CompletionParameters> {
  static final ElementPattern<PsiElement> IN_TYPE_ARGS = psiElement().inside(PsiReferenceParameterList.class);
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.completion.TypeArgumentCompletionProvider");
  private final boolean mySmart;
  @Nullable private final JavaCompletionSession mySession;

  TypeArgumentCompletionProvider(boolean smart, @Nullable JavaCompletionSession session) {
    mySmart = smart;
    mySession = session;
  }

  @Override
  protected void addCompletions(@NotNull final CompletionParameters parameters, final ProcessingContext processingContext, @NotNull final CompletionResultSet resultSet) {
    final PsiElement context = parameters.getPosition();

    final Pair<PsiTypeParameterListOwner, Integer> pair = getTypeParameterInfo(context);
    if (pair == null) return;

    PsiTypeParameterListOwner paramOwner = pair.first;
    if (suggestByExpectedType(resultSet, context, paramOwner, pair.second)) return;

    if (mySmart && paramOwner instanceof PsiClass) {
      addInheritors(parameters, resultSet, (PsiClass)paramOwner, pair.second);
    }
  }

  private boolean suggestByExpectedType(@NotNull CompletionResultSet resultSet,
                                        PsiElement context,
                                        PsiTypeParameterListOwner paramOwner, int index) {
    PsiExpression expression = PsiTreeUtil.getContextOfType(context, PsiExpression.class, true);
    ExpectedTypeInfo[] types = ExpectedTypesProvider.getExpectedTypes(expression, true, false, false);
    if (expression == null || types.length == 0) return false;

    for (ExpectedTypeInfo info : types) {
      PsiType type = info.getType();
      if (type instanceof PsiClassType && !type.equals(expression.getType())) {
        JBIterable<PsiTypeParameter> remainingParams = JBIterable.of(paramOwner.getTypeParameters()).skip(index);
        List<PsiType> expectedArgs = getExpectedTypeArgs(context, paramOwner, remainingParams, (PsiClassType)type);
        createLookupItems(resultSet, context, info, expectedArgs, paramOwner);
      }
    }
    return true;
  }

  @NotNull
  private static List<PsiType> getExpectedTypeArgs(PsiElement context,
                                                   PsiTypeParameterListOwner paramOwner,
                                                   JBIterable<PsiTypeParameter> typeParams, PsiClassType expectedType) {
    if (paramOwner instanceof PsiClass) {
      PsiClassType.ClassResolveResult resolve = expectedType.resolveGenerics();
      final PsiClass expectedClass = resolve.getElement();

      if (!InheritanceUtil.isInheritorOrSelf((PsiClass)paramOwner, expectedClass, true)) {
        return typeParams.map(p -> (PsiType)null).toList();
      }

      PsiSubstitutor substitutor = TypeConversionUtil.getClassSubstitutor(expectedClass, (PsiClass)paramOwner, PsiSubstitutor.EMPTY);
      assert substitutor != null;

      return typeParams.map(p -> getExpectedTypeArg(context, resolve, substitutor, p)).toList();
    }

    PsiSubstitutor substitutor = SmartCompletionDecorator.calculateMethodReturnTypeSubstitutor((PsiMethod)paramOwner, expectedType);
    return typeParams.map(substitutor::substitute).toList();
  }

  private void createLookupItems(CompletionResultSet resultSet,
                                 PsiElement context,
                                 ExpectedTypeInfo info,
                                 List<PsiType> expectedArgs, PsiTypeParameterListOwner paramOwner) {
    if (expectedArgs.contains(null)) {
      PsiType arg = expectedArgs.get(0);
      if (arg != null) {
        resultSet.addElement(TailTypeDecorator.withTail(PsiTypeLookupItem.createLookupItem(arg, context), getTail(expectedArgs.size() == 1)));
      }
    } else {
      fillAllArgs(resultSet, context, info, expectedArgs, paramOwner);
    }
  }

  private void fillAllArgs(CompletionResultSet resultSet,
                           PsiElement context,
                           ExpectedTypeInfo info,
                           List<PsiType> expectedArgs,
                           PsiTypeParameterListOwner paramOwner) {
    List<PsiTypeLookupItem> typeItems = ContainerUtil.map(expectedArgs, arg -> PsiTypeLookupItem.createLookupItem(arg, context));
    TailType globalTail = mySmart ? info.getTailType() : TailType.NONE;
    TypeArgsLookupElement element = new TypeArgsLookupElement(typeItems, globalTail, hasParameters(paramOwner, context));
    element.registerSingleClass(mySession);
    resultSet.addElement(element);
  }

  private static boolean hasParameters(PsiTypeParameterListOwner paramOwner, PsiElement context) {
    return paramOwner instanceof PsiClass && ConstructorInsertHandler.hasConstructorParameters((PsiClass)paramOwner, context);
  }

  @Nullable
  private static PsiType getExpectedTypeArg(PsiElement context,
                                            PsiClassType.ClassResolveResult expectedType,
                                            PsiSubstitutor currentSubstitutor, PsiTypeParameter typeParam) {
    PsiClass expectedClass = expectedType.getElement();
    assert expectedClass != null;
    for (PsiTypeParameter parameter : PsiUtil.typeParametersIterable(expectedClass)) {
      final PsiType argSubstitution = expectedType.getSubstitutor().substitute(parameter);
      final PsiType paramSubstitution = currentSubstitutor.substitute(parameter);
      final PsiType substitution = JavaPsiFacade.getInstance(context.getProject()).getResolveHelper()
        .getSubstitutionForTypeParameter(typeParam, paramSubstitution, argSubstitution, false, PsiUtil.getLanguageLevel(context));
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
    JavaInheritorsGetter.processInheritors(parameters, typeList, resultSet.getPrefixMatcher(), type -> {
      final PsiClass psiClass = PsiUtil.resolveClassInType(type);
      if (psiClass == null) return;

      resultSet.addElement(TailTypeDecorator.withTail(new JavaPsiClassReferenceElement(psiClass),
                                                      getTail(parameterIndex == referencedClass.getTypeParameters().length - 1)));
    });
  }

  private static TailType getTail(boolean last) {
    return last ? new CharTailType('>') : TailType.COMMA;
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
    private final List<PsiTypeLookupItem> myTypeItems;
    private final TailType myGlobalTail;
    private final boolean myHasParameters;

    public TypeArgsLookupElement(List<PsiTypeLookupItem> typeItems, TailType globalTail, boolean hasParameters) {
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
    public void handleInsert(InsertionContext context) {
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
