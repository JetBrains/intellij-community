/*
 * Copyright 2000-2017 JetBrains s.r.o.
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

import com.intellij.codeInsight.ExpectedTypeInfo;
import com.intellij.codeInsight.TailType;
import com.intellij.codeInsight.completion.JavaCompletionUtil.JavaLookupElementHighlighter;
import com.intellij.codeInsight.completion.impl.BetterPrefixMatcher;
import com.intellij.codeInsight.completion.impl.CamelHumpMatcher;
import com.intellij.codeInsight.lookup.*;
import com.intellij.java.JavaBundle;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
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
import java.util.stream.Collectors;

import static com.intellij.patterns.PsiJavaPatterns.psiElement;

public class JavaNoVariantsDelegator extends CompletionContributor implements DumbAware {
  @Override
  public void fillCompletionVariants(@NotNull final CompletionParameters parameters, @NotNull final CompletionResultSet result) {
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
          LookupElement item = BasicExpressionCompletionContributor.createKeywordLookupItem(parameters.getPosition(), PsiKeyword.NULL);
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
    if (parameters.getCompletionType() == CompletionType.BASIC) {
      PsiElement position = parameters.getPosition();
      suggestCollectionUtilities(parameters, result, position);

      if (parameters.getInvocationCount() <= 1 &&
          (JavaCompletionContributor.mayStartClassName(result) || suggestAllAnnotations(parameters)) &&
          JavaCompletionContributor.isClassNamePossible(parameters)) {
        suggestNonImportedClasses(parameters, result, session);
        return;
      }
      suggestTagCalls(parameters, result, position);
      suggestChainedCalls(parameters, result, position);
    }

    if (parameters.getCompletionType() == CompletionType.SMART && parameters.getInvocationCount() == 2) {
      result.runRemainingContributors(parameters.withInvocationCount(3), true);
    }
  }

  private static void suggestTagCalls(CompletionParameters parameters, CompletionResultSet result, PsiElement position) {
    if (!Registry.is("java.completion.methods.use.tags")) {
      return;
    }
    if (!(JavaKeywordCompletion.AFTER_DOT.accepts(position))) {
      return;
    }
    if (!(position.getParent() instanceof PsiReferenceExpression psiReferenceExpression)) {
      return;
    }
    PsiExpression qualifierExpression = psiReferenceExpression.getQualifierExpression();
    if (!(qualifierExpression != null && PsiUtil.resolveClassInClassTypeOnly(qualifierExpression.getType()) != null)) {
      return;
    }
    PrefixMatcher prefixMatcher = result.getPrefixMatcher();
    if (!(prefixMatcher instanceof CamelHumpMatcher camelHumpMatcher) || camelHumpMatcher.isTypoTolerant()) {
      //there is no reason to check it one more time
      return;
    }
    TagMatcher tagMatcher = new TagMatcher(prefixMatcher);
    CompletionResultSet tagResultSet = result.withPrefixMatcher(tagMatcher);
    tagResultSet.runRemainingContributors(parameters, new Consumer<>() {
      @Override
      public void consume(CompletionResult result) {
        LookupElement element = result.getLookupElement();
        if (element != null && !prefixMatcher.prefixMatches(element) && tagMatcher.prefixMatches(element)) {
          LookupElement lookupElement = wrapLookup(element, prefixMatcher);
          if (lookupElement == null) {
            return;
          }
          CompletionResult wrapped = CompletionResult.wrap(lookupElement, result.getPrefixMatcher(),
                                                           result.getSorter());
          if (wrapped != null) {
            tagResultSet.passResult(wrapped);
          }
        }
      }

      @Nullable
      private static LookupElement wrapLookup(@NotNull LookupElement element, @NotNull PrefixMatcher matcher) {
        String lookupString = element.getLookupString();
        PsiElement psiElement = element.getPsiElement();
        if (!(psiElement instanceof PsiMember psiMember)) {
          return null;
        }
        PsiClass psiClass = psiMember.getContainingClass();
        Set<String> tags = MethodTags.tags(lookupString).stream()
          .filter(t -> matcher.prefixMatches(t.getName()) && t.getMatcher().test(psiClass))
          .map(t -> t.getName())
          .collect(Collectors.toSet());
        if (tags.isEmpty()) {
          return null;
        }
        return new TagLookupElementDecorator(element, tags);
      }
    });
  }

  private static void suggestCollectionUtilities(CompletionParameters parameters, final CompletionResultSet result, PsiElement position) {
    if (StringUtil.isNotEmpty(result.getPrefixMatcher().getPrefix())) {
      for (ExpectedTypeInfo info : JavaSmartCompletionContributor.getExpectedTypes(parameters)) {
        new CollectionsUtilityMethodsProvider(position, info.getType(), info.getDefaultType(), result).addCompletions(true);
      }
    }
  }

  private static void suggestChainedCalls(CompletionParameters parameters, CompletionResultSet result, PsiElement position) {
    PsiElement parent = position.getParent();
    if (!(parent instanceof PsiJavaCodeReferenceElement) || PsiTreeUtil.getParentOfType(parent, PsiImportStatementBase.class) != null) {
      return;
    }
    PsiElement qualifier = ((PsiJavaCodeReferenceElement)parent).getQualifier();
    if (!(qualifier instanceof PsiJavaCodeReferenceElement) ||
        ((PsiJavaCodeReferenceElement)qualifier).isQualified()) {
      return;
    }
    PsiElement target = ((PsiJavaCodeReferenceElement)qualifier).resolve();
    if (target != null && !(target instanceof PsiPackage)) {
      return;
    }

    PsiFile file = position.getContainingFile();
    if (file instanceof PsiJavaCodeReferenceCodeFragment) {
      return;
    }

    String fullPrefix = parent.getText().substring(0, parameters.getOffset() - parent.getTextRange().getStartOffset());
    CompletionResultSet qualifiedCollector = result.withPrefixMatcher(fullPrefix);
    ElementFilter filter = JavaCompletionContributor.getReferenceFilter(position);
    JavaLookupElementHighlighter highlighter = JavaCompletionUtil.getHighlighterForPlace(position);
    for (LookupElement base : suggestQualifierItems(parameters, (PsiJavaCodeReferenceElement)qualifier, filter)) {
      PsiType type = JavaCompletionUtil.getLookupElementType(base);
      if (type != null && !PsiTypes.voidType().equals(type)) {
        String separator = parent instanceof PsiMethodReferenceExpression ? "::" : ".";
        PsiReferenceExpression ref = ReferenceExpressionCompletionContributor.createMockReference(position, type, base, separator);
        if (ref != null) {
          for (LookupElement item : JavaSmartCompletionContributor.completeReference(position, ref, filter, true, true, parameters,
                                                                                     result.getPrefixMatcher())) {
            LookupElement chain = highlighter.highlightIfNeeded(null, new JavaChainLookupElement(base, item, separator), item.getObject());
            if (JavaCompletionContributor.shouldInsertSemicolon(position)) {
              chain = TailTypeDecorator.withTail(chain, TailType.SEMICOLON);
            }
            qualifiedCollector.addElement(chain);
          }
        }
      }
    }
  }

  private static Set<LookupElement> suggestQualifierItems(CompletionParameters parameters,
                                                          PsiJavaCodeReferenceElement qualifier,
                                                          ElementFilter filter) {
    String referenceName = qualifier.getReferenceName();
    if (referenceName == null) {
      return Collections.emptySet();
    }

    PrefixMatcher qMatcher = new CamelHumpMatcher(referenceName);
    Set<LookupElement> plainVariants =
      JavaSmartCompletionContributor.completeReference(qualifier, qualifier, filter, true, true, parameters, qMatcher);

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

  static void suggestNonImportedClasses(CompletionParameters parameters, CompletionResultSet result, @Nullable JavaCompletionSession session) {
    List<LookupElement> sameNamedBatch = new ArrayList<>();
    PsiElement position = parameters.getPosition();
    JavaLookupElementHighlighter highlighter = JavaCompletionUtil.getHighlighterForPlace(position);
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

  private static class TagMatcher extends PrefixMatcher {
    @NotNull
    private final PrefixMatcher myMatcher;

    protected TagMatcher(@NotNull PrefixMatcher matcher) {
      super(matcher.getPrefix());
      myMatcher = matcher;
    }

    @Override
    public boolean prefixMatches(@NotNull String name) {
      if (myMatcher.prefixMatches(name)) {
        return true;
      }
      Set<MethodTags.Tag> tags = MethodTags.tags(name);
      for (MethodTags.Tag tag : tags) {
        if (myMatcher.prefixMatches(tag.getName())) {
          return true;
        }
      }
      return false;
    }

    @Override
    public @NotNull PrefixMatcher cloneWithPrefix(@NotNull String prefix) {
      PrefixMatcher matcher = myMatcher.cloneWithPrefix(prefix);
      return new TagMatcher(matcher);
    }
  }

  static class TagLookupElementDecorator extends LookupElementDecorator<LookupElement> {

    @NotNull
    private final Set<String> myTags;

    protected TagLookupElementDecorator(@NotNull LookupElement delegate, @NotNull Set<String> tags) {
      super(delegate);
      myTags = tags;
    }

    @NotNull
    public Set<String> getTags() {
      return myTags;
    }

    @Override
    public Set<String> getAllLookupStrings() {
      Set<String> all = new HashSet<>(super.getAllLookupStrings());
      all.addAll(myTags);
      return all;
    }

    @Override
    public void renderElement(@NotNull LookupElementPresentation presentation) {
      super.renderElement(presentation);
      if (myTags.size()==1) {
        presentation.appendTailText(" " + JavaBundle.message("java.completion.tag") + " ", true);
        presentation.appendTailText(myTags.iterator().next(), true, true);
      }
      else if (myTags.size() > 1) {
        presentation.appendTailText(" " + JavaBundle.message("java.completion.tags") + " ", true);
        Iterator<String> iterator = myTags.iterator();
        String firstTag = iterator.next();
        presentation.appendTailText(firstTag, true, true);
        while (iterator.hasNext()) {
          presentation.appendTailText(", ", true);
          presentation.appendTailText(iterator.next(), true, true);
        }
      }
    }
  }
}
