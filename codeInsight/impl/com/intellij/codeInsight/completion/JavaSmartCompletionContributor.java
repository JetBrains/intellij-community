/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.*;
import com.intellij.codeInsight.lookup.AutoCompletionPolicy;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupItem;
import com.intellij.codeInsight.lookup.LookupItemUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Computable;
import com.intellij.patterns.ElementPattern;
import com.intellij.patterns.PlatformPatterns;
import static com.intellij.patterns.PlatformPatterns.psiElement;
import com.intellij.patterns.PsiJavaPatterns;
import static com.intellij.patterns.PsiJavaPatterns.psiMethod;
import static com.intellij.patterns.StandardPatterns.*;
import com.intellij.psi.*;
import com.intellij.psi.filters.ElementExtractorFilter;
import com.intellij.psi.filters.ElementFilter;
import com.intellij.psi.filters.GeneratorFilter;
import com.intellij.psi.filters.OrFilter;
import com.intellij.psi.filters.getters.*;
import com.intellij.psi.filters.types.AssignableFromFilter;
import com.intellij.psi.filters.types.AssignableGroupFilter;
import com.intellij.psi.filters.types.AssignableToFilter;
import com.intellij.psi.filters.types.ReturnTypeFilter;
import com.intellij.psi.infos.CandidateInfo;
import com.intellij.psi.javadoc.PsiDocTag;
import com.intellij.psi.statistics.JavaStatisticsManager;
import com.intellij.psi.statistics.StatisticsInfo;
import com.intellij.psi.statistics.StatisticsManager;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.Consumer;
import com.intellij.util.ProcessingContext;
import com.intellij.util.Processor;
import com.intellij.util.SmartList;
import gnu.trove.THashSet;
import gnu.trove.TObjectHashingStrategy;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * @author peter
 */
public class JavaSmartCompletionContributor extends CompletionContributor {
  private static final JavaAwareCompletionData SMART_DATA = new JavaAwareCompletionData();
  private static final ElementExtractorFilter THROWABLES_FILTER = new ElementExtractorFilter(new AssignableFromFilter(CommonClassNames.JAVA_LANG_THROWABLE));
  @NonNls private static final String EXCEPTION_TAG = "exception";
  private static final ElementPattern<PsiElement> AFTER_NEW =
      psiElement().afterLeaf(
          psiElement().withText(PsiKeyword.NEW).andNot(
              psiElement().afterLeaf(
                  psiElement().withText(PsiKeyword.THROW))));
  private static final ElementPattern<PsiElement> AFTER_THROW_NEW =
      psiElement().afterLeaf(
          psiElement().withText(PsiKeyword.NEW).afterLeaf(
              psiElement().withText(PsiKeyword.THROW)));
  private static final OrFilter THROWABLE_TYPE_FILTER = new OrFilter(
      new GeneratorFilter(AssignableGroupFilter.class, new ThrowsListGetter()),
      new AssignableFromFilter("java.lang.RuntimeException"));
  private static final TObjectHashingStrategy<ExpectedTypeInfo> EXPECTED_TYPE_INFO_STRATEGY = new TObjectHashingStrategy<ExpectedTypeInfo>() {
    public int computeHashCode(final ExpectedTypeInfo object) {
      return object.getType().hashCode();
    }

    public boolean equals(final ExpectedTypeInfo o1, final ExpectedTypeInfo o2) {
      return o1.getType().equals(o2.getType());
    }
  };

  @Nullable
  private static Pair<ElementFilter, TailType> getReferenceFilter(PsiElement element) {
    //throw new foo
    if (AFTER_THROW_NEW.accepts(element)) {
      return new Pair<ElementFilter, TailType>(new ElementExtractorFilter(THROWABLE_TYPE_FILTER), TailType.SEMICOLON);
    }

    //new xxx.yyy
    if (psiElement().afterLeaf(psiElement().withText(".")).withSuperParent(2, psiElement(PsiNewExpression.class)).accepts(element)) {
      return new Pair<ElementFilter, TailType>(new GeneratorFilter(AssignableGroupFilter.class, new ExpectedTypesGetter()), TailType.UNKNOWN);
    }

    //new HashMap<xxx,>
    if (psiElement().inside(
        psiElement(PsiReferenceList.class).save("refList").withParent(
            psiMethod().withThrowsList(get("refList")))).accepts(element)) {
      return new Pair<ElementFilter, TailType>(THROWABLES_FILTER, TailType.NONE);
    }

    if (psiElement().withParent(
        psiElement(PsiReferenceExpression.class).afterLeaf(
            psiElement().withText(")").withParent(PsiTypeCastExpression.class))).accepts(element)) {
      return new Pair<ElementFilter, TailType>(new ReturnTypeFilter(new GeneratorFilter(AssignableToFilter.class, new CastTypeGetter())), TailType.NONE);
    }

    return null;
  }



  public JavaSmartCompletionContributor() {
    extend(CompletionType.SMART, psiElement().afterLeaf(psiElement().withText("(").withParent(PsiTypeCastExpression.class)), new CompletionProvider<CompletionParameters>() {
      protected void addCompletions(@NotNull final CompletionParameters parameters, final ProcessingContext context, @NotNull final CompletionResultSet result) {
        for (final ExpectedTypeInfo type : getExpectedTypes(parameters.getPosition())) {
          result.addElement(LookupItemUtil.objectToLookupItem(type.getType()).setTailType(TailTypes.CAST_RPARENTH).setAutoCompletionPolicy(
              AutoCompletionPolicy.ALWAYS_AUTOCOMPLETE));
        }
      }
    });

    extend(CompletionType.SMART, psiElement().afterLeaf(PsiKeyword.INSTANCEOF), new CompletionProvider<CompletionParameters>() {
      protected void addCompletions(@NotNull final CompletionParameters parameters, final ProcessingContext context, @NotNull final CompletionResultSet result) {
        for (final PsiType type : new InheritorsGetter(new InstanceOfLeftPartTypeGetter(), true, false).getInheritors(parameters.getPosition(), null)) {
          result.addElement(LookupItemUtil.objectToLookupItem(type));
        }
      }
    });

    extend(CompletionType.SMART, psiElement(), new CompletionProvider<CompletionParameters>() {
      protected void addCompletions(@NotNull final CompletionParameters parameters, final ProcessingContext context, @NotNull final CompletionResultSet result) {
        final PsiElement element = parameters.getPosition();
        final PsiReference reference = element.getContainingFile().findReferenceAt(parameters.getOffset());
        if (reference != null) {
          final Pair<ElementFilter, TailType> pair = getReferenceFilter(element);
          if (pair != null) {
            for (final LookupElement item : completeReference(element, reference, parameters.getOriginalFile(), pair.second, pair.first, result)) {
              if (AFTER_THROW_NEW.accepts(element)) {
                ((LookupItem)item).setAttribute(LookupItem.DONT_CHECK_FOR_INNERS, "");
                if (item.getObject() instanceof PsiClass) {
                  JavaAwareCompletionData.setShowFQN((LookupItem)item);
                }
              }
              result.addElement(item);
            }
          }

        }
      }
    });

    extend(CompletionType.SMART, PlatformPatterns.or(
        psiElement().withParent(PsiExpression.class),
        psiElement().inside(PsiClassObjectAccessExpression.class),
        psiElement().inside(PsiThisExpression.class),
        psiElement().inside(PsiSuperExpression.class)
        ),
           new CompletionProvider<CompletionParameters>(true, false) {
             public void addCompletions(@NotNull final CompletionParameters params, final ProcessingContext matchingContext, @NotNull final CompletionResultSet result) {
               final PsiElement position = params.getPosition();
               if (position.getParent() instanceof PsiLiteralExpression) return;

               final THashSet<ExpectedTypeInfo> infos = new THashSet<ExpectedTypeInfo>(EXPECTED_TYPE_INFO_STRATEGY);
               ApplicationManager.getApplication().runReadAction(new Runnable() {
                 public void run() {
                   infos.addAll(Arrays.asList(getExpectedTypes(position)));
                 }
               });
               for (final ExpectedTypeInfo type : infos) {
                 final JavaSmartCompletionParameters parameters = new JavaSmartCompletionParameters(params, type);
                 final ElementFilter filter = new ReturnTypeFilter(new AssignableFromFilter(type.getType()));

                 CompletionService.getCompletionService().getVariantsFromContributors(
                     ExpressionSmartCompletionContributor.CONTRIBUTORS, parameters, null,
                     new Consumer<LookupElement>() {
                       public void consume(final LookupElement lookupElement) {
                         final Object object = lookupElement.getObject();
                         if (!filter.isClassAcceptable(object.getClass())) return;

                         final PsiSubstitutor substitutor;
                         if (lookupElement instanceof LookupItem) {
                           substitutor = (PsiSubstitutor)((LookupItem)lookupElement).getAttribute(LookupItem.SUBSTITUTOR);
                         }
                         else {
                           substitutor = null;
                         }
                         if (filter.isAcceptable(object, position)) {
                           result.addElement(lookupElement);
                         }
                         else if (substitutor != null &&
                                  object instanceof PsiElement &&
                                  filter.isAcceptable(new CandidateInfo((PsiElement)object, substitutor), position)) {
                           result.addElement(lookupElement);
                         }
                       }
                     });
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
            final LookupItem item = LookupItemUtil.objectToLookupItem(type).setTailType(TailType.NONE);
            if (needQualify) JavaAwareCompletionData.qualify(item);
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
              result.addElement(LookupItemUtil.objectToLookupItem(exception).setTailType(TailType.SPACE));
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
          result.addElement(LookupItemUtil.objectToLookupItem(type).setTailType(TailType.SPACE));
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
                         resultSet.addElement(LookupItemUtil.objectToLookupItem(substitution));
                       }
                     }
                   }
                   return true;
                 }
               }).booleanValue();


               if (!hasExpected) {
                 boolean isLast = parameterIndex == typeParameters.length - 1;
                 final TailType tail = isLast ? new CharTailType('>') : TailType.COMMA;

                 final List<PsiClassType> typeList = Collections.singletonList((PsiClassType)TypeConversionUtil.typeParameterErasure(targetParameter));
                 processInheritors(parameters, context, parameters.getOriginalFile(), typeList, new Consumer<PsiType>() {
                   public void consume(final PsiType type) {
                     final PsiClass psiClass = PsiUtil.resolveClassInType(type);
                     if (psiClass == null) return;

                     resultSet.addElement(new JavaPsiClassReferenceElement(psiClass).setTailType(tail));
                   }
                 });

               }
             }
           });


    extend(CompletionType.SMART, AFTER_NEW, new CompletionProvider<CompletionParameters>(true, false) {
      public void addCompletions(@NotNull final CompletionParameters parameters, final ProcessingContext matchingContext, @NotNull final CompletionResultSet result) {
        final PsiElement identifierCopy = parameters.getPosition();
        final PsiFile file = parameters.getOriginalFile();

        final List<PsiClassType> expectedClassTypes = new SmartList<PsiClassType>();
        final List<PsiArrayType> expectedArrayTypes = new SmartList<PsiArrayType>();

        ApplicationManager.getApplication().runReadAction(new Runnable() {
          public void run() {
            for (PsiType type : ExpectedTypesGetter.getExpectedTypes(identifierCopy, true)) {
              type = JavaCompletionUtil.eliminateWildcards(type);
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
              final LookupItem item = LookupItemUtil.objectToLookupItem(JavaCompletionUtil.eliminateWildcards(type));
              item.setAttribute(LookupItem.DONT_CHECK_FOR_INNERS, "");
              if (item.getObject() instanceof PsiClass) {
                JavaAwareCompletionData.setShowFQN(item);
              }
              result.addElement(item);
            }
          });
        }

        processInheritors(parameters, identifierCopy, file, expectedClassTypes, new Consumer<PsiType>() {
          public void consume(final PsiType type) {
            addExpectedType(result, type, parameters);
          }
        });
      }
    });
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


  private static void processInheritors(final CompletionParameters parameters, final PsiElement identifierCopy, final PsiFile file, final List<PsiClassType> expectedClassTypes,
                                        final Consumer<PsiType> consumer) {
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
              StatisticsManager.getInstance().getAllValues(JavaStatisticsManager.getMemberUseKey1(type));
          for (final StatisticsInfo statisticsInfo : statisticsInfos) {
            final String value = statisticsInfo.getValue();
            if (value.startsWith(JavaStatisticsManager.CLASS_PREFIX)) {
              final String qname = value.substring(JavaStatisticsManager.CLASS_PREFIX.length());
              for (final PsiClass psiClass : JavaPsiFacade.getInstance(file.getProject()).findClasses(qname, file.getResolveScope())) {
                if (!PsiTreeUtil.isAncestor(file, psiClass, true) && !processor.process(psiClass)) break;
              }
            }
          }

          for (final PsiType variant : statVariants) {
            consumer.consume(variant);
          }
        }
      });
    }

    //long
    for (final PsiClassType type : expectedClassTypes) {
      final boolean shouldSearchForInheritors = ApplicationManager.getApplication().runReadAction(new Computable<Boolean>() {
        public Boolean compute() {
          final PsiClass psiClass = type.resolve();
          assert psiClass != null;
          return !psiClass.hasModifierProperty(PsiModifier.FINAL) &&
                 !CommonClassNames.JAVA_LANG_OBJECT.equals(psiClass.getQualifiedName());
        }
      }).booleanValue();
      if (shouldSearchForInheritors) {
        for (final PsiType psiType : CodeInsightUtil.addSubtypes(type, identifierCopy, false)) {
          ApplicationManager.getApplication().runReadAction(new Runnable() {
            public void run() {
              consumer.consume(psiType);
            }
          });
        }
      }
    }
  }

  private static ExpectedTypeInfo[] getExpectedTypes(final PsiElement position) {
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

    return ExpectedTypesProvider.getInstance(position.getProject()).getExpectedTypes(expression, true);
  }

  private static void addExpectedType(final CompletionResultSet result, final PsiType type, final CompletionParameters parameters) {
    if (!InheritorsGetter.hasAccessibleConstructor(type)) return;

    final PsiClass psiClass = PsiUtil.resolveClassInType(type);
    if (psiClass == null) return;

    final PsiClass parentClass = psiClass.getContainingClass();
    if (parentClass != null && !psiClass.hasModifierProperty(PsiModifier.STATIC) &&
        !PsiTreeUtil.isAncestor(parentClass, parameters.getPosition(), false) &&
        !(parentClass.getContainingFile().equals(parameters.getOriginalFile()) &&
          parentClass.getTextRange().contains(parameters.getOffset()))) {
      return;
    }

    final LookupItem item = LookupItemUtil.objectToLookupItem(JavaCompletionUtil.eliminateWildcards(type));
    item.setAttribute(LookupItem.DONT_CHECK_FOR_INNERS, "");
    JavaAwareCompletionData.setShowFQN(item);

    if (psiClass.isInterface() || psiClass.hasModifierProperty(PsiModifier.ABSTRACT)) {
      item.setAutoCompletionPolicy(AutoCompletionPolicy.NEVER_AUTOCOMPLETE);
      item.setAttribute(LookupItem.INDICATE_ANONYMOUS, "");
    }
    result.addElement(item);
  }

  public static THashSet<LookupElement> completeReference(final PsiElement element, final PsiReference reference, final PsiFile originalFile,
                                                        final TailType tailType, final ElementFilter filter, final CompletionResultSet result) {
    final THashSet<LookupElement> set = new THashSet<LookupElement>();
    SMART_DATA.completeReference(reference, element, set, tailType, originalFile, filter, new CompletionVariant());
    return set;
  }
}
