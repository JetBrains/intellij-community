// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.AnnotationTargetUtil;
import com.intellij.codeInsight.ExceptionUtil;
import com.intellij.codeInsight.ExpectedTypeInfo;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementWeigher;
import com.intellij.codeInsight.lookup.TypedLookupItem;
import com.intellij.java.syntax.parser.JavaKeywords;
import com.intellij.openapi.util.Key;
import com.intellij.patterns.ElementPattern;
import com.intellij.psi.*;
import com.intellij.psi.filters.getters.MembersGetter;
import com.intellij.psi.impl.source.tree.JavaElementType;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.util.*;
import com.intellij.psi.util.proximity.KnownElementWeigher;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.ThreeState;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.JBIterable;
import com.intellij.util.text.CharArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.*;
import java.util.function.Function;

import static com.intellij.patterns.PsiJavaPatterns.elementType;
import static com.intellij.patterns.PsiJavaPatterns.psiElement;
import static com.intellij.patterns.StandardPatterns.or;

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
    psiElement().afterLeaf(JavaKeywords.THROWS, ",").inside(psiElement(JavaElementType.THROWS_LIST));

  static final ElementPattern<PsiElement> IN_RESOURCE =
    psiElement().withParent(or(
      psiElement(PsiJavaCodeReferenceElement.class).withParent(PsiTypeElement.class).
        withSuperParent(2, or(psiElement(PsiResourceVariable.class), psiElement(PsiResourceList.class))),
      psiElement(PsiReferenceExpression.class).withParent(PsiResourceExpression.class)));
  static final Function<PsiClass, MyResult>
    PREFER_THROWABLE = psiClass -> preferClassIf(InheritanceUtil.isInheritor(psiClass, CommonClassNames.JAVA_LANG_THROWABLE));

  private final CompletionType myCompletionType;
  private final PsiElement myPosition;
  private final Set<PsiField> myNonInitializedFields;
  private final Function<PsiClass, MyResult> myClassSuitability;
  private final ExpectedTypeInfo[] myExpectedTypes;

  public PreferByKindWeigher(CompletionType completionType, final PsiElement position, ExpectedTypeInfo[] expectedTypes) {
    super("kind");
    myCompletionType = completionType;
    myPosition = position;
    myNonInitializedFields = CheckInitialized.getNonInitializedFields(position);
    myClassSuitability = createSuitabilityCondition(position);
    myExpectedTypes = expectedTypes;
  }

  private static @NotNull Function<PsiClass, MyResult> createSuitabilityCondition(final PsiElement position) {
    if (isExceptionPosition(position)) {
      PsiElement container = PsiTreeUtil.getParentOfType(position, PsiTryStatement.class, PsiMethod.class);
      List<PsiClass> thrownExceptions = new ArrayList<>();
      if (container != null) {
        PsiElement block = container instanceof PsiTryStatement ? ((PsiTryStatement)container).getTryBlock() : container;
        if (block != null) {
          for (PsiClassType type : ExceptionUtil.getThrownExceptions(block)) {
            ContainerUtil.addIfNotNull(thrownExceptions, type.resolve());
          }
        }
      }
      return psiClass -> {
        if (ContainerUtil.exists(thrownExceptions, t -> InheritanceUtil.isInheritorOrSelf(psiClass, t, true))) {
          return MyResult.verySuitableClass;
        }
        return PREFER_THROWABLE.apply(psiClass);
      };
    }
    else if (JavaSmartCompletionContributor.AFTER_THROW_NEW.accepts(position)) {
      return PREFER_THROWABLE;
    }

    if (IN_RESOURCE.accepts(position)) {
      return psiClass -> preferClassIf(InheritanceUtil.isInheritor(psiClass, CommonClassNames.JAVA_LANG_AUTO_CLOSEABLE));
    }

    PsiElement parent = position.getParent();
    if (parent instanceof PsiJavaCodeReferenceElement) {
      PsiElement refParent = parent.getParent();
      if (refParent instanceof PsiAnnotation) {
        PsiAnnotation.TargetType[] targets = AnnotationTargetUtil.getTargetsForLocation(((PsiAnnotation)refParent).getOwner());
        return psiClass -> preferClassIf(psiClass.isAnnotationType() && AnnotationTargetUtil.findAnnotationTarget(psiClass, targets) != null);
      }
      if (refParent instanceof PsiTypeElement) {
        List<PsiClass> bounds = getTypeBounds((PsiTypeElement)refParent);
        return psiClass -> preferClassIf(ContainerUtil.exists(bounds, bound -> InheritanceUtil.isInheritorOrSelf(psiClass, bound, true)));
      }
    }

    return aClass -> MyResult.classNameOrGlobalStatic;
  }

  static @Unmodifiable List<PsiClass> getTypeBounds(PsiTypeElement typeElement) {
    PsiElement typeParent = typeElement.getParent();
    if (typeParent instanceof PsiReferenceParameterList) {
      int index = Arrays.asList(((PsiReferenceParameterList)typeParent).getTypeParameterElements()).indexOf(typeElement);
      PsiElement listParent = typeParent.getParent();
      if (index >= 0 && listParent instanceof PsiJavaCodeReferenceElement) {
        PsiElement target = ((PsiJavaCodeReferenceElement)listParent).resolve();
        if (target instanceof PsiClass) {
          PsiTypeParameter[] typeParameters = ((PsiClass)target).getTypeParameters();
          if (index < typeParameters.length) {
            return ContainerUtil.mapNotNull(typeParameters[index].getExtendsListTypes(), PsiUtil::resolveClassInType);
          }
        }
      }
    }
    return Collections.emptyList();
  }

  static boolean isExceptionPosition(PsiElement position) {
    return IN_CATCH_TYPE.accepts(position) || IN_MULTI_CATCH_TYPE.accepts(position) ||
           INSIDE_METHOD_THROWS_CLAUSE.accepts(position) ||
           JavaDocCompletionContributor.THROWS_TAG_EXCEPTION.accepts(position);
  }

  private static @NotNull MyResult preferClassIf(boolean condition) {
    return condition ? MyResult.suitableClass : MyResult.classNameOrGlobalStatic;
  }

  static boolean isEnumClass(@NotNull ExpectedTypeInfo info) {
    PsiClass expectedClass = PsiUtil.resolveClassInType(info.getType());
    return expectedClass != null && expectedClass.isEnum();
  }

  enum MyResult {
    annoMethod,
    probableKeyword,
    castVariable,
    expectedTypeVariable,
    lambda,
    likelyMethodRef,
    methodRef,
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
    basicChain,
    collectionFactory,
    expectedTypeMethod,
    verySuitableClass,
    suitableClass,
    nonInitialized,
    classNameOrGlobalStatic,
    introducedVariable,
    unlikelyItem,
    improbableKeyword,
  }

  @Override
  public @NotNull MyResult weigh(@NotNull LookupElement item) {
    final Object object = item.getObject();

    if (object instanceof PsiKeyword) {
      ThreeState result = isProbableKeyword(((PsiKeyword)object).getText());
      if (result == ThreeState.YES) return MyResult.probableKeyword;
      if (result == ThreeState.NO) return MyResult.improbableKeyword;
    }

    if (item.as(CastingLookupElementDecorator.CLASS_CONDITION_KEY) != null) {
      return MyResult.castVariable;
    }

    JavaChainLookupElement chain = item.as(JavaChainLookupElement.CLASS_CONDITION_KEY);
    if (chain != null) {
      if (myCompletionType == CompletionType.BASIC) {
        return MyResult.basicChain;
      }
      Object qualifier = chain.getQualifier().getObject();
      if (qualifier instanceof PsiVariable && PsiUtil.isJvmLocalVariable((PsiVariable)qualifier)) {
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

    if (object instanceof PsiLocalVariable || object instanceof PsiParameter ||
        object instanceof PsiThisExpression ||
        object instanceof PsiField && !((PsiField)object).hasModifierProperty(PsiModifier.STATIC)) {
      if (PsiTreeUtil.getParentOfType(myPosition, PsiDocComment.class) == null) {
        return isComparisonWithItself((PsiElement)object) ? MyResult.unlikelyItem :
               isExpectedTypeItem(item) ? MyResult.expectedTypeVariable :
               MyResult.variable;
      }
    }

    if (object instanceof String && item.getUserData(JavaCompletionUtil.SUPER_METHOD_PARAMETERS) == Boolean.TRUE) {
      return MyResult.superMethodParameters;
    }

    if (item.getUserData(FunctionalExpressionCompletionProvider.LAMBDA_ITEM) != null) {
      return MyResult.lambda;
    }
    Boolean methodRefPreference = item.getUserData(FunctionalExpressionCompletionProvider.METHOD_REF_PREFERRED);
    if (methodRefPreference != null) {
      return methodRefPreference ? MyResult.likelyMethodRef : MyResult.methodRef;
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
      return MyResult.unlikelyItem;
    }
    Boolean expectedTypeMember = item.getUserData(MembersGetter.EXPECTED_TYPE_MEMBER);
    if (expectedTypeMember != null) {
      return expectedTypeMember ? (object instanceof PsiField ? MyResult.expectedTypeConstant : MyResult.expectedTypeMethod) : MyResult.classNameOrGlobalStatic;
    }
    if (item instanceof TypeArgumentCompletionProvider.TypeArgsLookupElement) {
      return MyResult.expectedTypeArgument;
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

      JavaConstructorCallElement constructorCall = item.as(JavaConstructorCallElement.class);
      if (constructorCall != null) {
        return myClassSuitability.apply(constructorCall.getConstructedClass());
      }

      if (object instanceof PsiMethod && PsiUtil.isAnnotationMethod((PsiElement)object)) {
        return MyResult.annoMethod;
      }

      if (object instanceof PsiClass) {
        return myClassSuitability.apply((PsiClass)object);
      }

      if (object instanceof PsiField && myNonInitializedFields.contains(object)) {
        return MyResult.nonInitialized;
      }

      if (object instanceof PsiPackage) {
        String name = ((PsiPackage)object).getName();
        if (name != null && !name.isEmpty() && Character.isUpperCase(name.charAt(0))) {
          // Disprefer package names starting with uppercase letter, as they could pop up before classes
          return MyResult.unlikelyItem;
        }
      }
    }

    return MyResult.normal;
  }

  private boolean isComparisonWithItself(PsiElement itemObject) {
    if (isComparisonRhs(myPosition) && myPosition.getParent().getParent() instanceof PsiPolyadicExpression) {
      PsiExpression[] operands = ((PsiPolyadicExpression)myPosition.getParent().getParent()).getOperands();
      if (operands[0] instanceof PsiReferenceExpression && ((PsiReferenceExpression)operands[0]).resolve() == itemObject) {
        return true;
      }
    }
    return false;
  }

  private boolean isExpectedTypeItem(@NotNull LookupElement item) {
    TypedLookupItem typed = item.as(TypedLookupItem.CLASS_CONDITION_KEY);
    PsiType itemType = typed == null ? null : typed.getType();
    return itemType != null &&
           Arrays.stream(myExpectedTypes)
             .map(ExpectedTypeInfo::getType)
             .anyMatch(type -> !isTooGeneric(type) && type.isAssignableFrom(itemType));
  }

  private static boolean isTooGeneric(PsiType type) {
    PsiType erasure = TypeConversionUtil.erasure(type);
    return erasure == null || erasure.equalsToText(CommonClassNames.JAVA_LANG_OBJECT);
  }

  private @NotNull ThreeState isProbableKeyword(String keyword) {
    PsiStatement parentStatement = PsiTreeUtil.getParentOfType(myPosition, PsiStatement.class);
    if (JavaKeywords.RETURN.equals(keyword)) {
      if (isLastStatement(parentStatement) &&
          !isOnTopLevelInVoidMethod(parentStatement) &&
          !(parentStatement.getParent() instanceof PsiLoopStatement)) {
        return ThreeState.YES;
      }
    }
    if ((JavaKeywords.BREAK.equals(keyword) || JavaKeywords.CONTINUE.equals(keyword)) &&
        PsiTreeUtil.getParentOfType(myPosition, PsiLoopStatement.class) != null &&
        isLastStatement(parentStatement)) {
      return ThreeState.YES;
    }
    if (JavaKeywords.ELSE.equals(keyword) || JavaKeywords.FINALLY.equals(keyword) || JavaKeywords.CATCH.equals(keyword)) {
      return ThreeState.YES;
    }
    if (JavaKeywords.TRUE.equals(keyword) || JavaKeywords.FALSE.equals(keyword)) {
      if (myCompletionType == CompletionType.SMART) {
        boolean inReturn = psiElement().withParents(PsiReferenceExpression.class, PsiReturnStatement.class).accepts(myPosition);
        return inReturn ? ThreeState.YES : ThreeState.UNSURE;
      } else if (ContainerUtil.exists(myExpectedTypes, info -> PsiTypes.booleanType().isAssignableFrom(info.getDefaultType())) &&
                 !(myPosition.getParent() instanceof PsiIfStatement)) {
        return ThreeState.YES;
      }
    }
    if (JavaKeywords.INTERFACE.equals(keyword) && psiElement().afterLeaf("@").accepts(myPosition)) {
      return ThreeState.NO;
    }
    if (JavaKeywords.NULL.equals(keyword) && isComparisonRhs(myPosition)) {
      boolean expectsNotNull = ContainerUtil.exists(myExpectedTypes, PreferByKindWeigher::isEnumClass);
      return expectsNotNull ? ThreeState.NO : ThreeState.YES;
    }
    if (JavaKeywordCompletion.PRIMITIVE_TYPES.contains(keyword) || JavaKeywords.VOID.equals(keyword)) {
      boolean inCallArg = psiElement().withParents(PsiReferenceExpression.class, PsiExpressionList.class).accepts(myPosition);
      return inCallArg || isInMethodTypeArg(myPosition) ? ThreeState.NO : ThreeState.UNSURE;
    }
    if (JavaKeywords.FINAL.equals(keyword) && isBeforeVariableOnSameLine(parentStatement)) {
      return ThreeState.YES;
    }
    return ThreeState.UNSURE;
  }

  static boolean isComparisonRhs(PsiElement position) {
    return psiElement().afterLeaf(psiElement().withElementType(elementType().oneOf(JavaTokenType.EQEQ, JavaTokenType.NE))).accepts(position);
  }

  private boolean isBeforeVariableOnSameLine(@Nullable PsiStatement parentStatement) {
    return parentStatement != null &&
           parentStatement.getTextRange().getStartOffset() == myPosition.getTextRange().getStartOffset() &&
           JBIterable.generate(parentStatement, PsiElement::getNextSibling)
                     .takeWhile(e -> !e.textContains('\n'))
                     .skip(1)
                     .filter(PsiStatement.class)
                     .isNotEmpty();
  }

  private boolean isAccessibleFieldGetter(Object object) {
    if (!(object instanceof PsiMethod)) return false;

    PsiField field = PropertyUtil.getFieldOfGetter((PsiMethod)object);
    return field != null && PsiResolveHelper.getInstance(myPosition.getProject()).isAccessible(field, myPosition, null);
  }

  static boolean isInMethodTypeArg(PsiElement position) {
    return psiElement().inside(PsiReferenceParameterList.class).accepts(position);
  }

  private static boolean isOnTopLevelInVoidMethod(@NotNull PsiStatement statement) {
    if (!(statement.getParent() instanceof PsiCodeBlock)) return false;

    PsiElement parent = statement.getParent().getParent();
    if (parent instanceof PsiMethod) {
      return ((PsiMethod)parent).isConstructor() || PsiTypes.voidType().equals(((PsiMethod)parent).getReturnType());
    }
    if (parent instanceof PsiLambdaExpression) {
      PsiMethod method = LambdaUtil.getFunctionalInterfaceMethod(((PsiLambdaExpression)parent).getFunctionalInterfaceType());
      return method != null && PsiTypes.voidType().equals(method.getReturnType());
    }
    return false;
  }

  private static boolean isGetter(Object object) {
    if (!(object instanceof PsiMethod method)) return false;

    if (!PropertyUtilBase.hasGetterName(method)) return false;
    if (method.hasTypeParameters()) return false;

    return !KnownElementWeigher.isGetClass(method);
  }

  private boolean isLastStatement(PsiStatement statement) {
    if (statement == null) {
      return false;
    }
    if (!(statement.getParent() instanceof PsiCodeBlock codeBlock)) {
      return true;
    }
    PsiStatement[] siblings = codeBlock.getStatements();
    PsiStatement lastOne = siblings[siblings.length - 1];
    if (statement == lastOne) {
      return true;
    }

    int posEnd = myPosition.getTextRange().getEndOffset();
    int blockContentEnd = lastOne.getTextRange().getEndOffset();
    CharSequence fileText = myPosition.getContainingFile().getViewProvider().getContents();
    String afterPos = fileText.subSequence(posEnd, blockContentEnd).toString();
    int nonSpace = CharArrayUtil.shiftForward(afterPos, 0, " \t");
    if (nonSpace < afterPos.length() && afterPos.charAt(nonSpace) == '\n') return false;

    try {
      PsiStatement asStatement = JavaPsiFacade.getElementFactory(myPosition.getProject()).createStatementFromText(afterPos.trim(), null);
      return asStatement instanceof PsiExpressionStatement;
    }
    catch (IncorrectOperationException e) {
      return false;
    }
  }
}
