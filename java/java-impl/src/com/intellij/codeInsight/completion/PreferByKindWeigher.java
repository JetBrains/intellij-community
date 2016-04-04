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
import com.intellij.codeInsight.completion.scope.JavaCompletionProcessor;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementWeigher;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Conditions;
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
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Set;

import static com.intellij.patterns.PsiJavaPatterns.elementType;
import static com.intellij.patterns.PsiJavaPatterns.psiElement;
import static com.intellij.patterns.StandardPatterns.or;

/**
 * @author peter
*/
public class PreferByKindWeigher extends LookupElementWeigher {
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
  private final PsiElement myPosition;
  private final Set<PsiField> myNonInitializedFields;
  private final Condition<PsiClass> myRequiredSuper;

  public PreferByKindWeigher(CompletionType completionType, final PsiElement position) {
    super("kind");
    myCompletionType = completionType;
    myPosition = position;
    myNonInitializedFields = JavaCompletionProcessor.getNonInitializedFields(position);
    myRequiredSuper = createSuitabilityCondition(position);
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
      return new Condition<PsiClass>() {
        @Override
        public boolean value(PsiClass psiClass) {
          for (PsiClass exception : thrownExceptions) {
            if (InheritanceUtil.isInheritorOrSelf(psiClass, exception, true)) {
              return true;
            }
          }
          return false;
        }
      };
    }
    else if (JavaSmartCompletionContributor.AFTER_THROW_NEW.accepts(position) || INSIDE_METHOD_THROWS_CLAUSE.accepts(position)) {
      return new Condition<PsiClass>() {
        @Override
        public boolean value(PsiClass psiClass) {
          return InheritanceUtil.isInheritor(psiClass, CommonClassNames.JAVA_LANG_THROWABLE);
        }
      };
    }

    if (IN_RESOURCE.accepts(position)) {
      return new Condition<PsiClass>() {
        @Override
        public boolean value(PsiClass psiClass) {
          return InheritanceUtil.isInheritor(psiClass, CommonClassNames.JAVA_LANG_AUTO_CLOSEABLE);
        }
      };
    }

    if (psiElement().withParents(PsiJavaCodeReferenceElement.class, PsiAnnotation.class).accepts(position)) {
      final PsiAnnotation annotation = PsiTreeUtil.getParentOfType(position, PsiAnnotation.class);
      assert annotation != null;
      final PsiAnnotation.TargetType[] targets = AnnotationTargetUtil.getTargetsForLocation(annotation.getOwner());
      return new Condition<PsiClass>() {
        @Override
        public boolean value(PsiClass psiClass) {
          return psiClass.isAnnotationType() && AnnotationTargetUtil.findAnnotationTarget(psiClass, targets) != null;
        }
      };
    }

    return Conditions.alwaysFalse();
  }

  enum MyResult {
    annoMethod,
    probableKeyword,
    castVariable,
    localOrParameter,
    qualifiedWithField,
    qualifiedWithGetter,
    superMethodParameters,
    field,
    expectedTypeConstant,
    expectedTypeArgument,
    getter,
    normal,
    collectionFactory,
    expectedTypeMethod,
    suitableClass,
    improbableKeyword,
    nonInitialized,
    classLiteral,
    classNameOrGlobalStatic,
  }

  @NotNull
  @Override
  public MyResult weigh(@NotNull LookupElement item) {
    final Object object = item.getObject();

    if (object instanceof PsiKeyword) {
      String keyword = ((PsiKeyword)object).getText();
      if (PsiKeyword.RETURN.equals(keyword) && isLastStatement(PsiTreeUtil.getParentOfType(myPosition, PsiStatement.class))) {
        return MyResult.probableKeyword;
      }
      if (PsiKeyword.ELSE.equals(keyword) || PsiKeyword.FINALLY.equals(keyword)) {
        return MyResult.probableKeyword;
      }
      if ((PsiKeyword.TRUE.equals(keyword) || PsiKeyword.FALSE.equals(keyword)) && myCompletionType == CompletionType.SMART) {
        boolean inReturn = psiElement().withParents(PsiReferenceExpression.class, PsiReturnStatement.class).accepts(myPosition);
        return inReturn ? MyResult.probableKeyword : MyResult.normal;
      }
      if (PsiKeyword.INTERFACE.equals(keyword) && psiElement().afterLeaf("@").accepts(myPosition)) {
        return MyResult.improbableKeyword;
      }
      if (PsiKeyword.NULL.equals(keyword) && psiElement().afterLeaf(psiElement().withElementType(elementType().oneOf(JavaTokenType.EQEQ, JavaTokenType.NE))).accepts(myPosition)) {
        return MyResult.probableKeyword;
      }
    }

    if (item.as(CastingLookupElementDecorator.CLASS_CONDITION_KEY) != null) {
      return MyResult.castVariable;
    }

    if (object instanceof PsiLocalVariable || object instanceof PsiParameter || object instanceof PsiThisExpression) {
      return MyResult.localOrParameter;
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
        return MyResult.localOrParameter;
      }
      if (qualifier instanceof PsiField) {
        return MyResult.qualifiedWithField;
      }
      if (isGetter(qualifier)) {
        return MyResult.qualifiedWithGetter;
      }
    }


    if (myCompletionType == CompletionType.SMART) {
      if (object instanceof PsiField) return MyResult.field;
      if (isGetter(object)) return MyResult.getter;

      return MyResult.normal;
    }

    if (myCompletionType == CompletionType.BASIC) {
      StaticallyImportable callElement = item.as(StaticallyImportable.CLASS_CONDITION_KEY);
      if (callElement != null && callElement.canBeImported() && !callElement.willBeImported()) {
        return MyResult.classNameOrGlobalStatic;
      }

      if (object instanceof PsiKeyword && PsiKeyword.CLASS.equals(item.getLookupString())) {
        return MyResult.classLiteral;
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

  private static boolean isGetter(Object object) {
    if (!(object instanceof PsiMethod)) return false;
    
    PsiMethod method = (PsiMethod)object;
    if (!PropertyUtil.hasGetterName(method)) return false;
    
    return !KnownElementWeigher.isGetClass(method);
  }

  private static boolean isLastStatement(PsiStatement statement) {
    if (statement == null || !(statement.getParent() instanceof PsiCodeBlock)) {
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
