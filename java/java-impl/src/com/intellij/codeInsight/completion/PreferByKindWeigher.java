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

import com.intellij.codeInsight.AnnotationTargetUtil;
import com.intellij.codeInsight.ExceptionUtil;
import com.intellij.codeInsight.ExpectedTypeInfo;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementWeigher;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Conditions;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.patterns.ElementPattern;
import com.intellij.psi.*;
import com.intellij.psi.filters.getters.MembersGetter;
import com.intellij.psi.impl.source.tree.JavaElementType;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PropertyUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.proximity.KnownElementWeigher;
import com.intellij.util.ThreeState;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static com.intellij.patterns.PsiJavaPatterns.elementType;
import static com.intellij.patterns.PsiJavaPatterns.psiElement;
import static com.intellij.patterns.StandardPatterns.or;

/**
 * @author peter
*/
public class PreferByKindWeigher extends LookupElementWeigher {
  public static final Key<Boolean> INTRODUCED_VARIABLE = Key.create("INTRODUCED_VARIABLE");

  static final ElementPattern<PsiElement> IN_CATCH_TYPE =
    psiElement().withParent(psiElement(PsiJavaCodeReferenceElement.class).
      withParent(psiElement(PsiTypeElement.class).
        withParent(or(psiElement(PsiCatchSection.class),
                      psiElement(PsiVariable.class).withParent(PsiCatchSection.class)))));

  static final ElementPattern<PsiElement> IN_MULTI_CATCH_TYPE =
    or(psiElement().afterLeaf(psiElement().withText("|").
         withParent(PsiTypeElement.class).withSuperParent(2, PsiCatchSection.class)),
       psiElement().afterLeaf(psiElement().withText("|").
         withParent(PsiTypeElement.class).withSuperParent(2, PsiParameter.class).withSuperParent(3, PsiCatchSection.class)));

  static final ElementPattern<PsiElement> INSIDE_METHOD_THROWS_CLAUSE =
    psiElement().afterLeaf(PsiKeyword.THROWS, ",").inside(psiElement(JavaElementType.THROWS_LIST));

  static final ElementPattern<PsiElement> IN_RESOURCE =
    psiElement().withParent(or(
      psiElement(PsiJavaCodeReferenceElement.class).withParent(PsiTypeElement.class).
        withSuperParent(2, or(psiElement(PsiResourceVariable.class), psiElement(PsiResourceList.class))),
      psiElement(PsiReferenceExpression.class).withParent(PsiResourceExpression.class)));

  private final CompletionType myCompletionType;
  @NotNull
  private final PsiElement myPosition;
  private final Set<PsiField> myNonInitializedFields;
  private final Condition<PsiClass> myRequiredSuper;
  private final ExpectedTypeInfo[] myExpectedTypes;

  public PreferByKindWeigher(CompletionType completionType, @NotNull final PsiElement position, ExpectedTypeInfo[] expectedTypes) {
    super("kind");
    myCompletionType = completionType;
    myPosition = position;
    myNonInitializedFields = CheckInitialized.getNonInitializedFields(position);
    myRequiredSuper = createSuitabilityCondition(position);
    myExpectedTypes = expectedTypes;
  }

  @NotNull
  private static Condition<PsiClass> createSuitabilityCondition(final PsiElement position) {
    if (IN_CATCH_TYPE.accepts(position) || IN_MULTI_CATCH_TYPE.accepts(position)) {
      PsiTryStatement tryStatement = PsiTreeUtil.getParentOfType(position, PsiTryStatement.class);
      final List<PsiClass> thrownExceptions = ContainerUtil.newArrayList();
      if (tryStatement != null && tryStatement.getTryBlock() != null) {
        for (PsiClassType type : ExceptionUtil.getThrownExceptions(tryStatement.getTryBlock())) {
          ContainerUtil.addIfNotNull(thrownExceptions, type.resolve());
        }
      }
      if (thrownExceptions.isEmpty()) {
        ContainerUtil.addIfNotNull(thrownExceptions, 
                                   JavaPsiFacade.getInstance(position.getProject()).findClass(
                                     CommonClassNames.JAVA_LANG_THROWABLE, position.getResolveScope()));
      }
      return psiClass -> {
        for (PsiClass exception : thrownExceptions) {
          if (InheritanceUtil.isInheritorOrSelf(psiClass, exception, true)) {
            return true;
          }
        }
        return false;
      };
    }
    else if (JavaSmartCompletionContributor.AFTER_THROW_NEW.accepts(position) || INSIDE_METHOD_THROWS_CLAUSE.accepts(position)) {
      return psiClass -> InheritanceUtil.isInheritor(psiClass, CommonClassNames.JAVA_LANG_THROWABLE);
    }

    if (IN_RESOURCE.accepts(position)) {
      return psiClass -> InheritanceUtil.isInheritor(psiClass, CommonClassNames.JAVA_LANG_AUTO_CLOSEABLE);
    }

    if (psiElement().withParents(PsiJavaCodeReferenceElement.class, PsiAnnotation.class).accepts(position)) {
      final PsiAnnotation annotation = PsiTreeUtil.getParentOfType(position, PsiAnnotation.class);
      assert annotation != null;
      final PsiAnnotation.TargetType[] targets = AnnotationTargetUtil.getTargetsForLocation(annotation.getOwner());
      return psiClass -> psiClass.isAnnotationType() && AnnotationTargetUtil.findAnnotationTarget(psiClass, targets) != null;
    }

    return Conditions.alwaysFalse();
  }

  enum MyResult {
    annoMethod,
    probableKeyword,
    castVariable,
    variable,
    getter,
    qualifiedWithField,
    qualifiedWithGetter,
    superMethodParameters,
    expectedTypeConstant,
    expectedTypeArgument,
    getterQualifiedByMethod,
    accessibleFieldGetter,
    normal,
    collectionFactory,
    expectedTypeMethod,
    suitableClass,
    nonInitialized,
    classNameOrGlobalStatic,
    introducedVariable,
    unlikelyClass,
    improbableKeyword,
  }

  @NotNull
  @Override
  public MyResult weigh(@NotNull LookupElement item) {
    final Object object = item.getObject();

    if (object instanceof PsiKeyword) {
      ThreeState result = isProbableKeyword(((PsiKeyword)object).getText());
      if (result == ThreeState.YES) return MyResult.probableKeyword;
      if (result == ThreeState.NO) return MyResult.improbableKeyword;
    }

    if (item.as(CastingLookupElementDecorator.CLASS_CONDITION_KEY) != null) {
      return MyResult.castVariable;
    }

    if (object instanceof PsiLocalVariable || object instanceof PsiParameter ||
        object instanceof PsiThisExpression ||
        object instanceof PsiField && !((PsiField)object).hasModifierProperty(PsiModifier.STATIC)) {
      return MyResult.variable;
    }

    if (object instanceof String && item.getUserData(JavaCompletionUtil.SUPER_METHOD_PARAMETERS) == Boolean.TRUE) {
      return MyResult.superMethodParameters;
    }

    if (object instanceof PsiMethod) {
      PsiClass containingClass = ((PsiMethod)object).getContainingClass();
      if (containingClass != null && CommonClassNames.JAVA_UTIL_COLLECTIONS.equals(containingClass.getQualifiedName())) {
        return MyResult.collectionFactory;
      }
    }
    if (object instanceof PsiClass &&
        CommonClassNames.JAVA_LANG_STRING.equals(((PsiClass)object).getQualifiedName()) &&
        JavaSmartCompletionContributor.AFTER_NEW.accepts(myPosition)) {
      return MyResult.unlikelyClass;
    }
    Boolean expectedTypeMember = item.getUserData(MembersGetter.EXPECTED_TYPE_MEMBER);
    if (expectedTypeMember != null) {
      return expectedTypeMember ? (object instanceof PsiField ? MyResult.expectedTypeConstant : MyResult.expectedTypeMethod) : MyResult.classNameOrGlobalStatic;
    }
    if (item instanceof TypeArgumentCompletionProvider.TypeArgsLookupElement) {
      return MyResult.expectedTypeArgument;
    }
    final JavaChainLookupElement chain = item.as(JavaChainLookupElement.CLASS_CONDITION_KEY);
    if (chain != null) {
      Object qualifier = chain.getQualifier().getObject();
      if (qualifier instanceof PsiLocalVariable || qualifier instanceof PsiParameter) {
        return MyResult.variable;
      }
      if (qualifier instanceof PsiField) {
        return MyResult.qualifiedWithField;
      }
      if (isGetter(qualifier)) {
        return MyResult.qualifiedWithGetter;
      }
      if (chain.getQualifier().getUserData(INTRODUCED_VARIABLE) == Boolean.TRUE) {
        return MyResult.introducedVariable;
      }
      if (myCompletionType == CompletionType.SMART && qualifier instanceof PsiMethod && isGetter(object)) {
        return MyResult.getterQualifiedByMethod;
      }
    }

    if (myCompletionType == CompletionType.SMART) {
      if (isGetter(object)) {
        return chain == null && isAccessibleFieldGetter(object) ? MyResult.accessibleFieldGetter : MyResult.getter;
      }
      return MyResult.normal;
    }

    if (myCompletionType == CompletionType.BASIC) {
      StaticallyImportable callElement = item.as(StaticallyImportable.CLASS_CONDITION_KEY);
      if (callElement != null && callElement.canBeImported() && !callElement.willBeImported()) {
        return MyResult.classNameOrGlobalStatic;
      }

      if (object instanceof PsiMethod && PsiUtil.isAnnotationMethod((PsiElement)object)) {
        return MyResult.annoMethod;
      }

      if (object instanceof PsiClass) {
        if (myRequiredSuper.value((PsiClass)object)) {
          return MyResult.suitableClass;
        }
        return MyResult.classNameOrGlobalStatic;
      }

      if (object instanceof PsiField && myNonInitializedFields.contains(object)) {
        return MyResult.nonInitialized;
      }
    }

    return MyResult.normal;
  }

  @NotNull
  private ThreeState isProbableKeyword(String keyword) {
    if (PsiKeyword.RETURN.equals(keyword)) {
      PsiStatement parentStatement = PsiTreeUtil.getParentOfType(myPosition, PsiStatement.class);
      if (isLastStatement(parentStatement) && !isOnTopLevelInVoidMethod(parentStatement)) {
        return ThreeState.YES;
      }
    }
    if ((PsiKeyword.BREAK.equals(keyword) || PsiKeyword.CONTINUE.equals(keyword)) &&
        PsiTreeUtil.getParentOfType(myPosition, PsiLoopStatement.class) != null &&
        isLastStatement(PsiTreeUtil.getParentOfType(myPosition, PsiStatement.class))) {
      return ThreeState.YES;
    }
    if (PsiKeyword.ELSE.equals(keyword) || PsiKeyword.FINALLY.equals(keyword) || PsiKeyword.CATCH.equals(keyword)) {
      return ThreeState.YES;
    }
    if (PsiKeyword.TRUE.equals(keyword) || PsiKeyword.FALSE.equals(keyword)) {
      if (myCompletionType == CompletionType.SMART) {
        boolean inReturn = psiElement().withParents(PsiReferenceExpression.class, PsiReturnStatement.class).accepts(myPosition);
        return inReturn ? ThreeState.YES : ThreeState.UNSURE;
      } else if (Arrays.stream(myExpectedTypes).anyMatch(info -> PsiType.BOOLEAN.isConvertibleFrom(info.getDefaultType())) &&
          PsiTreeUtil.getParentOfType(myPosition, PsiIfStatement.class, true, PsiStatement.class, PsiMember.class) == null) {
        return ThreeState.YES;
      }
    }
    if (PsiKeyword.INTERFACE.equals(keyword) && psiElement().afterLeaf("@").accepts(myPosition)) {
      return ThreeState.NO;
    }
    if (PsiKeyword.NULL.equals(keyword) && psiElement().afterLeaf(psiElement().withElementType(elementType().oneOf(JavaTokenType.EQEQ, JavaTokenType.NE))).accepts(myPosition)) {
      return ThreeState.YES;
    }
    if (JavaKeywordCompletion.PRIMITIVE_TYPES.contains(keyword) || PsiKeyword.VOID.equals(keyword)) {
      boolean inCallArg = psiElement().withParents(PsiReferenceExpression.class, PsiExpressionList.class).accepts(myPosition);
      return inCallArg || isInMethodTypeArg(myPosition) ? ThreeState.NO : ThreeState.UNSURE;
    }
    return ThreeState.UNSURE;
  }

  private boolean isAccessibleFieldGetter(Object object) {
    if (!(object instanceof PsiMethod)) return false;

    PsiField field = PropertyUtil.getFieldOfGetter((PsiMethod)object);
    return field != null && PsiResolveHelper.SERVICE.getInstance(myPosition.getProject()).isAccessible(field, myPosition, null);
  }

  static boolean isInMethodTypeArg(PsiElement position) {
    return psiElement().inside(PsiReferenceParameterList.class).accepts(position);
  }

  private static boolean isOnTopLevelInVoidMethod(@NotNull PsiStatement statement) {
    if (!(statement.getParent() instanceof PsiCodeBlock)) return false;

    PsiElement parent = statement.getParent().getParent();
    if (parent instanceof PsiMethod) {
      return ((PsiMethod)parent).isConstructor() || PsiType.VOID.equals(((PsiMethod)parent).getReturnType());
    }
    if (parent instanceof PsiLambdaExpression) {
      PsiMethod method = LambdaUtil.getFunctionalInterfaceMethod(((PsiLambdaExpression)parent).getFunctionalInterfaceType());
      return method != null && PsiType.VOID.equals(method.getReturnType());
    }
    return false;
  }

  private static boolean isGetter(Object object) {
    if (!(object instanceof PsiMethod)) return false;
    
    PsiMethod method = (PsiMethod)object;
    if (!PropertyUtil.hasGetterName(method)) return false;
    
    return !KnownElementWeigher.isGetClass(method);
  }

  private static boolean isLastStatement(PsiStatement statement) {
    if (statement == null) {
      return false;
    }
    if (!(statement.getParent() instanceof PsiCodeBlock)) {
      return true;
    }
    PsiStatement[] siblings = ((PsiCodeBlock)statement.getParent()).getStatements();
    PsiStatement lastOne = siblings[siblings.length - 1];
    if (statement == lastOne) {
      return true;
    }

    // we might complete 'return' before an expression, then it's still last statement
    if (siblings.length >= 2 && statement == siblings[siblings.length - 2] && lastOne instanceof PsiExpressionStatement) {
      int start = statement.getTextRange().getStartOffset();
      int end = lastOne.getTextRange().getStartOffset();
      return !StringUtil.contains(statement.getContainingFile().getViewProvider().getContents(), start, end, '\n');
    }

    return false;
  }
}
