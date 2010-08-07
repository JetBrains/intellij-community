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
import com.intellij.codeInsight.lookup.LookupItem;
import com.intellij.featureStatistics.FeatureUsageTracker;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Iconable;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.patterns.PsiJavaPatterns;
import com.intellij.patterns.PsiMethodPattern;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.filters.*;
import com.intellij.psi.filters.element.ModifierFilter;
import com.intellij.psi.filters.types.AssignableFromFilter;
import com.intellij.psi.impl.source.PostprocessReformattingAspect;
import com.intellij.psi.infos.CandidateInfo;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.Consumer;
import com.intellij.util.Icons;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

import static com.intellij.patterns.PsiJavaPatterns.psiElement;
import static com.intellij.patterns.PsiJavaPatterns.psiMethod;

/**
 * @author peter
 */
public class ReferenceExpressionCompletionContributor {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.completion.ReferenceExpressionCompletionContributor");
  private static final PsiMethodPattern OBJECT_METHOD_PATTERN = psiMethod().withName(
      PsiJavaPatterns.string().oneOf("hashCode", "equals", "finalize", "wait", "notify", "notifyAll", "getClass", "clone", "toString")).
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

  private ReferenceExpressionCompletionContributor() {
  }

  @NotNull 
  private static ElementFilter getReferenceFilter(PsiElement element, boolean allowRecursion) {
    //throw foo
    if (psiElement().withParent(psiElement(PsiReferenceExpression.class).withParent(PsiThrowStatement.class)).accepts(element)) {
      return TrueFilter.INSTANCE;
    }

    if (psiElement().inside(
        PsiJavaPatterns.or(
            psiElement(PsiAnnotationParameterList.class),
            psiElement(PsiSwitchLabelStatement.class))
    ).accepts(element)) {
      return new ElementExtractorFilter(new AndFilter(
          new ClassFilter(PsiField.class),
          new ModifierFilter(PsiKeyword.STATIC, PsiKeyword.FINAL)
      ));
    }

    if (!allowRecursion) {
      final ElementFilter filter = JavaCompletionUtil.recursionFilter(element);
      if (filter != null) {
        return new ElementExtractorFilter(filter);
      }
    }

    return TrueFilter.INSTANCE;
  }

  public static void fillCompletionVariants(final JavaSmartCompletionParameters parameters, final CompletionResultSet result) {
    ApplicationManager.getApplication().runReadAction(new Runnable() {
      public void run() {
        final PsiElement element = parameters.getPosition();
        if (JavaSmartCompletionContributor.INSIDE_TYPECAST_EXPRESSION.accepts(element)) return;

        final int offset = parameters.getOffset();
        final PsiReference reference = element.getContainingFile().findReferenceAt(offset);
        if (reference != null) {
          final ElementFilter filter = getReferenceFilter(element, false);
          for (final LookupElement item : completeFinalReference(element, reference, filter, parameters)) {
            result.addElement(item);
          }

          final boolean secondTime = parameters.getInvocationCount() >= 2;

          for (final LookupElement item : JavaSmartCompletionContributor.completeReference(element, reference, filter, false, parameters)) {
            addSingleArrayElementAccess(element, item, parameters, result);
            
            if (secondTime) {
              addSecondCompletionVariants(element, reference, item, parameters, result);
            }
          }

          if (secondTime) {
            if (!psiElement().afterLeaf(".").accepts(element)) {
              BasicExpressionCompletionContributor.processDataflowExpressionTypes(element, null, TRUE_MATCHER, new Consumer<LookupElement>() {
                public void consume(LookupElement baseItem) {
                  addSecondCompletionVariants(element, reference, baseItem, parameters, result);
                }
              });
            }
          }
        }
      }
    });
  }

  private static Set<LookupElement> completeFinalReference(final PsiElement element, PsiReference reference, ElementFilter filter,
                                                           final JavaSmartCompletionParameters parameters) {
    final Set<LookupElement> elements =
      JavaSmartCompletionContributor.completeReference(element, reference, new AndFilter(filter, new ElementFilter() {
        public boolean isAcceptable(Object o, PsiElement context) {
          if (o instanceof CandidateInfo) {
            final CandidateInfo info = (CandidateInfo)o;
            final PsiElement member = info.getElement();

            final PsiType expectedType = parameters.getExpectedType();
            if (expectedType.equals(PsiType.VOID)) {
              return member instanceof PsiMethod;
            }

            return AssignableFromFilter.isAcceptable(member, element, expectedType, info.getSubstitutor());
          }
          return false;
        }

        public boolean isClassAcceptable(Class hintClass) {
          return true;
        }
      }), false, parameters);
    for (LookupElement lookupElement : elements) {
      if (lookupElement.getObject() instanceof PsiMethod) {
        final JavaMethodCallElement item = lookupElement.as(JavaMethodCallElement.class);
        assert item != null;
        final PsiMethod method = (PsiMethod)lookupElement.getObject();
        if (SmartCompletionDecorator.hasUnboundTypeParams(method)) {
          item.setInferenceSubstitutor(SmartCompletionDecorator.calculateMethodReturnTypeSubstitutor(method, parameters.getExpectedType()));
        }
      }
    }

    return elements;
  }

  private static void addSingleArrayElementAccess(PsiElement element, LookupElement item, JavaSmartCompletionParameters parameters,
                                           CompletionResultSet result) {
    if (item.getObject() instanceof PsiLocalVariable) {
      final PsiLocalVariable variable = (PsiLocalVariable)item.getObject();
      final PsiType type = variable.getType();
      if (type instanceof PsiArrayType && parameters.getExpectedType().isAssignableFrom(((PsiArrayType)type).getComponentType())) {
        final PsiExpression expression = variable.getInitializer();
        if (expression instanceof PsiNewExpression) {
          final PsiNewExpression newExpression = (PsiNewExpression)expression;
          final PsiExpression[] dimensions = newExpression.getArrayDimensions();
          if (dimensions.length == 1 && "1".equals(dimensions[0].getText()) && newExpression.getArrayInitializer() == null) {
            final String text = variable.getName() + "[0]";
            final PsiExpression conversion = createExpression(text, element);
            result.addElement(new ExpressionLookupItem(conversion).setIcon(variable.getIcon(Iconable.ICON_FLAG_VISIBILITY)));
          }
        }
      }
    }
  }

  private static PsiExpression createExpression(String text, PsiElement element) {
    return JavaPsiFacade.getInstance(element.getProject()).getElementFactory().createExpressionFromText(text, element);
  }

  private static void addSecondCompletionVariants(PsiElement element, PsiReference reference, LookupElement baseItem,
                                                  JavaSmartCompletionParameters parameters, CompletionResultSet result) {
    final Object object = baseItem.getObject();

    try {
      PsiType itemType = JavaCompletionUtil.getLookupElementType(baseItem);
      if (itemType instanceof PsiWildcardType) {
        itemType = ((PsiWildcardType)itemType).getExtendsBound();
      }
      if (itemType == null) return;

      final PsiElement qualifier = JavaCompletionUtil.getQualifier(reference.getElement());
      final PsiType expectedType = parameters.getExpectedType();
      if (!OBJECT_METHOD_PATTERN.accepts(object) || allowGetClass(object, parameters)) {
        if (!itemType.equalsToText(CommonClassNames.JAVA_LANG_STRING)) {
          addChainedCallVariants(element, baseItem, result, itemType, expectedType, parameters);
        }
      }

      final String prefix = getItemText(object);
      if (prefix == null) return;

      addArraysAsListConversions(element, prefix, itemType, result, qualifier, expectedType);

      addToArrayConversions(element, object, prefix, itemType, result, qualifier, expectedType);

      addArrayMemberAccessors(element, prefix, itemType, qualifier, result, (PsiModifierListOwner)object, expectedType);
    }
    catch (IncorrectOperationException ignored) {
    }
  }

  private static void addArrayMemberAccessors(final PsiElement element, final String prefix, final PsiType itemType,
                                              final PsiElement qualifier, final CompletionResultSet result, PsiModifierListOwner object,
                                              final PsiType expectedType)
      throws IncorrectOperationException {
    if (itemType instanceof PsiArrayType && expectedType.isAssignableFrom(((PsiArrayType)itemType).getComponentType())) {
      final PsiExpression conversion = createExpression(getQualifierText(qualifier) + prefix + "[0]", element);
      final LookupItem item = new ExpressionLookupItem(conversion);

      @NonNls final String presentable = prefix + "[...]";
      item.setLookupString(prefix);
      item.setPresentableText(presentable);
      item.addLookupStrings(prefix);
      item.setIcon(object.getIcon(Iconable.ICON_FLAG_VISIBILITY));
      item.setInsertHandler(new InsertHandler<LookupElement>() {
        public void handleInsert(InsertionContext context, LookupElement item) {
          FeatureUsageTracker.getInstance().triggerFeatureUsed(JavaCompletionFeatures.SECOND_SMART_COMPLETION_ARRAY_MEMBER);
          final Editor editor = context.getEditor();
          final int startOffset = context.getStartOffset();

          final Document document = editor.getDocument();
          final int tailOffset = startOffset + item.getLookupString().length();
          final String callSpace = getSpace(CodeStyleSettingsManager.getSettings(element.getProject()).SPACE_WITHIN_BRACKETS);
          final String access = "[" + callSpace + callSpace + "]";
          document.insertString(tailOffset, access);
          editor.getCaretModel().moveToOffset(tailOffset + 1 + callSpace.length());
        }
      });
      result.addElement(item);
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

  private static void addArraysAsListConversions(final PsiElement element, final String prefix, final PsiType itemType, final CompletionResultSet result,
                                                 @Nullable PsiElement qualifier,
                                                 final PsiType expectedType) throws IncorrectOperationException {
    PsiType componentType = PsiUtil.extractIterableTypeParameter(expectedType, true);
    if (componentType == null ||
        !(itemType instanceof PsiArrayType) ||
        !componentType.isAssignableFrom(((PsiArrayType)itemType).getComponentType())) {
      return;

    }
    final String qualifierText = getQualifierText(qualifier);
    final PsiExpression conversion = createExpression("java.util.Arrays.asList(" + qualifierText + prefix + ")", element);
    final LookupItem item = new ExpressionLookupItem(conversion);

    @NonNls final String presentable = "Arrays.asList(" + qualifierText + prefix + ")";
    item.setLookupString(StringUtil.isEmpty(qualifierText) ? presentable : prefix);
    item.setPresentableText(presentable);
    item.addLookupStrings(prefix, presentable, "asList(" + prefix + ")");
    item.setIcon(Icons.METHOD_ICON);
    item.setInsertHandler(new InsertHandler<LookupElement>() {
      public void handleInsert(InsertionContext context, LookupElement item) {
        FeatureUsageTracker.getInstance().triggerFeatureUsed(JavaCompletionFeatures.SECOND_SMART_COMPLETION_ASLIST);

        final Editor editor = context.getEditor();
        int startOffset = context.getStartOffset();
        final Document document = editor.getDocument();
        final int tailOffset = startOffset + item.getLookupString().length();
        startOffset -= qualifierText.length();
        final Project project = element.getProject();
        final String callSpace = getSpace(CodeStyleSettingsManager.getSettings(project).SPACE_WITHIN_METHOD_CALL_PARENTHESES);
        @NonNls final String newText = "java.util.Arrays.asList(" + callSpace + qualifierText + prefix + callSpace + ")";
        document.replaceString(startOffset, tailOffset, newText);

        PsiDocumentManager.getInstance(project).commitDocument(document);
        final PsiFile file = PsiDocumentManager.getInstance(project).getPsiFile(document);
        try {
          JavaCodeStyleManager.getInstance(project)
              .shortenClassReferences(file, startOffset, startOffset + CommonClassNames.JAVA_UTIL_ARRAYS.length());
        }
        catch (IncorrectOperationException e) {
          LOG.error(e);
        }
        PostprocessReformattingAspect.getInstance(project).doPostponedFormatting();
      }
    });
    result.addElement(item);
  }

  private static void addToArrayConversions(final PsiElement element, final Object object, final String prefix, final PsiType itemType,
                                            final CompletionResultSet result, @Nullable final PsiElement qualifier,
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
    if (object instanceof PsiVariable && !JavaCompletionUtil.containsMethodCalls(qualifier)) {
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
                                             final CompletionResultSet result,
                                             PsiType qualifierType,
                                             final PsiType expectedType, JavaSmartCompletionParameters parameters) throws IncorrectOperationException {
    final PsiElementFactory elementFactory = JavaPsiFacade.getInstance(place.getProject()).getElementFactory();
    PsiType varType = qualifierType;
    if (varType instanceof PsiEllipsisType) {
      varType = ((PsiEllipsisType)varType).getComponentType();
    }
    if (varType instanceof PsiWildcardType) {
      varType = TypeConversionUtil.erasure(expectedType);
    }

    final String typeText = varType.getCanonicalText();
    final JavaCodeFragment block = elementFactory.createCodeBlockCodeFragment(typeText + " xxx;xxx.xxx;", place, false);
    final PsiElement secondChild = block.getChildren()[1];
    if (!(secondChild instanceof PsiExpressionStatement)) {
      LOG.error(typeText);
    }
    final PsiExpressionStatement expressionStatement = (PsiExpressionStatement)secondChild;
    final PsiReferenceExpression mockRef = (PsiReferenceExpression) expressionStatement.getExpression();

    final ElementFilter filter = getReferenceFilter(place, true);
    for (final LookupElement item : completeFinalReference(place, mockRef, filter, parameters)) {
      if (shoudChain(place, varType, expectedType, item)) {
        result.addElement(JavaChainLookupElement.chainElements(qualifierItem, item));
      }
    }
  }

  private static boolean shoudChain(PsiElement element, PsiType qualifierType, PsiType expectedType, LookupElement item) {
    if (item.getObject() instanceof PsiMethod) {
      final PsiMethod method = (PsiMethod)item.getObject();
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

  private static void addToArrayConversion(final PsiElement element, final String prefix, @NonNls final String expressionString, @NonNls String presentableString, final CompletionResultSet result, PsiElement qualifier) {
    final boolean callSpace = CodeStyleSettingsManager.getSettings(element.getProject()).SPACE_WITHIN_METHOD_CALL_PARENTHESES;
    final PsiExpression conversion;
    try {
      conversion = createExpression(
        getQualifierText(qualifier) + prefix + ".toArray(" + getSpace(callSpace) + expressionString + getSpace(callSpace) + ")", element);
    }
    catch (IncorrectOperationException e) {
      return;
    }

    final LookupItem item = new ExpressionLookupItem(conversion);
    item.setLookupString(prefix + ".toArray(" + getSpace(callSpace) + expressionString + getSpace(callSpace) + ")");
    item.setPresentableText(prefix + ".toArray(" + presentableString + ")");
    item.addLookupStrings(presentableString);
    item.setIcon(Icons.METHOD_ICON);
    item.setInsertHandler(new InsertHandler<LookupItem>(){
      public void handleInsert(InsertionContext context, LookupItem item) {
        FeatureUsageTracker.getInstance().triggerFeatureUsed(JavaCompletionFeatures.SECOND_SMART_COMPLETION_TOAR);

        final Editor editor = context.getEditor();
        final int startOffset = context.getStartOffset();
        final Document document = editor.getDocument();
        final int tailOffset = startOffset + item.getLookupString().length();
        final Project project = editor.getProject();
        //PsiDocumentManager.getInstance(project).commitDocument(document);
        final PsiFile file = PsiDocumentManager.getInstance(project).getPsiFile(document);
        try {
          JavaCodeStyleManager.getInstance(project).shortenClassReferences(file, startOffset, tailOffset);
        }
        catch (IncorrectOperationException e) {
          LOG.error(e);
        }
        PostprocessReformattingAspect.getInstance(project).doPostponedFormatting();
      }
    });
    result.addElement(item);
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
