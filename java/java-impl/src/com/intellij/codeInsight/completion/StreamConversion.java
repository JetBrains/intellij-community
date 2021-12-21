// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.completion;

import com.intellij.application.options.CodeStyle;
import com.intellij.codeInsight.ExpectedTypeInfo;
import com.intellij.codeInsight.ExpectedTypesProvider;
import com.intellij.codeInsight.lookup.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.filters.TrueFilter;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.statistics.JavaStatisticsManager;
import com.intellij.psi.statistics.StatisticsInfo;
import com.intellij.psi.util.*;
import com.intellij.util.Consumer;
import com.intellij.util.PlatformIcons;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.JBIterable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.stream.Collector;

import static com.intellij.codeInsight.completion.ReferenceExpressionCompletionContributor.getSpace;
import static com.intellij.psi.CommonClassNames.*;

/**
 * @author peter
 */
final class StreamConversion {

  static List<LookupElement> addToStreamConversion(PsiReferenceExpression ref, CompletionParameters parameters) {
    PsiExpression qualifier = ref.getQualifierExpression();
    if (qualifier == null) return Collections.emptyList();

    PsiType type = qualifier.getType();
    if (type instanceof PsiClassType) {
      PsiClass qualifierClass = ((PsiClassType)type).resolve();
      if (qualifierClass == null || InheritanceUtil.isInheritor(qualifierClass, JAVA_UTIL_STREAM_BASE_STREAM)) {
        return Collections.emptyList();
      }

      PsiMethod streamMethod = ContainerUtil.find(qualifierClass.findMethodsByName("stream", true), m -> !m.hasParameters());
      if (streamMethod == null ||
          streamMethod.hasModifierProperty(PsiModifier.STATIC) ||
          !PsiUtil.isAccessible(streamMethod, ref, null) ||
          !InheritanceUtil.isInheritor(streamMethod.getReturnType(), JAVA_UTIL_STREAM_BASE_STREAM)) {
        return Collections.emptyList();
      }

      return generateStreamSuggestions(parameters, qualifier, qualifier.getText() + ".stream()", context -> {
        String space = getSpace(CodeStyle.getLanguageSettings(context.getFile()).SPACE_WITHIN_EMPTY_METHOD_CALL_PARENTHESES);
        context.getDocument().insertString(context.getStartOffset(), "stream(" + space + ").");
      });
    }
    else if (type instanceof PsiArrayType) {
      String arraysStream = JAVA_UTIL_ARRAYS + ".stream";
      return generateStreamSuggestions(parameters, qualifier, arraysStream + "(" + qualifier.getText() + ")",
                                       context -> wrapQualifiedIntoMethodCall(context, arraysStream));
    }

    return Collections.emptyList();
  }

  @NotNull
  private static List<LookupElement> generateStreamSuggestions(CompletionParameters parameters,
                                                               PsiExpression qualifier,
                                                               String changedQualifier,
                                                               Consumer<InsertionContext> beforeInsertion) {
    String refText = changedQualifier + ".x";
    PsiExpression expr = PsiElementFactory.getInstance(qualifier.getProject()).createExpressionFromText(refText, qualifier);
    if (!(expr instanceof PsiReferenceExpression)) {
      return Collections.emptyList();
    }

    Set<LookupElement> streamSuggestions = ReferenceExpressionCompletionContributor
      .completeFinalReference(qualifier, (PsiReferenceExpression)expr, TrueFilter.INSTANCE,
                              PsiType.getJavaLangObject(qualifier.getManager(), qualifier.getResolveScope()),
                              parameters);
    return ContainerUtil.mapNotNull(streamSuggestions, e ->
      ChainedCallCompletion.OBJECT_METHOD_PATTERN.accepts(e.getObject()) ? null : new StreamMethodInvocation(e, beforeInsertion));
  }

  private static void wrapQualifiedIntoMethodCall(@NotNull InsertionContext context, @NotNull String methodQualifiedName) {
    PsiFile file = context.getFile();
    PsiReferenceExpression ref =
      PsiTreeUtil.findElementOfClassAtOffset(file, context.getStartOffset(), PsiReferenceExpression.class, false);
    if (ref != null) {
      PsiElement qualifier = ref.getQualifier();
      if (qualifier != null) {
        TextRange range = qualifier.getTextRange();
        int startOffset = range.getStartOffset();

        String callSpace = getSpace(CodeStyle.getLanguageSettings(file).SPACE_WITHIN_METHOD_CALL_PARENTHESES);
        context.getDocument().insertString(range.getEndOffset(), callSpace + ")");
        context.getDocument().insertString(startOffset, methodQualifiedName + "(" + callSpace);

        context.commitDocument();
        Project project = context.getProject();
        JavaCodeStyleManager.getInstance(project).shortenClassReferences(
          file, startOffset, startOffset + methodQualifiedName.length());
        PsiDocumentManager.getInstance(project).doPostponedOperationsAndUnblockDocument(context.getDocument());
      }
    }
  }

  static void addCollectConversion(PsiReferenceExpression ref, Collection<? extends ExpectedTypeInfo> expectedTypes, Consumer<? super LookupElement> consumer) {
    PsiClass collectors = JavaPsiFacade.getInstance(ref.getProject()).findClass(JAVA_UTIL_STREAM_COLLECTORS, ref.getResolveScope());
    if (collectors == null) return;

    PsiExpression qualifier = ref.getQualifierExpression();
    if (qualifier == null) {
      if (ref.getParent() instanceof PsiExpressionList && ref.getParent().getParent() instanceof PsiMethodCallExpression) {
        PsiReferenceExpression methodExpression = ((PsiMethodCallExpression)ref.getParent().getParent()).getMethodExpression();
        qualifier = methodExpression.getQualifierExpression();
        if ("collect".equals(methodExpression.getReferenceName()) && qualifier != null) {
          suggestCollectorsArgument(expectedTypes, consumer, collectors, qualifier);
        }
      }
      return;
    }

    convertQualifierViaCollectors(ref, expectedTypes, consumer, qualifier, collectors);
  }

  private static void suggestCollectorsArgument(Collection<? extends ExpectedTypeInfo> expectedTypes, Consumer<? super LookupElement> consumer, PsiClass collectors, PsiExpression qualifier) {
    PsiType matchingExpectation = JBIterable.from(expectedTypes).map(ExpectedTypeInfo::getType)
      .find(t -> TypeConversionUtil.erasure(t).equalsToText(Collector.class.getName()));
    if (matchingExpectation == null) return;

    for (Pair<String, PsiType> pair : suggestCollectors(Arrays.asList(ExpectedTypesProvider.getExpectedTypes(qualifier, true)), qualifier)) {
      for (PsiMethod method : collectors.findMethodsByName(pair.first, false)) {
        JavaMethodCallElement item = new JavaMethodCallElement(method, false, false);
        item.setAutoCompletionPolicy(AutoCompletionPolicy.NEVER_AUTOCOMPLETE);
        item.setInferenceSubstitutorFromExpectedType(qualifier, matchingExpectation);
        consumer.consume(PrioritizedLookupElement.withPriority(item, 1));
      }
    }
  }

  private static void convertQualifierViaCollectors(PsiReferenceExpression ref,
                                                    Collection<? extends ExpectedTypeInfo> expectedTypes,
                                                    Consumer<? super LookupElement> consumer,
                                                    PsiExpression qualifier,
                                                    @NotNull PsiClass collectors) {
    for (Pair<String, PsiType> pair : suggestCollectors(expectedTypes, qualifier)) {
      if (collectors.findMethodsByName(pair.first, true).length == 0) continue;
      consumer.consume(new CollectLookupElement(pair.first, pair.second, ref));
    }
  }

  // each pair of method name in Collectors class, and the corresponding collection type
  private static List<Pair<String, PsiType>> suggestCollectors(Collection<? extends ExpectedTypeInfo> expectedTypes, PsiExpression qualifier) {
    PsiType component = PsiUtil.substituteTypeParameter(qualifier.getType(), JAVA_UTIL_STREAM_STREAM, 0, true);
    if (component == null) return Collections.emptyList();

    JavaPsiFacade facade = JavaPsiFacade.getInstance(qualifier.getProject());
    PsiElementFactory factory = facade.getElementFactory();
    GlobalSearchScope scope = qualifier.getResolveScope();

    boolean joiningApplicable = InheritanceUtil.isInheritor(component, CharSequence.class.getName());

    PsiClass list = facade.findClass(JAVA_UTIL_LIST, scope);
    PsiClass set = facade.findClass(JAVA_UTIL_SET, scope);
    PsiClass collection = facade.findClass(JAVA_UTIL_COLLECTION, scope);
    PsiClass string = facade.findClass(JAVA_LANG_STRING, scope);
    if (list == null || set == null || collection == null || string == null) return Collections.emptyList();

    PsiType listType = null;
    PsiType setType = null;
    for (ExpectedTypeInfo info : expectedTypes) {
      PsiType type = info.getDefaultType();
      PsiClass expectedClass = PsiUtil.resolveClassInClassTypeOnly(type);
      PsiType expectedComponent = PsiUtil.extractIterableTypeParameter(type, true);
      if (expectedClass == null || expectedComponent == null || !TypeConversionUtil.isAssignable(expectedComponent, component)) continue;

      if (InheritanceUtil.isInheritorOrSelf(list, expectedClass, true)) {
        listType = type;
      }
      if (InheritanceUtil.isInheritorOrSelf(set, expectedClass, true)) {
        setType = type;
      }
    }

    if (listType == null) {
      listType = factory.createType(list, component);
    }

    if (setType == null) {
      setType = factory.createType(set, component);
    }

    List<Pair<String, PsiType>> result = new ArrayList<>();
    result.add(Pair.create("toList", listType));
    result.add(Pair.create("toUnmodifiableList", listType));
    result.add(Pair.create("toSet", setType));
    result.add(Pair.create("toUnmodifiableSet", setType));
    result.add(Pair.create("toCollection", factory.createType(collection, component)));
    if (joiningApplicable) {
      result.add(Pair.create("joining", factory.createType(string)));
    }
    return result;
  }

  private static class CollectLookupElement extends LookupElement implements TypedLookupItem, JavaCompletionStatistician.CustomStatisticsInfoProvider {
    private final String myLookupString;
    private final String myTypeText;
    private final String myMethodName;
    @NotNull private final PsiType myExpectedType;
    private final boolean myHasImport;

    CollectLookupElement(String methodName, @NotNull PsiType expectedType, @NotNull PsiElement context) {
      myMethodName = methodName;
      myExpectedType = expectedType;
      myTypeText = myExpectedType.getPresentableText();

      PsiMethodCallExpression call = (PsiMethodCallExpression)
        JavaPsiFacade.getElementFactory(context.getProject()).createExpressionFromText(methodName + "()", context);
      myHasImport = ContainerUtil.or(call.getMethodExpression().multiResolve(true), result -> {
        PsiElement element = result.getElement();
        return element instanceof PsiMember &&
               (JAVA_UTIL_STREAM_COLLECTORS + "." + myMethodName).equals(PsiUtil.getMemberQualifiedName((PsiMember)element));
      });

      myLookupString = "collect(" + (myHasImport ? "" : "Collectors.") + myMethodName + "())";
    }

    @NotNull
    @Override
    public String getLookupString() {
      return myLookupString;
    }

    @Override
    public Set<String> getAllLookupStrings() {
      return ContainerUtil.newHashSet(myLookupString, myMethodName);
    }

    @Override
    public void renderElement(LookupElementPresentation presentation) {
      super.renderElement(presentation);
      presentation.setTypeText(myTypeText);
      presentation.setIcon(PlatformIcons.METHOD_ICON);
    }

    @Override
    public void handleInsert(@NotNull InsertionContext context) {
      context.getDocument().replaceString(context.getStartOffset(), context.getTailOffset(), getInsertString());
      context.commitDocument();

      PsiMethodCallExpression call =
        PsiTreeUtil.findElementOfClassAtOffset(context.getFile(), context.getStartOffset(), PsiMethodCallExpression.class, false);
      if (call == null) return;

      PsiExpression[] args = call.getArgumentList().getExpressions();
      if (args.length != 1 || !(args[0] instanceof PsiMethodCallExpression)) return;

      PsiMethodCallExpression innerCall = (PsiMethodCallExpression)args[0];
      PsiMethod collectorMethod = innerCall.resolveMethod();
      if (collectorMethod != null && (!collectorMethod.getParameterList().isEmpty() || MethodSignatureUtil.hasOverloads(collectorMethod))) {
        context.getEditor().getCaretModel().moveToOffset(innerCall.getArgumentList().getFirstChild().getTextRange().getEndOffset());
      }

      JavaCodeStyleManager.getInstance(context.getProject()).shortenClassReferences(innerCall);
    }

    @NotNull
    private String getInsertString() {
      return "collect(" + (myHasImport ? "" : JAVA_UTIL_STREAM_COLLECTORS + ".") + myMethodName + "())";
    }

    @Override
    public PsiType getType() {
      return myExpectedType;
    }

    @Override
    public @Nullable StatisticsInfo getStatisticsInfo() {
      return JavaStatisticsManager.createInfoForNoArgMethod(JAVA_UTIL_STREAM_COLLECTORS, myMethodName);
    }
  }

  static class StreamMethodInvocation extends LookupElementDecorator<LookupElement> {
    private final Consumer<? super InsertionContext> myBeforeInsertion;

    StreamMethodInvocation(LookupElement e, Consumer<? super InsertionContext> beforeInsertion) {
      super(e);
      myBeforeInsertion = beforeInsertion;
      JavaMethodMergingContributor.disallowMerge(this);
    }

    @Override
    public void renderElement(LookupElementPresentation presentation) {
      super.renderElement(presentation);
      presentation.setItemText("stream()." + presentation.getItemText());
    }

    @Override
    public void handleInsert(@NotNull InsertionContext context) {
      myBeforeInsertion.consume(context);
      super.handleInsert(context);
    }
  }
}
