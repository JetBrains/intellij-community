/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

import com.intellij.codeInsight.*;
import com.intellij.codeInsight.completion.scope.JavaCompletionProcessor;
import com.intellij.codeInsight.lookup.*;
import com.intellij.openapi.util.Key;
import com.intellij.patterns.ElementPattern;
import com.intellij.patterns.PatternCondition;
import com.intellij.patterns.PsiElementPattern;
import com.intellij.patterns.PsiJavaPatterns;
import com.intellij.psi.*;
import com.intellij.psi.filters.ElementExtractorFilter;
import com.intellij.psi.filters.ElementFilter;
import com.intellij.psi.filters.GeneratorFilter;
import com.intellij.psi.filters.OrFilter;
import com.intellij.psi.filters.getters.*;
import com.intellij.psi.filters.types.AssignableFromFilter;
import com.intellij.psi.filters.types.AssignableGroupFilter;
import com.intellij.psi.filters.types.AssignableToFilter;
import com.intellij.psi.impl.source.PsiLabelReference;
import com.intellij.psi.impl.source.resolve.reference.impl.PsiMultiReference;
import com.intellij.psi.infos.CandidateInfo;
import com.intellij.psi.javadoc.PsiDocTag;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.Consumer;
import com.intellij.util.ProcessingContext;
import com.intellij.util.ReflectionUtil;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import gnu.trove.THashSet;
import gnu.trove.TObjectHashingStrategy;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static com.intellij.patterns.PlatformPatterns.psiElement;
import static com.intellij.patterns.PsiJavaPatterns.psiMethod;
import static com.intellij.patterns.StandardPatterns.*;

/**
 * @author peter
 */
public class JavaSmartCompletionContributor extends CompletionContributor {
  private static final TObjectHashingStrategy<ExpectedTypeInfo> EXPECTED_TYPE_INFO_STRATEGY = new TObjectHashingStrategy<ExpectedTypeInfo>() {
    @Override
    public int computeHashCode(final ExpectedTypeInfo object) {
      return object.getType().hashCode();
    }

    @Override
    public boolean equals(final ExpectedTypeInfo o1, final ExpectedTypeInfo o2) {
      return o1.getType().equals(o2.getType());
    }
  };

  private static final ElementExtractorFilter THROWABLES_FILTER = new ElementExtractorFilter(new AssignableFromFilter(CommonClassNames.JAVA_LANG_THROWABLE));
  @NonNls private static final String EXCEPTION_TAG = "exception";
  public static final ElementPattern<PsiElement> AFTER_NEW =
      psiElement().afterLeaf(
          psiElement().withText(PsiKeyword.NEW).andNot(
              psiElement().afterLeaf(
                  psiElement().withText(PsiKeyword.THROW))));
  static final ElementPattern<PsiElement> AFTER_THROW_NEW = psiElement().afterLeaf(psiElement().withText(PsiKeyword.NEW).afterLeaf(PsiKeyword.THROW));
  private static final OrFilter THROWABLE_TYPE_FILTER = new OrFilter(
      new GeneratorFilter(AssignableGroupFilter.class, new ThrowsListGetter()),
      new AssignableFromFilter(CommonClassNames.JAVA_LANG_THROWABLE));
  public static final ElementPattern<PsiElement> INSIDE_EXPRESSION = or(
        psiElement().withParent(PsiExpression.class).andNot(psiElement().withParent(PsiLiteralExpression.class)).andNot(psiElement().withParent(PsiMethodReferenceExpression.class)),
        psiElement().inside(PsiClassObjectAccessExpression.class),
        psiElement().inside(PsiThisExpression.class),
        psiElement().inside(PsiSuperExpression.class)
        );
  static final ElementPattern<PsiElement> INSIDE_TYPECAST_EXPRESSION = psiElement().withParent(
    psiElement(PsiReferenceExpression.class).afterLeaf(
      psiElement().withText(")").withParent(PsiTypeCastExpression.class)));
  static final PsiElementPattern.Capture<PsiElement> IN_TYPE_ARGS =
    psiElement().inside(psiElement(PsiReferenceParameterList.class));
  static final PsiElementPattern.Capture<PsiElement> LAMBDA = psiElement().with(new PatternCondition<PsiElement>("LAMBDA_CONTEXT") {
    @Override
    public boolean accepts(@NotNull PsiElement element, ProcessingContext context) {
      final PsiElement rulezzRef = element.getParent();
      return rulezzRef != null &&
             rulezzRef instanceof PsiReferenceExpression &&
             ((PsiReferenceExpression)rulezzRef).getQualifier() == null &&
             LambdaUtil.isValidLambdaContext(rulezzRef.getParent());
    }});

  static final PsiElementPattern.Capture<PsiElement> METHOD_REFERENCE = psiElement().with(new PatternCondition<PsiElement>("METHOD_REFERENCE_CONTEXT") {
    @Override
    public boolean accepts(@NotNull PsiElement element, ProcessingContext context) {
      final PsiElement rulezzRef = element.getParent();
      return rulezzRef != null &&
             LambdaUtil.isValidLambdaContext(rulezzRef.getParent());
    }});

  @Nullable
  private static ElementFilter getReferenceFilter(PsiElement element) {
    //throw new foo
    if (AFTER_THROW_NEW.accepts(element)) {
      return new ElementExtractorFilter(THROWABLE_TYPE_FILTER);
    }

    //new xxx.yyy
    if (psiElement().afterLeaf(psiElement().withText(".")).withSuperParent(2, psiElement(PsiNewExpression.class)).accepts(element)) {
      if (((PsiNewExpression)element.getParent().getParent()).getClassReference() == element.getParent()) {
        return new GeneratorFilter(AssignableGroupFilter.class, new ExpectedTypesGetter());
      }
    }

    return null;
  }



  public JavaSmartCompletionContributor() {
    extend(CompletionType.SMART, SmartCastProvider.INSIDE_TYPECAST_TYPE, new SmartCastProvider());

    extend(CompletionType.SMART, SameSignatureCallParametersProvider.IN_CALL_ARGUMENT, new SameSignatureCallParametersProvider());

    extend(CompletionType.SMART, psiElement().afterLeaf(PsiKeyword.INSTANCEOF), new CompletionProvider<CompletionParameters>() {
      @Override
      protected void addCompletions(@NotNull final CompletionParameters parameters, final ProcessingContext context, @NotNull final CompletionResultSet result) {
        final PsiElement position = parameters.getPosition();
        final PsiType[] leftTypes = InstanceOfLeftPartTypeGetter.getLeftTypes(position);
        final Set<PsiClassType> expectedClassTypes = new LinkedHashSet<PsiClassType>();
        final Set<PsiClass> parameterizedTypes = new THashSet<PsiClass>();
        for (final PsiType type : leftTypes) {
          if (type instanceof PsiClassType) {
            final PsiClassType classType = (PsiClassType)type;
            if (!classType.isRaw()) {
              ContainerUtil.addIfNotNull(classType.resolve(), parameterizedTypes);
            }

            expectedClassTypes.add(classType.rawType());
          }
        }

        JavaInheritorsGetter
          .processInheritors(parameters, expectedClassTypes, result.getPrefixMatcher(), new Consumer<PsiType>() {
            @Override
            public void consume(PsiType type) {
              final PsiClass psiClass = PsiUtil.resolveClassInType(type);
              if (psiClass == null || psiClass instanceof PsiTypeParameter) return;

              //noinspection SuspiciousMethodCalls
              if (expectedClassTypes.contains(type)) return;

              result.addElement(createInstanceofLookupElement(psiClass, parameterizedTypes));
            }
          });
      }
    });

    extend(CompletionType.SMART, psiElement(), new CompletionProvider<CompletionParameters>() {
      @Override
      protected void addCompletions(@NotNull final CompletionParameters parameters, final ProcessingContext context, @NotNull final CompletionResultSet result) {
        final PsiElement element = parameters.getPosition();
        final PsiReference reference = element.getContainingFile().findReferenceAt(parameters.getOffset());
        if (reference != null) {
          final ElementFilter filter = getReferenceFilter(element);
          if (filter != null) {
            final List<ExpectedTypeInfo> infos = Arrays.asList(getExpectedTypes(parameters));
            for (final LookupElement item : completeReference(element, reference, filter, true, false, parameters, result.getPrefixMatcher())) {
              if (item.getObject() instanceof PsiClass) {
                result.addElement(decorate(LookupElementDecorator.withInsertHandler(item, ConstructorInsertHandler.SMART_INSTANCE), infos));
              }
            }
          }
          else if (INSIDE_TYPECAST_EXPRESSION.accepts(element)) {
            for (final LookupElement item : completeReference(element, reference, new GeneratorFilter(AssignableToFilter.class, new CastTypeGetter()), false, true, parameters,
                                                              result.getPrefixMatcher())) {
              result.addElement(item);
            }
          }

        }
      }
    });

    //method throws clause
    extend(CompletionType.SMART, psiElement().inside(
      psiElement(PsiReferenceList.class).save("refList").withParent(
        psiMethod().withThrowsList(get("refList")))), new CompletionProvider<CompletionParameters>() {
      @Override
      protected void addCompletions(@NotNull CompletionParameters parameters,
                                    ProcessingContext context,
                                    @NotNull CompletionResultSet result) {
        final PsiElement element = parameters.getPosition();
        final PsiReference reference = element.getContainingFile().findReferenceAt(parameters.getOffset());
        assert reference != null;
        for (final LookupElement item : completeReference(element, reference, THROWABLES_FILTER, true, false, parameters, result.getPrefixMatcher())) {
          result.addElement(item);
        }
      }
    });

    extend(CompletionType.SMART, INSIDE_EXPRESSION, new ExpectedTypeBasedCompletionProvider() {
      @Override
      protected void addCompletions(final CompletionParameters params, final CompletionResultSet result, final Collection<ExpectedTypeInfo> _infos) {
        Consumer<LookupElement> noTypeCheck = decorateWithoutTypeCheck(result, _infos);

        THashSet<ExpectedTypeInfo> mergedInfos = new THashSet<ExpectedTypeInfo>(_infos, EXPECTED_TYPE_INFO_STRATEGY);
        List<Runnable> chainedEtc = new ArrayList<Runnable>();
        for (final ExpectedTypeInfo info : mergedInfos) {
          Runnable slowContinuation =
            ReferenceExpressionCompletionContributor.fillCompletionVariants(new JavaSmartCompletionParameters(params, info), noTypeCheck);
          ContainerUtil.addIfNotNull(chainedEtc, slowContinuation);
        }
        addExpectedTypeMembers(params, mergedInfos, true, noTypeCheck);

        PsiElement parent = params.getPosition().getParent();
        if (parent instanceof PsiReferenceExpression) {
          CollectConversion.addCollectConversion((PsiReferenceExpression)parent, mergedInfos, noTypeCheck);
        }

        for (final ExpectedTypeInfo info : mergedInfos) {
          BasicExpressionCompletionContributor.fillCompletionVariants(new JavaSmartCompletionParameters(params, info), new Consumer<LookupElement>() {
            @Override
            public void consume(LookupElement lookupElement) {
              final TypedLookupItem typed = lookupElement.as(TypedLookupItem.CLASS_CONDITION_KEY);
              if (typed != null) {
                final PsiType psiType = typed.getType();
                if (psiType != null && info.getType().isAssignableFrom(psiType)) {
                  result.addElement(decorate(lookupElement, _infos));
                }
              }
            }
          }, result.getPrefixMatcher());
          
        }

        for (Runnable runnable : chainedEtc) {
          runnable.run();
        }


        final boolean searchInheritors = params.getInvocationCount() > 1;
        if (searchInheritors) {
          addExpectedTypeMembers(params, mergedInfos, false, noTypeCheck);
        }
      }
    });

    extend(CompletionType.SMART, or(
      PsiJavaPatterns.psiElement().withParent(PsiNameValuePair.class),
      PsiJavaPatterns.psiElement().withSuperParent(2, PsiNameValuePair.class)), new CompletionProvider<CompletionParameters>() {
      @Override
      public void addCompletions(@NotNull final CompletionParameters parameters, final ProcessingContext context, @NotNull final CompletionResultSet result) {
        final PsiElement element = parameters.getPosition();
        final ElementPattern<? extends PsiElement> leftNeighbor = JavaCompletionData.AFTER_DOT;
        final boolean needQualify = leftNeighbor.accepts(element);

        for (final PsiType type : ExpectedTypesGetter.getExpectedTypes(element, false)) {
          final PsiClass psiClass = PsiUtil.resolveClassInType(type);
          if (psiClass != null && psiClass.isAnnotationType()) {
            final LookupItem item = AllClassesGetter.createLookupItem(psiClass, AnnotationInsertHandler.INSTANCE);
            if (needQualify) JavaCompletionUtil.qualify(item);
            result.addElement(item);
          }
        }

      }
    });

    extend(CompletionType.SMART, psiElement().inside(
      psiElement(PsiDocTag.class).withName(
        string().oneOf(PsiKeyword.THROWS, EXCEPTION_TAG))), new CompletionProvider<CompletionParameters>() {
      @Override
      public void addCompletions(@NotNull final CompletionParameters parameters, final ProcessingContext context, @NotNull final CompletionResultSet result) {
        final PsiElement element = parameters.getPosition();
        final Set<PsiClass> throwsSet = new HashSet<PsiClass>();
        final PsiMethod method = PsiTreeUtil.getContextOfType(element, PsiMethod.class, true);
        if(method != null){
          for (PsiClassType ref : method.getThrowsList().getReferencedTypes()) {
            final PsiClass exception = ref.resolve();
            if (exception != null && throwsSet.add(exception)) {
              result.addElement(TailTypeDecorator.withTail(new JavaPsiClassReferenceElement(exception), TailType.HUMBLE_SPACE_BEFORE_WORD));
            }
          }
        }

      }
    });

    final Key<PsiTryStatement> tryKey = Key.create("try");
    extend(CompletionType.SMART, psiElement().insideStarting(
      psiElement(PsiTypeElement.class).withParent(
        psiElement(PsiCatchSection.class).withParent(
          psiElement(PsiTryStatement.class).save(tryKey)))), new CompletionProvider<CompletionParameters>() {
      @Override
      protected void addCompletions(@NotNull final CompletionParameters parameters, final ProcessingContext context, @NotNull final CompletionResultSet result) {
        final PsiCodeBlock tryBlock = context.get(tryKey).getTryBlock();
        if (tryBlock == null) return;

        final InheritorsHolder holder = new InheritorsHolder(parameters.getPosition(), result);

        for (final PsiClassType type : ExceptionUtil.getThrownExceptions(tryBlock.getStatements())) {
          PsiClass typeClass = type.resolve();
          if (typeClass != null) {
            result.addElement(createCatchTypeVariant(tryBlock, type));
            holder.registerClass(typeClass);
          }
        }

        final Collection<PsiClassType> expectedClassTypes = ContainerUtil.createMaybeSingletonList(JavaPsiFacade.getElementFactory(
          tryBlock.getProject()).createTypeByFQClassName(CommonClassNames.JAVA_LANG_THROWABLE));
        JavaInheritorsGetter.processInheritors(parameters, expectedClassTypes, result.getPrefixMatcher(), new Consumer<PsiType>() {
          @Override
          public void consume(PsiType type) {
            final PsiClass psiClass = type instanceof PsiClassType ? ((PsiClassType)type).resolve() : null;
            if (psiClass == null || psiClass instanceof PsiTypeParameter) return;

            if (!holder.alreadyProcessed(psiClass)) {
              result.addElement(createCatchTypeVariant(tryBlock, (PsiClassType)type));
            }
          }
        });
      }

      @NotNull
      private TailTypeDecorator<LookupItem> createCatchTypeVariant(PsiCodeBlock tryBlock, PsiClassType type) {
        return TailTypeDecorator.withTail(PsiTypeLookupItem.createLookupItem(type, tryBlock).setInsertHandler(new DefaultInsertHandler()),
                                          TailType.HUMBLE_SPACE_BEFORE_WORD);
      }
    });

    extend(CompletionType.SMART, IN_TYPE_ARGS, new TypeArgumentCompletionProvider(true, null));


    extend(CompletionType.SMART, AFTER_NEW, new JavaInheritorsGetter(ConstructorInsertHandler.SMART_INSTANCE));

    extend(CompletionType.SMART, psiElement().afterLeaf(PsiKeyword.BREAK, PsiKeyword.CONTINUE), new CompletionProvider<CompletionParameters>() {
      @Override
      protected void addCompletions(@NotNull CompletionParameters parameters,
                                    ProcessingContext context,
                                    @NotNull CompletionResultSet result) {
        PsiReference ref = parameters.getPosition().getContainingFile().findReferenceAt(parameters.getOffset());
        if (ref instanceof PsiLabelReference) {
          JavaCompletionContributor.processLabelReference(result, (PsiLabelReference)ref);
        }
      }
    });

    extend(CompletionType.SMART, LAMBDA, new FunctionalExpressionCompletionProvider());
    extend(CompletionType.SMART, METHOD_REFERENCE, new MethodReferenceCompletionProvider());
  }

  @NotNull
  static Consumer<LookupElement> decorateWithoutTypeCheck(final CompletionResultSet result, final Collection<ExpectedTypeInfo> infos) {
    return new Consumer<LookupElement>() {
      @Override
      public void consume(final LookupElement lookupElement) {
        result.addElement(decorate(lookupElement, infos));
      }
    };
  }

  private static void addExpectedTypeMembers(CompletionParameters params,
                                             THashSet<ExpectedTypeInfo> mergedInfos,
                                             boolean quick,
                                             Consumer<LookupElement> consumer) {
    PsiElement position = params.getPosition();
    if (!JavaCompletionData.AFTER_DOT.accepts(position)) {
      for (ExpectedTypeInfo info : mergedInfos) {
        new JavaMembersGetter(info.getType(), params).addMembers(!quick, consumer);
        if (!info.getDefaultType().equals(info.getType())) {
          new JavaMembersGetter(info.getDefaultType(), params).addMembers(!quick, consumer);
        }
      }
    }
  }

  @Override
  public void fillCompletionVariants(@NotNull CompletionParameters parameters, @NotNull CompletionResultSet result) {
    super.fillCompletionVariants(parameters, JavaCompletionSorting.addJavaSorting(parameters, result));
  }

  public static SmartCompletionDecorator decorate(LookupElement lookupElement, Collection<ExpectedTypeInfo> infos) {
    LookupItem item = lookupElement.as(LookupItem.CLASS_CONDITION_KEY);
    if (item != null && item.getInsertHandler() == null) {
      item.setInsertHandler(DefaultInsertHandler.NO_TAIL_HANDLER);
    }

    return new SmartCompletionDecorator(lookupElement, infos);
  }

  private static LookupElement createInstanceofLookupElement(PsiClass psiClass, Set<PsiClass> toWildcardInheritors) {
    final PsiTypeParameter[] typeParameters = psiClass.getTypeParameters();
    if (typeParameters.length > 0) {
      for (final PsiClass parameterizedType : toWildcardInheritors) {
        if (psiClass.isInheritor(parameterizedType, true)) {
          PsiSubstitutor substitutor = PsiSubstitutor.EMPTY;
          final PsiWildcardType wildcard = PsiWildcardType.createUnbounded(psiClass.getManager());
          for (final PsiTypeParameter typeParameter : typeParameters) {
            substitutor = substitutor.put(typeParameter, wildcard);
          }
          final PsiElementFactory factory = JavaPsiFacade.getElementFactory(psiClass.getProject());
          return PsiTypeLookupItem.createLookupItem(factory.createType(psiClass, substitutor), psiClass);
        }
      }
    }


    return new JavaPsiClassReferenceElement(psiClass);
  }


  @NotNull
  public static ExpectedTypeInfo[] getExpectedTypes(final CompletionParameters parameters) {
    return getExpectedTypes(parameters, parameters.getCompletionType() == CompletionType.SMART);
  }

  @NotNull
  public static ExpectedTypeInfo[] getExpectedTypes(final CompletionParameters parameters, boolean voidable) {
    final PsiElement position = parameters.getPosition();
    if (psiElement().withParent(psiElement(PsiReferenceExpression.class).withParent(PsiThrowStatement.class)).accepts(position)) {
      final PsiElementFactory factory = JavaPsiFacade.getInstance(position.getProject()).getElementFactory();
      final PsiClassType classType = factory
          .createTypeByFQClassName(CommonClassNames.JAVA_LANG_RUNTIME_EXCEPTION, position.getResolveScope());
      final List<ExpectedTypeInfo> result = new SmartList<ExpectedTypeInfo>();
      result.add(new ExpectedTypeInfoImpl(classType, ExpectedTypeInfo.TYPE_OR_SUBTYPE, classType, TailType.SEMICOLON, null, ExpectedTypeInfoImpl.NULL));
      final PsiMethod method = PsiTreeUtil.getContextOfType(position, PsiMethod.class, true);
      if (method != null) {
        for (final PsiClassType type : method.getThrowsList().getReferencedTypes()) {
          result.add(new ExpectedTypeInfoImpl(type, ExpectedTypeInfo.TYPE_OR_SUBTYPE, type, TailType.SEMICOLON, null, ExpectedTypeInfoImpl.NULL));
        }
      }
      return result.toArray(new ExpectedTypeInfo[result.size()]);
    }

    PsiExpression expression = PsiTreeUtil.getContextOfType(position, PsiExpression.class, true);
    if (expression == null) return ExpectedTypeInfo.EMPTY_ARRAY;

    return ExpectedTypesProvider.getExpectedTypes(expression, true, voidable, false);
  }

  static Set<LookupElement> completeReference(final PsiElement element,
                                              PsiReference reference,
                                              final ElementFilter filter,
                                              final boolean acceptClasses,
                                              final boolean acceptMembers,
                                              CompletionParameters parameters, final PrefixMatcher matcher) {
    if (reference instanceof PsiMultiReference) {
      reference = ContainerUtil.findInstance(((PsiMultiReference) reference).getReferences(), PsiJavaReference.class);
    }

    if (reference instanceof PsiJavaReference) {
      final PsiJavaReference javaReference = (PsiJavaReference)reference;

      ElementFilter checkClass = new ElementFilter() {
        @Override
        public boolean isAcceptable(Object element, PsiElement context) {
          return filter.isAcceptable(element, context);
        }

        @Override
        public boolean isClassAcceptable(Class hintClass) {
          if (ReflectionUtil.isAssignable(PsiClass.class, hintClass)) {
            return acceptClasses;
          }

          if (ReflectionUtil.isAssignable(PsiVariable.class, hintClass) ||
              ReflectionUtil.isAssignable(PsiMethod.class, hintClass) ||
              ReflectionUtil.isAssignable(CandidateInfo.class, hintClass)) {
            return acceptMembers;
          }
          return false;
        }
      };
      JavaCompletionProcessor.Options options =
        JavaCompletionProcessor.Options.DEFAULT_OPTIONS.withFilterStaticAfterInstance(parameters.getInvocationCount() <= 1);
      return JavaCompletionUtil.processJavaReference(element, javaReference, checkClass, options, matcher, parameters);
    }

    return Collections.emptySet();
  }

  @Override
  public void beforeCompletion(@NotNull CompletionInitializationContext context) {
    if (context.getCompletionType() != CompletionType.SMART) {
      return;
    }

    if (!context.getEditor().getSelectionModel().hasSelection()) {
      final PsiFile file = context.getFile();
      PsiElement element = file.findElementAt(context.getStartOffset());
      if (element instanceof PsiIdentifier) {
        element = element.getParent();
        while (element instanceof PsiJavaCodeReferenceElement || element instanceof PsiCall ||
               element instanceof PsiThisExpression || element instanceof PsiSuperExpression ||
               element instanceof PsiTypeElement ||
               element instanceof PsiClassObjectAccessExpression) {
          int newEnd = element.getTextRange().getEndOffset();
          if (element instanceof PsiMethodCallExpression) {
            newEnd = ((PsiMethodCallExpression)element).getMethodExpression().getTextRange().getEndOffset();
          }
          else if (element instanceof PsiNewExpression) {
            final PsiJavaCodeReferenceElement classReference = ((PsiNewExpression)element).getClassReference();
            if (classReference != null) {
              newEnd = classReference.getTextRange().getEndOffset();
            }
          }
          context.setReplacementOffset(newEnd);
          element = element.getParent();
        }
      }
    }

    PsiElement lastElement = context.getFile().findElementAt(context.getStartOffset() - 1);
    if (lastElement != null && lastElement.getText().equals("(")) {
      final PsiElement parent = lastElement.getParent();
      if (parent instanceof PsiTypeCastExpression) {
        context.setDummyIdentifier("");
        return;
      }
      if (parent instanceof PsiParenthesizedExpression) {
        context.setDummyIdentifier(CompletionUtil.DUMMY_IDENTIFIER_TRIMMED + ")" + CompletionUtil.DUMMY_IDENTIFIER_TRIMMED + " "); // to handle type cast
        return;
      }
    }
    context.setDummyIdentifier(CompletionUtil.DUMMY_IDENTIFIER_TRIMMED);
  }
}
