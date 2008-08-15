/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.TailType;
import com.intellij.codeInsight.completion.simple.SimpleInsertHandler;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupItem;
import com.intellij.codeInsight.lookup.LookupItemUtil;
import com.intellij.featureStatistics.FeatureUsageTracker;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.patterns.PsiJavaPatterns;
import static com.intellij.patterns.PsiJavaPatterns.psiElement;
import static com.intellij.patterns.PsiJavaPatterns.psiMethod;
import com.intellij.patterns.PsiMethodPattern;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.filters.*;
import com.intellij.psi.filters.element.ExcludeDeclaredFilter;
import com.intellij.psi.filters.element.ModifierFilter;
import com.intellij.psi.filters.element.ExcludeSillyAssignment;
import com.intellij.psi.impl.source.PostprocessReformattingAspect;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.Icons;
import com.intellij.util.IncorrectOperationException;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author peter
 */
public class ReferenceExpressionCompletionContributor extends ExpressionSmartCompletionContributor{
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.completion.ReferenceExpressionCompletionContributor");
  private static final PsiMethodPattern OBJECT_METHOD_PATTERN = psiMethod().withName(
      PsiJavaPatterns.string().oneOf("hashCode", "equals", "finalize", "wait", "notify", "notifyAll", "getClass", "clone", "toString")).
      definedInClass(CommonClassNames.JAVA_LANG_OBJECT);

  @NotNull 
  private static Pair<ElementFilter, TailType> getReferenceFilter(PsiElement element, boolean secondBase, boolean secondChain) {
    //throw foo
    if (psiElement().withParent(psiElement(PsiReferenceExpression.class).withParent(PsiThrowStatement.class)).accepts(element)) {
      return new Pair<ElementFilter, TailType>(TrueFilter.INSTANCE, TailType.SEMICOLON);
    }

    if (psiElement().afterLeaf(PsiKeyword.RETURN).inside(PsiReturnStatement.class).accepts(element) && !secondBase && !secondChain) {
      return new Pair<ElementFilter, TailType>(new ElementExtractorFilter(new ExcludeDeclaredFilter(new ClassFilter(PsiMethod.class))
      ), TailType.SEMICOLON);
    }

    if (psiElement().inside(
        PsiJavaPatterns.or(
            psiElement(PsiAnnotationParameterList.class),
            psiElement(PsiSwitchLabelStatement.class))
    ).accepts(element)) {
      return new Pair<ElementFilter, TailType>(new ElementExtractorFilter(new AndFilter(
          new ClassFilter(PsiField.class),
          new ModifierFilter(PsiKeyword.STATIC, PsiKeyword.FINAL)
      )), TailType.NONE);
    }

    if (psiElement().inside(
        PsiJavaPatterns.or(
            PsiJavaPatterns.psiElement(PsiAssignmentExpression.class),
            PsiJavaPatterns.psiElement(PsiVariable.class))).
        andNot(psiElement().afterLeaf(".")).accepts(element) &&
        (secondBase || !secondChain)) {
      return new Pair<ElementFilter, TailType>(
          new ElementExtractorFilter(new AndFilter(new ExcludeSillyAssignment(),
                                                   new ExcludeDeclaredFilter(new ClassFilter(PsiVariable.class)))),
          TailType.NONE);
    }

    return new Pair<ElementFilter, TailType>(TrueFilter.INSTANCE, TailType.NONE);
  }

  public boolean fillCompletionVariants(final JavaSmartCompletionParameters parameters, final CompletionResultSet result) {
    ApplicationManager.getApplication().runReadAction(new Runnable() {
      public void run() {
        final PsiElement element = parameters.getPosition();
        if (psiElement().afterLeaf(psiElement().withText(")").withParent(PsiTypeCastExpression.class)).accepts(element)) return;

        final int offset = parameters.getOffset();
        final PsiReference reference = element.getContainingFile().findReferenceAt(offset);
        if (reference != null) {
          final Pair<ElementFilter, TailType> pair = getReferenceFilter(element, false, false);
          if (pair != null) {
            final PsiFile originalFile = parameters.getOriginalFile();
            final TailType tailType = pair.second;
            final ElementFilter filter = pair.first;
            final THashSet<LookupItem> set = JavaSmartCompletionContributor.completeReference(element, reference, originalFile, tailType, filter, result);
            for (final LookupItem item : set) {
              result.addElement(item);
            }

            if (parameters.getInvocationCount() >= 2) {
              ElementFilter baseFilter = getReferenceFilter(element, true, false).first;
              final PsiClassType stringType = PsiType.getJavaLangString(element.getManager(), element.getResolveScope());
              for (final LookupItem<?> baseItem : JavaSmartCompletionContributor.completeReference(element, reference, originalFile, tailType, baseFilter, result)) {
                final Object object = baseItem.getObject();
                final String prefix = getItemText(object);
                if (prefix == null) continue;

                final PsiSubstitutor substitutor = (PsiSubstitutor)baseItem.getAttribute(LookupItem.SUBSTITUTOR);
                try {
                  PsiType itemType = object instanceof PsiVariable ? ((PsiVariable) object).getType() :
                                     object instanceof PsiMethod ? ((PsiMethod) object).getReturnType() : null;
                  if (substitutor != null) {
                    itemType = substitutor.substitute(itemType);
                  }
                  if (itemType == null) continue;

                  final PsiElement qualifier = getQualifier(reference.getElement());
                  if (!OBJECT_METHOD_PATTERN.accepts(object) || allowGetClass(object, parameters)) {
                    if (!stringType.equals(itemType)) {
                      addChainedCallVariants(element, originalFile, tailType, getReferenceFilter(element, true, true).first, prefix, substitutor, qualifier, result);
                    }
                  }

                  addArraysAsListConversions(element, prefix, itemType, parameters, result, qualifier);

                  addToArrayConversions(element, object, prefix, itemType, parameters, result, qualifier);
                }
                catch (IncorrectOperationException e) {
                }
              }
            }
          }
        }
      }
    });
    return true;
  }

  private static boolean allowGetClass(final Object object, final JavaSmartCompletionParameters parameters) {
    if (!"getClass".equals(((PsiMethod)object).getName())) return false;

    final PsiType type = parameters.getDefaultType();
    @NonNls final String canonicalText = type.getCanonicalText();
    if ("java.lang.ClassLoader".equals(canonicalText)) return true;
    if (canonicalText.startsWith("java.lang.reflect.")) return true;
    return false;
  }

  @Nullable
  private static PsiElement getQualifier(final PsiElement element) {
    return element instanceof PsiJavaCodeReferenceElement ? ((PsiJavaCodeReferenceElement)element).getQualifier() : null;
  }

  private static void addArraysAsListConversions(final PsiElement element, final String prefix,
                                                final PsiType itemType, final JavaSmartCompletionParameters parameters,
                                                final CompletionResultSet result, @Nullable PsiElement qualifier) throws IncorrectOperationException {
    final PsiElementFactory elementFactory = JavaPsiFacade.getInstance(element.getProject()).getElementFactory();
    PsiType componentType = PsiUtil.extractIterableTypeParameter(parameters.getExpectedType(), true);
    if (componentType == null ||
        !(itemType instanceof PsiArrayType) ||
        !componentType.isAssignableFrom(((PsiArrayType)itemType).getComponentType())) {
      return;

    }
    final String qualifierText = getQualifierText(qualifier);
    final PsiExpression conversion = elementFactory.createExpressionFromText("java.util.Arrays.asList(" + qualifierText + prefix + ")", element);
    final LookupItem item = LookupItemUtil.objectToLookupItem(conversion);

    @NonNls final String presentable = "Arrays.asList(" + qualifierText + prefix + ")";
    item.setLookupString(StringUtil.isEmpty(qualifierText) ? presentable : prefix);
    item.setPresentableText(presentable);
    item.addLookupStrings(prefix, presentable, "asList(" + prefix + ")");
    item.setIcon(Icons.METHOD_ICON);
    item.setInsertHandler(new SimpleInsertHandler() {
      public int handleInsert(final Editor editor,
                              int startOffset,
                              final LookupElement item,
                              final LookupElement[] allItems,
                              final TailType tailType,
                              final char completionChar) throws IncorrectOperationException {
        FeatureUsageTracker.getInstance().triggerFeatureUsed(JavaCompletionFeatures.SECOND_SMART_COMPLETION_ASLIST);

        final Document document = editor.getDocument();
        final int tailOffset = startOffset + item.getLookupString().length();
        RangeMarker tail = document.createRangeMarker(tailOffset, tailOffset);
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
        return tail.getEndOffset();
      }
    });
    result.addElement(item);
  }

  private static void addToArrayConversions(final PsiElement element, final Object object, final String prefix,
                                           final PsiType itemType,
                                           final JavaSmartCompletionParameters parameters,
                                           final CompletionResultSet result,
                                           @Nullable final PsiElement qualifier) {
    final PsiElementFactory elementFactory = JavaPsiFacade.getInstance(element.getProject()).getElementFactory();
    final String callSpace =
                      getSpace(CodeStyleSettingsManager.getSettings(element.getProject()).SPACE_WITHIN_METHOD_CALL_PARENTHESES);
    final PsiType componentType = PsiUtil.extractIterableTypeParameter(itemType, true);
    if (componentType == null || !(parameters.getExpectedType() instanceof PsiArrayType)) return;

    final PsiArrayType type = (PsiArrayType)parameters.getExpectedType();
    if (!type.getComponentType().isAssignableFrom(componentType) ||
        componentType instanceof PsiClassType && ((PsiClassType) componentType).hasParameters()) {
      return;
    }

    final String bracketSpace = getSpace(CodeStyleSettingsManager.getSettings(element.getProject()).SPACE_WITHIN_BRACKETS);
    if (object instanceof PsiVariable && !containsMethodCalls(qualifier)) {
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
              JavaPsiFacade.getInstance(field.getProject()).getResolveHelper().isAccessible(field, parameters.getPosition(), null) &&
              type.isAssignableFrom(field.getType()) && isEmptyArrayInitializer(field.getInitializer())) {
            boolean needQualify;
            try {
              needQualify = !field.isEquivalentTo(
                ((PsiReferenceExpression)elementFactory.createExpressionFromText(field.getName(), element)).resolve());
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

  private static boolean containsMethodCalls(@Nullable final PsiElement qualifier) {
    if (qualifier == null) return false;
    if (qualifier instanceof PsiMethodCallExpression) return true;
    return containsMethodCalls(getQualifier(qualifier));
  }

  private static void addChainedCallVariants(final PsiElement element, final PsiFile originalFile, final TailType tailType,
                                             final ElementFilter qualifierFilter, final String prefix, final PsiSubstitutor substitutor,
                                             final PsiElement qualifier,
                                             final CompletionResultSet result) throws IncorrectOperationException {
    final PsiElementFactory elementFactory = JavaPsiFacade.getInstance(element.getProject()).getElementFactory();
    final PsiExpression ref = elementFactory.createExpressionFromText(getQualifierText(qualifier) + prefix + ".xxx", element);
    String beautifulPrefix = prefix.endsWith(" )") ? prefix.substring(0, prefix.length() - 2) + ")" : prefix;
    for (final LookupItem<?> item : JavaSmartCompletionContributor.completeReference(element, (PsiReferenceExpression)ref, originalFile, tailType, qualifierFilter, result)) {
      if (item.getObject() instanceof PsiMethod) {
        final PsiMethod method = (PsiMethod)item.getObject();
        if (psiMethod().withName("toArray").withParameterCount(1)
            .definedInClass(CommonClassNames.JAVA_UTIL_COLLECTION).accepts(method)) {
          continue;
        }
        final PsiMethod parentMethod = PsiTreeUtil.getParentOfType(element, PsiMethod.class);
        if (OBJECT_METHOD_PATTERN.accepts(method) &&
            !(OBJECT_METHOD_PATTERN.accepts(parentMethod) && parentMethod != null && method.getName().equals(parentMethod.getName()))) {
          continue;
        }

        final QualifiedMethodLookupItem newItem = new QualifiedMethodLookupItem(method, beautifulPrefix);
        final PsiSubstitutor newSubstitutor = (PsiSubstitutor)item.getAttribute(LookupItem.SUBSTITUTOR);
        if (substitutor != null || newSubstitutor != null) {
          newItem.setAttribute(LookupItem.SUBSTITUTOR, substitutor == null ? newSubstitutor :
                                                       newSubstitutor == null ? substitutor : substitutor.putAll(newSubstitutor));
        }
        result.addElement(newItem);
      } else {
        item.setAttribute(JavaCompletionUtil.QUALIFIER_PREFIX_ATTRIBUTE, beautifulPrefix + ".");
        item.setLookupString(beautifulPrefix + "." + item.getLookupString());
        result.addElement(item);
      }
    }
  }

  private static void addToArrayConversion(final PsiElement element, final String prefix, @NonNls final String expressionString, @NonNls String presentableString, final CompletionResultSet result, PsiElement qualifier) {
    final PsiElementFactory elementFactory = JavaPsiFacade.getInstance(element.getProject()).getElementFactory();
    final boolean callSpace = CodeStyleSettingsManager.getSettings(element.getProject()).SPACE_WITHIN_METHOD_CALL_PARENTHESES;
    final PsiExpression conversion;
    try {
      conversion = elementFactory.createExpressionFromText(
        getQualifierText(qualifier) + prefix + ".toArray(" + getSpace(callSpace) + expressionString + getSpace(callSpace) + ")", element);
    }
    catch (IncorrectOperationException e) {
      return;
    }

    final LookupItem item = LookupItemUtil.objectToLookupItem(conversion);
    item.setLookupString(prefix + ".toArray(" + getSpace(callSpace) + expressionString + getSpace(callSpace) + ")");
    item.setPresentableText(prefix + ".toArray(" + presentableString + ")");
    item.addLookupStrings(presentableString);
    item.setIcon(Icons.METHOD_ICON);
    item.setInsertHandler(new SimpleInsertHandler(){
      public int handleInsert(final Editor editor, final int startOffset, final LookupElement item, final LookupElement[] allItems,
                              final TailType tailType,
                              final char completionChar) throws IncorrectOperationException {
        FeatureUsageTracker.getInstance().triggerFeatureUsed(JavaCompletionFeatures.SECOND_SMART_COMPLETION_TOAR);
        final Document document = editor.getDocument();
        final int tailOffset = startOffset + item.getLookupString().length();
        RangeMarker tail = document.createRangeMarker(tailOffset, tailOffset);
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
        return tail.getEndOffset();
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

  private static class QualifiedMethodLookupItem extends LookupItem<PsiMethod> {
    private final String myQualifier;

    public QualifiedMethodLookupItem(final PsiMethod method, @NotNull final String qualifier) {
      super(method, qualifier + "." + method.getName());
      myQualifier = qualifier;
      addLookupStrings(method.getName());
      setAttribute(JavaCompletionUtil.QUALIFIER_PREFIX_ATTRIBUTE, qualifier + ".");
    }

    @Override
    public boolean equals(final Object o) {
      if (this == o) return true;
      if (!(o instanceof QualifiedMethodLookupItem)) return false;
      if (!super.equals(o)) return false;

      final QualifiedMethodLookupItem that = (QualifiedMethodLookupItem)o;

      if (myQualifier != null ? !myQualifier.equals(that.myQualifier) : that.myQualifier != null) return false;

      return true;
    }

    @Override
    public int hashCode() {
      int result = super.hashCode();
      result = 31 * result + (myQualifier != null ? myQualifier.hashCode() : 0);
      return result;
    }
  }
}
