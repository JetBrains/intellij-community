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

import com.intellij.codeInsight.TailType;
import com.intellij.codeInsight.TailTypes;
import com.intellij.codeInsight.daemon.impl.quickfix.ImportClassFix;
import com.intellij.codeInsight.hint.ShowParameterInfoHandler;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupItem;
import com.intellij.codeInsight.lookup.LookupItemUtil;
import com.intellij.codeInsight.lookup.TailTypeDecorator;
import com.intellij.lang.LangBundle;
import com.intellij.lang.StdLanguages;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.highlighter.HighlighterIterator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.patterns.ElementPattern;
import com.intellij.patterns.PatternCondition;
import com.intellij.patterns.PsiNameValuePairPattern;
import com.intellij.psi.*;
import com.intellij.psi.filters.*;
import com.intellij.psi.filters.classes.AssignableFromContextFilter;
import com.intellij.psi.filters.classes.ThisOrAnyInnerFilter;
import com.intellij.psi.filters.element.ExcludeDeclaredFilter;
import com.intellij.psi.filters.element.ModifierFilter;
import com.intellij.psi.filters.getters.ExpectedTypesGetter;
import com.intellij.psi.filters.types.AssignableFromFilter;
import com.intellij.psi.impl.source.tree.ElementType;
import com.intellij.psi.scope.ElementClassFilter;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.Consumer;
import com.intellij.util.PairConsumer;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;

import static com.intellij.patterns.PsiJavaPatterns.*;

/**
 * @author peter
 */
public class JavaCompletionContributor extends CompletionContributor {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.completion.JavaCompletionContributor");
  private static final Java15CompletionData ourJava15CompletionData = new Java15CompletionData();
  private static final JavaCompletionData ourJavaCompletionData = new JavaCompletionData();
  private static final PsiNameValuePairPattern NAME_VALUE_PAIR = psiNameValuePair().withSuperParent(
          2,
          psiElement(PsiAnnotation.class));
  private static final ElementPattern<PsiElement> ANNOTATION_ATTRIBUTE_NAME =
    or(psiElement(PsiIdentifier.class).withParent(NAME_VALUE_PAIR),
       psiElement().afterLeaf("(").withParent(psiReferenceExpression().withParent(NAME_VALUE_PAIR)));
  public static final ElementPattern SWITCH_LABEL =
    psiElement().withSuperParent(2, psiElement(PsiSwitchLabelStatement.class).withSuperParent(2,
      psiElement(PsiSwitchStatement.class).with(new PatternCondition<PsiSwitchStatement>("enumExpressionType") {
        @Override
        public boolean accepts(@NotNull PsiSwitchStatement psiSwitchStatement, ProcessingContext context) {
          final PsiExpression expression = psiSwitchStatement.getExpression();
          if(expression == null) return false;
          final PsiType type = expression.getType();
          return type instanceof PsiClassType;
        }
      })));

  @Nullable 
  private static ElementFilter getReferenceFilter(PsiElement position) {
    // Completion after extends in interface, type parameter and implements in class
    final PsiClass containingClass = PsiTreeUtil.getParentOfType(position, PsiClass.class, false, PsiCodeBlock.class, PsiMethod.class, PsiExpressionList.class, PsiVariable.class);
    if (containingClass != null && psiElement().afterLeaf(PsiKeyword.EXTENDS, PsiKeyword.IMPLEMENTS, ",", "&").accepts(position)) {
      return new AndFilter(ElementClassFilter.CLASS, new NotFilter(new AssignableFromContextFilter()));
    }

    if (JavaCompletionData.DECLARATION_START.isAcceptable(position, position)) {
      return new OrFilter(ElementClassFilter.CLASS, ElementClassFilter.PACKAGE_FILTER);
    }

    if (JavaCompletionData.INSIDE_PARAMETER_LIST.accepts(position)) {
      return ElementClassFilter.CLASS;
    }

    // Completion for classes in method throws section
    if (psiElement().afterLeaf(PsiKeyword.THROWS, ",").inside(psiElement(PsiReferenceList.class).withParent(PsiMethod.class)).accepts(position)) {
      return new OrFilter(new ThisOrAnyInnerFilter(new AssignableFromFilter("java.lang.Throwable")), ElementClassFilter.PACKAGE_FILTER);
    }

    if (psiElement().afterLeaf(PsiKeyword.INSTANCEOF).accepts(position)) {
      return new ElementExtractorFilter(ElementClassFilter.CLASS);
    }

    if (JavaCompletionData.AFTER_FINAL.accepts(position)) {
      return ElementClassFilter.CLASS;
    }

    if (JavaCompletionData.AFTER_TRY_BLOCK.isAcceptable(position, position) ||
        JavaCompletionData.START_SWITCH.isAcceptable(position, position) ||
        JavaCompletionData.INSTANCEOF_PLACE.isAcceptable(position, position)) {
      return null;
    }

    if (psiElement().afterLeaf(psiElement().withText("(").withParent(PsiTryStatement.class)).accepts(position)) {
      return new OrFilter(new ThisOrAnyInnerFilter(new AssignableFromFilter(CommonClassNames.JAVA_LANG_THROWABLE)), ElementClassFilter.PACKAGE_FILTER);
    }

    if (JavaSmartCompletionContributor.AFTER_THROW_NEW.accepts(position)) {
      return new ThisOrAnyInnerFilter(new AssignableFromFilter("java.lang.Throwable"));
    }

    if (JavaSmartCompletionContributor.AFTER_NEW.accepts(position)) {
      return ElementClassFilter.CLASS;
    }

    if (psiElement().inside(PsiReferenceParameterList.class).accepts(position)) {
      return ElementClassFilter.CLASS;
    }

    if (psiElement().inside(PsiAnnotationParameterList.class).accepts(position)) {
      return new OrFilter(new ClassFilter(PsiAnnotationMethod.class), ElementClassFilter.CLASS, ElementClassFilter.PACKAGE_FILTER, new AndFilter(new ClassFilter(PsiField.class),
                                                                                                                                                 new ModifierFilter(PsiModifier.STATIC, PsiModifier.FINAL)));
    }

    if (psiElement().afterLeaf("=").inside(PsiVariable.class).accepts(position)) {
      return new OrFilter(
        new ClassFilter(PsiVariable.class, false),
        new ExcludeDeclaredFilter(new ClassFilter(PsiVariable.class)));
    }

    if (SWITCH_LABEL.accepts(position)) {
      return new ClassFilter(PsiField.class) {
        @Override
        public boolean isAcceptable(Object element, PsiElement context) {
          return element instanceof PsiEnumConstant;
        }
      };
    }

    return TrueFilter.INSTANCE;
  }

  public void fillCompletionVariants(final CompletionParameters parameters, final CompletionResultSet _result) {
    if (parameters.getCompletionType() != CompletionType.BASIC) {
      return;
    }

    final PsiElement position = parameters.getPosition();
    if (!position.getContainingFile().getLanguage().isKindOf(StdLanguages.JAVA)) {
      return;
    }

    final PsiFile file = parameters.getOriginalFile();
    final int offset = parameters.getOffset();
    final PsiElement lastElement = file.findElementAt(offset - 1);
    final JavaAwareCompletionData completionData = ApplicationManager.getApplication().runReadAction(new Computable<JavaAwareCompletionData>() {
      public JavaAwareCompletionData compute() {
        return getCompletionDataByElementInner(lastElement);
      }
    });

    if (ANNOTATION_ATTRIBUTE_NAME.accepts(position)) {
      ApplicationManager.getApplication().runReadAction(new Runnable() {
        public void run() {
          completeAnnotationAttributeName(_result, file, position, parameters);
        }
      });
      _result.stopHere();
      return;
    }

    final boolean checkAccess = parameters.getInvocationCount() == 1;

    LegacyCompletionContributor.processReferences(parameters, _result, completionData, new PairConsumer<PsiReference, CompletionResultSet>() {
      public void consume(final PsiReference reference, final CompletionResultSet result) {
        ApplicationManager.getApplication().runReadAction(new Runnable() {
          public void run() {
            if (reference instanceof PsiJavaReference) {
              final ElementFilter filter = getReferenceFilter(position);
              if (filter != null) {
                final boolean isSwitchLabel = SWITCH_LABEL.accepts(position);
                for (LookupElement element : JavaCompletionUtil.processJavaReference(position,
                                                                                     (PsiJavaReference) reference,
                                                                                     new ElementExtractorFilter(filter),
                                                                                     checkAccess,
                                                                                     result.getPrefixMatcher(), parameters)) {
                  if (isSwitchLabel) {
                    result.addElement(TailTypeDecorator.withTail(element, TailType.createSimpleTailType(':')));
                  } else {
                    final LookupItem item = element.as(LookupItem.class);
                    if (file instanceof PsiJavaCodeReferenceCodeFragment &&
                        !((PsiJavaCodeReferenceCodeFragment)file).isClassesAccepted() && item != null) {
                      item.setTailType(TailType.NONE);
                    }

                    result.addElement(element);
                  }
                }
              }
              return;
            }


            final Object[] variants = reference.getVariants();
            if (variants == null) {
              LOG.error("Reference=" + reference);
            }
            for (Object completion : variants) {
              if (completion == null) {
                LOG.error("Position=" + position + "\n;Reference=" + reference + "\n;variants=" + Arrays.toString(variants));
              }
              if (completion instanceof LookupElement) {
                result.addElement((LookupElement)completion);
              }
              else if (completion instanceof PsiClass) {
                result.addElement(AllClassesGetter.createLookupItem((PsiClass)completion));
              }
              else {
                result.addElement(LookupItemUtil.objectToLookupItem(completion));
              }
            }
          }
        });
      }
    });

    final Set<LookupElement> lookupSet = new LinkedHashSet<LookupElement>();
    final Set<CompletionVariant> keywordVariants = new HashSet<CompletionVariant>();
    completionData.addKeywordVariants(keywordVariants, position, parameters.getOriginalFile());
    final CompletionResultSet result = _result.withPrefixMatcher(completionData.findPrefix(position, offset));
    completionData.completeKeywordsBySet(lookupSet, keywordVariants, position, result.getPrefixMatcher(), parameters.getOriginalFile());

    ApplicationManager.getApplication().runReadAction(new Runnable() {
      public void run() {
        completionData.fillCompletions(parameters, result);
      }
    });

    for (final LookupElement item : lookupSet) {
      if (item instanceof LookupItem && ((LookupItem)item).getInsertHandler() == null) {
        ((LookupItem)item).setInsertHandler(new InsertHandler() {
          public void handleInsert(final InsertionContext context, final LookupElement item) {
            analyzeItem((LookupItem)item, item.getObject(), parameters.getPosition());
            new DefaultInsertHandler().handleInsert(context, item);
          }
        });
      }

      result.addElement(item);
    }

    if (shouldRunClassNameCompletion(result, file, position)) {
      result.runRemainingContributors(new CompletionParameters(position, file, CompletionType.CLASS_NAME, offset, parameters.getInvocationCount()), new Consumer<LookupElement>() {
        @Override
        public void consume(LookupElement lookupElement) {
          result.addElement(lookupElement);
        }
      });
    }
    result.stopHere();
  }

  private static boolean shouldRunClassNameCompletion(CompletionResultSet result, PsiFile file, PsiElement position) {
    final PsiElement parent = position.getParent();
    if (!(parent instanceof PsiJavaCodeReferenceElement)) return false;
    if (((PsiJavaCodeReferenceElement)parent).getQualifier() != null) return false;
    
    PsiElement grand = parent.getParent();
    if (grand instanceof PsiSwitchLabelStatement) {
      return false;
    }

    if (grand instanceof PsiAnonymousClass) {
      grand = grand.getParent();
    }
    if (grand instanceof PsiNewExpression && ((PsiNewExpression)grand).getQualifier() != null) {
      return false;
    }

    final String s = result.getPrefixMatcher().getPrefix();
    if (StringUtil.isEmpty(s) || !Character.isUpperCase(s.charAt(0))) return false;
    return true;
  }


  private static void completeAnnotationAttributeName(CompletionResultSet result, PsiFile file, PsiElement insertedElement,
                                                      CompletionParameters parameters) {
    PsiNameValuePair pair = PsiTreeUtil.getParentOfType(insertedElement, PsiNameValuePair.class);
    PsiAnnotationParameterList parameterList = (PsiAnnotationParameterList)pair.getParent();
    PsiAnnotation anno = (PsiAnnotation)parameterList.getParent();
    boolean showClasses = psiElement().afterLeaf("(").accepts(insertedElement);
    PsiClass annoClass = null;
    final PsiJavaCodeReferenceElement referenceElement = anno.getNameReferenceElement();
    if (referenceElement != null) {
      final PsiElement element = referenceElement.resolve();
      if (element instanceof PsiClass) {
        annoClass = (PsiClass)element;
        if (annoClass.findMethodsByName("value", false).length == 0) {
          showClasses = false;
        }
      }
    }

    if (showClasses && insertedElement.getParent() instanceof PsiReferenceExpression) {
      final Set<LookupElement> set = JavaCompletionUtil.processJavaReference(insertedElement, (PsiJavaReference)insertedElement.getParent(), TrueFilter.INSTANCE, true, result.getPrefixMatcher(), parameters);

      for (final LookupElement element : set) {
        result.addElement(element);
      }

    }

    if (annoClass != null) {
      final PsiNameValuePair[] existingPairs = parameterList.getAttributes();

      methods: for (PsiMethod method : annoClass.getMethods()) {
        final String attrName = method.getName();
        for (PsiNameValuePair apair : existingPairs) {
          if (Comparing.equal(apair.getName(), attrName)) continue methods;
        }
        result.addElement(new LookupItem<PsiMethod>(method, attrName).setInsertHandler(new InsertHandler<LookupElement>() {
          public void handleInsert(InsertionContext context, LookupElement item) {
            final Editor editor = context.getEditor();
            TailType.EQ.processTail(editor, editor.getCaretModel().getOffset());
            context.setAddCompletionChar(false);
          }
        }));
      }
    }
  }

  private static JavaAwareCompletionData getCompletionDataByElementInner(PsiElement element) {
    return element != null && PsiUtil.isLanguageLevel5OrHigher(element) ? ourJava15CompletionData : ourJavaCompletionData;
  }


  public String advertise(@NotNull final CompletionParameters parameters) {
    if (!(parameters.getOriginalFile() instanceof PsiJavaFile)) return null;

    if (parameters.getCompletionType() != CompletionType.SMART && shouldSuggestSmartCompletion(parameters.getPosition())) {
      if (CompletionUtil.shouldShowFeature(parameters, CodeCompletionFeatures.EDITING_COMPLETION_SMARTTYPE_GENERAL)) {
        final String shortcut = getActionShortcut(IdeActions.ACTION_SMART_TYPE_COMPLETION);
        if (shortcut != null) {
          return CompletionBundle.message("completion.smart.hint", shortcut);
        }
      }
    }

    if (parameters.getCompletionType() != CompletionType.CLASS_NAME && shouldSuggestClassNameCompletion(parameters.getPosition())) {
      if (CompletionUtil.shouldShowFeature(parameters, CodeCompletionFeatures.EDITING_COMPLETION_CLASSNAME)) {
        final String shortcut = getActionShortcut(IdeActions.ACTION_CLASS_NAME_COMPLETION);
        if (shortcut != null) {
          return CompletionBundle.message("completion.class.name.hint", shortcut);
        }
      }
    }

    if (parameters.getCompletionType() == CompletionType.SMART && parameters.getInvocationCount() == 1) {
      final PsiType[] psiTypes = ExpectedTypesGetter.getExpectedTypes(parameters.getPosition(), true);
      if (psiTypes.length > 0) {
        if (CompletionUtil.shouldShowFeature(parameters, JavaCompletionFeatures.SECOND_SMART_COMPLETION_TOAR)) {
          final String shortcut = getActionShortcut(IdeActions.ACTION_SMART_TYPE_COMPLETION);
          if (shortcut != null) {
            for (final PsiType psiType : psiTypes) {
              final PsiType type = PsiUtil.extractIterableTypeParameter(psiType, false);
              if (type != null) {
                return CompletionBundle.message("completion.smart.aslist.hint", shortcut, type.getPresentableText());
              }
            }
          }
        }
        if (CompletionUtil.shouldShowFeature(parameters, JavaCompletionFeatures.SECOND_SMART_COMPLETION_ASLIST)) {
          final String shortcut = getActionShortcut(IdeActions.ACTION_SMART_TYPE_COMPLETION);
          if (shortcut != null) {
            for (final PsiType psiType : psiTypes) {
              if (psiType instanceof PsiArrayType) {
                final PsiType componentType = ((PsiArrayType)psiType).getComponentType();
                if (!(componentType instanceof PsiPrimitiveType)) {
                  return CompletionBundle.message("completion.smart.toar.hint", shortcut, componentType.getPresentableText());
                }
              }
            }
          }
        }

        if (CompletionUtil.shouldShowFeature(parameters, JavaCompletionFeatures.SECOND_SMART_COMPLETION_CHAIN)) {
          final String shortcut = getActionShortcut(IdeActions.ACTION_SMART_TYPE_COMPLETION);
          if (shortcut != null) {
            return CompletionBundle.message("completion.smart.chain.hint", shortcut);
          }
        }
      }
    }
    return null;
  }

  public String handleEmptyLookup(@NotNull final CompletionParameters parameters, final Editor editor) {
    if (!(parameters.getOriginalFile() instanceof PsiJavaFile)) return null;

    final String ad = advertise(parameters);
    final String suffix = ad == null ? "" : "; " + StringUtil.decapitalize(ad);
    if (parameters.getCompletionType() == CompletionType.SMART) {
      if (!ApplicationManager.getApplication().isUnitTestMode()) {

        final Project project = parameters.getPosition().getProject();
        final PsiFile file = parameters.getOriginalFile();

        PsiExpression expression = PsiTreeUtil.getContextOfType(parameters.getPosition(), PsiExpression.class, true);
        if (expression != null && expression.getParent() instanceof PsiExpressionList) {
          int lbraceOffset = expression.getParent().getTextRange().getStartOffset();
          new ShowParameterInfoHandler().invoke(project, editor, file, lbraceOffset, null);
        }

        if (expression instanceof PsiLiteralExpression) {
          return LangBundle.message("completion.no.suggestions") + suffix;
        }

        if (expression instanceof PsiInstanceOfExpression) {
          final PsiInstanceOfExpression instanceOfExpression = (PsiInstanceOfExpression)expression;
          if (PsiTreeUtil.isAncestor(instanceOfExpression.getCheckType(), parameters.getPosition(), false)) {
            return LangBundle.message("completion.no.suggestions") + suffix;
          }
        }
      }

      final Set<PsiType> expectedTypes = JavaCompletionUtil.getExpectedTypes(parameters);
      if (expectedTypes != null) {
        PsiType type = expectedTypes.size() == 1 ? expectedTypes.iterator().next() : null;
        if (type != null) {
          final PsiType deepComponentType = type.getDeepComponentType();
          if (deepComponentType instanceof PsiClassType) {
            if (((PsiClassType)deepComponentType).resolve() != null) {
              return CompletionBundle.message("completion.no.suggestions.of.type", type.getPresentableText()) + suffix;
            }
            return CompletionBundle.message("completion.unknown.type", type.getPresentableText()) + suffix;
          }
          if (!PsiType.NULL.equals(type)) {
            return CompletionBundle.message("completion.no.suggestions.of.type", type.getPresentableText()) + suffix;
          }
        }
      }
    }
    return LangBundle.message("completion.no.suggestions") + suffix;
  }

  private static boolean shouldSuggestSmartCompletion(final PsiElement element) {
    if (shouldSuggestClassNameCompletion(element)) return false;

    final PsiElement parent = element.getParent();
    if (parent instanceof PsiReferenceExpression && ((PsiReferenceExpression)parent).getQualifier() != null) return false;
    if (parent instanceof PsiReferenceExpression && parent.getParent() instanceof PsiReferenceExpression) return true;

    return new ExpectedTypesGetter().get(element, null).length > 0;
  }

  private static boolean shouldSuggestClassNameCompletion(final PsiElement element) {
    if (element == null) return false;
    final PsiElement parent = element.getParent();
    if (parent == null) return false;
    return parent.getParent() instanceof PsiTypeElement || parent.getParent() instanceof PsiExpressionStatement || parent.getParent() instanceof PsiReferenceList;
  }

  public static void analyzeItem(final LookupItem item, final Object completion, final PsiElement position) {
    if(completion instanceof PsiKeyword){
      if(PsiKeyword.BREAK.equals(((PsiKeyword)completion).getText())
         || PsiKeyword.CONTINUE.equals(((PsiKeyword)completion).getText())){
        PsiElement scope = position;
        while(true){
          if (scope instanceof PsiFile
              || scope instanceof PsiMethod
              || scope instanceof PsiClassInitializer){
            item.setTailType(TailType.SEMICOLON);
            break;
          }
          else if (scope instanceof PsiLabeledStatement){
            item.setTailType(TailType.NONE);
            break;
          }
          scope = scope.getParent();
        }
      }
      if(PsiKeyword.RETURN.equals(((PsiKeyword)completion).getText())){
        PsiElement scope = position;
        while(true){
          if (scope instanceof PsiFile
              || scope instanceof PsiClassInitializer){
            item.setTailType(TailType.NONE);
            break;
          }
          else if (scope instanceof PsiMethod){
            final PsiMethod method = (PsiMethod)scope;
            if(method.isConstructor() || PsiType.VOID.equals(method.getReturnType())) {
              item.setTailType(TailType.SEMICOLON);
            }
            else item.setTailType(TailType.SPACE);

            break;
          }
          scope = scope.getParent();
        }
      }
      if(PsiKeyword.SYNCHRONIZED.equals(((PsiKeyword)completion).getText())){
        if (PsiTreeUtil.getParentOfType(position, PsiMember.class, PsiCodeBlock.class) instanceof PsiCodeBlock){
          item.setTailType(TailTypes.SYNCHRONIZED_LPARENTH);
        }
      }
    }

  }

  public void beforeCompletion(@NotNull final CompletionInitializationContext context) {
    final PsiFile file = context.getFile();
    final Project project = context.getProject();

    JavaCompletionUtil.initOffsets(file, project, context.getOffsetMap());

    if (file instanceof PsiJavaFile) {
      autoImport(file, context.getStartOffset() - 1, context.getEditor());
    }

    if (context.getCompletionType() == CompletionType.BASIC && file instanceof PsiJavaFile) {
      if (semicolonNeeded(context)) {
        context.setFileCopyPatcher(new DummyIdentifierPatcher(CompletionInitializationContext.DUMMY_IDENTIFIER.trim() + ";"));
        return;
      }

      final PsiElement element = file.findElementAt(context.getStartOffset());

      if (psiElement().inside(PsiAnnotation.class).accepts(element)) {
        return;
      }

      context.setFileCopyPatcher(new DummyIdentifierPatcher(CompletionInitializationContext.DUMMY_IDENTIFIER.trim()));
    }
  }

  private static boolean semicolonNeeded(CompletionInitializationContext context) {
    HighlighterIterator iterator = ((EditorEx) context.getEditor()).getHighlighter().createIterator(context.getStartOffset());
    if (iterator.atEnd()) return false;

    if (iterator.getTokenType() == JavaTokenType.IDENTIFIER) {
      iterator.advance();
    }

    if (!iterator.atEnd() && iterator.getTokenType() == JavaTokenType.LPARENTH) {
      return true;
    }

    while (!iterator.atEnd() && ElementType.JAVA_COMMENT_OR_WHITESPACE_BIT_SET.contains(iterator.getTokenType())) {
      iterator.advance();
    }

    if (iterator.atEnd() || iterator.getTokenType() != JavaTokenType.IDENTIFIER) return false;
    iterator.advance();

    while (!iterator.atEnd() && ElementType.JAVA_COMMENT_OR_WHITESPACE_BIT_SET.contains(iterator.getTokenType())) {
      iterator.advance();
    }
    if (iterator.atEnd()) return false;

    return iterator.getTokenType() == JavaTokenType.EQ || iterator.getTokenType() == JavaTokenType.LPARENTH;
  }

  private static void autoImport(final PsiFile file, int offset, final Editor editor) {
    final CharSequence text = editor.getDocument().getCharsSequence();
    while (offset > 0 && Character.isJavaIdentifierPart(text.charAt(offset))) offset--;
    if (offset <= 0) return;

    while (offset > 0 && Character.isWhitespace(text.charAt(offset))) offset--;
    if (offset <= 0 || text.charAt(offset) != '.') return;

    offset--;

    while (offset > 0 && Character.isWhitespace(text.charAt(offset))) offset--;
    if (offset <= 0) return;

    PsiJavaCodeReferenceElement element = extractReference(PsiTreeUtil.findElementOfClassAtOffset(file, offset, PsiExpression.class, false));
    if (element == null) return;

    while (true) {
      final PsiJavaCodeReferenceElement qualifier = extractReference(element.getQualifier());
      if (qualifier == null) break;

      element = qualifier;
    }
    if (!(element.getParent() instanceof PsiMethodCallExpression) && element.multiResolve(true).length == 0) {
      new ImportClassFix(element).doFix(editor, false, false);
    }
  }

  @Nullable
  private static PsiJavaCodeReferenceElement extractReference(@Nullable PsiElement expression) {
    if (expression instanceof PsiJavaCodeReferenceElement) {
      return (PsiJavaCodeReferenceElement)expression;
    }
    if (expression instanceof PsiMethodCallExpression) {
      return ((PsiMethodCallExpression)expression).getMethodExpression();
    }
    return null;
  }

}
