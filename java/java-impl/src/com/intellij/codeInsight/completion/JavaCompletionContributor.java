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
import com.intellij.codeInsight.daemon.impl.quickfix.ImportClassFix;
import com.intellij.codeInsight.hint.ShowParameterInfoHandler;
import com.intellij.codeInsight.lookup.*;
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
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.patterns.ElementPattern;
import com.intellij.patterns.PatternCondition;
import com.intellij.patterns.PsiNameValuePairPattern;
import com.intellij.psi.*;
import com.intellij.psi.filters.*;
import com.intellij.psi.filters.classes.AssignableFromContextFilter;
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

import java.util.*;

import static com.intellij.patterns.PsiJavaPatterns.*;

/**
 * @author peter
 */
public class JavaCompletionContributor extends CompletionContributor {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.completion.JavaCompletionContributor");

  private static final Java15CompletionData ourJava15CompletionData = new Java15CompletionData();
  private static final JavaCompletionData ourJavaCompletionData = new JavaCompletionData();

  private static final PsiNameValuePairPattern NAME_VALUE_PAIR =
    psiNameValuePair().withSuperParent(2, psiElement(PsiAnnotation.class));
  private static final ElementPattern<PsiElement> ANNOTATION_ATTRIBUTE_NAME =
    or(psiElement(PsiIdentifier.class).withParent(NAME_VALUE_PAIR),
       psiElement().afterLeaf("(").withParent(psiReferenceExpression().withParent(NAME_VALUE_PAIR)));
  private static final ElementPattern SWITCH_LABEL =
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
  private static final ElementPattern<PsiElement> AFTER_NUMBER_LITERAL =
    psiElement().afterLeaf(psiElement().withElementType(elementType().oneOf(JavaTokenType.DOUBLE_LITERAL, JavaTokenType.LONG_LITERAL,
                                                                            JavaTokenType.FLOAT_LITERAL, JavaTokenType.INTEGER_LITERAL)));
  private static final ElementPattern<PsiElement> IMPORT_REFERENCE =
    psiElement().withParent(psiElement(PsiJavaCodeReferenceElement.class).withParent(PsiImportStatementBase.class));

  static final ElementPattern<PsiElement> IN_CATCH_TYPE =
    psiElement().withParent(psiElement(PsiJavaCodeReferenceElement.class).
      withParent(psiElement(PsiTypeElement.class).
        withParent(or(psiElement(PsiCatchSection.class),
                      psiElement(PsiVariable.class).withParent(PsiCatchSection.class)))));
  static final ElementPattern<PsiElement> IN_MULTI_CATCH_TYPE =
    or(psiElement().afterLeaf(psiElement().withText("|").withParent(PsiTypeElement.class).withSuperParent(2, PsiCatchSection.class)),
       psiElement().afterLeaf(psiElement().withText("|").withParent(PsiTypeElement.class).withSuperParent(2, PsiParameter.class).withSuperParent(3, PsiCatchSection.class)));
  static final ElementPattern<PsiElement> INSIDE_METHOD_THROWS_CLAUSE =
    psiElement().afterLeaf(PsiKeyword.THROWS, ",").inside(PsiMethod.class).andNot(psiElement().inside(PsiCodeBlock.class)).andNot(psiElement().inside(PsiParameterList.class));
  static final ElementPattern<PsiElement> IN_RESOURCE_TYPE =
    psiElement().withParent(psiElement(PsiJavaCodeReferenceElement.class).
      withParent(psiElement(PsiTypeElement.class).
        withParent(or(psiElement(PsiResourceVariable.class), psiElement(PsiResourceList.class)))));

  @Nullable
  private static ElementFilter getReferenceFilter(PsiElement position) {
    // Completion after extends in interface, type parameter and implements in class
    final PsiClass containingClass = PsiTreeUtil.getParentOfType(position, PsiClass.class, false, PsiCodeBlock.class, PsiMethod.class, PsiExpressionList.class, PsiVariable.class);
    if (containingClass != null && psiElement().afterLeaf(PsiKeyword.EXTENDS, PsiKeyword.IMPLEMENTS, ",", "&").accepts(position)) {
      return new AndFilter(ElementClassFilter.CLASS, new NotFilter(new AssignableFromContextFilter()));
    }

    if (JavaCompletionData.DECLARATION_START.isAcceptable(position, position) ||
        JavaCompletionData.INSIDE_PARAMETER_LIST.accepts(position)) {
      return new OrFilter(ElementClassFilter.CLASS, ElementClassFilter.PACKAGE_FILTER);
    }

    if (INSIDE_METHOD_THROWS_CLAUSE.accepts(position)) {
      return new AssignableFromFilter(CommonClassNames.JAVA_LANG_THROWABLE);
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

    if (JavaCompletionData.START_FOR.accepts(position)) {
      return ElementClassFilter.VARIABLE;
    }

    if (IN_CATCH_TYPE.accepts(position) || IN_MULTI_CATCH_TYPE.accepts(position)) {
      return new AssignableFromFilter(CommonClassNames.JAVA_LANG_THROWABLE);
    }

    if (IN_RESOURCE_TYPE.accepts(position)) {
      return new AssignableFromFilter(CommonClassNames.JAVA_LANG_AUTO_CLOSEABLE);
    }

    if (JavaSmartCompletionContributor.AFTER_THROW_NEW.accepts(position)) {
      return new AssignableFromFilter(CommonClassNames.JAVA_LANG_THROWABLE);
    }

    if (JavaSmartCompletionContributor.AFTER_NEW.accepts(position)) {
      return ElementClassFilter.CLASS;
    }

    if (psiElement().inside(PsiReferenceParameterList.class).accepts(position)) {
      return ElementClassFilter.CLASS;
    }

    if (psiElement().inside(PsiAnnotationParameterList.class).accepts(position)) {
      return new OrFilter(new ClassFilter(PsiAnnotationMethod.class),
                          ElementClassFilter.CLASS,
                          ElementClassFilter.PACKAGE_FILTER,
                          new AndFilter(new ClassFilter(PsiField.class), new ModifierFilter(PsiModifier.STATIC, PsiModifier.FINAL)));
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

    if (AFTER_NUMBER_LITERAL.accepts(position)) {
      _result.stopHere();
      return;
    }

    final CompletionResultSet result = JavaCompletionSorting.addJavaSorting(parameters, _result);

    if (ANNOTATION_ATTRIBUTE_NAME.accepts(position)) {
      completeAnnotationAttributeName(result, position, parameters);
      result.stopHere();
      return;
    }

    final InheritorsHolder inheritors = new InheritorsHolder(position, result);
    if (JavaSmartCompletionContributor.AFTER_NEW.accepts(position)) {
      new JavaInheritorsGetter(ConstructorInsertHandler.BASIC_INSTANCE).generateVariants(parameters, result.getPrefixMatcher(), inheritors);
    }

    if (IMPORT_REFERENCE.accepts(position)) {
      result.addElement(LookupElementBuilder.create("*"));
    }

    Set<String> usedWords = addReferenceVariants(parameters, result, inheritors);

    addKeywords(parameters, result);

    if (psiElement().inside(PsiLiteralExpression.class).accepts(position)) {
      PsiReference reference = position.getContainingFile().findReferenceAt(parameters.getOffset());
      if (reference == null || reference.isSoft()) {
        WordCompletionContributor.addWordCompletionVariants(result, parameters, usedWords);
      }
    }

    addAllClasses(parameters, result, inheritors);
    result.stopHere();
  }

  public static void addAllClasses(CompletionParameters parameters,
                                   final CompletionResultSet result,
                                   final InheritorsHolder inheritors) {
    if (shouldRunClassNameCompletion(result, parameters)) {
      JavaClassNameCompletionContributor.addAllClasses(parameters, result, new Consumer<LookupElement>() {
        @Override
        public void consume(LookupElement element) {
          if (!inheritors.alreadyProcessed(element)) {
            result.addElement(element);
          }
        }
      });
    }
  }

  private static Set<String> addReferenceVariants(final CompletionParameters parameters, CompletionResultSet result, final InheritorsHolder inheritors) {
    final Set<String> usedWords = new HashSet<String>();
    final PsiElement position = parameters.getPosition();
    final boolean checkAccess = parameters.getInvocationCount() <= 1;
    LegacyCompletionContributor.processReferences(parameters, result, new PairConsumer<PsiReference, CompletionResultSet>() {
      public void consume(final PsiReference reference, final CompletionResultSet result) {
        if (reference instanceof PsiJavaReference) {
          final ElementFilter filter = getReferenceFilter(position);
          if (filter != null) {
            boolean filterVoid = JavaSmartCompletionContributor.getExpectedTypes(parameters).length > 0 && parameters.getInvocationCount() < 2;

            final boolean isSwitchLabel = SWITCH_LABEL.accepts(position);
            final PsiFile originalFile = parameters.getOriginalFile();
            for (LookupElement element : JavaCompletionUtil.processJavaReference(position,
                                                                                 (PsiJavaReference)reference,
                                                                                 new ElementExtractorFilter(filter),
                                                                                 checkAccess,
                                                                                 result.getPrefixMatcher(), parameters)) {
              if (inheritors.alreadyProcessed(element)) {
                continue;
              }

              if (isSwitchLabel) {
                result.addElement(TailTypeDecorator.withTail(element, TailType.createSimpleTailType(':')));
              }
              else {
                final LookupItem item = element.as(LookupItem.CLASS_CONDITION_KEY);
                if (originalFile instanceof PsiJavaCodeReferenceCodeFragment &&
                    !((PsiJavaCodeReferenceCodeFragment)originalFile).isClassesAccepted() && item != null) {
                  item.setTailType(TailType.NONE);
                }

                Object object = element.getObject();
                if (filterVoid && object instanceof PsiMethod && PsiType.VOID.equals(((PsiMethod)object).getReturnType())) {
                  continue;
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
          if (completion instanceof LookupElement && !inheritors.alreadyProcessed((LookupElement)completion)) {
            usedWords.add(((LookupElement)completion).getLookupString());
            result.addElement((LookupElement)completion);
          }
          else if (completion instanceof PsiClass) {
            if (!inheritors.alreadyProcessed((PsiClass)completion)) {
              JavaPsiClassReferenceElement item = JavaClassNameCompletionContributor.createClassLookupItem((PsiClass)completion, true);
              usedWords.add(item.getLookupString());
              result.addElement(item);
            }
          }
          else {
            LookupElement element = LookupItemUtil.objectToLookupItem(completion);
            usedWords.add(element.getLookupString());
            result.addElement(element);
          }
        }
      }
    });
    return usedWords;
  }

  private static void addKeywords(CompletionParameters parameters, CompletionResultSet result) {
    PsiElement position = parameters.getPosition();
    final Set<LookupElement> lookupSet = new LinkedHashSet<LookupElement>();
    final Set<CompletionVariant> keywordVariants = new HashSet<CompletionVariant>();
    final JavaCompletionData completionData = PsiUtil.isLanguageLevel5OrHigher(position) ? ourJava15CompletionData : ourJavaCompletionData;
    completionData.addKeywordVariants(keywordVariants, position, parameters.getOriginalFile());
    completionData.completeKeywordsBySet(lookupSet, keywordVariants, position, result.getPrefixMatcher(), parameters.getOriginalFile());
    completionData.fillCompletions(parameters, result);

    for (final LookupElement item : lookupSet) {
      result.addElement(item);
    }
  }

  private static boolean shouldRunClassNameCompletion(CompletionResultSet result, CompletionParameters parameters) {
    PsiElement position = parameters.getPosition();
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

    return StringUtil.isCapitalized(result.getPrefixMatcher().getPrefix()) || parameters.relaxMatching();
  }

  private static void completeAnnotationAttributeName(CompletionResultSet result, PsiElement insertedElement,
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
      final Set<LookupElement> set = JavaCompletionUtil.processJavaReference(
        insertedElement, (PsiJavaReference)insertedElement.getParent(), TrueFilter.INSTANCE, true, result.getPrefixMatcher(), parameters);
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

  public String advertise(@NotNull final CompletionParameters parameters) {
    if (!(parameters.getOriginalFile() instanceof PsiJavaFile)) return null;

    if (parameters.getCompletionType() == CompletionType.BASIC && parameters.getInvocationCount() > 0) {
      PsiElement position = parameters.getPosition();
      if (psiElement().withParent(psiReferenceExpression().withFirstChild(psiReferenceExpression().referencing(psiClass()))).accepts(position)) {
        if (CompletionUtil.shouldShowFeature(parameters, JavaCompletionFeatures.GLOBAL_MEMBER_NAME)) {
          final String shortcut = getActionShortcut(IdeActions.ACTION_CLASS_NAME_COMPLETION);
          if (shortcut != null) {
            return "Pressing " + shortcut + " without a class qualifier would show all accessible static methods";
          }
        }
      }
    }

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

    return ExpectedTypesGetter.getExpectedTypes(element, false).length > 0;
  }

  private static boolean shouldSuggestClassNameCompletion(final PsiElement element) {
    if (element == null) return false;
    final PsiElement parent = element.getParent();
    if (parent == null) return false;
    return parent.getParent() instanceof PsiTypeElement || parent.getParent() instanceof PsiExpressionStatement || parent.getParent() instanceof PsiReferenceList;
  }

  public void beforeCompletion(@NotNull final CompletionInitializationContext context) {
    final PsiFile file = context.getFile();

    if (file instanceof PsiJavaFile) {
      JavaCompletionUtil.initOffsets(file, context.getProject(), context.getOffsetMap());

      autoImport(file, context.getStartOffset() - 1, context.getEditor());

      if (context.getCompletionType() == CompletionType.BASIC) {
        if (semicolonNeeded(context)) {
          context.setDummyIdentifier(CompletionInitializationContext.DUMMY_IDENTIFIER.trim() + ";");
          return;
        }

        final PsiJavaCodeReferenceElement ref = PsiTreeUtil.findElementOfClassAtOffset(file, context.getStartOffset(), PsiJavaCodeReferenceElement.class, false);
        if (ref != null && !(ref instanceof PsiReferenceExpression)) {
          context.setDummyIdentifier(CompletionInitializationContext.DUMMY_IDENTIFIER.trim() + ";");

          if (JavaSmartCompletionContributor.AFTER_NEW.accepts(ref)) {
            final PsiReferenceParameterList paramList = ref.getParameterList();
            if (paramList != null && paramList.getTextLength() > 0) {
              context.getOffsetMap().addOffset(ConstructorInsertHandler.PARAM_LIST_START, paramList.getTextRange().getStartOffset());
              context.getOffsetMap().addOffset(ConstructorInsertHandler.PARAM_LIST_END, paramList.getTextRange().getEndOffset());
            }
          }

          return;
        }

        final PsiElement element = file.findElementAt(context.getStartOffset());

        if (psiElement().inside(PsiAnnotation.class).accepts(element)) {
          return;
        }

        context.setDummyIdentifier(CompletionInitializationContext.DUMMY_IDENTIFIER_TRIMMED);
      }
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
