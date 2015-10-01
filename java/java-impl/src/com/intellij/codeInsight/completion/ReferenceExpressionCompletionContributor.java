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

import com.intellij.codeInsight.lookup.ExpressionLookupItem;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.featureStatistics.FeatureUsageTracker;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Iconable;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.patterns.ElementPattern;
import com.intellij.patterns.PsiMethodPattern;
import com.intellij.patterns.StandardPatterns;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.filters.*;
import com.intellij.psi.filters.element.ModifierFilter;
import com.intellij.psi.filters.types.AssignableFromFilter;
import com.intellij.psi.infos.CandidateInfo;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.Consumer;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.PlatformIcons;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

import static com.intellij.patterns.PsiJavaPatterns.psiElement;
import static com.intellij.patterns.PsiJavaPatterns.psiMethod;

/**
 * @author peter
 */
public class ReferenceExpressionCompletionContributor {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.completion.ReferenceExpressionCompletionContributor");
  private static final PsiMethodPattern OBJECT_METHOD_PATTERN = psiMethod().withName(
    StandardPatterns.string().oneOf("hashCode", "equals", "finalize", "wait", "notify", "notifyAll", "getClass", "clone", "toString")).
      definedInClass(CommonClassNames.JAVA_LANG_OBJECT);
  private static final PrefixMatcher TRUE_MATCHER = new PrefixMatcher("") {
    @Override
    public boolean prefixMatches(@NotNull String name) {
      return true;
    }

    @NotNull
    @Override
    public PrefixMatcher cloneWithPrefix(@NotNull String prefix) {
      return this;
    }
  };
  public static final ElementPattern<PsiElement> IN_SWITCH_LABEL =
    psiElement().withSuperParent(2, psiElement(PsiSwitchLabelStatement.class).withSuperParent(2, PsiSwitchStatement.class));

  @NotNull 
  private static ElementFilter getReferenceFilter(PsiElement element, boolean allowRecursion) {
    //throw foo
    if (psiElement().withParent(psiElement(PsiReferenceExpression.class).withParent(PsiThrowStatement.class)).accepts(element)) {
      return TrueFilter.INSTANCE;
    }

    if (psiElement().inside(
      StandardPatterns.or(
        psiElement(PsiAnnotationParameterList.class),
        psiElement(PsiSwitchLabelStatement.class))
    ).accepts(element)) {
      return new ElementExtractorFilter(new AndFilter(
          new ClassFilter(PsiField.class),
          new ModifierFilter(PsiKeyword.STATIC, PsiKeyword.FINAL)
      ));
    }

    final PsiForeachStatement foreach = PsiTreeUtil.getParentOfType(element, PsiForeachStatement.class);
    if (foreach != null && !PsiTreeUtil.isAncestor(foreach.getBody(), element, false)) {
      return new ElementExtractorFilter(new ElementFilter() {
        @Override
        public boolean isAcceptable(Object element, @Nullable PsiElement context) {
          return element != foreach.getIterationParameter();
        }

        @Override
        public boolean isClassAcceptable(Class hintClass) {
          return true;
        }
      });
    }

    if (!allowRecursion) {
      final ElementFilter filter = RecursionWeigher.recursionFilter(element);
      if (filter != null) {
        return new ElementExtractorFilter(filter);
      }
    }

    return TrueFilter.INSTANCE;
  }

  @Nullable 
  public static Runnable fillCompletionVariants(final JavaSmartCompletionParameters parameters, final Consumer<LookupElement> result) {
    final PsiElement element = parameters.getPosition();
    if (JavaSmartCompletionContributor.INSIDE_TYPECAST_EXPRESSION.accepts(element)) return null;
    if (JavaKeywordCompletion.isAfterPrimitiveOrArrayType(element)) return null;

    final int offset = parameters.getParameters().getOffset();
    final PsiReference reference = element.getContainingFile().findReferenceAt(offset);
    if (reference != null) {
      final ElementFilter filter = getReferenceFilter(element, false);
      for (final LookupElement item : completeFinalReference(element, reference, filter, parameters)) {
        result.consume(item);
      }

      final boolean secondTime = parameters.getParameters().getInvocationCount() >= 2;

      final Set<LookupElement> base =
        JavaSmartCompletionContributor.completeReference(element, reference, filter, false, true, parameters.getParameters(), PrefixMatcher.ALWAYS_TRUE);
      for (final LookupElement item : new LinkedHashSet<LookupElement>(base)) {
        ExpressionLookupItem access = getSingleArrayElementAccess(element, item);
        if (access != null) {
          base.add(access);
          PsiType type = access.getType();
          if (type != null && parameters.getExpectedType().isAssignableFrom(type)) {
            result.consume(access);
          }
        }
      }

      if (secondTime) {
        return new Runnable() {
          @Override
          public void run() {
            for (final LookupElement item : base) {
              addSecondCompletionVariants(element, reference, item, parameters, result);
            }
            if (!psiElement().afterLeaf(".").accepts(element)) {
              BasicExpressionCompletionContributor.processDataflowExpressionTypes(element, null, TRUE_MATCHER, new Consumer<LookupElement>() {
                @Override
                public void consume(LookupElement baseItem) {
                  addSecondCompletionVariants(element, reference, baseItem, parameters, result);
                }
              });
            }
          }
        };
      }
    }
    return null;
  }

  private static Set<LookupElement> completeFinalReference(final PsiElement element, PsiReference reference, ElementFilter filter,
                                                           final JavaSmartCompletionParameters parameters) {
    final Set<PsiField> used = parameters.getParameters().getInvocationCount() < 2 ? findConstantsUsedInSwitch(element) : Collections.<PsiField>emptySet();

    final Set<LookupElement> elements =
      JavaSmartCompletionContributor.completeReference(element, reference, new AndFilter(filter, new ElementFilter() {
        @Override
        public boolean isAcceptable(Object o, PsiElement context) {
          if (o instanceof CandidateInfo) {
            final CandidateInfo info = (CandidateInfo)o;
            final PsiElement member = info.getElement();

            final PsiType expectedType = parameters.getExpectedType();
            if (expectedType.equals(PsiType.VOID)) {
              return member instanceof PsiMethod;
            }

            //noinspection SuspiciousMethodCalls
            if (member instanceof PsiEnumConstant && used.contains(CompletionUtil.getOriginalOrSelf(member))) {
              return false;
            }

            return AssignableFromFilter.isAcceptable(member, element, expectedType, info.getSubstitutor());
          }
          return false;
        }

        @Override
        public boolean isClassAcceptable(Class hintClass) {
          return true;
        }
      }), false, true, parameters.getParameters(), PrefixMatcher.ALWAYS_TRUE);
    for (LookupElement lookupElement : elements) {
      if (lookupElement.getObject() instanceof PsiMethod) {
        final JavaMethodCallElement item = lookupElement.as(JavaMethodCallElement.CLASS_CONDITION_KEY);
        if (item != null) {
          final PsiMethod method = (PsiMethod)lookupElement.getObject();
          if (SmartCompletionDecorator.hasUnboundTypeParams(method, parameters.getExpectedType())) {
            item.setInferenceSubstitutor(SmartCompletionDecorator.calculateMethodReturnTypeSubstitutor(method, parameters.getExpectedType()), element);
          }
        }
      }
    }

    return elements;
  }

  @NotNull 
  public static Set<PsiField> findConstantsUsedInSwitch(@Nullable PsiElement position) {
    if (IN_SWITCH_LABEL.accepts(position)) {
      Set<PsiField> used = ContainerUtil.newLinkedHashSet();
      PsiSwitchStatement sw = PsiTreeUtil.getParentOfType(position, PsiSwitchStatement.class);
      assert sw != null;
      final PsiCodeBlock body = sw.getBody();
      assert body != null;
      for (PsiStatement statement : body.getStatements()) {
        if (statement instanceof PsiSwitchLabelStatement) {
          final PsiExpression value = ((PsiSwitchLabelStatement)statement).getCaseValue();
          if (value instanceof PsiReferenceExpression) {
            final PsiElement target = ((PsiReferenceExpression)value).resolve();
            if (target instanceof PsiField) {
              used.add(CompletionUtil.getOriginalOrSelf((PsiField)target));
            }
          }
        }
      }
      return used;
    }
    return Collections.emptySet();
  }

  @Nullable
  private static ExpressionLookupItem getSingleArrayElementAccess(PsiElement element, LookupElement item) {
    if (item.getObject() instanceof PsiLocalVariable) {
      final PsiLocalVariable variable = (PsiLocalVariable)item.getObject();
      final PsiType type = variable.getType();
      final PsiExpression expression = variable.getInitializer();
      if (type instanceof PsiArrayType && expression instanceof PsiNewExpression) {
        final PsiNewExpression newExpression = (PsiNewExpression)expression;
        final PsiExpression[] dimensions = newExpression.getArrayDimensions();
        if (dimensions.length == 1 && "1".equals(dimensions[0].getText()) && newExpression.getArrayInitializer() == null) {
          final String text = variable.getName() + "[0]";
          return new ExpressionLookupItem(createExpression(text, element), variable.getIcon(Iconable.ICON_FLAG_VISIBILITY), text, text);
        }
      }
    }
    return null;
  }

  private static PsiExpression createExpression(String text, PsiElement element) {
    return JavaPsiFacade.getInstance(element.getProject()).getElementFactory().createExpressionFromText(text, element);
  }

  private static void addSecondCompletionVariants(PsiElement element, PsiReference reference, LookupElement baseItem,
                                                  JavaSmartCompletionParameters parameters, Consumer<LookupElement> result) {
    final Object object = baseItem.getObject();

    try {
      PsiType itemType = JavaCompletionUtil.getLookupElementType(baseItem);
      if (itemType instanceof PsiWildcardType) {
        itemType = ((PsiWildcardType)itemType).getExtendsBound();
      }
      if (itemType == null) return;
      assert itemType.isValid() : baseItem + "; " + baseItem.getClass();

      final PsiElement element1 = reference.getElement();
      final PsiElement qualifier =
        element1 instanceof PsiJavaCodeReferenceElement ? ((PsiJavaCodeReferenceElement)element1).getQualifier() : null;
      final PsiType expectedType = parameters.getExpectedType();
      if (!OBJECT_METHOD_PATTERN.accepts(object) || allowGetClass(object, parameters)) {
        if (parameters.getParameters().getInvocationCount() >= 3 || !itemType.equalsToText(CommonClassNames.JAVA_LANG_STRING)) {
          if (!(object instanceof PsiMethod && ((PsiMethod)object).getParameterList().getParametersCount() > 0)) {
            addChainedCallVariants(element, baseItem, result, itemType, expectedType, parameters);
          }
        }
      }

      final String prefix = getItemText(object);
      if (prefix == null) return;

      addConversionsToArray(element, prefix, itemType, result, qualifier, expectedType);

      addToArrayConversions(element, object, prefix, itemType, result, qualifier, expectedType);

      addArrayMemberAccessors(element, prefix, itemType, qualifier, result, (PsiModifierListOwner)object, expectedType);
    }
    catch (IncorrectOperationException ignored) {
    }
  }

  private static void addArrayMemberAccessors(final PsiElement element, final String prefix, final PsiType itemType,
                                              final PsiElement qualifier, final Consumer<LookupElement> result, PsiModifierListOwner object,
                                              final PsiType expectedType)
      throws IncorrectOperationException {
    if (itemType instanceof PsiArrayType && expectedType.isAssignableFrom(((PsiArrayType)itemType).getComponentType())) {
      final PsiExpression conversion = createExpression(getQualifierText(qualifier) + prefix + "[0]", element);
      result.consume(new ExpressionLookupItem(conversion, object.getIcon(Iconable.ICON_FLAG_VISIBILITY), prefix + "[...]", prefix) {
        @Override
        public void handleInsert(InsertionContext context) {
          FeatureUsageTracker.getInstance().triggerFeatureUsed(JavaCompletionFeatures.SECOND_SMART_COMPLETION_ARRAY_MEMBER);

          final int tailOffset = context.getTailOffset();
          final String callSpace = getSpace(CodeStyleSettingsManager.getSettings(element.getProject()).SPACE_WITHIN_BRACKETS);
          context.getDocument().insertString(tailOffset, "[" + callSpace + callSpace + "]");
          context.getEditor().getCaretModel().moveToOffset(tailOffset + 1 + callSpace.length());
        }
      });
    }
  }

  private static boolean allowGetClass(final Object object, final JavaSmartCompletionParameters parameters) {
    if (!"getClass".equals(((PsiMethod)object).getName())) return false;

    final PsiType type = parameters.getDefaultType();
    @NonNls final String canonicalText = type.getCanonicalText();
    if ("java.lang.ClassLoader".equals(canonicalText)) return true;
    if (canonicalText.startsWith("java.lang.reflect.")) return true;
    return false;
  }

  private static void addConversionsToArray(final PsiElement element,
                                            final String prefix,
                                            final PsiType itemType,
                                            final Consumer<LookupElement> result,
                                            @Nullable PsiElement qualifier,
                                            final PsiType expectedType) throws IncorrectOperationException {
    final String methodName = getArraysConversionMethod(itemType, expectedType);
    if (methodName == null) return;
    
    final String qualifierText = getQualifierText(qualifier);
    final PsiExpression conversion = createExpression("java.util.Arrays." + methodName + "(" + qualifierText + prefix + ")", element);
    final String presentable = "Arrays." + methodName + "(" + qualifierText + prefix + ")";
    String[] lookupStrings = {StringUtil.isEmpty(qualifierText) ? presentable : prefix, prefix, presentable, methodName + "(" + prefix + ")"};
    result.consume(new ExpressionLookupItem(conversion, PlatformIcons.METHOD_ICON, presentable, lookupStrings) {
      @Override
      public void handleInsert(InsertionContext context) {
        FeatureUsageTracker.getInstance().triggerFeatureUsed(JavaCompletionFeatures.SECOND_SMART_COMPLETION_ASLIST);

        int startOffset = context.getStartOffset() - qualifierText.length();
        final Project project = element.getProject();
        final String callSpace = getSpace(CodeStyleSettingsManager.getSettings(project).SPACE_WITHIN_METHOD_CALL_PARENTHESES);
        final String newText = "java.util.Arrays." + methodName + "(" + callSpace + qualifierText + prefix + callSpace + ")";
        context.getDocument().replaceString(startOffset, context.getTailOffset(), newText);

        context.commitDocument();
        JavaCodeStyleManager.getInstance(project).shortenClassReferences(context.getFile(), startOffset, startOffset + CommonClassNames.JAVA_UTIL_ARRAYS.length());
      }
    });
  }

  @Nullable
  private static String getArraysConversionMethod(PsiType itemType, PsiType expectedType) {
    String methodName = "asList";
    PsiType componentType = PsiUtil.extractIterableTypeParameter(expectedType, true);
    if (componentType == null) {
      methodName = "stream";
      componentType = getStreamComponentType(expectedType);
      PsiPrimitiveType unboxedType = PsiPrimitiveType.getUnboxedType(componentType);
      if (unboxedType != null) {
        componentType = unboxedType;
      }
    }

    if (componentType == null ||
        !(itemType instanceof PsiArrayType) ||
        !componentType.isAssignableFrom(((PsiArrayType)itemType).getComponentType())) {
      return null;

    }
    return methodName;
  }

  private static PsiType getStreamComponentType(PsiType expectedType) {
    return PsiUtil.substituteTypeParameter(expectedType, CommonClassNames.JAVA_UTIL_STREAM_BASE_STREAM, 0, true);
  }

  private static void addToArrayConversions(final PsiElement element, final Object object, final String prefix, final PsiType itemType,
                                            final Consumer<LookupElement> result, @Nullable final PsiElement qualifier,
                                            final PsiType expectedType) {
    final String callSpace = getSpace(CodeStyleSettingsManager.getSettings(element.getProject()).SPACE_WITHIN_METHOD_CALL_PARENTHESES);
    final PsiType componentType = PsiUtil.extractIterableTypeParameter(itemType, true);
    if (componentType == null || !(expectedType instanceof PsiArrayType)) return;

    final PsiArrayType type = (PsiArrayType)expectedType;
    if (!type.getComponentType().isAssignableFrom(componentType) ||
        componentType instanceof PsiClassType && ((PsiClassType) componentType).hasParameters()) {
      return;
    }

    final String bracketSpace = getSpace(CodeStyleSettingsManager.getSettings(element.getProject()).SPACE_WITHIN_BRACKETS);
    if (object instanceof PsiVariable && !JavaCompletionUtil.mayHaveSideEffects(qualifier)) {
      final PsiVariable variable = (PsiVariable)object;
      addToArrayConversion(element, prefix,
                           "new " + componentType.getCanonicalText() +
                           "[" + bracketSpace + getQualifierText(qualifier) + variable.getName() + ".size(" + callSpace + ")" + bracketSpace + "]",
                           "new " + getQualifierText(qualifier) + componentType.getPresentableText() + "[" + variable.getName() + ".size()]", result, qualifier);
    } else {
      boolean hasEmptyArrayField = false;
      final PsiClass psiClass = PsiUtil.resolveClassInType(type);
      if (psiClass != null) {
        for (final PsiField field : psiClass.getAllFields()) {
          if (field.hasModifierProperty(PsiModifier.STATIC) && field.hasModifierProperty(PsiModifier.FINAL) &&
              JavaPsiFacade.getInstance(field.getProject()).getResolveHelper().isAccessible(field, element, null) &&
              type.isAssignableFrom(field.getType()) && isEmptyArrayInitializer(field.getInitializer())) {
            boolean needQualify;
            try {
              needQualify = !field.isEquivalentTo(((PsiReferenceExpression)createExpression(field.getName(), element)).resolve());
            }
            catch (IncorrectOperationException e) {
              continue;
            }

            addToArrayConversion(element, prefix,
                                 (needQualify ? field.getContainingClass().getQualifiedName() + "." : "") + field.getName(),
                                 (needQualify ? field.getContainingClass().getName() + "." : "") + field.getName(), result, qualifier);
            hasEmptyArrayField = true;
          }
        }
      }
      if (!hasEmptyArrayField) {
        addToArrayConversion(element, prefix,
                             "new " + componentType.getCanonicalText() + "[" + bracketSpace + "0" + bracketSpace + "]",
                             "new " + componentType.getPresentableText() + "[0]", result, qualifier);
      }
    }
  }

  private static String getQualifierText(@Nullable final PsiElement qualifier) {
    return qualifier == null ? "" : qualifier.getText() + ".";
  }

  private static void addChainedCallVariants(final PsiElement place, LookupElement qualifierItem,
                                             final Consumer<LookupElement> result,
                                             PsiType qualifierType,
                                             final PsiType expectedType, JavaSmartCompletionParameters parameters) throws IncorrectOperationException {
    final PsiReferenceExpression mockRef = createMockReference(place, qualifierType, qualifierItem);
    if (mockRef == null) {
      return;
    }

    final ElementFilter filter = getReferenceFilter(place, true);
    for (final LookupElement item : completeFinalReference(place, mockRef, filter, parameters)) {
      if (shouldChain(place, qualifierType, expectedType, item)) {
        result.consume(new JavaChainLookupElement(qualifierItem, item) {
          @Override
          public void handleInsert(InsertionContext context) {
            FeatureUsageTracker.getInstance().triggerFeatureUsed(JavaCompletionFeatures.SECOND_SMART_COMPLETION_CHAIN);
            super.handleInsert(context);
          }
        });
      }
    }
  }

  @Nullable
  public static PsiReferenceExpression createMockReference(final PsiElement place, @NotNull PsiType qualifierType, LookupElement qualifierItem) {
    PsiElementFactory factory = JavaPsiFacade.getElementFactory(place.getProject());
    if (qualifierItem.getObject() instanceof PsiClass) {
      final String qname = ((PsiClass)qualifierItem.getObject()).getQualifiedName();
      if (qname == null) return null;
      
      final String text = qname + ".xxx";
      try {
        final PsiExpression expr = factory.createExpressionFromText(text, place);
        if (expr instanceof PsiReferenceExpression) {
          return (PsiReferenceExpression)expr;
        }
        return null; // ignore ill-formed qualified names like "org.spark-project.jetty" that can't be used from Java code anyway
      }
      catch (IncorrectOperationException e) {
        LOG.info(e);
        return null;
      }
    }

    return (PsiReferenceExpression) factory.createExpressionFromText("xxx.xxx", JavaCompletionUtil
      .createContextWithXxxVariable(place, qualifierType));
  }

  private static boolean shouldChain(PsiElement element, PsiType qualifierType, PsiType expectedType, LookupElement item) {
    Object object = item.getObject();
    if (object instanceof PsiModifierListOwner && ((PsiModifierListOwner)object).hasModifierProperty(PsiModifier.STATIC)) {
      return false;
    }

    if (object instanceof PsiMethod) {
      final PsiMethod method = (PsiMethod)object;
      if (psiMethod().withName("toArray").withParameterCount(1)
                     .definedInClass(CommonClassNames.JAVA_UTIL_COLLECTION).accepts(method)) {
        return false;
      }
      final PsiMethod parentMethod = PsiTreeUtil.getParentOfType(element, PsiMethod.class);
      if (isUselessObjectMethod(method, parentMethod, qualifierType)) {
        return false;
      }

      final PsiType type = method.getReturnType();
      if (type instanceof PsiClassType) {
        final PsiClassType classType = (PsiClassType)type;
        final PsiClass psiClass = classType.resolve();
        if (psiClass instanceof PsiTypeParameter && method.getTypeParameterList() == psiClass.getParent()) {
          final PsiTypeParameter typeParameter = (PsiTypeParameter)psiClass;
          if (typeParameter.getExtendsListTypes().length == 0) return false;
          if (!expectedType.isAssignableFrom(TypeConversionUtil.typeParameterErasure(typeParameter))) return false;
        }
      }
    }
    return true;
  }

  private static boolean isUselessObjectMethod(PsiMethod method, PsiMethod parentMethod, PsiType qualifierType) {
    if (!OBJECT_METHOD_PATTERN.accepts(method)) {
      return false;
    }

    if (OBJECT_METHOD_PATTERN.accepts(parentMethod) && method.getName().equals(parentMethod.getName())) {
      return false;
    }

    if ("toString".equals(method.getName())) {
      if (qualifierType.equalsToText(CommonClassNames.JAVA_LANG_STRING_BUFFER) ||
          InheritanceUtil.isInheritor(qualifierType, CommonClassNames.JAVA_LANG_ABSTRACT_STRING_BUILDER)) {
        return false;
      }
    }

    return true;
  }

  private static void addToArrayConversion(final PsiElement element, final String prefix, @NonNls final String expressionString, @NonNls String presentableString, final Consumer<LookupElement> result, PsiElement qualifier) {
    final boolean callSpace = CodeStyleSettingsManager.getSettings(element.getProject()).SPACE_WITHIN_METHOD_CALL_PARENTHESES;
    final PsiExpression conversion;
    try {
      conversion = createExpression(
        getQualifierText(qualifier) + prefix + ".toArray(" + getSpace(callSpace) + expressionString + getSpace(callSpace) + ")", element);
    }
    catch (IncorrectOperationException e) {
      return;
    }

    String[] lookupStrings = {prefix + ".toArray(" + getSpace(callSpace) + expressionString + getSpace(callSpace) + ")", presentableString};
    result.consume(new ExpressionLookupItem(conversion, PlatformIcons.METHOD_ICON, prefix + ".toArray(" + presentableString + ")", lookupStrings) {
        @Override
        public void handleInsert(InsertionContext context) {
          FeatureUsageTracker.getInstance().triggerFeatureUsed(JavaCompletionFeatures.SECOND_SMART_COMPLETION_TOAR);

          context.commitDocument();
          JavaCodeStyleManager.getInstance(context.getProject()).shortenClassReferences(context.getFile(), context.getStartOffset(), context.getTailOffset());
        }
      });
  }

  private static boolean isEmptyArrayInitializer(@Nullable PsiElement element) {
    if (element instanceof PsiNewExpression) {
      final PsiNewExpression expression = (PsiNewExpression)element;
      final PsiExpression[] dimensions = expression.getArrayDimensions();
      for (final PsiExpression dimension : dimensions) {
        if (!(dimension instanceof PsiLiteralExpression) || !"0".equals(dimension.getText())) {
          return false;
        }
      }
      final PsiArrayInitializerExpression initializer = expression.getArrayInitializer();
      if (initializer != null && initializer.getInitializers().length > 0) return false;

      return true;
    }
    return false;
  }

  @Nullable
  private static String getItemText(Object o) {
    if (o instanceof PsiMethod) {
      final PsiMethod method = (PsiMethod)o;
      final PsiType type = method.getReturnType();
      if (PsiType.VOID.equals(type) || PsiType.NULL.equals(type)) return null;
      if (method.getParameterList().getParametersCount() > 0) return null;
      return method.getName() + "(" + getSpace(CodeStyleSettingsManager.getSettings(method.getProject()).SPACE_WITHIN_METHOD_CALL_PARENTHESES) + ")"; }
    else if (o instanceof PsiVariable) {
      return ((PsiVariable)o).getName();
    }
    return null;
  }

  private static String getSpace(boolean needSpace) {
    return needSpace ? " " : "";
  }

}
