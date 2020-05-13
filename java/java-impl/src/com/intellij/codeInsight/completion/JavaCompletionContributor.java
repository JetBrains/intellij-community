// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.ExpectedTypeInfo;
import com.intellij.codeInsight.ExpectedTypesProvider;
import com.intellij.codeInsight.TailType;
import com.intellij.codeInsight.TailTypes;
import com.intellij.codeInsight.completion.scope.JavaCompletionProcessor;
import com.intellij.codeInsight.daemon.impl.quickfix.ImportClassFix;
import com.intellij.codeInsight.lookup.*;
import com.intellij.featureStatistics.FeatureUsageTracker;
import com.intellij.icons.AllIcons;
import com.intellij.java.JavaBundle;
import com.intellij.lang.LangBundle;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.patterns.ElementPattern;
import com.intellij.patterns.PatternCondition;
import com.intellij.patterns.PsiNameValuePairPattern;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.filters.*;
import com.intellij.psi.filters.classes.AnnotationTypeFilter;
import com.intellij.psi.filters.classes.AssignableFromContextFilter;
import com.intellij.psi.filters.element.ModifierFilter;
import com.intellij.psi.filters.getters.ExpectedTypesGetter;
import com.intellij.psi.filters.getters.JavaMembersGetter;
import com.intellij.psi.impl.PsiImplUtil;
import com.intellij.psi.impl.java.stubs.index.JavaAutoModuleNameIndex;
import com.intellij.psi.impl.java.stubs.index.JavaModuleNameIndex;
import com.intellij.psi.impl.java.stubs.index.JavaSourceModuleNameIndex;
import com.intellij.psi.impl.source.PsiJavaCodeReferenceElementImpl;
import com.intellij.psi.impl.source.PsiLabelReference;
import com.intellij.psi.scope.ElementClassFilter;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.ProjectScope;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.Consumer;
import com.intellij.util.DocumentUtil;
import com.intellij.util.ProcessingContext;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static com.intellij.patterns.PsiJavaPatterns.*;

/**
 * @author peter
 */
public class JavaCompletionContributor extends CompletionContributor {
  private static final Logger LOG = Logger.getInstance(JavaCompletionContributor.class);

  private static final ElementPattern<PsiElement> UNEXPECTED_REFERENCE_AFTER_DOT =
    psiElement().afterLeaf(".").insideStarting(psiExpressionStatement());
  private static final PsiNameValuePairPattern NAME_VALUE_PAIR =
    psiNameValuePair().withSuperParent(2, psiElement(PsiAnnotation.class));
  private static final ElementPattern<PsiElement> ANNOTATION_ATTRIBUTE_NAME =
    or(psiElement(PsiIdentifier.class).withParent(NAME_VALUE_PAIR),
       psiElement().afterLeaf("(").withParent(psiReferenceExpression().withParent(NAME_VALUE_PAIR)));

  public static final ElementPattern<PsiElement> IN_SWITCH_LABEL =
    psiElement().withSuperParent(2, psiElement(PsiExpressionList.class).withParent(psiElement(PsiSwitchLabelStatementBase.class).withSuperParent(2, PsiSwitchBlock.class)));
  private static final ElementPattern<PsiElement> IN_ENUM_SWITCH_LABEL =
    psiElement().withSuperParent(2, psiElement(PsiExpressionList.class).withParent(psiElement(PsiSwitchLabelStatementBase.class).withSuperParent(2,
      psiElement(PsiSwitchBlock.class).with(new PatternCondition<PsiSwitchBlock>("enumExpressionType") {
        @Override
        public boolean accepts(@NotNull PsiSwitchBlock psiSwitchBlock, ProcessingContext context) {
          PsiExpression expression = psiSwitchBlock.getExpression();
          if (expression == null) return false;
          PsiClass aClass = PsiUtil.resolveClassInClassTypeOnly(expression.getType());
          return aClass != null && aClass.isEnum();
        }
      }))));

  private static final ElementPattern<PsiElement> AFTER_NUMBER_LITERAL =
    psiElement().afterLeaf(psiElement().withElementType(
      elementType().oneOf(JavaTokenType.DOUBLE_LITERAL, JavaTokenType.LONG_LITERAL, JavaTokenType.FLOAT_LITERAL, JavaTokenType.INTEGER_LITERAL)));
  private static final ElementPattern<PsiElement> IMPORT_REFERENCE =
    psiElement().withParent(psiElement(PsiJavaCodeReferenceElement.class).withParent(PsiImportStatementBase.class));
  private static final ElementPattern<PsiElement> CATCH_OR_FINALLY = psiElement().afterLeaf(
    psiElement().withText("}").withParent(
      psiElement(PsiCodeBlock.class).afterLeaf(PsiKeyword.TRY)));
  private static final ElementPattern<PsiElement> INSIDE_CONSTRUCTOR = psiElement().inside(psiMethod().constructor(true));
  private static final ElementPattern<PsiElement> AFTER_ENUM_CONSTANT =
    psiElement().inside(PsiTypeElement.class).afterLeaf(
      psiElement().inside(true, psiElement(PsiEnumConstant.class), psiElement(PsiClass.class, PsiExpressionList.class)));

  @Nullable
  public static ElementFilter getReferenceFilter(PsiElement position) {
    if (isInExtendsOrImplementsList(position)) {
      return new AndFilter(ElementClassFilter.CLASS, new NotFilter(new AssignableFromContextFilter()));
    }

    if (getAnnotationNameIfInside(position) != null) {
      return new OrFilter(ElementClassFilter.PACKAGE, new AnnotationTypeFilter());
    }

    if (JavaKeywordCompletion.isDeclarationStart(position) ||
        JavaKeywordCompletion.isInsideParameterList(position) ||
        isInsideAnnotationName(position) ||
        psiElement().inside(PsiReferenceParameterList.class).accepts(position) ||
        isDefinitelyVariableType(position)) {
      return new OrFilter(ElementClassFilter.CLASS, ElementClassFilter.PACKAGE);
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

    if (JavaKeywordCompletion.START_FOR.withParents(PsiJavaCodeReferenceElement.class, PsiExpressionStatement.class, PsiForStatement.class).accepts(position)) {
      return new OrFilter(ElementClassFilter.CLASS, ElementClassFilter.VARIABLE);
    }

    if (JavaSmartCompletionContributor.AFTER_NEW.accepts(position)) {
      return ElementClassFilter.CLASS;
    }

    if (psiElement().inside(PsiAnnotationParameterList.class).accepts(position)) {
      return createAnnotationFilter(position);
    }

    PsiVariable var = PsiTreeUtil.getParentOfType(position, PsiVariable.class, false, PsiClass.class);
    if (var != null && PsiTreeUtil.isAncestor(var.getInitializer(), position, false)) {
      return new ExcludeFilter(var);
    }

    if (IN_ENUM_SWITCH_LABEL.accepts(position)) {
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

    if (PsiTreeUtil.getParentOfType(position, PsiPackageAccessibilityStatement.class) != null) {
      return applyScopeFilter(ElementClassFilter.PACKAGE, position);
    }

    if (PsiTreeUtil.getParentOfType(position, PsiUsesStatement.class, PsiProvidesStatement.class) != null) {
      ElementFilter filter = new OrFilter(ElementClassFilter.CLASS, ElementClassFilter.PACKAGE);
      if (PsiTreeUtil.getParentOfType(position, PsiReferenceList.class) != null) {
        filter = applyScopeFilter(filter, position);
      }
      return filter;
    }

    return TrueFilter.INSTANCE;
  }

  private static boolean isDefinitelyVariableType(PsiElement position) {
    return psiElement().withParents(PsiJavaCodeReferenceElement.class, PsiTypeElement.class, PsiDeclarationStatement.class).afterLeaf(psiElement().inside(psiAnnotation())).accepts(position);
  }

  private static boolean isInExtendsOrImplementsList(PsiElement position) {
    PsiClass containingClass = PsiTreeUtil.getParentOfType(
      position, PsiClass.class, false, PsiCodeBlock.class, PsiMethod.class, PsiExpressionList.class, PsiVariable.class, PsiAnnotation.class);
    return containingClass != null &&
           psiElement().afterLeaf(
             psiElement()
               .withText(string().oneOf(PsiKeyword.EXTENDS, PsiKeyword.IMPLEMENTS, ",", "&"))
               .withParent(PsiReferenceList.class)
           ).accepts(position);
  }

  private static boolean isInsideAnnotationName(PsiElement position) {
    PsiAnnotation anno = PsiTreeUtil.getParentOfType(position, PsiAnnotation.class, true, PsiMember.class);
    return anno != null && PsiTreeUtil.isAncestor(anno.getNameReferenceElement(), position, true);
  }

  private static ElementFilter createAnnotationFilter(PsiElement position) {
    List<ElementFilter> filters = ContainerUtil.newArrayList(
      ElementClassFilter.CLASS,
      ElementClassFilter.PACKAGE,
      new AndFilter(new ClassFilter(PsiField.class), new ModifierFilter(PsiModifier.STATIC, PsiModifier.FINAL)));

    if (psiElement().insideStarting(psiNameValuePair()).accepts(position)) {
      filters.add(new ClassFilter(PsiAnnotationMethod.class) {
        @Override
        public boolean isAcceptable(Object element, PsiElement context) {
          return element instanceof PsiAnnotationMethod && PsiUtil.isAnnotationMethod((PsiElement)element);
        }
      });
    }

    return new OrFilter(filters.toArray(ElementFilter.EMPTY_ARRAY));
  }

  public static ElementFilter applyScopeFilter(ElementFilter filter, PsiElement position) {
    Module module = ModuleUtilCore.findModuleForPsiElement(position);
    return module != null ? new AndFilter(filter, new SearchScopeFilter(module.getModuleScope())) : filter;
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

    if (AFTER_NUMBER_LITERAL.accepts(position) ||
        UNEXPECTED_REFERENCE_AFTER_DOT.accepts(position) ||
        AFTER_ENUM_CONSTANT.accepts(position)) {
      _result.stopHere();
      return;
    }

    final CompletionResultSet result = JavaCompletionSorting.addJavaSorting(parameters, _result);
    JavaCompletionSession session = new JavaCompletionSession(result);

    if (ANNOTATION_ATTRIBUTE_NAME.accepts(position) && !JavaKeywordCompletion.isAfterPrimitiveOrArrayType(position)) {
      addExpectedTypeMembers(parameters, result);
      JavaKeywordCompletion.addPrimitiveTypes(result, position, session);
      completeAnnotationAttributeName(result, position, parameters);
      result.stopHere();
      return;
    }

    PrefixMatcher matcher = result.getPrefixMatcher();
    PsiElement parent = position.getParent();

    if (new JavaKeywordCompletion(parameters, session).addWildcardExtendsSuper(result, position)) {
      result.stopHere();
      return;
    }

    if (position instanceof PsiIdentifier) {
      addIdentifierVariants(parameters, position, result, session, matcher);
    }

    MultiMap<CompletionResultSet, LookupElement> referenceVariants = addReferenceVariants(parameters, result, session);
    Set<String> usedWords = ContainerUtil.map2Set(referenceVariants.values(), LookupElement::getLookupString);
    for (Map.Entry<CompletionResultSet, Collection<LookupElement>> entry : referenceVariants.entrySet()) {
      session.registerBatchItems(entry.getKey(), entry.getValue());
    }

    session.flushBatchItems();

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
      FunctionalExpressionCompletionProvider.addFunctionalVariants(parameters, true, result.getPrefixMatcher(), result);
    }

    if (position instanceof PsiIdentifier &&
        parent instanceof PsiReferenceExpression &&
        !((PsiReferenceExpression)parent).isQualified() &&
        parameters.isExtendedCompletion() &&
        StringUtil.isNotEmpty(matcher.getPrefix())) {
      new JavaStaticMemberProcessor(parameters).processStaticMethodsGlobally(matcher, result);
    }

    if (parent instanceof PsiJavaModuleReferenceElement) {
      addModuleReferences(parent, parameters.getOriginalFile(), result);
    }

    result.stopHere();
  }

  private static void addIdentifierVariants(@NotNull CompletionParameters parameters,
                                            PsiElement position,
                                            CompletionResultSet result,
                                            JavaCompletionSession session, PrefixMatcher matcher) {
    session.registerBatchItems(result, getFastIdentifierVariants(parameters, position, matcher, position.getParent(), session));

    if (JavaSmartCompletionContributor.AFTER_NEW.accepts(position)) {
      session.flushBatchItems();
      new JavaInheritorsGetter(ConstructorInsertHandler.BASIC_INSTANCE).generateVariants(parameters, matcher, lookupElement -> {
        if (!isSuggestedByKeywordCompletion(lookupElement)) {
          session.addClassItem(lookupElement);
        }
      });
    }

    suggestSmartCast(parameters, session, false, result);
  }

  private static boolean isSuggestedByKeywordCompletion(LookupElement lookupElement) {
    if (lookupElement instanceof PsiTypeLookupItem) {
      PsiType type = ((PsiTypeLookupItem)lookupElement).getType();
      return type instanceof PsiArrayType && ((PsiArrayType)type).getComponentType() instanceof PsiPrimitiveType;
    }
    return false;
  }

  private static void suggestSmartCast(CompletionParameters parameters, JavaCompletionSession session, boolean quick, Consumer<? super LookupElement> result) {
    if (SmartCastProvider.shouldSuggestCast(parameters)) {
      session.flushBatchItems();
      SmartCastProvider.addCastVariants(parameters, session.getMatcher(), element -> {
        registerClassFromTypeElement(element, session);
        result.consume(PrioritizedLookupElement.withPriority(element, 1));
      }, quick);
    }
  }

  private static List<LookupElement> getFastIdentifierVariants(@NotNull CompletionParameters parameters,
                                                               PsiElement position,
                                                               PrefixMatcher matcher,
                                                               PsiElement parent,
                                                               @NotNull JavaCompletionSession session) {
    List<LookupElement> items = new ArrayList<>();
    if (TypeArgumentCompletionProvider.IN_TYPE_ARGS.accepts(position)) {
      new TypeArgumentCompletionProvider(false, session).addTypeArgumentVariants(parameters, items::add, matcher);
    }

    FunctionalExpressionCompletionProvider.addFunctionalVariants(parameters, false, matcher, items::add);

    if (MethodReturnTypeProvider.IN_METHOD_RETURN_TYPE.accepts(position)) {
      MethodReturnTypeProvider.addProbableReturnTypes(parameters, element -> {
        registerClassFromTypeElement(element, session);
        items.add(element);
      });
    }

    suggestSmartCast(parameters, session, true, items::add);

    if (parent instanceof PsiReferenceExpression) {
      final List<ExpectedTypeInfo> expected = Arrays.asList(ExpectedTypesProvider.getExpectedTypes((PsiExpression)parent, true));
      CollectConversion.addCollectConversion((PsiReferenceExpression)parent, expected,
                                             lookupElement -> items.add(JavaSmartCompletionContributor.decorate(lookupElement, expected)));
    }

    if (IMPORT_REFERENCE.accepts(position)) {
      items.add(LookupElementBuilder.create("*"));
    }

    items.addAll(new JavaKeywordCompletion(parameters, session).getResults());

    addExpressionVariants(parameters, position, items::add);

    return items;
  }

  private static void registerClassFromTypeElement(LookupElement element, JavaCompletionSession session) {
    PsiType type = Objects.requireNonNull(element.as(PsiTypeLookupItem.CLASS_CONDITION_KEY)).getType();
    if (type instanceof PsiPrimitiveType) {
      session.registerKeyword(type.getCanonicalText(false));
    }
    else if (type instanceof PsiClassType && ((PsiClassType)type).getParameterCount() == 0) {
      PsiClass aClass = ((PsiClassType)type).resolve();
      if (aClass != null) {
        session.registerClass(aClass);
      }
    }
  }

  private static void addExpressionVariants(@NotNull CompletionParameters parameters, PsiElement position, Consumer<? super LookupElement> result) {
    if (JavaSmartCompletionContributor.INSIDE_EXPRESSION.accepts(position) &&
        !JavaKeywordCompletion.AFTER_DOT.accepts(position) && !SmartCastProvider.shouldSuggestCast(parameters)) {
      addExpectedTypeMembers(parameters, result);
      if (SameSignatureCallParametersProvider.IN_CALL_ARGUMENT.accepts(position)) {
        new SameSignatureCallParametersProvider().addSignatureItems(parameters, result);
      }
    }
  }

  public static boolean isInJavaContext(PsiElement position) {
    return PsiUtilCore.findLanguageFromElement(position).isKindOf(JavaLanguage.INSTANCE);
  }

  public static void addAllClasses(CompletionParameters parameters, CompletionResultSet result, JavaCompletionSession session) {
    if (!isClassNamePossible(parameters) || !mayStartClassName(result)) {
      return;
    }

    if (parameters.getInvocationCount() >= 2) {
      JavaClassNameCompletionContributor.addAllClasses(parameters, parameters.getInvocationCount() <= 2, result.getPrefixMatcher(), element -> {
        if (!session.alreadyProcessed(element)) {
          result.addElement(JavaCompletionUtil.highlightIfNeeded(null, element, element.getObject(), parameters.getPosition()));
        }
      });
    }
    else {
      advertiseSecondCompletion(parameters.getPosition().getProject(), result);
    }
  }

  public static void advertiseSecondCompletion(Project project, CompletionResultSet result) {
    if (FeatureUsageTracker.getInstance().isToBeAdvertisedInLookup(CodeCompletionFeatures.SECOND_BASIC_COMPLETION, project)) {
      result.addLookupAdvertisement(JavaBundle.message("press.0.to.see.non.imported.classes",
                                                             KeymapUtil.getFirstKeyboardShortcutText(IdeActions.ACTION_CODE_COMPLETION)));
    }
  }

  private static MultiMap<CompletionResultSet, LookupElement> addReferenceVariants(final CompletionParameters parameters,
                                                                                   CompletionResultSet result,
                                                                                   JavaCompletionSession session) {
    MultiMap<CompletionResultSet, LookupElement> items = MultiMap.create();
    final PsiElement position = parameters.getPosition();
    final boolean first = parameters.getInvocationCount() <= 1;
    final TailType switchLabelTail;
    if (IN_ENUM_SWITCH_LABEL.accepts(position)) {
      PsiSwitchBlock block = Objects.requireNonNull(PsiTreeUtil.getParentOfType(position, PsiSwitchBlock.class));
      switchLabelTail = TailTypes.forSwitchLabel(block);
    } else {
      switchLabelTail = null;
    }
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

            if (switchLabelTail != null) {
              items.putValue(result1, new IndentingDecorator(TailTypeDecorator.withTail(element, switchLabelTail)));
            }
            else {
              LookupItem<?> item = element.as(LookupItem.CLASS_CONDITION_KEY);
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
                    call.setInferenceSubstitutorFromExpectedType(position, matchingExpectation);
                  }
                }
              }

              items.putValue(result1, element);
            }
          }
        }

        return;
      }

      if (reference instanceof PsiLabelReference) {
        items.putValues(result1, LabelReferenceCompletion.processLabelReference((PsiLabelReference)reference));
        return;
      }

      final Object[] variants = reference.getVariants();
      if (variants == null) {
        LOG.error("Reference=" + reference);
        return;
      }
      for (Object completion : variants) {
        if (completion == null) {
          LOG.error("Position=" + position + "\n;Reference=" + reference + "\n;variants=" + Arrays.toString(variants));
        }
        if (completion instanceof LookupElement && !session.alreadyProcessed((LookupElement)completion)) {
          items.putValue(result1, (LookupElement)completion);
        }
        else if (completion instanceof PsiClass) {
          Condition<PsiClass> condition = psiClass -> !session.alreadyProcessed(psiClass) &&
                                                      JavaCompletionUtil.isSourceLevelAccessible(position, psiClass, pkgContext);
          items.putValues(result1, JavaClassNameCompletionContributor.createClassLookupItems(
            (PsiClass)completion,
            isAfterNew,
            JavaClassNameInsertHandler.JAVA_CLASS_INSERT_HANDLER,
            condition));

        }
        else {
          //noinspection deprecation
          items.putValue(result1, LookupItemUtil.objectToLookupItem(completion));
        }
      }
    });
    return items;
  }

  static boolean isClassNamePossible(CompletionParameters parameters) {
    boolean isSecondCompletion = parameters.getInvocationCount() >= 2;

    PsiElement position = parameters.getPosition();
    if (JavaKeywordCompletion.isInstanceofPlace(position) ||
        JavaMemberNameCompletionContributor.INSIDE_TYPE_PARAMS_PATTERN.accepts(position) ||
        AFTER_ENUM_CONSTANT.accepts(position)) {
      return false;
    }

    final PsiElement parent = position.getParent();
    if (!(parent instanceof PsiJavaCodeReferenceElement)) return isSecondCompletion;
    if (((PsiJavaCodeReferenceElement)parent).getQualifier() != null) return isSecondCompletion;

    if (parent instanceof PsiJavaCodeReferenceElementImpl &&
        ((PsiJavaCodeReferenceElementImpl)parent).getKindEnum(parent.getContainingFile()) == PsiJavaCodeReferenceElementImpl.Kind.PACKAGE_NAME_KIND) {
      return false;
    }

    if (IN_SWITCH_LABEL.accepts(position)) {
      return false;
    }

    if (psiElement().inside(PsiImportStatement.class).accepts(parent)) {
      return isSecondCompletion;
    }

    PsiElement grand = parent.getParent();
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
    return InternalCompletionSettings.getInstance().mayStartClassNameCompletion(result);
  }

  private static void completeAnnotationAttributeName(CompletionResultSet result, PsiElement insertedElement,
                                                      CompletionParameters parameters) {
    PsiNameValuePair pair = PsiTreeUtil.getParentOfType(insertedElement, PsiNameValuePair.class);
    PsiAnnotationParameterList parameterList = (PsiAnnotationParameterList)Objects.requireNonNull(pair).getParent();
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
        insertedElement, (PsiJavaReference)insertedElement.getParent(), new ElementExtractorFilter(createAnnotationFilter(insertedElement)),
        JavaCompletionProcessor.Options.DEFAULT_OPTIONS, result.getPrefixMatcher(), parameters);
      for (final LookupElement element : set) {
        result.addElement(element);
      }
      addAllClasses(parameters, result, new JavaCompletionSession(result));
    }

    if (annoClass != null && annoClass.isAnnotationType()) {
      final PsiNameValuePair[] existingPairs = parameterList.getAttributes();

      methods: for (PsiMethod method : annoClass.getMethods()) {
        if (!(method instanceof PsiAnnotationMethod)) continue;

        final String attrName = method.getName();
        for (PsiNameValuePair existingAttr : existingPairs) {
          if (PsiTreeUtil.isAncestor(existingAttr, insertedElement, false)) break;
          if (Objects.equals(existingAttr.getName(), attrName) ||
              PsiAnnotation.DEFAULT_REFERENCED_METHOD_NAME.equals(attrName) && existingAttr.getName() == null) continue methods;
        }
        LookupElementBuilder element = LookupElementBuilder.createWithIcon(method)
                            .withStrikeoutness(PsiImplUtil.isDeprecated(method))
                            .withInsertHandler(new InsertHandler<LookupElement>() {
          @Override
          public void handleInsert(@NotNull InsertionContext context, @NotNull LookupElement item) {
            final Editor editor = context.getEditor();
            EqTailType.INSTANCE.processTail(editor, editor.getCaretModel().getOffset());
            context.setAddCompletionChar(false);

            context.commitDocument();
            PsiAnnotationParameterList paramList =
              PsiTreeUtil.findElementOfClassAtOffset(context.getFile(), context.getStartOffset(), PsiAnnotationParameterList.class, false);
            if (paramList != null && paramList.getAttributes().length > 0 && paramList.getAttributes()[0].getName() == null) {
              int valueOffset = paramList.getAttributes()[0].getTextRange().getStartOffset();
              context.getDocument().insertString(valueOffset, PsiAnnotation.DEFAULT_REFERENCED_METHOD_NAME);
              EqTailType.INSTANCE.processTail(editor, valueOffset + PsiAnnotation.DEFAULT_REFERENCED_METHOD_NAME.length());
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
          final String shortcut = KeymapUtil.getFirstKeyboardShortcutText(IdeActions.ACTION_CODE_COMPLETION);
          if (StringUtil.isNotEmpty(shortcut)) {
            return JavaBundle.message("pressing.0.twice.without.a.class.qualifier", shortcut);
          }
        }
      }
    }

    if (parameters.getCompletionType() != CompletionType.SMART && shouldSuggestSmartCompletion(parameters.getPosition())) {
      if (CompletionUtil.shouldShowFeature(parameters, CodeCompletionFeatures.EDITING_COMPLETION_SMARTTYPE_GENERAL)) {
        final String shortcut = KeymapUtil.getFirstKeyboardShortcutText(IdeActions.ACTION_SMART_TYPE_COMPLETION);
        if (StringUtil.isNotEmpty(shortcut)) {
          return JavaBundle.message("completion.smart.hint", shortcut);
        }
      }
    }

    if (parameters.getCompletionType() == CompletionType.SMART && parameters.getInvocationCount() == 1) {
      final PsiType[] psiTypes = ExpectedTypesGetter.getExpectedTypes(parameters.getPosition(), true);
      if (psiTypes.length > 0) {
        if (CompletionUtil.shouldShowFeature(parameters, JavaCompletionFeatures.SECOND_SMART_COMPLETION_TOAR)) {
          final String shortcut = KeymapUtil.getFirstKeyboardShortcutText(IdeActions.ACTION_SMART_TYPE_COMPLETION);
          if (StringUtil.isNotEmpty(shortcut)) {
            for (final PsiType psiType : psiTypes) {
              final PsiType type = PsiUtil.extractIterableTypeParameter(psiType, false);
              if (type != null) {
                return JavaBundle.message("completion.smart.aslist.hint", shortcut, type.getPresentableText());
              }
            }
          }
        }
        if (CompletionUtil.shouldShowFeature(parameters, JavaCompletionFeatures.SECOND_SMART_COMPLETION_ASLIST)) {
          final String shortcut = KeymapUtil.getFirstKeyboardShortcutText(IdeActions.ACTION_SMART_TYPE_COMPLETION);
          if (StringUtil.isNotEmpty(shortcut)) {
            for (final PsiType psiType : psiTypes) {
              if (psiType instanceof PsiArrayType) {
                final PsiType componentType = ((PsiArrayType)psiType).getComponentType();
                if (!(componentType instanceof PsiPrimitiveType)) {
                  return JavaBundle.message("completion.smart.toar.hint", shortcut, componentType.getPresentableText());
                }
              }
            }
          }
        }

        if (CompletionUtil.shouldShowFeature(parameters, JavaCompletionFeatures.SECOND_SMART_COMPLETION_CHAIN)) {
          final String shortcut = KeymapUtil.getFirstKeyboardShortcutText(IdeActions.ACTION_SMART_TYPE_COMPLETION);
          if (StringUtil.isNotEmpty(shortcut)) {
            return JavaBundle.message("completion.smart.chain.hint", shortcut);
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
              return JavaBundle.message("completion.no.suggestions.of.type", expectedType) + suffix;
            }
            return JavaBundle.message("completion.unknown.type", expectedType) + suffix;
          }
          if (!PsiType.NULL.equals(type)) {
            return JavaBundle.message("completion.no.suggestions.of.type", expectedType) + suffix;
          }
        }
      }
    }
    return LangBundle.message("completion.no.suggestions") + suffix;
  }

  @Override
  public boolean invokeAutoPopup(@NotNull PsiElement position, char typeChar) {
    return typeChar == ':' && JavaTokenType.COLON == PsiUtilCore.getElementType(position);
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
    return parent.getParent() instanceof PsiTypeElement || parent.getParent() instanceof PsiExpressionStatement ||
           parent.getParent() instanceof PsiReferenceList;
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

      String dummyIdentifier = customizeDummyIdentifier(context, file);
      if (dummyIdentifier != null) {
        context.setDummyIdentifier(dummyIdentifier);
      }

      PsiLiteralExpression literal =
        PsiTreeUtil.findElementOfClassAtOffset(file, context.getStartOffset(), PsiLiteralExpression.class, false);
      if (literal != null) {
        TextRange range = literal.getTextRange();
        if (range.getStartOffset() == context.getStartOffset()) {
          context.setReplacementOffset(range.getEndOffset());
        }
      }

      PsiJavaCodeReferenceElement ref = getAnnotationNameIfInside(file.findElementAt(context.getStartOffset()));
      if (ref != null) {
        context.setReplacementOffset(ref.getTextRange().getEndOffset());
      }
    }
  }

  @Nullable
  static PsiJavaCodeReferenceElement getAnnotationNameIfInside(@Nullable PsiElement position) {
    PsiAnnotation anno = PsiTreeUtil.getParentOfType(position, PsiAnnotation.class);
    PsiJavaCodeReferenceElement ref = anno == null ? null : anno.getNameReferenceElement();
    return ref != null && PsiTreeUtil.isAncestor(ref, position, false) ? ref : null;
  }

  @Nullable
  private static String customizeDummyIdentifier(@NotNull CompletionInitializationContext context, PsiFile file) {
    if (context.getCompletionType() != CompletionType.BASIC) return null;

    int offset = context.getStartOffset();
    if (PsiTreeUtil.findElementOfClassAtOffset(file, offset - 1, PsiReferenceParameterList.class, false) != null) {
      return CompletionInitializationContext.DUMMY_IDENTIFIER_TRIMMED;
    }

    if (semicolonNeeded(file, offset)) {
      return CompletionInitializationContext.DUMMY_IDENTIFIER_TRIMMED + ";";
    }

    PsiElement leaf = file.findElementAt(offset);
    if (leaf instanceof PsiIdentifier || leaf instanceof PsiKeyword) {
      return CompletionInitializationContext.DUMMY_IDENTIFIER_TRIMMED;
    }

    return null;
  }

  public static boolean semicolonNeeded(PsiFile file, int startOffset) {
    return semicolonNeeded(null, file, startOffset);
  }

  /**
   * @deprecated use {@link #semicolonNeeded(PsiFile, int)}
   */
  @Deprecated
  public static boolean semicolonNeeded(@Nullable Editor editor, PsiFile file, int startOffset) {
    PsiJavaCodeReferenceElement ref = PsiTreeUtil.findElementOfClassAtOffset(file, startOffset, PsiJavaCodeReferenceElement.class, false);
    if (ref != null && !(ref instanceof PsiReferenceExpression)) {
      if (ref.getParent() instanceof PsiTypeElement) {
        return true;
      }
    }
    PsiElement at = file.findElementAt(startOffset);
    if (psiElement(PsiIdentifier.class).withParent(psiParameter()).accepts(at)) {
      return true;
    }

    if (PsiUtilCore.getElementType(at) == JavaTokenType.IDENTIFIER) {
      at = PsiTreeUtil.nextLeaf(at);
    }

    at = skipWhitespacesAndComments(at);

    if (PsiUtilCore.getElementType(at) == JavaTokenType.LPARENTH &&
        PsiTreeUtil.getParentOfType(ref, PsiExpression.class, PsiClass.class) == null) {
      // looks like a method declaration, e.g. StringBui<caret>methodName() inside a class
      return true;
    }

    if (PsiUtilCore.getElementType(at) == JavaTokenType.COLON &&
        PsiTreeUtil.findElementOfClassAtOffset(file, startOffset, PsiConditionalExpression.class, false) == null) {
      return true;
    }

    at = skipWhitespacesAndComments(at);

    if (PsiUtilCore.getElementType(at) != JavaTokenType.IDENTIFIER) {
      return false;
    }

    at = PsiTreeUtil.nextLeaf(at);
    at = skipWhitespacesAndComments(at);

    // <caret> foo = something, we don't want the reference to be treated as a type
    return at != null && at.getNode().getElementType() == JavaTokenType.EQ;
  }

  @Nullable
  private static PsiElement skipWhitespacesAndComments(@Nullable PsiElement at) {
    PsiElement nextLeaf = at;
    while (nextLeaf != null && (nextLeaf instanceof PsiWhiteSpace ||
                                nextLeaf instanceof PsiComment ||
                                nextLeaf instanceof PsiErrorElement ||
                                nextLeaf.getTextLength() == 0)) {
      nextLeaf = PsiTreeUtil.nextLeaf(nextLeaf, true);
    }
    return nextLeaf;
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
      new ImportClassFix(element).fixSilently(editor);
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

  private static void addExpectedTypeMembers(CompletionParameters parameters, final Consumer<? super LookupElement> result) {
    if (parameters.getInvocationCount() <= 1) { // on second completion, StaticMemberProcessor will suggest those
      for (final ExpectedTypeInfo info : JavaSmartCompletionContributor.getExpectedTypes(parameters)) {
        new JavaMembersGetter(info.getDefaultType(), parameters).addMembers(false, result);
      }
    }
  }

  private static void addModuleReferences(PsiElement moduleRef, PsiFile originalFile, CompletionResultSet result) {
    PsiElement statement = moduleRef.getParent();
    boolean requires;
    if ((requires = statement instanceof PsiRequiresStatement) || statement instanceof PsiPackageAccessibilityStatement) {
      PsiElement parent = statement.getParent();
      if (parent != null) {
        Project project = moduleRef.getProject();
        Set<String> filter = new HashSet<>();
        filter.add(((PsiJavaModule)parent).getName());

        JavaModuleNameIndex index = JavaModuleNameIndex.getInstance();
        GlobalSearchScope scope = ProjectScope.getAllScope(project);
        for (String name : index.getAllKeys(project)) {
          if (index.get(name, project, scope).size() > 0 && filter.add(name)) {
            LookupElement lookup = LookupElementBuilder.create(name).withIcon(AllIcons.Nodes.JavaModule);
            if (requires) lookup = TailTypeDecorator.withTail(lookup, TailType.SEMICOLON);
            result.addElement(lookup);
          }
        }

        if (requires) {
          Module module = ModuleUtilCore.findModuleForFile(originalFile);
          if (module != null) {
            scope = GlobalSearchScope.projectScope(project);
            for (String name : JavaSourceModuleNameIndex.getAllKeys(project)) {
              if (JavaSourceModuleNameIndex.getFilesByKey(name, scope).size() > 0) {
                addAutoModuleReference(name, parent, filter, result);
              }
            }
            VirtualFile[] roots = ModuleRootManager.getInstance(module).orderEntries().withoutSdk().librariesOnly().getClassesRoots();
            scope = GlobalSearchScope.filesScope(project, Arrays.asList(roots));
            for (String name : JavaAutoModuleNameIndex.getAllKeys(project)) {
              if (JavaAutoModuleNameIndex.getFilesByKey(name, scope).size() > 0) {
                addAutoModuleReference(name, parent, filter, result);
              }
            }
          }
        }
      }
    }
  }

  private static void addAutoModuleReference(String name, PsiElement parent, Set<String> filter, CompletionResultSet result) {
    if (PsiNameHelper.isValidModuleName(name, parent) && filter.add(name)) {
      LookupElement lookup = LookupElementBuilder.create(name).withIcon(AllIcons.FileTypes.Archive);
      lookup = TailTypeDecorator.withTail(lookup, TailType.SEMICOLON);
      lookup = PrioritizedLookupElement.withPriority(lookup, -1);
      result.addElement(lookup);
    }
  }

  static class IndentingDecorator extends LookupElementDecorator<LookupElement> {
    IndentingDecorator(LookupElement delegate) {
      super(delegate);
    }

    @Override
    public void handleInsert(@NotNull InsertionContext context) {
      super.handleInsert(context);
      Project project = context.getProject();
      Document document = context.getDocument();
      int lineStartOffset = DocumentUtil.getLineStartOffset(context.getStartOffset(), document);
      PsiDocumentManager.getInstance(project).commitDocument(document);
      CodeStyleManager.getInstance(project).adjustLineIndent(context.getFile(), lineStartOffset);
    }
  }

  private static class SearchScopeFilter implements ElementFilter {
    private final GlobalSearchScope myScope;

    SearchScopeFilter(GlobalSearchScope scope) {
      myScope = scope;
    }

    @Override
    public boolean isAcceptable(Object element, @Nullable PsiElement context) {
      if (element instanceof PsiPackage) {
        return ((PsiDirectoryContainer)element).getDirectories(myScope).length > 0;
      }
      else if (element instanceof PsiElement) {
        PsiFile psiFile = ((PsiElement)element).getContainingFile();
        if (psiFile != null) {
          VirtualFile file = psiFile.getVirtualFile();
          return file != null && myScope.contains(file);
        }
      }

      return false;
    }

    @Override
    public boolean isClassAcceptable(Class hintClass) {
      return true;
    }
  }
}