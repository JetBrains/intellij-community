/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
import com.intellij.codeInsight.ExceptionUtil;
import com.intellij.codeInsight.lookup.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import com.intellij.patterns.ElementPattern;
import com.intellij.patterns.PsiJavaPatterns;
import com.intellij.psi.*;
import com.intellij.psi.filters.ElementExtractorFilter;
import com.intellij.psi.filters.ElementFilter;
import com.intellij.psi.filters.GeneratorFilter;
import com.intellij.psi.filters.OrFilter;
import com.intellij.psi.filters.getters.CastTypeGetter;
import com.intellij.psi.filters.getters.ExpectedTypesGetter;
import com.intellij.psi.filters.getters.InstanceOfLeftPartTypeGetter;
import com.intellij.psi.filters.getters.ThrowsListGetter;
import com.intellij.psi.filters.types.AssignableFromFilter;
import com.intellij.psi.filters.types.AssignableGroupFilter;
import com.intellij.psi.filters.types.AssignableToFilter;
import com.intellij.psi.impl.source.resolve.reference.impl.PsiMultiReference;
import com.intellij.psi.infos.CandidateInfo;
import com.intellij.psi.javadoc.PsiDocTag;
import com.intellij.psi.statistics.JavaStatisticsManager;
import com.intellij.psi.statistics.StatisticsInfo;
import com.intellij.psi.statistics.StatisticsManager;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.*;
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
    public int computeHashCode(final ExpectedTypeInfo object) {
      return object.getType().hashCode();
    }

    public boolean equals(final ExpectedTypeInfo o1, final ExpectedTypeInfo o2) {
      return o1.getType().equals(o2.getType());
    }
  };

  private static final ElementExtractorFilter THROWABLES_FILTER = new ElementExtractorFilter(new AssignableFromFilter(CommonClassNames.JAVA_LANG_THROWABLE));
  @NonNls private static final String EXCEPTION_TAG = "exception";
  static final ElementPattern<PsiElement> AFTER_NEW =
      psiElement().afterLeaf(
          psiElement().withText(PsiKeyword.NEW).andNot(
              psiElement().afterLeaf(
                  psiElement().withText(PsiKeyword.THROW))));
  static final ElementPattern<PsiElement> AFTER_THROW_NEW =
      psiElement().afterLeaf(
          psiElement().withText(PsiKeyword.NEW).afterLeaf(
              psiElement().withText(PsiKeyword.THROW)));
  private static final OrFilter THROWABLE_TYPE_FILTER = new OrFilter(
      new GeneratorFilter(AssignableGroupFilter.class, new ThrowsListGetter()),
      new AssignableFromFilter("java.lang.RuntimeException"));
  public static final ElementPattern<PsiElement> INSIDE_EXPRESSION = or(
        psiElement().withParent(PsiExpression.class).andNot(psiElement().withParent(PsiLiteralExpression.class)),
        psiElement().inside(PsiClassObjectAccessExpression.class),
        psiElement().inside(PsiThisExpression.class),
        psiElement().inside(PsiSuperExpression.class)
        );
  static final ElementPattern<PsiElement> INSIDE_TYPECAST_EXPRESSION = psiElement().withParent(
    psiElement(PsiReferenceExpression.class).afterLeaf(
      psiElement().withText(")").withParent(PsiTypeCastExpression.class)));

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

    extend(CompletionType.SMART,
           psiElement().beforeLeaf(psiElement(JavaTokenType.RPARENTH)).afterLeaf("(").withParent(
             psiElement(PsiReferenceExpression.class).withParent(
               psiElement(PsiExpressionList.class).withParent(PsiMethodCallExpression.class))), new SameSignatureCallParametersProvider());

    extend(CompletionType.SMART, psiElement().afterLeaf(PsiKeyword.INSTANCEOF), new CompletionProvider<CompletionParameters>(false) {
      protected void addCompletions(@NotNull final CompletionParameters parameters, final ProcessingContext context, @NotNull final CompletionResultSet result) {
        final PsiElement position = parameters.getPosition();
        final PsiType[] leftTypes = ApplicationManager.getApplication().runReadAction(new Computable<PsiType[]>() {
          public PsiType[] compute() {
            return InstanceOfLeftPartTypeGetter.getLeftTypes(position);
          }
        });
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

        processInheritors(parameters, position, position.getContainingFile(), expectedClassTypes, new Consumer<PsiType>() {
          public void consume(PsiType type) {
            final PsiClass psiClass = PsiUtil.resolveClassInType(type);
            if (psiClass == null) return;

            if (expectedClassTypes.contains(type)) return;

            result.addElement(createInstanceofLookupElement(psiClass, parameterizedTypes));
          }
        }, result.getPrefixMatcher());
      }
    });

    extend(CompletionType.SMART, psiElement(), new CompletionProvider<CompletionParameters>() {
      protected void addCompletions(@NotNull final CompletionParameters parameters, final ProcessingContext context, @NotNull final CompletionResultSet result) {
        final PsiElement element = parameters.getPosition();
        final PsiReference reference = element.getContainingFile().findReferenceAt(parameters.getOffset());
        if (reference != null) {
          final ElementFilter filter = getReferenceFilter(element);
          if (filter != null) {
            final List<ExpectedTypeInfo> infos = Arrays.asList(getExpectedTypes(parameters));
            for (final LookupElement item : completeReference(element, reference, filter, true, parameters)) {
              if (item.getObject() instanceof PsiClass) {
                result.addElement(decorate(LookupElementDecorator.withInsertHandler((LookupItem)item, ConstructorInsertHandler.INSTANCE), infos));
              }
            }
          }
          else if (INSIDE_TYPECAST_EXPRESSION.accepts(element)) {
            for (final LookupElement item : completeReference(element, reference, new GeneratorFilter(AssignableToFilter.class, new CastTypeGetter()), false, parameters)) {
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
        for (final LookupElement item : completeReference(element, reference, THROWABLES_FILTER, true, parameters)) {
          result.addElement(item);
        }
      }
    });

    extend(CompletionType.SMART, INSIDE_EXPRESSION, new ExpectedTypeBasedCompletionProvider() {
      protected void addCompletions(final CompletionParameters params, final CompletionResultSet result, final Collection<ExpectedTypeInfo> _infos) {
        final Set<ExpectedTypeInfo> infos = ApplicationManager.getApplication().runReadAction(new Computable<Set<ExpectedTypeInfo>>() {
          public Set<ExpectedTypeInfo> compute() {
            return new THashSet<ExpectedTypeInfo>(_infos, EXPECTED_TYPE_INFO_STRATEGY);
          }
        });
        for (final ExpectedTypeInfo info : infos) {
          final JavaSmartCompletionParameters parameters = new JavaSmartCompletionParameters(params, info);
          final PsiType type = info.getType();

          final CompletionService service = CompletionService.getCompletionService();
          new BasicExpressionCompletionContributor().fillCompletionVariants(parameters, service.createResultSet(parameters, new Consumer<LookupElement>() {
            public void consume(final LookupElement lookupElement) {
              final TypedLookupItem typed = JavaCompletionUtil.typedFrom(lookupElement);
              if (typed != null) {
                final PsiType psiType = typed.getType();
                if (psiType != null && type.isAssignableFrom(psiType)) {
                  result.addElement(decorate(lookupElement, _infos));
                }
              }
            }
          }, JavaSmartCompletionContributor.this));
          ReferenceExpressionCompletionContributor.fillCompletionVariants(parameters, service.createResultSet(parameters, new Consumer<LookupElement>() {
            public void consume(final LookupElement lookupElement) {
              result.addElement(decorate(lookupElement, _infos));
            }
          }, JavaSmartCompletionContributor.this));

        }
      }
    });

    extend(CompletionType.SMART, or(
        PsiJavaPatterns.psiElement().withParent(PsiNameValuePair.class),
        PsiJavaPatterns.psiElement().withSuperParent(2, PsiNameValuePair.class)), new CompletionProvider<CompletionParameters>() {
      public void addCompletions(@NotNull final CompletionParameters parameters, final ProcessingContext context, @NotNull final CompletionResultSet result) {
        final PsiElement element = parameters.getPosition();
        final ElementPattern<? extends PsiElement> leftNeighbor = PsiJavaPatterns.psiElement().afterLeaf(PsiJavaPatterns.psiElement().withText("."));
        final boolean needQualify = leftNeighbor.accepts(element);

        for (final PsiType type : ExpectedTypesGetter.getExpectedTypes(element, false)) {
          final PsiClass psiClass = PsiUtil.resolveClassInType(type);
          if (psiClass != null && psiClass.isAnnotationType()) {
            final LookupItem item = new JavaPsiClassReferenceElement(psiClass).setTailType(TailType.NONE);
            if (needQualify) JavaCompletionUtil.qualify(item);
            result.addElement(item);
          }
        }

      }
    });

    extend(CompletionType.SMART, psiElement().inside(
        psiElement(PsiDocTag.class).withName(
            string().oneOf(PsiKeyword.THROWS, EXCEPTION_TAG))), new CompletionProvider<CompletionParameters>() {
      public void addCompletions(@NotNull final CompletionParameters parameters, final ProcessingContext context, @NotNull final CompletionResultSet result) {
        final PsiElement element = parameters.getPosition();
        final Set<PsiClass> throwsSet = new HashSet<PsiClass>();
        final PsiMethod method = PsiTreeUtil.getContextOfType(element, PsiMethod.class, true);
        if(method != null){
          for (PsiClassType ref : method.getThrowsList().getReferencedTypes()) {
            final PsiClass exception = ref.resolve();
            if (exception != null && throwsSet.add(exception)) {
              result.addElement(TailTypeDecorator.withTail(new JavaPsiClassReferenceElement(exception).setInsertHandler(new DefaultInsertHandler()), TailType.SPACE));
            }
          }
        }

      }
    });

    final Key<PsiTryStatement> tryKey = Key.create("try");
    extend(CompletionType.SMART, psiElement().afterLeaf(
        psiElement().withText("("))
        .withSuperParent(3, psiElement(PsiCatchSection.class).withParent(
        psiElement(PsiTryStatement.class).save(tryKey))), new CompletionProvider<CompletionParameters>() {
      protected void addCompletions(@NotNull final CompletionParameters parameters, final ProcessingContext context, @NotNull final CompletionResultSet result) {
        final PsiCodeBlock tryBlock = context.get(tryKey).getTryBlock();
        if (tryBlock == null) return;

        for (final PsiClassType type : ExceptionUtil.getThrownExceptions(tryBlock.getStatements())) {
          result.addElement(TailTypeDecorator.withTail(PsiTypeLookupItem.createLookupItem(type, tryBlock).setInsertHandler(new DefaultInsertHandler()), TailType.SPACE));
        }
      }
    });

    extend(CompletionType.SMART, psiElement().inside(psiElement(PsiReferenceParameterList.class)),
           new CompletionProvider<CompletionParameters>() {

             protected void addCompletions(@NotNull final CompletionParameters parameters, final ProcessingContext processingContext, @NotNull final CompletionResultSet resultSet) {
               final PsiElement context = parameters.getPosition();

               final Pair<PsiClass, Integer> pair = getTypeParameterInfo(context);
               if (pair == null) return;

               final PsiClass referencedClass = pair.first;
               final int parameterIndex = pair.second.intValue();
               final PsiTypeParameter[] typeParameters = referencedClass.getTypeParameters();
               final PsiTypeParameter targetParameter = typeParameters[parameterIndex];

               boolean isLast = parameterIndex == typeParameters.length - 1;
               final TailType tail = isLast ? new CharTailType('>') : TailType.COMMA;

               boolean hasExpected = ApplicationManager.getApplication().runReadAction(new Computable<Boolean>() {
                 public Boolean compute() {
                   PsiResolveHelper resolveHelper = JavaPsiFacade.getInstance(context.getProject()).getResolveHelper();
                   final PsiType[] psiTypes = ExpectedTypesGetter.getExpectedTypes(context, false);
                   if (psiTypes.length == 0) return false;

                   for (PsiType type : psiTypes) {
                     if (!(type instanceof PsiClassType)) continue;
                     final PsiClassType.ClassResolveResult result = ((PsiClassType)type).resolveGenerics();
                     final PsiClass typeClass = result.getElement();
                     final PsiSubstitutor substitutor = result.getSubstitutor();

                     if (!InheritanceUtil.isInheritorOrSelf(referencedClass, typeClass, true)) continue;

                     final PsiSubstitutor currentSubstitutor =
                         TypeConversionUtil.getClassSubstitutor(typeClass, referencedClass, PsiSubstitutor.EMPTY);
                     for (PsiTypeParameter parameter : PsiUtil.typeParametersIterable(typeClass)) {
                       final PsiType argSubstitution = substitutor.substitute(parameter);
                       final PsiType paramSubstitution = currentSubstitutor.substitute(parameter);
                       final PsiType substitution = resolveHelper
                           .getSubstitutionForTypeParameter(targetParameter, paramSubstitution, argSubstitution, false,
                                                            PsiUtil.getLanguageLevel(context));
                       if (substitution != null && substitution != PsiType.NULL) {
                         final LookupItem item = PsiTypeLookupItem.createLookupItem(substitution, context);
                         resultSet.addElement(TailTypeDecorator.withTail(item.setInsertHandler(new DefaultInsertHandler()), tail));
                       }
                     }
                   }
                   return true;
                 }
               }).booleanValue();


               if (!hasExpected) {
                 final List<PsiClassType> typeList = Collections.singletonList((PsiClassType)TypeConversionUtil.typeParameterErasure(targetParameter));
                 processInheritors(parameters, context, parameters.getOriginalFile(), typeList, new Consumer<PsiType>() {
                   public void consume(final PsiType type) {
                     final PsiClass psiClass = PsiUtil.resolveClassInType(type);
                     if (psiClass == null) return;

                     resultSet.addElement(TailTypeDecorator.withTail(new JavaPsiClassReferenceElement(psiClass).setInsertHandler(new DefaultInsertHandler()), tail));
                   }
                 }, resultSet.getPrefixMatcher());

               }
             }
           });


    extend(CompletionType.SMART, AFTER_NEW, new CompletionProvider<CompletionParameters>(false) {
      public void addCompletions(@NotNull final CompletionParameters parameters, final ProcessingContext matchingContext, @NotNull final CompletionResultSet result) {
        final PsiElement identifierCopy = parameters.getPosition();
        final PsiFile file = parameters.getOriginalFile();

        final List<PsiClassType> expectedClassTypes = new SmartList<PsiClassType>();
        final List<PsiArrayType> expectedArrayTypes = new SmartList<PsiArrayType>();
        final List<ExpectedTypeInfo> infos = new SmartList<ExpectedTypeInfo>();

        ApplicationManager.getApplication().runReadAction(new Runnable() {
          public void run() {
            ContainerUtil.addAll(infos, getExpectedTypes(parameters));
            for (PsiType type : ExpectedTypesGetter.getExpectedTypes(identifierCopy, true)) {
              if (type instanceof PsiClassType) {
                final PsiClassType classType = (PsiClassType)type;
                if (classType.resolve() != null) {
                  expectedClassTypes.add(classType);
                }
              }
              else if (type instanceof PsiArrayType) {
                expectedArrayTypes.add((PsiArrayType)type);
              }
            }
          }
        });


        for (final PsiArrayType type : expectedArrayTypes) {
          ApplicationManager.getApplication().runReadAction(new Runnable() {
            public void run() {
              final LookupItem item = PsiTypeLookupItem.createLookupItem(JavaCompletionUtil.eliminateWildcards(type), identifierCopy);
              if (item.getObject() instanceof PsiClass) {
                JavaCompletionUtil.setShowFQN(item);
              }
              item.setInsertHandler(new DefaultInsertHandler()); //braces & shortening
              result.addElement(decorate(item, infos));
            }
          });
        }
        
        processInheritors(parameters, identifierCopy, file, expectedClassTypes, new Consumer<PsiType>() {
          public void consume(final PsiType type) {
            addExpectedType(result, type, parameters, infos);
          }
        }, result.getPrefixMatcher());
      }
    });
  }

  public static SmartCompletionDecorator decorate(LookupElement lookupElement, Collection<ExpectedTypeInfo> infos) {
    if (lookupElement instanceof LookupItem) {
      final LookupItem lookupItem = (LookupItem)lookupElement;
      if (lookupItem.getInsertHandler() == null) {
        lookupItem.setInsertHandler(DefaultInsertHandler.NO_TAIL_HANDLER);
      }
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

  @Nullable
  public static Pair<PsiClass, Integer> getTypeParameterInfo(PsiElement context) {
    final PsiReferenceParameterList parameterList = PsiTreeUtil.getContextOfType(context, PsiReferenceParameterList.class, true);
    if (parameterList == null) return null;

    final PsiJavaCodeReferenceElement referenceElement = (PsiJavaCodeReferenceElement)parameterList.getParent();
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


  public static void processInheritors(final CompletionParameters parameters, final PsiElement identifierCopy, final PsiFile file, final Collection<PsiClassType> expectedClassTypes,
                                        final Consumer<PsiType> consumer, final PrefixMatcher matcher) {
    //quick
    for (final PsiClassType type : expectedClassTypes) {
      ApplicationManager.getApplication().runReadAction(new Runnable() {
        public void run() {
          consumer.consume(type);

          final PsiClassType.ClassResolveResult baseResult = JavaCompletionUtil.originalize(type).resolveGenerics();
          final PsiClass baseClass = baseResult.getElement();
          if (baseClass == null) return;

          final PsiSubstitutor baseSubstitutor = baseResult.getSubstitutor();

          final THashSet<PsiType> statVariants = new THashSet<PsiType>();
          final Processor<PsiClass> processor = CodeInsightUtil
              .createInheritorsProcessor(parameters.getPosition(), type, 0, false, statVariants, baseClass, baseSubstitutor);
          final StatisticsInfo[] statisticsInfos =
              StatisticsManager.getInstance().getAllValues(JavaStatisticsManager.getAfterNewKey(type));
          for (final StatisticsInfo statisticsInfo : statisticsInfos) {
            final String value = statisticsInfo.getValue();
            if (value.startsWith(JavaStatisticsManager.CLASS_PREFIX)) {
              final String qname = value.substring(JavaStatisticsManager.CLASS_PREFIX.length());
              final PsiClass psiClass = JavaPsiFacade.getInstance(file.getProject()).findClass(qname, file.getResolveScope());
              if (psiClass != null && !PsiTreeUtil.isAncestor(file, psiClass, true) && !processor.process(psiClass)) break;
            }
          }

          for (final PsiType variant : statVariants) {
            consumer.consume(variant);
          }
        }
      });
    }

    //long
    final Condition<String> shortNameCondition = new Condition<String>() {
      public boolean value(String s) {
        return matcher.prefixMatches(s);
      }
    };
    for (final PsiClassType type : expectedClassTypes) {
      final boolean shouldSearchForInheritors = ApplicationManager.getApplication().runReadAction(new Computable<Boolean>() {
        public Boolean compute() {
          final PsiClass psiClass = type.resolve();
          return psiClass != null && psiClass.isValid() && !psiClass.hasModifierProperty(PsiModifier.FINAL);
        }
      }).booleanValue();
      if (shouldSearchForInheritors) {
        final Set<PsiType> psiTypes = CodeInsightUtil.addSubtypes(type, identifierCopy, false, shortNameCondition);
        for (final PsiType psiType : psiTypes) {
          ApplicationManager.getApplication().runReadAction(new Runnable() {
            public void run() {
              if (psiType.isValid()) {
                consumer.consume(psiType);
              }
            }
          });
        }
      }
    }
  }

  public static ExpectedTypeInfo[] getExpectedTypes(final CompletionParameters parameters) {
    final PsiElement position = parameters.getPosition();
    if (psiElement().withParent(psiElement(PsiReferenceExpression.class).withParent(PsiThrowStatement.class)).accepts(position)) {
      final PsiElementFactory factory = JavaPsiFacade.getInstance(position.getProject()).getElementFactory();
      final PsiClassType classType = factory
          .createTypeByFQClassName(CommonClassNames.JAVA_LANG_RUNTIME_EXCEPTION, position.getResolveScope());
      final List<ExpectedTypeInfo> result = new SmartList<ExpectedTypeInfo>();
      result.add(new ExpectedTypeInfoImpl(classType, ExpectedTypeInfo.TYPE_OR_SUBTYPE, 0, classType, TailType.SEMICOLON));
      final PsiMethod method = PsiTreeUtil.getContextOfType(position, PsiMethod.class, true);
      if (method != null) {
        for (final PsiClassType type : method.getThrowsList().getReferencedTypes()) {
          result.add(new ExpectedTypeInfoImpl(type, ExpectedTypeInfo.TYPE_OR_SUBTYPE, 0, type, TailType.SEMICOLON));
        }
      }
      return result.toArray(new ExpectedTypeInfo[result.size()]);
    }

    PsiExpression expression = PsiTreeUtil.getContextOfType(position, PsiExpression.class, true);
    if (expression == null) return ExpectedTypeInfo.EMPTY_ARRAY;

    return ExpectedTypesProvider.getExpectedTypes(expression, true, parameters.getCompletionType() == CompletionType.SMART, false);
  }

  private static void addExpectedType(final CompletionResultSet result, final PsiType type, final CompletionParameters parameters, Collection<ExpectedTypeInfo> infos) {
    if (!JavaCompletionUtil.hasAccessibleConstructor(type)) return;

    final PsiClass psiClass = PsiUtil.resolveClassInType(type);
    if (psiClass == null) return;

    final PsiClass parentClass = psiClass.getContainingClass();
    if (parentClass != null && !psiClass.hasModifierProperty(PsiModifier.STATIC) &&
        !PsiTreeUtil.isAncestor(parentClass, parameters.getPosition(), false) &&
        !(parentClass.getContainingFile().equals(parameters.getOriginalFile()) &&
          parentClass.getTextRange().contains(parameters.getOffset()))) {
      return;
    }

    final LookupItem item = PsiTypeLookupItem.createLookupItem(JavaCompletionUtil.eliminateWildcards(type), parameters.getPosition());
    JavaCompletionUtil.setShowFQN(item);

    if (psiClass.isInterface() || psiClass.hasModifierProperty(PsiModifier.ABSTRACT)) {
      item.setAutoCompletionPolicy(AutoCompletionPolicy.NEVER_AUTOCOMPLETE);
      item.setAttribute(LookupItem.INDICATE_ANONYMOUS, "");
    }

    result.addElement(decorate(type instanceof PsiClassType ? LookupElementDecorator.withInsertHandler(item, ConstructorInsertHandler.INSTANCE) : item, infos));
  }

  static Set<LookupElement> completeReference(final PsiElement element, PsiReference reference, final ElementFilter filter, final boolean acceptClasses, CompletionParameters parameters) {
    if (reference instanceof PsiMultiReference) {
      reference = ContainerUtil.findInstance(((PsiMultiReference) reference).getReferences(), PsiJavaReference.class);
    }

    if (reference instanceof PsiJavaReference) {
      final PsiJavaReference javaReference = (PsiJavaReference)reference;

      return JavaCompletionUtil.processJavaReference(element, javaReference, new ElementFilter() {
        public boolean isAcceptable(Object element, PsiElement context) {
          return filter.isAcceptable(element, context);
        }

        public boolean isClassAcceptable(Class hintClass) {
          if (acceptClasses) {
            return ReflectionCache.isAssignable(PsiClass.class, hintClass);
          }

          return ReflectionCache.isAssignable(PsiVariable.class, hintClass) ||
                 ReflectionCache.isAssignable(PsiMethod.class, hintClass) ||
                 ReflectionCache.isAssignable(CandidateInfo.class, hintClass);
        }
      }, true, null, parameters);
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
          context.getOffsetMap().addOffset(CompletionInitializationContext.IDENTIFIER_END_OFFSET, newEnd);
          element = element.getParent();
        }
      }
    }

    PsiElement lastElement = context.getFile().findElementAt(context.getStartOffset() - 1);
    if (lastElement != null && lastElement.getText().equals("(")) {
      final PsiElement parent = lastElement.getParent();
      if (parent instanceof PsiTypeCastExpression) {
        context.setFileCopyPatcher(new DummyIdentifierPatcher(""));
        return;
      }
      if (parent instanceof PsiParenthesizedExpression) {
        context.setFileCopyPatcher(new DummyIdentifierPatcher("xxx)yyy ")); // to handle type cast
        return;
      }
    }
    context.setFileCopyPatcher(new DummyIdentifierPatcher("xxx"));
  }

}
