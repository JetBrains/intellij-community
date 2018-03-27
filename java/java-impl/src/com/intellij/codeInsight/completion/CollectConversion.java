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

import com.intellij.codeInsight.ExpectedTypeInfo;
import com.intellij.codeInsight.ExpectedTypesProvider;
import com.intellij.codeInsight.lookup.AutoCompletionPolicy;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementPresentation;
import com.intellij.codeInsight.lookup.TypedLookupItem;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.Consumer;
import com.intellij.util.PlatformIcons;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.JBIterable;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.stream.Collector;

import static com.intellij.psi.CommonClassNames.*;

/**
 * @author peter
 */
class CollectConversion {
 
  static void addCollectConversion(PsiReferenceExpression ref, Collection<ExpectedTypeInfo> expectedTypes, Consumer<LookupElement> consumer) {
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

    convertQualifierViaCollectors(ref, expectedTypes, consumer, qualifier);
  }

  private static void suggestCollectorsArgument(Collection<ExpectedTypeInfo> expectedTypes, Consumer<LookupElement> consumer, PsiClass collectors, PsiExpression qualifier) {
    PsiType matchingExpectation = JBIterable.from(expectedTypes).map(ExpectedTypeInfo::getType)
      .find(t -> TypeConversionUtil.erasure(t).equalsToText(Collector.class.getName()));
    if (matchingExpectation == null) return;

    for (Pair<String, PsiType> pair : suggestCollectors(Arrays.asList(ExpectedTypesProvider.getExpectedTypes(qualifier, true)), qualifier)) {
      PsiMethod[] methods = collectors.findMethodsByName(pair.first, false);
      if (methods.length == 0) continue;
      
      JavaMethodCallElement item = new JavaMethodCallElement(methods[0], false, false);
      item.setAutoCompletionPolicy(AutoCompletionPolicy.NEVER_AUTOCOMPLETE);
      item.setInferenceSubstitutorFromExpectedType(qualifier, matchingExpectation);
      consumer.consume(PrioritizedLookupElement.withPriority(item, 1));
    }
  }

  private static void convertQualifierViaCollectors(PsiReferenceExpression ref,
                                                    Collection<ExpectedTypeInfo> expectedTypes,
                                                    Consumer<LookupElement> consumer, PsiExpression qualifier) {
    for (Pair<String, PsiType> pair : suggestCollectors(expectedTypes, qualifier)) {
      consumer.consume(new MyLookupElement(pair.first, pair.second, ref));
    }
  }
  
  // each pair of method name in Collectors class, and the corresponding collection type
  private static List<Pair<String, PsiType>> suggestCollectors(Collection<ExpectedTypeInfo> expectedTypes, PsiExpression qualifier) {
    PsiType component = PsiUtil.substituteTypeParameter(qualifier.getType(), JAVA_UTIL_STREAM_STREAM, 0, true);
    if (component == null) return Collections.emptyList();

    JavaPsiFacade facade = JavaPsiFacade.getInstance(qualifier.getProject());
    PsiElementFactory factory = facade.getElementFactory();
    GlobalSearchScope scope = qualifier.getResolveScope();
    PsiClass list = facade.findClass(JAVA_UTIL_LIST, scope);
    PsiClass set = facade.findClass(JAVA_UTIL_SET, scope);
    PsiClass collection = facade.findClass(JAVA_UTIL_COLLECTION, scope);
    if (list == null || set == null || collection == null) return Collections.emptyList();

    PsiType listType = null;
    PsiType setType = null;
    boolean hasIterable = false;
    for (ExpectedTypeInfo info : expectedTypes) {
      PsiType type = info.getDefaultType();
      PsiClass expectedClass = PsiUtil.resolveClassInClassTypeOnly(type);
      PsiType expectedComponent = PsiUtil.extractIterableTypeParameter(type, true);
      if (expectedClass == null || expectedComponent == null || !TypeConversionUtil.isAssignable(expectedComponent, component)) continue;
      hasIterable = true;

      if (InheritanceUtil.isInheritorOrSelf(list, expectedClass, true)) {
        listType = type;
      }
      if (InheritanceUtil.isInheritorOrSelf(set, expectedClass, true)) {
        setType = type;
      }
    }

    if (expectedTypes.isEmpty()) {
      listType = factory.createType(list, component);
      setType = factory.createType(set, component);
    }

    List<Pair<String, PsiType>> result = new ArrayList<>();
    if (listType != null) {
      result.add(Pair.create("toList", listType));
    }
    if (setType != null) {
      result.add(Pair.create("toSet", setType));
    }
    if (expectedTypes.isEmpty() || hasIterable) {
      result.add(Pair.create("toCollection", factory.createType(collection, component)));
    }
    return result;
  }

  private static class MyLookupElement extends LookupElement implements TypedLookupItem {
    private final String myLookupString;
    private final String myTypeText;
    private final String myMethodName;
    @NotNull private final PsiType myExpectedType;
    private final boolean myHasImport;

    MyLookupElement(String methodName, @NotNull PsiType expectedType, @NotNull PsiElement context) {
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
    public void handleInsert(InsertionContext context) {
      context.getDocument().replaceString(context.getStartOffset(), context.getTailOffset(), getInsertString());
      context.commitDocument();
      
      PsiMethodCallExpression call =
        PsiTreeUtil.findElementOfClassAtOffset(context.getFile(), context.getStartOffset(), PsiMethodCallExpression.class, false);
      if (call == null) return;

      PsiExpression[] args = call.getArgumentList().getExpressions();
      if (args.length != 1 || !(args[0] instanceof PsiMethodCallExpression)) return;

      PsiMethodCallExpression innerCall = (PsiMethodCallExpression)args[0];
      PsiMethod collectorMethod = innerCall.resolveMethod();
      if (collectorMethod != null && collectorMethod.getParameterList().getParametersCount() > 0) {
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
  }
}
