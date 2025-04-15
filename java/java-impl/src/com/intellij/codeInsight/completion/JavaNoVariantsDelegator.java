// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.ExpectedTypeInfo;
import com.intellij.codeInsight.TailTypes;
import com.intellij.codeInsight.completion.JavaCompletionUtil.JavaLookupElementHighlighter;
import com.intellij.codeInsight.completion.impl.BetterPrefixMatcher;
import com.intellij.codeInsight.completion.impl.CamelHumpMatcher;
import com.intellij.codeInsight.completion.scope.JavaCompletionProcessor;
import com.intellij.codeInsight.lookup.AutoCompletionPolicy;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.TailTypeDecorator;
import com.intellij.java.syntax.parser.JavaKeywords;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.filters.ElementFilter;
import com.intellij.psi.search.PsiShortNamesCache;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.CollectConsumer;
import com.intellij.util.Consumer;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static com.intellij.codeInsight.completion.JavaCompletionContributor.UNEXPECTED_REFERENCE_AFTER_DOT;
import static com.intellij.patterns.PsiJavaPatterns.psiElement;

public final class JavaNoVariantsDelegator extends CompletionContributor implements DumbAware {
  @Override
  public void fillCompletionVariants(final @NotNull CompletionParameters parameters, final @NotNull CompletionResultSet result) {
    ResultTracker tracker = new ResultTracker(result) {
      @Override
      public void consume(CompletionResult plainResult) {
        super.consume(plainResult);

        LookupElement element = plainResult.getLookupElement();
        if (element instanceof TypeArgumentCompletionProvider.TypeArgsLookupElement) {
          ((TypeArgumentCompletionProvider.TypeArgsLookupElement)element).registerSingleClass(session);
        }
      }
    };
    result.runRemainingContributors(parameters, tracker);
    final boolean empty = tracker.containsOnlyPackages || suggestAllAnnotations(parameters);

    if (parameters.getCompletionType() == CompletionType.SMART && !tracker.hasStartMatches) {
      addNullKeyword(parameters, result);
    }

    if (JavaCompletionContributor.isClassNamePossible(parameters) && !JavaCompletionContributor.mayStartClassName(result)) {
      result.restartCompletionOnAnyPrefixChange();
    }

    if (empty) {
      delegate(parameters, JavaCompletionSorting.addJavaSorting(parameters, result), tracker.session);
    } else {
      if (parameters.getCompletionType() == CompletionType.BASIC &&
          parameters.getInvocationCount() <= 1 &&
          JavaCompletionContributor.mayStartClassName(result) &&
          JavaCompletionContributor.isClassNamePossible(parameters) &&
          !JavaCompletionContributor.IN_PERMITS_LIST.accepts(parameters.getPosition())) {
        suggestNonImportedClasses(parameters, JavaCompletionSorting.addJavaSorting(parameters, result.withPrefixMatcher(tracker.betterMatcher)), tracker.session);
      }
    }
  }

  private static void addNullKeyword(@NotNull CompletionParameters parameters, @NotNull CompletionResultSet result) {
    if (JavaSmartCompletionContributor.INSIDE_EXPRESSION.accepts(parameters.getPosition()) &&
        !psiElement().afterLeaf(".").accepts(parameters.getPosition()) &&
        result.getPrefixMatcher().getPrefix().startsWith("n")) {
      ExpectedTypeInfo[] infos = JavaSmartCompletionContributor.getExpectedTypes(parameters);
      for (ExpectedTypeInfo info : infos) {
        if (!(info.getType() instanceof PsiPrimitiveType)) {
          LookupElement item = BasicExpressionCompletionContributor.createKeywordLookupItem(parameters.getPosition(), JavaKeywords.NULL);
          result.addElement(JavaSmartCompletionContributor.decorate(item, ContainerUtil.newHashSet(infos)));
          return;
        }
      }
    }
  }

  private static boolean suggestAllAnnotations(CompletionParameters parameters) {
    return psiElement().withParents(PsiJavaCodeReferenceElement.class, PsiAnnotation.class).accepts(parameters.getPosition());
  }

  private static void delegate(CompletionParameters parameters, CompletionResultSet result, JavaCompletionSession session) {
    boolean added = false;
    if (parameters.getCompletionType() == CompletionType.BASIC) {
      PsiElement position = parameters.getPosition();
      added |= suggestCollectionUtilities(parameters, result, position);

      if (parameters.getInvocationCount() <= 1 &&
          (JavaCompletionContributor.mayStartClassName(result) || suggestAllAnnotations(parameters)) &&
          JavaCompletionContributor.isClassNamePossible(parameters)) {
        suggestNonImportedClasses(parameters, result, session);
        return;
      }
      if (parameters.getInvocationCount() <= 1) { //case with >=2 in JavaCompletionContributor.fillCompletionVariants
        added |= suggestTagCalls(parameters, result, position);
      }
      added |= suggestChainedCalls(parameters, result, position);
    }

    if (parameters.getCompletionType() == CompletionType.SMART && parameters.getInvocationCount() == 2) {
      result.runRemainingContributors(parameters.withInvocationCount(3), true);
    }

    if (!added && parameters.getCompletionType() == CompletionType.BASIC && parameters.getInvocationCount() == 2) {
      JavaQualifierAsArgumentContributor.fillQualifierAsArgumentContributor(parameters.withInvocationCount(3), result);
    }
  }

  private static boolean suggestTagCalls(CompletionParameters parameters, CompletionResultSet result, PsiElement position) {
    if (!Registry.is("java.completion.methods.use.tags")) {
      return false;
    }
    if (!(JavaKeywordCompletion.AFTER_DOT.accepts(position))) {
      return false;
    }
    if (!(position.getParent() instanceof PsiReferenceExpression psiReferenceExpression)) {
      return false;
    }
    PsiExpression qualifierExpression = psiReferenceExpression.getQualifierExpression();
    if (!(qualifierExpression != null && PsiUtil.resolveClassInClassTypeOnly(qualifierExpression.getType()) != null)) {
      return false;
    }
    PrefixMatcher prefixMatcher = result.getPrefixMatcher();
    if (!(prefixMatcher instanceof CamelHumpMatcher camelHumpMatcher) || camelHumpMatcher.isTypoTolerant()) {
      //there is no reason to check it one more time
      return false;
    }
    Ref<Boolean> added = new Ref<>();
    added.set(false);
    MethodTags.TagMatcher tagMatcher = new MethodTags.TagMatcher(prefixMatcher);
    CompletionResultSet tagResultSet = result.withPrefixMatcher(tagMatcher);
    tagResultSet.runRemainingContributors(parameters, downstream -> {
      LookupElement element = downstream.getLookupElement();
      if (element != null && !prefixMatcher.prefixMatches(element) && tagMatcher.prefixMatches(element)) {
        LookupElement lookupElement = MethodTags.wrapLookupWithTags(element, prefixMatcher::prefixMatches, prefixMatcher.getPrefix(),
                                                                    parameters.getCompletionType());
        if (lookupElement != null) {
          CompletionResult wrapped = CompletionResult.wrap(lookupElement, downstream.getPrefixMatcher(),
                                                           downstream.getSorter());
          if (wrapped != null) {
            added.set(true);
            tagResultSet.passResult(wrapped);
          }
        }
      }
    });
    return added.get();
  }

  private static boolean suggestCollectionUtilities(CompletionParameters parameters,
                                                    final CompletionResultSet result,
                                                    PsiElement position) {
    Ref<Boolean> added = new Ref<>();
    added.set(false);
    if (StringUtil.isNotEmpty(result.getPrefixMatcher().getPrefix())) {
      for (ExpectedTypeInfo info : JavaSmartCompletionContributor.getExpectedTypes(parameters)) {
        new CollectionsUtilityMethodsProvider(position, info.getType(), info.getDefaultType(), element -> {
          added.set(true);
          result.consume(element);
        }).addCompletions(true);
      }
    }
    return added.get();
  }

  private static boolean suggestChainedCalls(CompletionParameters parameters, CompletionResultSet result, PsiElement position) {
    PsiElement parent = position.getParent();
    if (!(parent instanceof PsiJavaCodeReferenceElement) || PsiTreeUtil.getParentOfType(parent, PsiImportStatementBase.class) != null) {
      return false;
    }
    PsiElement qualifier = ((PsiJavaCodeReferenceElement)parent).getQualifier();
    if (!(qualifier instanceof PsiJavaCodeReferenceElement) ||
        ((PsiJavaCodeReferenceElement)qualifier).isQualified()) {
      return false;
    }
    PsiElement target = ((PsiJavaCodeReferenceElement)qualifier).resolve();
    if (target != null && !(target instanceof PsiPackage)) {
      return false;
    }

    PsiFile file = position.getContainingFile();
    if (file instanceof PsiJavaCodeReferenceCodeFragment) {
      return false;
    }

    String fullPrefix = parent.getText().substring(0, parameters.getOffset() - parent.getTextRange().getStartOffset());
    CompletionResultSet qualifiedCollector = result.withPrefixMatcher(fullPrefix);
    ElementFilter filter = JavaCompletionContributor.getReferenceFilter(position);
    JavaLookupElementHighlighter highlighter = JavaCompletionUtil.getHighlighterForPlace(position, parameters.getOriginalFile().getVirtualFile());
    boolean added = false;
    for (LookupElement base : suggestQualifierItems(parameters, (PsiJavaCodeReferenceElement)qualifier, filter)) {
      PsiType type = JavaCompletionUtil.getLookupElementType(base);
      if (type != null && !PsiTypes.voidType().equals(type)) {
        String separator = parent instanceof PsiMethodReferenceExpression ? "::" : ".";
        PsiReferenceExpression ref = ReferenceExpressionCompletionContributor.createMockReference(position, type, base, separator);
        if (ref != null) {
          for (LookupElement item : JavaSmartCompletionContributor.completeReference(position, ref, filter, true, true, parameters,
                                                                                     result.getPrefixMatcher())) {
            if (!JavaChainLookupElement.isReasonableChain(base, item)) continue;
            JavaChainLookupElement chainedElement = new JavaChainLookupElement(base, item, separator);
            LookupElement chain = highlighter.highlightIfNeeded(null, chainedElement, item.getObject());
            if (JavaCompletionContributor.shouldInsertSemicolon(position)) {
              chain = TailTypeDecorator.withTail(chain, TailTypes.semicolonType());
            }
            qualifiedCollector.addElement(chain);
            added = true;
          }
        }
      }
    }
    return added;
  }

  private static Set<LookupElement> suggestQualifierItems(CompletionParameters parameters,
                                                          PsiJavaCodeReferenceElement qualifier,
                                                          ElementFilter filter) {
    String referenceName = qualifier.getReferenceName();
    if (referenceName == null) {
      return Collections.emptySet();
    }

    PrefixMatcher qMatcher = new CamelHumpMatcher(referenceName);
    JavaCompletionProcessor.Options options =
      JavaCompletionProcessor.Options.DEFAULT_OPTIONS.withFilterStaticAfterInstance(parameters.getInvocationCount() <= 1)
        .withInstantiableOnly(false);
    Set<LookupElement> plainVariants =
      JavaCompletionUtil.processJavaReference(qualifier, qualifier, filter, options, qMatcher::prefixMatches, parameters);

    Project project = qualifier.getProject();
    PsiResolveHelper helper = JavaPsiFacade.getInstance(project).getResolveHelper();
    for (PsiClass aClass : PsiShortNamesCache.getInstance(project).getClassesByName(referenceName, qualifier.getResolveScope())) {
      if (helper.isAccessible(aClass, qualifier, null)) {
        plainVariants.add(JavaClassNameCompletionContributor.createClassLookupItem(aClass, true));
      }
    }

    if (!plainVariants.isEmpty()) {
      return plainVariants;
    }

    final Set<LookupElement> allClasses = new LinkedHashSet<>();
    PsiElement qualifierName = qualifier.getReferenceNameElement();
    if (qualifierName != null) {
      JavaClassNameCompletionContributor.addAllClasses(parameters.withPosition(qualifierName, qualifierName.getTextRange().getEndOffset()),
                                                       true, qMatcher, new CollectConsumer<>(allClasses));
    }
    return allClasses;
  }

  static void suggestNonImportedClasses(CompletionParameters parameters,
                                        CompletionResultSet result,
                                        @Nullable JavaCompletionSession session) {
    if (UNEXPECTED_REFERENCE_AFTER_DOT.accepts(parameters.getPosition())) return;
    List<LookupElement> sameNamedBatch = new ArrayList<>();
    PsiElement position = parameters.getPosition();
    JavaLookupElementHighlighter highlighter = JavaCompletionUtil.getHighlighterForPlace(position, parameters.getOriginalFile().getVirtualFile());
    JavaClassNameCompletionContributor.addAllClasses(parameters, parameters.getInvocationCount() <= 2, result.getPrefixMatcher(), element -> {
      if (session != null && session.alreadyProcessed(element)) {
        return;
      }
      JavaPsiClassReferenceElement classElement = element.as(JavaPsiClassReferenceElement.CLASS_CONDITION_KEY);
      if (classElement != null && parameters.getInvocationCount() < 2) {
        if (JavaClassNameCompletionContributor.AFTER_NEW.accepts(position) &&
            JavaPsiClassReferenceElement.isInaccessibleConstructorSuggestion(position, classElement.getObject())) {
          return;
        }
        classElement.setAutoCompletionPolicy(AutoCompletionPolicy.NEVER_AUTOCOMPLETE);
      }

      element = highlighter.highlightIfNeeded(null, element, element.getObject());
      if (!sameNamedBatch.isEmpty() && !element.getLookupString().equals(sameNamedBatch.get(0).getLookupString())) {
        result.addAllElements(sameNamedBatch);
        sameNamedBatch.clear();
      }
      sameNamedBatch.add(element);
    });
    result.addAllElements(sameNamedBatch);
  }

  public static class ResultTracker implements Consumer<CompletionResult> {
    private final CompletionResultSet myResult;
    public final JavaCompletionSession session;
    boolean hasStartMatches = false;
    public boolean containsOnlyPackages = true;
    public BetterPrefixMatcher betterMatcher;

    public ResultTracker(CompletionResultSet result) {
      myResult = result;
      betterMatcher = new BetterPrefixMatcher.AutoRestarting(result);
      session = new JavaCompletionSession(result);
    }

    @Override
    public void consume(CompletionResult plainResult) {
      myResult.passResult(plainResult);

      if (!hasStartMatches && plainResult.getPrefixMatcher().isStartMatch(plainResult.getLookupElement())) {
        hasStartMatches = true;
      }

      LookupElement element = plainResult.getLookupElement();
      if (containsOnlyPackages && !(CompletionUtil.getTargetElement(element) instanceof PsiPackage)) {
        containsOnlyPackages = false;
      }

      session.registerClassFrom(element);

      betterMatcher = betterMatcher.improve(plainResult);
    }
  }
}
