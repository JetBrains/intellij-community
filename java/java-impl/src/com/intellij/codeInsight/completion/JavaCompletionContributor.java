/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
import com.intellij.codeInsight.TailType;
import com.intellij.codeInsight.completion.scope.JavaCompletionProcessor;
import com.intellij.codeInsight.daemon.impl.quickfix.ImportClassFix;
import com.intellij.codeInsight.lookup.*;
import com.intellij.featureStatistics.FeatureUsageTracker;
import com.intellij.lang.LangBundle;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.highlighter.HighlighterIterator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.patterns.ElementPattern;
import com.intellij.patterns.PatternCondition;
import com.intellij.patterns.PsiJavaElementPattern;
import com.intellij.patterns.PsiNameValuePairPattern;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.filters.*;
import com.intellij.psi.filters.classes.AnnotationTypeFilter;
import com.intellij.psi.filters.classes.AssignableFromContextFilter;
import com.intellij.psi.filters.element.ModifierFilter;
import com.intellij.psi.filters.getters.ExpectedTypesGetter;
import com.intellij.psi.impl.source.PsiJavaCodeReferenceElementImpl;
import com.intellij.psi.impl.source.PsiLabelReference;
import com.intellij.psi.impl.source.tree.ElementType;
import com.intellij.psi.scope.ElementClassFilter;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.DocumentUtil;
import com.intellij.util.ProcessingContext;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.intellij.patterns.PsiJavaPatterns.*;
import static com.intellij.util.ObjectUtils.assertNotNull;

/**
 * @author peter
 */
public class JavaCompletionContributor extends CompletionContributor {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.completion.JavaCompletionContributor");

  public static final ElementPattern<PsiElement> ANNOTATION_NAME = psiElement().
    withParents(PsiJavaCodeReferenceElement.class, PsiAnnotation.class).afterLeaf("@");
  private static final PsiJavaElementPattern.Capture<PsiElement> UNEXPECTED_REFERENCE_AFTER_DOT =
    psiElement().afterLeaf(".").insideStarting(psiExpressionStatement());

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
          PsiClass aClass = PsiUtil.resolveClassInClassTypeOnly(expression.getType());
          return aClass != null && aClass.isEnum();
        }
      })));
  private static final ElementPattern<PsiElement> AFTER_NUMBER_LITERAL =
    psiElement().afterLeaf(psiElement().withElementType(elementType().oneOf(JavaTokenType.DOUBLE_LITERAL, JavaTokenType.LONG_LITERAL,
                                                                            JavaTokenType.FLOAT_LITERAL, JavaTokenType.INTEGER_LITERAL)));
  private static final ElementPattern<PsiElement> IMPORT_REFERENCE =
    psiElement().withParent(psiElement(PsiJavaCodeReferenceElement.class).withParent(PsiImportStatementBase.class));
  private static final ElementPattern<PsiElement> CATCH_OR_FINALLY = psiElement().afterLeaf(
    psiElement().withText("}").withParent(
      psiElement(PsiCodeBlock.class).afterLeaf(PsiKeyword.TRY)));
  private static final ElementPattern<PsiElement> INSIDE_CONSTRUCTOR = psiElement().inside(psiMethod().constructor(true));

  @Nullable
  public static ElementFilter getReferenceFilter(PsiElement position) {
    // Completion after extends in interface, type parameter and implements in class
    final PsiClass containingClass = PsiTreeUtil.getParentOfType(position, PsiClass.class, false, PsiCodeBlock.class, PsiMethod.class, PsiExpressionList.class, PsiVariable.class, PsiAnnotation.class);
    if (containingClass != null && psiElement().afterLeaf(PsiKeyword.EXTENDS, PsiKeyword.IMPLEMENTS, ",", "&").accepts(position)) {
      return new AndFilter(ElementClassFilter.CLASS, new NotFilter(new AssignableFromContextFilter()));
    }

    if (ANNOTATION_NAME.accepts(position)) {
      return new AnnotationTypeFilter();
    }

    if (JavaKeywordCompletion.DECLARATION_START.getValue().accepts(position) ||
        JavaKeywordCompletion.isInsideParameterList(position) ||
        isInsideAnnotationName(position)) {
      return new OrFilter(ElementClassFilter.CLASS, ElementClassFilter.PACKAGE_FILTER);
    }

    if (psiElement().afterLeaf(PsiKeyword.INSTANCEOF).accepts(position)) {
      return new ElementExtractorFilter(ElementClassFilter.CLASS);
    }

    if (JavaKeywordCompletion.VARIABLE_AFTER_FINAL.accepts(position)) {
      return ElementClassFilter.CLASS;
    }

    if (CATCH_OR_FINALLY.accepts(position) ||
        JavaKeywordCompletion.START_SWITCH.accepts(position) ||
        JavaKeywordCompletion.isInstanceofPlace(position) ||
        JavaKeywordCompletion.isAfterPrimitiveOrArrayType(position)) {
      return null;
    }

    if (JavaKeywordCompletion.START_FOR.accepts(position)) {
      return new OrFilter(ElementClassFilter.CLASS, ElementClassFilter.VARIABLE);
    }

    if (JavaSmartCompletionContributor.AFTER_NEW.accepts(position)) {
      return ElementClassFilter.CLASS;
    }

    if (psiElement().inside(PsiReferenceParameterList.class).accepts(position)) {
      return ElementClassFilter.CLASS;
    }

    if (psiElement().inside(PsiAnnotationParameterList.class).accepts(position)) {
      return createAnnotationFilter(position);
    }

    PsiVariable var = PsiTreeUtil.getParentOfType(position, PsiVariable.class, false, PsiClass.class);
    if (var != null && PsiTreeUtil.isAncestor(var.getInitializer(), position, false)) {
      return new ExcludeFilter(var);
    }

    if (SWITCH_LABEL.accepts(position)) {
      return new ClassFilter(PsiField.class) {
        @Override
        public boolean isAcceptable(Object element, PsiElement context) {
          return element instanceof PsiEnumConstant;
        }
      };
    }

    PsiForeachStatement loop = PsiTreeUtil.getParentOfType(position, PsiForeachStatement.class);
    if (loop != null && PsiTreeUtil.isAncestor(loop.getIteratedValue(), position, false)) {
      return new ExcludeFilter(loop.getIterationParameter());
    }

    return TrueFilter.INSTANCE;
  }

  private static boolean isInsideAnnotationName(PsiElement position) {
    PsiAnnotation anno = PsiTreeUtil.getParentOfType(position, PsiAnnotation.class, true, PsiMember.class);
    return anno != null && PsiTreeUtil.isAncestor(anno.getNameReferenceElement(), position, true);
  }

  private static ElementFilter createAnnotationFilter(PsiElement position) {
    OrFilter orFilter = new OrFilter(ElementClassFilter.CLASS,
                                   ElementClassFilter.PACKAGE_FILTER,
                                   new AndFilter(new ClassFilter(PsiField.class),
                                                 new ModifierFilter(PsiModifier.STATIC, PsiModifier.FINAL)));
    if (psiElement().insideStarting(psiNameValuePair()).accepts(position)) {
      orFilter.addFilter(new ClassFilter(PsiAnnotationMethod.class) {
        @Override
        public boolean isAcceptable(Object element, PsiElement context) {
          return element instanceof PsiAnnotationMethod && PsiUtil.isAnnotationMethod((PsiElement)element);
        }
      });
    }
    return orFilter;
  }

  @Override
  public void fillCompletionVariants(@NotNull final CompletionParameters parameters, @NotNull final CompletionResultSet _result) {
    if (parameters.getCompletionType() != CompletionType.BASIC) {
      return;
    }

    final PsiElement position = parameters.getPosition();
    if (!isInJavaContext(position)) {
      return;
    }

    if (AFTER_NUMBER_LITERAL.accepts(position) || UNEXPECTED_REFERENCE_AFTER_DOT.accepts(position)) {
      _result.stopHere();
      return;
    }

    final CompletionResultSet result = JavaCompletionSorting.addJavaSorting(parameters, _result);
    JavaCompletionSession session = new JavaCompletionSession(result);

    if (ANNOTATION_ATTRIBUTE_NAME.accepts(position) && !JavaKeywordCompletion.isAfterPrimitiveOrArrayType(position)) {
      JavaKeywordCompletion.addExpectedTypeMembers(parameters, result);
      JavaKeywordCompletion.addPrimitiveTypes(result, position, session);
      completeAnnotationAttributeName(result, position, parameters);
      result.stopHere();
      return;
    }

    PrefixMatcher matcher = result.getPrefixMatcher();
    PsiElement parent = position.getParent();

    if (JavaModuleCompletion.isModuleFile(parameters.getOriginalFile())) {
      JavaModuleCompletion.addVariants(position, result);
      result.stopHere();
      return;
    }

    if (position instanceof PsiIdentifier) {
      addIdentifierVariants(parameters, position, result, matcher, parent, session);
    }

    Set<String> usedWords = addReferenceVariants(parameters, result, session);

    if (psiElement().inside(PsiLiteralExpression.class).accepts(position)) {
      PsiReference reference = position.getContainingFile().findReferenceAt(parameters.getOffset());
      if (reference == null || reference.isSoft()) {
        WordCompletionContributor.addWordCompletionVariants(result, parameters, usedWords);
      }
    }

    if (position instanceof PsiIdentifier) {
      JavaGenerateMemberCompletionContributor.fillCompletionVariants(parameters, result);
    }

    addAllClasses(parameters, result, session);

    if (position instanceof PsiIdentifier) {
      FunctionalExpressionCompletionProvider.addFunctionalVariants(parameters, false, true, result);
    }

    if (position instanceof PsiIdentifier &&
        parent instanceof PsiReferenceExpression &&
        !((PsiReferenceExpression)parent).isQualified() &&
        parameters.isExtendedCompletion() &&
        StringUtil.isNotEmpty(matcher.getPrefix())) {
      new JavaStaticMemberProcessor(parameters).processStaticMethodsGlobally(matcher, result);
    }

    result.stopHere();
  }

  private static void addIdentifierVariants(@NotNull CompletionParameters parameters,
                                            PsiElement position,
                                            CompletionResultSet result,
                                            PrefixMatcher matcher,
                                            PsiElement parent,
                                            @NotNull JavaCompletionSession session) {
    if (TypeArgumentCompletionProvider.IN_TYPE_ARGS.accepts(position)) {
      new TypeArgumentCompletionProvider(false, session).addCompletions(parameters, new ProcessingContext(), result);
    }

    FunctionalExpressionCompletionProvider.addFunctionalVariants(parameters, false, false, result);

    if (JavaSmartCompletionContributor.AFTER_NEW.accepts(position)) {
      new JavaInheritorsGetter(ConstructorInsertHandler.BASIC_INSTANCE).generateVariants(parameters, matcher, session);
    }

    if (MethodReturnTypeProvider.IN_METHOD_RETURN_TYPE.accepts(position)) {
      MethodReturnTypeProvider.addProbableReturnTypes(parameters, element -> {
        registerClassFromTypeElement(element, session);
        result.addElement(element);
      });
    }

    if (SmartCastProvider.shouldSuggestCast(parameters)) {
      SmartCastProvider.addCastVariants(parameters, element -> {
        registerClassFromTypeElement(element, session);
        result.addElement(PrioritizedLookupElement.withPriority(element, 1));
      });
    }

    if (parent instanceof PsiReferenceExpression) {
      final List<ExpectedTypeInfo> expected = Arrays.asList(ExpectedTypesProvider.getExpectedTypes((PsiExpression)parent, true));
      CollectConversion.addCollectConversion((PsiReferenceExpression)parent, expected,
                                             JavaSmartCompletionContributor.decorateWithoutTypeCheck(result, expected));
    }

    if (IMPORT_REFERENCE.accepts(position)) {
      result.addElement(LookupElementBuilder.create("*"));
    }

    addKeywords(parameters, result, session);

    addExpressionVariants(parameters, position, result);
  }

  private static void registerClassFromTypeElement(LookupElement element, JavaCompletionSession session) {
    PsiType type = assertNotNull(element.as(PsiTypeLookupItem.CLASS_CONDITION_KEY)).getType();
    if (type instanceof PsiPrimitiveType) {
      session.registerKeyword(type.getCanonicalText(false));
      return;
    }

    PsiClass aClass =
      type instanceof PsiClassType && ((PsiClassType)type).getParameterCount() == 0 ? ((PsiClassType)type).resolve() : null;
    if (aClass != null) {
      session.registerClass(aClass);
    }
  }

  private static void addExpressionVariants(@NotNull CompletionParameters parameters, PsiElement position, CompletionResultSet result) {
    if (JavaSmartCompletionContributor.INSIDE_EXPRESSION.accepts(position) &&
        !JavaKeywordCompletion.AFTER_DOT.accepts(position) && !SmartCastProvider.shouldSuggestCast(parameters)) {
      JavaKeywordCompletion.addExpectedTypeMembers(parameters, result);
      if (SameSignatureCallParametersProvider.IN_CALL_ARGUMENT.accepts(position)) {
        new SameSignatureCallParametersProvider().addCompletions(parameters, new ProcessingContext(), result);
      }
    }
  }

  public static boolean isInJavaContext(PsiElement position) {
    return PsiUtilCore.findLanguageFromElement(position).isKindOf(JavaLanguage.INSTANCE);
  }

  public static void addAllClasses(final CompletionParameters parameters,
                                   final CompletionResultSet result,
                                   final JavaCompletionSession session) {
    if (!isClassNamePossible(parameters) || !mayStartClassName(result)) {
      return;
    }

    if (parameters.getInvocationCount() >= 2) {
      JavaClassNameCompletionContributor.addAllClasses(parameters, parameters.getInvocationCount() <= 2, result.getPrefixMatcher(), element -> {
        if (!session.alreadyProcessed(element)) {
          result.addElement(JavaCompletionUtil.highlightIfNeeded(null, element, element.getObject(), parameters.getPosition()));
        }
      });
    } else {
      advertiseSecondCompletion(parameters.getPosition().getProject(), result);
    }
  }

  public static void advertiseSecondCompletion(Project project, CompletionResultSet result) {
    if (FeatureUsageTracker.getInstance().isToBeAdvertisedInLookup(CodeCompletionFeatures.SECOND_BASIC_COMPLETION, project)) {
      result.addLookupAdvertisement("Press " + getActionShortcut(IdeActions.ACTION_CODE_COMPLETION) + " to see non-imported classes");
    }
  }

  private static Set<String> addReferenceVariants(final CompletionParameters parameters, CompletionResultSet result, JavaCompletionSession session) {
    final Set<String> usedWords = new HashSet<>();
    final PsiElement position = parameters.getPosition();
    final boolean first = parameters.getInvocationCount() <= 1;
    final boolean isSwitchLabel = SWITCH_LABEL.accepts(position);
    final boolean isAfterNew = JavaClassNameCompletionContributor.AFTER_NEW.accepts(position);
    final boolean pkgContext = JavaCompletionUtil.inSomePackage(position);
    final PsiType[] expectedTypes = ExpectedTypesGetter.getExpectedTypes(parameters.getPosition(), true);
    LegacyCompletionContributor.processReferences(parameters, result, (reference, result1) -> {
      if (reference instanceof PsiJavaReference) {
        ElementFilter filter = getReferenceFilter(position);
        if (filter != null) {
          if (INSIDE_CONSTRUCTOR.accepts(position) &&
              (parameters.getInvocationCount() <= 1 || CheckInitialized.isInsideConstructorCall(position))) {
            filter = new AndFilter(filter, new CheckInitialized(position));
          }
          final PsiFile originalFile = parameters.getOriginalFile();
          JavaCompletionProcessor.Options options =
            JavaCompletionProcessor.Options.DEFAULT_OPTIONS
              .withCheckAccess(first)
              .withFilterStaticAfterInstance(first)
              .withShowInstanceInStaticContext(!first);
          for (LookupElement element : JavaCompletionUtil.processJavaReference(position,
                                                                               (PsiJavaReference)reference,
                                                                               new ElementExtractorFilter(filter),
                                                                               options,
                                                                               result1.getPrefixMatcher(), parameters)) {
            if (session.alreadyProcessed(element)) {
              continue;
            }

            if (isSwitchLabel) {
              result1.addElement(new IndentingDecorator(TailTypeDecorator.withTail(element, TailType.createSimpleTailType(':'))));
            }
            else {
              final LookupItem item = element.as(LookupItem.CLASS_CONDITION_KEY);
              if (originalFile instanceof PsiJavaCodeReferenceCodeFragment &&
                  !((PsiJavaCodeReferenceCodeFragment)originalFile).isClassesAccepted() && item != null) {
                item.setTailType(TailType.NONE);
              }
              if (item instanceof JavaMethodCallElement) {
                JavaMethodCallElement call = (JavaMethodCallElement)item;
                final PsiMethod method = call.getObject();
                if (method.getTypeParameters().length > 0) {
                  final PsiType returned = TypeConversionUtil.erasure(method.getReturnType());
                  PsiType matchingExpectation = returned == null
                                                ? null
                                                : ContainerUtil.find(expectedTypes, type -> type.isAssignableFrom(returned));
                  if (matchingExpectation != null) {
                    call.setInferenceSubstitutor(SmartCompletionDecorator.calculateMethodReturnTypeSubstitutor(method, matchingExpectation), position);
                  }
                }
              }

              result1.addElement(element);
            }
          }
        }
        return;
      }
      if (reference instanceof PsiLabelReference) {
        LabelReferenceCompletion.processLabelReference(result1, (PsiLabelReference)reference);
        return;
      }

      final Object[] variants = reference.getVariants();
      //noinspection ConstantConditions
      if (variants == null) {
        LOG.error("Reference=" + reference);
      }
      for (Object completion : variants) {
        if (completion == null) {
          LOG.error("Position=" + position + "\n;Reference=" + reference + "\n;variants=" + Arrays.toString(variants));
        }
        if (completion instanceof LookupElement && !session.alreadyProcessed((LookupElement)completion)) {
          usedWords.add(((LookupElement)completion).getLookupString());
          result1.addElement((LookupElement)completion);
        }
        else if (completion instanceof PsiClass) {
          Condition<PsiClass> condition = psiClass -> !session.alreadyProcessed(psiClass) &&
                                                      JavaCompletionUtil.isSourceLevelAccessible(position, psiClass, pkgContext);
          for (JavaPsiClassReferenceElement item : JavaClassNameCompletionContributor.createClassLookupItems((PsiClass)completion,
                                                                                                             isAfterNew,
                                                                                                             JavaClassNameInsertHandler.JAVA_CLASS_INSERT_HANDLER,
                                                                                                             condition)) {
            usedWords.add(item.getLookupString());
            result1.addElement(item);
          }

        }
        else {
          //noinspection deprecation
          LookupElement element = LookupItemUtil.objectToLookupItem(completion);
          usedWords.add(element.getLookupString());
          result1.addElement(element);
        }
      }
    });
    return usedWords;
  }

  private static void addKeywords(CompletionParameters parameters, CompletionResultSet result, JavaCompletionSession session) {
    JavaKeywordCompletion.addKeywords(parameters, session, element -> {
      if (element.getLookupString().startsWith(result.getPrefixMatcher().getPrefix())) {
        result.addElement(element);
      }
    });

    JavaKeywordCompletion.addEnumCases(result, parameters.getPosition());
  }

  static boolean isClassNamePossible(CompletionParameters parameters) {
    boolean isSecondCompletion = parameters.getInvocationCount() >= 2;

    PsiElement position = parameters.getPosition();
    if (JavaKeywordCompletion.isInstanceofPlace(position)) return false;

    final PsiElement parent = position.getParent();
    if (!(parent instanceof PsiJavaCodeReferenceElement)) return isSecondCompletion;
    if (((PsiJavaCodeReferenceElement)parent).getQualifier() != null) return isSecondCompletion;

    if (parent instanceof PsiJavaCodeReferenceElementImpl &&
        ((PsiJavaCodeReferenceElementImpl)parent).getKind(parent.getContainingFile()) == PsiJavaCodeReferenceElementImpl.PACKAGE_NAME_KIND) {
      return false;
    }

    PsiElement grand = parent.getParent();
    if (grand instanceof PsiSwitchLabelStatement) {
      return false;
    }

    if (psiElement().inside(PsiImportStatement.class).accepts(parent)) {
      return isSecondCompletion;
    }

    if (grand instanceof PsiAnonymousClass) {
      grand = grand.getParent();
    }
    if (grand instanceof PsiNewExpression && ((PsiNewExpression)grand).getQualifier() != null) {
      return false;
    }

    if (JavaKeywordCompletion.isAfterPrimitiveOrArrayType(position)) {
      return false;
    }

    return true;
  }

  public static boolean mayStartClassName(CompletionResultSet result) {
    return StringUtil.isNotEmpty(result.getPrefixMatcher().getPrefix());
  }

  private static void completeAnnotationAttributeName(CompletionResultSet result, PsiElement insertedElement,
                                                      CompletionParameters parameters) {
    PsiNameValuePair pair = PsiTreeUtil.getParentOfType(insertedElement, PsiNameValuePair.class);
    PsiAnnotationParameterList parameterList = (PsiAnnotationParameterList)assertNotNull(pair).getParent();
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
        insertedElement, (PsiJavaReference)insertedElement.getParent(), new ElementExtractorFilter(createAnnotationFilter(insertedElement)), JavaCompletionProcessor.Options.DEFAULT_OPTIONS, result.getPrefixMatcher(), parameters);
      for (final LookupElement element : set) {
        result.addElement(element);
      }
      addAllClasses(parameters, result, new JavaCompletionSession(result));
    }

    if (annoClass != null) {
      final PsiNameValuePair[] existingPairs = parameterList.getAttributes();

      methods: for (PsiMethod method : annoClass.getMethods()) {
        if (!(method instanceof PsiAnnotationMethod)) continue;

        final String attrName = method.getName();
        for (PsiNameValuePair existingAttr : existingPairs) {
          if (PsiTreeUtil.isAncestor(existingAttr, insertedElement, false)) break;
          if (Comparing.equal(existingAttr.getName(), attrName) ||
              PsiAnnotation.DEFAULT_REFERENCED_METHOD_NAME.equals(attrName) && existingAttr.getName() == null) continue methods;
        }
        LookupElementBuilder element = LookupElementBuilder.createWithIcon(method).withInsertHandler(new InsertHandler<LookupElement>() {
          @Override
          public void handleInsert(InsertionContext context, LookupElement item) {
            final Editor editor = context.getEditor();
            TailType.EQ.processTail(editor, editor.getCaretModel().getOffset());
            context.setAddCompletionChar(false);

            context.commitDocument();
            PsiAnnotationParameterList paramList =
              PsiTreeUtil.findElementOfClassAtOffset(context.getFile(), context.getStartOffset(), PsiAnnotationParameterList.class, false);
            if (paramList != null && paramList.getAttributes().length > 0 && paramList.getAttributes()[0].getName() == null) {
              int valueOffset = paramList.getAttributes()[0].getTextRange().getStartOffset();
              context.getDocument().insertString(valueOffset, PsiAnnotation.DEFAULT_REFERENCED_METHOD_NAME);
              TailType.EQ.processTail(editor, valueOffset + PsiAnnotation.DEFAULT_REFERENCED_METHOD_NAME.length());
            }
          }
        });

        PsiAnnotationMemberValue defaultValue = ((PsiAnnotationMethod)method).getDefaultValue();
        if (defaultValue != null) {
          element = element.withTailText(" default " + defaultValue.getText(), true);
        }

        result.addElement(element);
      }
    }
  }

  @Override
  public String advertise(@NotNull final CompletionParameters parameters) {
    if (!(parameters.getOriginalFile() instanceof PsiJavaFile)) return null;

    if (parameters.getCompletionType() == CompletionType.BASIC && parameters.getInvocationCount() > 0) {
      PsiElement position = parameters.getPosition();
      if (psiElement().withParent(psiReferenceExpression().withFirstChild(psiReferenceExpression().referencing(psiClass()))).accepts(position)) {
        if (CompletionUtil.shouldShowFeature(parameters, JavaCompletionFeatures.GLOBAL_MEMBER_NAME)) {
          final String shortcut = getActionShortcut(IdeActions.ACTION_CODE_COMPLETION);
          if (StringUtil.isNotEmpty(shortcut)) {
            return "Pressing " + shortcut + " twice without a class qualifier would show all accessible static methods";
          }
        }
      }
    }

    if (parameters.getCompletionType() != CompletionType.SMART && shouldSuggestSmartCompletion(parameters.getPosition())) {
      if (CompletionUtil.shouldShowFeature(parameters, CodeCompletionFeatures.EDITING_COMPLETION_SMARTTYPE_GENERAL)) {
        final String shortcut = getActionShortcut(IdeActions.ACTION_SMART_TYPE_COMPLETION);
        if (StringUtil.isNotEmpty(shortcut)) {
          return CompletionBundle.message("completion.smart.hint", shortcut);
        }
      }
    }

    if (parameters.getCompletionType() == CompletionType.SMART && parameters.getInvocationCount() == 1) {
      final PsiType[] psiTypes = ExpectedTypesGetter.getExpectedTypes(parameters.getPosition(), true);
      if (psiTypes.length > 0) {
        if (CompletionUtil.shouldShowFeature(parameters, JavaCompletionFeatures.SECOND_SMART_COMPLETION_TOAR)) {
          final String shortcut = getActionShortcut(IdeActions.ACTION_SMART_TYPE_COMPLETION);
          if (StringUtil.isNotEmpty(shortcut)) {
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
          if (StringUtil.isNotEmpty(shortcut)) {
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
          if (StringUtil.isNotEmpty(shortcut)) {
            return CompletionBundle.message("completion.smart.chain.hint", shortcut);
          }
        }
      }
    }
    return null;
  }

  @Override
  public String handleEmptyLookup(@NotNull final CompletionParameters parameters, final Editor editor) {
    if (!(parameters.getOriginalFile() instanceof PsiJavaFile)) return null;

    final String ad = advertise(parameters);
    final String suffix = ad == null ? "" : "; " + StringUtil.decapitalize(ad);
    if (parameters.getCompletionType() == CompletionType.SMART) {
      PsiExpression expression = PsiTreeUtil.getContextOfType(parameters.getPosition(), PsiExpression.class, true);
      if (expression instanceof PsiLiteralExpression) {
        return LangBundle.message("completion.no.suggestions") + suffix;
      }

      if (expression instanceof PsiInstanceOfExpression) {
        final PsiInstanceOfExpression instanceOfExpression = (PsiInstanceOfExpression)expression;
        if (PsiTreeUtil.isAncestor(instanceOfExpression.getCheckType(), parameters.getPosition(), false)) {
          return LangBundle.message("completion.no.suggestions") + suffix;
        }
      }

      final Set<PsiType> expectedTypes = JavaCompletionUtil.getExpectedTypes(parameters);
      if (expectedTypes != null) {
        PsiType type = expectedTypes.size() == 1 ? expectedTypes.iterator().next() : null;
        if (type != null) {
          final PsiType deepComponentType = type.getDeepComponentType();
          String expectedType = type.getPresentableText();
          if (expectedType.contains(CompletionUtil.DUMMY_IDENTIFIER_TRIMMED)) {
            return null;
          }

          if (deepComponentType instanceof PsiClassType) {
            if (((PsiClassType)deepComponentType).resolve() != null) {
              return CompletionBundle.message("completion.no.suggestions.of.type", expectedType) + suffix;
            }
            return CompletionBundle.message("completion.unknown.type", expectedType) + suffix;
          }
          if (!PsiType.NULL.equals(type)) {
            return CompletionBundle.message("completion.no.suggestions.of.type", expectedType) + suffix;
          }
        }
      }
    }
    return LangBundle.message("completion.no.suggestions") + suffix;
  }

  @Override
  public boolean invokeAutoPopup(@NotNull PsiElement position, char typeChar) {
    return typeChar == ':' && JavaTokenType.COLON == position.getNode().getElementType();
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

  @Override
  public void beforeCompletion(@NotNull final CompletionInitializationContext context) {
    final PsiFile file = context.getFile();

    if (file instanceof PsiJavaFile) {
      if (context.getInvocationCount() > 0) {
        autoImport(file, context.getStartOffset() - 1, context.getEditor());

        PsiElement leaf = file.findElementAt(context.getStartOffset() - 1);
        if (leaf != null) leaf = PsiTreeUtil.prevVisibleLeaf(leaf);

        PsiVariable variable = PsiTreeUtil.getParentOfType(leaf, PsiVariable.class);
        if (variable != null) {
          PsiTypeElement typeElement = variable.getTypeElement();
          if (typeElement != null) {
            PsiType type = typeElement.getType();
            if (type instanceof PsiClassType && ((PsiClassType)type).resolve() == null) {
              autoImportReference(file, context.getEditor(), typeElement.getInnermostComponentReferenceElement());
            }
          }
        }
      }

      if (context.getCompletionType() == CompletionType.BASIC) {
        if (semicolonNeeded(context.getEditor(), file, context.getStartOffset())) {
          context.setDummyIdentifier(CompletionInitializationContext.DUMMY_IDENTIFIER.trim() + ";");
          return;
        }

        final PsiJavaCodeReferenceElement ref = PsiTreeUtil.findElementOfClassAtOffset(file, context.getStartOffset(), PsiJavaCodeReferenceElement.class, false);
        if (ref != null && !(ref instanceof PsiReferenceExpression)) {
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

  public static boolean semicolonNeeded(final Editor editor, PsiFile file,  final int startOffset) {
    final PsiJavaCodeReferenceElement ref = PsiTreeUtil.findElementOfClassAtOffset(file, startOffset, PsiJavaCodeReferenceElement.class, false);
    if (ref != null && !(ref instanceof PsiReferenceExpression)) {
      if (ref.getParent() instanceof PsiTypeElement) {
        return true;
      }
    }

    HighlighterIterator iterator = ((EditorEx)editor).getHighlighter().createIterator(startOffset);
    if (iterator.atEnd()) return false;

    if (iterator.getTokenType() == JavaTokenType.IDENTIFIER) {
      iterator.advance();
    }

    while (!iterator.atEnd() && ElementType.JAVA_COMMENT_OR_WHITESPACE_BIT_SET.contains(iterator.getTokenType())) {
      iterator.advance();
    }

    if (!iterator.atEnd() && iterator.getTokenType() == JavaTokenType.LPARENTH && PsiTreeUtil.getParentOfType(ref, PsiExpression.class, PsiClass.class) == null) {
      // looks like a method declaration, e.g. StringBui<caret>methodName() inside a class
      return true;
    }

    if (!iterator.atEnd()
        && (iterator.getTokenType() == JavaTokenType.COLON)
        && null == PsiTreeUtil.findElementOfClassAtOffset(file, startOffset, PsiConditionalExpression.class, false)) {
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

    return iterator.getTokenType() == JavaTokenType.EQ; // <caret> foo = something, we don't want the reference to be treated as a type
  }

  private static void autoImport(@NotNull final PsiFile file, int offset, @NotNull final Editor editor) {
    final CharSequence text = editor.getDocument().getCharsSequence();
    while (offset > 0 && Character.isJavaIdentifierPart(text.charAt(offset))) offset--;
    if (offset <= 0) return;

    while (offset > 0 && Character.isWhitespace(text.charAt(offset))) offset--;
    if (offset <= 0 || text.charAt(offset) != '.') return;

    offset--;

    while (offset > 0 && Character.isWhitespace(text.charAt(offset))) offset--;
    if (offset <= 0) return;

    autoImportReference(file, editor, extractReference(PsiTreeUtil.findElementOfClassAtOffset(file, offset, PsiExpression.class, false)));
  }

  private static void autoImportReference(@NotNull PsiFile file, @NotNull Editor editor, @Nullable PsiJavaCodeReferenceElement element) {
    if (element == null) return;

    while (true) {
      final PsiJavaCodeReferenceElement qualifier = extractReference(element.getQualifier());
      if (qualifier == null) break;

      element = qualifier;
    }
    if (!(element.getParent() instanceof PsiMethodCallExpression) && element.multiResolve(true).length == 0) {
      new ImportClassFix(element).doFix(editor, false, false);
      PsiDocumentManager.getInstance(file.getProject()).commitDocument(editor.getDocument());
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

  static class IndentingDecorator extends LookupElementDecorator<LookupElement> {
    public IndentingDecorator(LookupElement delegate) {
      super(delegate);
    }

    @Override
    public void handleInsert(InsertionContext context) {
      super.handleInsert(context);
      Project project = context.getProject();
      Document document = context.getDocument();
      int lineStartOffset = DocumentUtil.getLineStartOffset(context.getStartOffset(), document);
      PsiDocumentManager.getInstance(project).commitDocument(document);
      CodeStyleManager.getInstance(project).adjustLineIndent(context.getFile(), lineStartOffset);
    }
  }
}
