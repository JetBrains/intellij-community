// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion;

import com.intellij.application.options.CodeStyle;
import com.intellij.codeInsight.ExpectedTypeInfo;
import com.intellij.codeInsight.ExpectedTypesProvider;
import com.intellij.codeInsight.TailType;
import com.intellij.codeInsight.TailTypes;
import com.intellij.codeInsight.completion.scope.CompletionElement;
import com.intellij.codeInsight.completion.scope.JavaCompletionProcessor;
import com.intellij.codeInsight.daemon.impl.analysis.GenericsHighlightUtil;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightingFeature;
import com.intellij.codeInsight.daemon.impl.analysis.JavaModuleGraphUtil;
import com.intellij.codeInsight.daemon.impl.analysis.LambdaHighlightingUtil;
import com.intellij.codeInsight.daemon.impl.quickfix.BringVariableIntoScopeFix;
import com.intellij.codeInsight.lookup.*;
import com.intellij.featureStatistics.FeatureUsageTracker;
import com.intellij.icons.AllIcons;
import com.intellij.java.JavaBundle;
import com.intellij.lang.LangBundle;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.lang.jvm.types.JvmPrimitiveTypeKind;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.patterns.ElementPattern;
import com.intellij.patterns.PatternCondition;
import com.intellij.patterns.PsiJavaElementPattern;
import com.intellij.patterns.PsiNameValuePairPattern;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.filters.*;
import com.intellij.psi.filters.classes.AnnotationTypeFilter;
import com.intellij.psi.filters.classes.AssignableFromContextFilter;
import com.intellij.psi.filters.classes.NoFinalLibraryClassesFilter;
import com.intellij.psi.filters.element.ExcludeDeclaredFilter;
import com.intellij.psi.filters.element.ModifierFilter;
import com.intellij.psi.filters.getters.ExpectedTypesGetter;
import com.intellij.psi.filters.getters.JavaMembersGetter;
import com.intellij.psi.filters.types.AssignableFromFilter;
import com.intellij.psi.impl.PsiImplUtil;
import com.intellij.psi.impl.java.stubs.index.JavaAutoModuleNameIndex;
import com.intellij.psi.impl.java.stubs.index.JavaModuleNameIndex;
import com.intellij.psi.impl.java.stubs.index.JavaSourceModuleNameIndex;
import com.intellij.psi.impl.source.PsiJavaCodeReferenceElementImpl;
import com.intellij.psi.impl.source.PsiLabelReference;
import com.intellij.psi.scope.ElementClassFilter;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.ProjectScope;
import com.intellij.psi.util.*;
import com.intellij.util.*;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.ig.psiutils.TypeUtils;
import it.unimi.dsi.fastutil.objects.ObjectOpenCustomHashSet;
import one.util.streamex.EntryStream;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.*;

import java.util.*;

import static com.intellij.patterns.PsiJavaPatterns.elementType;
import static com.intellij.patterns.PsiJavaPatterns.or;
import static com.intellij.patterns.PsiJavaPatterns.psiAnnotation;
import static com.intellij.patterns.PsiJavaPatterns.psiClass;
import static com.intellij.patterns.PsiJavaPatterns.psiElement;
import static com.intellij.patterns.PsiJavaPatterns.psiExpressionStatement;
import static com.intellij.patterns.PsiJavaPatterns.psiMethod;
import static com.intellij.patterns.PsiJavaPatterns.psiNameValuePair;
import static com.intellij.patterns.PsiJavaPatterns.psiParameter;
import static com.intellij.patterns.PsiJavaPatterns.psiReferenceExpression;
import static com.intellij.patterns.PsiJavaPatterns.string;

public final class JavaCompletionContributor extends CompletionContributor implements DumbAware {
  private static final ElementPattern<PsiElement> UNEXPECTED_REFERENCE_AFTER_DOT = or(
      // dot at the statement beginning
      psiElement().afterLeaf(".").insideStarting(psiExpressionStatement()),
      // like `call(Cls::methodRef.<caret>`
      psiElement().afterLeaf(psiElement(JavaTokenType.DOT).afterSibling(psiElement(PsiMethodCallExpression.class).withLastChild(
        psiElement(PsiExpressionList.class).withLastChild(psiElement(PsiErrorElement.class))))));
  private static final PsiNameValuePairPattern NAME_VALUE_PAIR =
    psiNameValuePair().withSuperParent(2, psiElement(PsiAnnotation.class));
  private static final ElementPattern<PsiElement> ANNOTATION_ATTRIBUTE_NAME =
    or(psiElement(PsiIdentifier.class).withParent(NAME_VALUE_PAIR),
       psiElement().afterLeaf("(").withParent(psiReferenceExpression().withParent(NAME_VALUE_PAIR)));
  private static final PsiJavaElementPattern.Capture<PsiElement> IN_TYPE_PARAMETER =
    psiElement().afterLeaf(PsiKeyword.EXTENDS, PsiKeyword.SUPER, "&").withParent(
      psiElement(PsiReferenceList.class).withParent(PsiTypeParameter.class));

  public static final ElementPattern<PsiElement> IN_SWITCH_LABEL =
    psiElement().withSuperParent(2, psiElement(PsiCaseLabelElementList.class).withParent(psiElement(PsiSwitchLabelStatementBase.class).withSuperParent(2, PsiSwitchBlock.class)));
  private static final ElementPattern<PsiElement> IN_ENUM_SWITCH_LABEL =
    psiElement().withSuperParent(2, psiElement(PsiCaseLabelElementList.class).withParent(psiElement(PsiSwitchLabelStatementBase.class).withSuperParent(2,
      psiElement(PsiSwitchBlock.class).with(new PatternCondition<>("enumExpressionType") {
        @Override
        public boolean accepts(@NotNull PsiSwitchBlock psiSwitchBlock, ProcessingContext context) {
          PsiExpression expression = psiSwitchBlock.getExpression();
          if (expression == null) return false;
          PsiClass aClass = PsiUtil.resolveClassInClassTypeOnly(expression.getType());
          return aClass != null && aClass.isEnum();
        }
      }))));
  private static final PsiJavaElementPattern.Capture<PsiElement> IN_CASE_LABEL_ELEMENT_LIST =
    psiElement().withSuperParent(2, psiElement(PsiCaseLabelElementList.class));

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
  static final ElementPattern<PsiElement> IN_EXTENDS_OR_IMPLEMENTS = psiElement().afterLeaf(
    psiElement()
      .withText(string().oneOf(PsiKeyword.EXTENDS, PsiKeyword.IMPLEMENTS, ",", "&"))
      .withParent(PsiReferenceList.class));
  static final ElementPattern<PsiElement> IN_PERMITS_LIST = psiElement().afterLeaf(
    psiElement()
      .withText(string().oneOf(PsiKeyword.PERMITS, ","))
      .withParent(psiElement(PsiReferenceList.class).withFirstChild(psiElement(PsiKeyword.class).withText(PsiKeyword.PERMITS))));
  private static final ElementPattern<PsiElement> IN_VARIABLE_TYPE = psiElement()
    .withParents(PsiJavaCodeReferenceElement.class, PsiTypeElement.class, PsiDeclarationStatement.class)
    .afterLeaf(psiElement().inside(psiAnnotation()));

  /**
   * @param position completion invocation position
   * @return filter for acceptable references; if null then no references are accepted at a given position
   */
  @Nullable
  public static ElementFilter getReferenceFilter(PsiElement position) {
    PsiClass containingClass = PsiTreeUtil.getParentOfType(position, PsiClass.class, false,
                                                           PsiCodeBlock.class, PsiMethod.class,
                                                           PsiExpressionList.class, PsiVariable.class, PsiAnnotation.class);
    if (containingClass != null) {
      if (IN_PERMITS_LIST.accepts(position)) {
        return createPermitsListFilter();
      }

      if (IN_EXTENDS_OR_IMPLEMENTS.accepts(position)) {
        AndFilter filter = new AndFilter(ElementClassFilter.CLASS, new NotFilter(new AssignableFromContextFilter()),
                                         new ExcludeDeclaredFilter(new ClassFilter(PsiClass.class)));
        PsiElement cls = position.getParent().getParent().getParent();
        if (cls instanceof PsiClass && !(cls instanceof PsiTypeParameter)) {
          filter = new AndFilter(filter, new NoFinalLibraryClassesFilter());
        }
        return filter;
      }
      if (IN_TYPE_PARAMETER.accepts(position)) {
        return new ExcludeDeclaredFilter(new ClassFilter(PsiTypeParameter.class));
      }
    }

    if (getAnnotationNameIfInside(position) != null) {
      return new OrFilter(ElementClassFilter.PACKAGE, new AnnotationTypeFilter());
    }

    if (JavaKeywordCompletion.isDeclarationStart(position) ||
        JavaKeywordCompletion.isInsideParameterList(position) ||
        isInsideAnnotationName(position) ||
        PsiTreeUtil.getParentOfType(position, PsiReferenceParameterList.class, false, PsiAnnotation.class) != null ||
        IN_VARIABLE_TYPE.accepts(position)) {
      return new OrFilter(ElementClassFilter.CLASS, ElementClassFilter.PACKAGE);
    }

    if (psiElement().afterLeaf(PsiKeyword.INSTANCEOF).accepts(position)) {
      return new ElementFilter() {
        @Override
        public boolean isAcceptable(Object element, @Nullable PsiElement context) {
          return element instanceof PsiClass && !(element instanceof PsiTypeParameter);
        }

        @Override
        public boolean isClassAcceptable(Class hintClass) {
          return PsiClass.class.isAssignableFrom(hintClass) && !PsiTypeParameter.class.isAssignableFrom(hintClass);
        }
      };
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
      return createAnnotationFilter();
    }

    PsiVariable var = PsiTreeUtil.getParentOfType(position, PsiVariable.class, false, PsiClass.class);
    if (var != null && PsiTreeUtil.isAncestor(var.getInitializer(), position, false)) {
      return new ExcludeFilter(var);
    }

    if (IN_CASE_LABEL_ELEMENT_LIST.accepts(position)) {
      return getCaseLabelElementListFilter(position);
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

    if (position.getParent() instanceof PsiReferenceExpression) {
      PsiClass enumClass = GenericsHighlightUtil.getEnumClassForExpressionInInitializer((PsiReferenceExpression)position.getParent());
      if (enumClass != null) {
        return new EnumStaticFieldsFilter(enumClass);
      }
    }

    return TrueFilter.INSTANCE;
  }

  @Contract(pure = true)
  private static @NotNull ElementFilter getCaseLabelElementListFilter(@NotNull PsiElement position) {
    if (IN_ENUM_SWITCH_LABEL.accepts(position)) {
      return new ClassFilter(PsiField.class) {
        @Override
        public boolean isAcceptable(Object element, PsiElement context) {
          return element instanceof PsiEnumConstant;
        }
      };
    }

    final PsiSwitchBlock switchBlock = PsiTreeUtil.getParentOfType(position, PsiSwitchBlock.class);
    if (switchBlock == null) return TrueFilter.INSTANCE;

    final PsiExpression selector = switchBlock.getExpression();
    if (selector == null || selector.getType() == null) return TrueFilter.INSTANCE;

    final PsiType selectorType = selector.getType();
    final ClassFilter constantVariablesFilter = new ClassFilter(PsiVariable.class) {
      @Override
      public boolean isAcceptable(Object element, PsiElement context) {
        final PsiVariable variable;
        if (element instanceof PsiField) {
          variable = (PsiField)element;
          if (variable.hasModifierProperty(PsiModifier.FINAL) && variable.hasModifierProperty(PsiModifier.STATIC)) {
            return true;
          }
        }
        else if (element instanceof PsiLocalVariable) {
          variable = (PsiLocalVariable)element;
          if (variable.hasModifierProperty(PsiModifier.FINAL)) {
            return true;
          }
        }
        else {
          return false;
        }
        return TypeConversionUtil.isAssignable(selectorType, variable.getType());
      }
    };

    if (isPrimitive(selectorType)) return constantVariablesFilter;

    if (!HighlightingFeature.PATTERNS_IN_SWITCH.isAvailable(position)) {
      return TypeUtils.isJavaLangString(selectorType)
             ? constantVariablesFilter
             : TrueFilter.INSTANCE;
    }

    if (TypeUtils.isJavaLangObject(selectorType)) {
      return new OrFilter(new ClassFilter(PsiClass.class), constantVariablesFilter);
    }

    final PsiClass typeClass = PsiUtil.resolveClassInType(selectorType);

    final ClassFilter inheritorsFilter = new ClassFilter(PsiClass.class) {
      @Override
      public boolean isAcceptable(Object element, PsiElement context) {
        return element instanceof PsiClass && InheritanceUtil.isInheritorOrSelf((PsiClass)element, typeClass, true);
      }
    };

    if (TypeUtils.isJavaLangString(selectorType)) {
      return new OrFilter(constantVariablesFilter, inheritorsFilter);
    }

    return selectorType instanceof PsiClassType
           ? inheritorsFilter
           : TrueFilter.INSTANCE;
  }

  @Contract(pure = true)
  private static boolean isPrimitive(@NotNull PsiType type) {
    if (type instanceof PsiPrimitiveType) return true;
    if (!(type instanceof PsiClassType)) return false;

    final PsiClass aClass = ((PsiClassType)type).resolve();
    final Collection<String> boxedPrimitiveTypes = JvmPrimitiveTypeKind.getBoxedFqns();
    return aClass != null && boxedPrimitiveTypes.contains(aClass.getQualifiedName());
  }

  @NotNull
  static AndFilter createPermitsListFilter() {
    return new AndFilter(ElementClassFilter.CLASS, new NotFilter(new AssignableFromContextFilter() {
      @Override
      protected boolean checkInheritance(@NotNull PsiClass curClass, @NotNull PsiClass candidate) {
        String qualifiedName = curClass.getQualifiedName();
        return qualifiedName != null && (qualifiedName.equals(candidate.getQualifiedName()) || curClass.isInheritor(candidate, true));
      }
    }));
  }

  private static boolean isInsideAnnotationName(PsiElement position) {
    PsiAnnotation anno = PsiTreeUtil.getParentOfType(position, PsiAnnotation.class, true, PsiMember.class);
    return anno != null && PsiTreeUtil.isAncestor(anno.getNameReferenceElement(), position, true);
  }

  private static ElementFilter createAnnotationFilter() {
    return new OrFilter(
      ElementClassFilter.CLASS,
      ElementClassFilter.PACKAGE,
      new AndFilter(new ClassFilter(PsiField.class), new ModifierFilter(PsiModifier.STATIC, PsiModifier.FINAL)));
  }

  public static ElementFilter applyScopeFilter(ElementFilter filter, PsiElement position) {
    Module module = ModuleUtilCore.findModuleForPsiElement(position);
    return module != null ? new AndFilter(filter, new SearchScopeFilter(module.getModuleScope())) : filter;
  }

  @Override
  public void fillCompletionVariants(@NotNull final CompletionParameters parameters, @NotNull final CompletionResultSet _result) {
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

    boolean smart = parameters.getCompletionType() == CompletionType.SMART;

    final CompletionResultSet result = JavaCompletionSorting.addJavaSorting(parameters, _result);
    JavaCompletionSession session = new JavaCompletionSession(result);

    PrefixMatcher matcher = result.getPrefixMatcher();
    PsiElement parent = position.getParent();

    if (!smart && new JavaKeywordCompletion(parameters, session).addWildcardExtendsSuper(result, position)) {
      result.stopHere();
      return;
    }

    boolean mayCompleteReference = true;

    if (position instanceof PsiIdentifier) {
      addIdentifierVariants(parameters, position, result, session, matcher);

      Set<ExpectedTypeInfo> expectedInfos = ContainerUtil.newHashSet(JavaSmartCompletionContributor.getExpectedTypes(parameters));
      boolean shouldAddExpressionVariants = shouldAddExpressionVariants(parameters);

      boolean hasTypeMatchingSuggestions =
        shouldAddExpressionVariants && addExpectedTypeMembers(parameters, false, expectedInfos,
                                                              item -> session.registerBatchItems(Collections.singleton(item)));

      if (!smart) {
        PsiAnnotation anno = findAnnotationWhoseAttributeIsCompleted(position);
        if (anno != null) {
          PsiClass annoClass = anno.resolveAnnotationType();
          mayCompleteReference = mayCompleteValueExpression(position, annoClass);
          if (annoClass != null) {
            completeAnnotationAttributeName(result, position, anno, annoClass);
            JavaKeywordCompletion.addPrimitiveTypes(result, position, session);
          }
        }
      }

      PsiReference ref = position.getContainingFile().findReferenceAt(parameters.getOffset());
      if (ref instanceof PsiLabelReference) {
        session.registerBatchItems(processLabelReference((PsiLabelReference)ref));
        result.stopHere();
      }

      List<LookupElement> refSuggestions = Collections.emptyList();
      if (parent instanceof PsiJavaCodeReferenceElement && mayCompleteReference) {
        PsiJavaCodeReferenceElement parentRef = (PsiJavaCodeReferenceElement)parent;
        if (IN_PERMITS_LIST.accepts(parent) && parameters.getInvocationCount() <= 1 && !parentRef.isQualified()) {
          refSuggestions = completePermitsListReference(parameters, parentRef, matcher);
        }
        else {
          refSuggestions = completeReference(parameters, parentRef, session, expectedInfos, matcher::prefixMatches);
        }
        List<LookupElement> filtered = filterReferenceSuggestions(parameters, expectedInfos, refSuggestions);
        hasTypeMatchingSuggestions |= ContainerUtil.exists(filtered, item ->
          ReferenceExpressionCompletionContributor.matchesExpectedType(item, expectedInfos));
        session.registerBatchItems(filtered);
        result.stopHere();
      }

      session.flushBatchItems();

      if (smart) {
        hasTypeMatchingSuggestions |= smartCompleteExpression(parameters, result, expectedInfos);
        smartCompleteNonExpression(parameters, result);
      }

      if ((!hasTypeMatchingSuggestions || parameters.getInvocationCount() >= 2) &&
          parent instanceof PsiJavaCodeReferenceElement &&
          !expectedInfos.isEmpty() &&
          JavaSmartCompletionContributor.INSIDE_EXPRESSION.accepts(position)) {
        List<LookupElement> base = ContainerUtil.concat(
          refSuggestions,
          completeReference(parameters, (PsiJavaCodeReferenceElement)parent, session, expectedInfos, s -> !matcher.prefixMatches(s)));
        SlowerTypeConversions.addChainedSuggestions(parameters, result, expectedInfos, base);
      }

      if (smart && parameters.getInvocationCount() > 1 && shouldAddExpressionVariants) {
        addExpectedTypeMembers(parameters, true, expectedInfos, result);
      }
    }

    if (!smart && psiElement().inside(PsiLiteralExpression.class).accepts(position)) {
      Set<String> usedWords = new HashSet<>();
      result.runRemainingContributors(parameters, cr -> {
        usedWords.add(cr.getLookupElement().getLookupString());
        result.passResult(cr);
      });

      PsiReference reference = position.getContainingFile().findReferenceAt(parameters.getOffset());
      if (reference == null || reference.isSoft()) {
        WordCompletionContributor.addWordCompletionVariants(result, parameters, usedWords);
      }
    }

    if (!smart && position instanceof PsiIdentifier) {
      JavaGenerateMemberCompletionContributor.fillCompletionVariants(parameters, result);
    }

    if (!smart && mayCompleteReference) {
      addAllClasses(parameters, result, session);
    }

    if (position instanceof PsiIdentifier) {
      FunctionalExpressionCompletionProvider.addFunctionalVariants(parameters, true, result.getPrefixMatcher(), result);
    }

    if (position instanceof PsiIdentifier &&
        !smart &&
        parent instanceof PsiReferenceExpression &&
        !((PsiReferenceExpression)parent).isQualified() &&
        parameters.isExtendedCompletion() &&
        StringUtil.isNotEmpty(matcher.getPrefix())) {
      new JavaStaticMemberProcessor(parameters).processStaticMethodsGlobally(matcher, result);
    }

    if (!smart && parent instanceof PsiJavaModuleReferenceElement) {
      addModuleReferences(parent, parameters.getOriginalFile(), result);
    }
  }

  @VisibleForTesting
  public static boolean mayCompleteValueExpression(@NotNull PsiElement position, @Nullable PsiClass annoClass) {
    return psiElement().afterLeaf("(").accepts(position) && annoClass != null && annoClass.findMethodsByName("value", false).length > 0;
  }

  private static List<LookupElement> filterReferenceSuggestions(CompletionParameters parameters,
                                                                Set<ExpectedTypeInfo> expectedInfos,
                                                                List<LookupElement> refSuggestions) {
    if (parameters.getCompletionType() == CompletionType.SMART) {
      return ReferenceExpressionCompletionContributor.smartCompleteReference(refSuggestions, expectedInfos);
    }
    return refSuggestions;
  }

  private static void smartCompleteNonExpression(CompletionParameters parameters, CompletionResultSet result) {
    PsiElement position = parameters.getPosition();
    PsiElement parent = position.getParent();
    if (!SmartCastProvider.inCastContext(parameters) && parent instanceof PsiJavaCodeReferenceElement) {
      JavaSmartCompletionContributor.addClassReferenceSuggestions(parameters, result, position, (PsiJavaCodeReferenceElement)parent);
    }
    if (InstanceofTypeProvider.AFTER_INSTANCEOF.accepts(position)) {
      InstanceofTypeProvider.addCompletions(parameters, result);
    }
    if (ExpectedAnnotationsProvider.ANNOTATION_ATTRIBUTE_VALUE.accepts(position)) {
      ExpectedAnnotationsProvider.addCompletions(position, result);
    }
    if (CatchTypeProvider.CATCH_CLAUSE_TYPE.accepts(position)) {
      CatchTypeProvider.addCompletions(parameters, result);
    }
  }

  private static boolean smartCompleteExpression(CompletionParameters parameters,
                                                 CompletionResultSet result,
                                                 Set<? extends ExpectedTypeInfo> infos) {
    PsiElement position = parameters.getPosition();
    if (SmartCastProvider.inCastContext(parameters) ||
        !JavaSmartCompletionContributor.INSIDE_EXPRESSION.accepts(position) ||
        !(position.getParent() instanceof PsiJavaCodeReferenceElement)) {
      return false;
    }

    boolean[] hadItems = new boolean[1];
    for (ExpectedTypeInfo info : new ObjectOpenCustomHashSet<>(infos, JavaSmartCompletionContributor.EXPECTED_TYPE_INFO_STRATEGY)) {
      BasicExpressionCompletionContributor.fillCompletionVariants(new JavaSmartCompletionParameters(parameters, info), lookupElement -> {
        final PsiType psiType = JavaCompletionUtil.getLookupElementType(lookupElement);
        if (psiType != null && info.getType().isAssignableFrom(psiType)) {
          hadItems[0] = true;
          result.addElement(JavaSmartCompletionContributor.decorate(lookupElement, infos));
        }
      }, result.getPrefixMatcher());
    }
    return hadItems[0];
  }

  @Nullable
  private static PsiAnnotation findAnnotationWhoseAttributeIsCompleted(@NotNull PsiElement position) {
    return ANNOTATION_ATTRIBUTE_NAME.accepts(position) && !JavaKeywordCompletion.isAfterPrimitiveOrArrayType(position)
           ? Objects.requireNonNull(PsiTreeUtil.getParentOfType(position, PsiAnnotation.class))
           : null;
  }

  private static void addIdentifierVariants(@NotNull CompletionParameters parameters,
                                            PsiElement position,
                                            CompletionResultSet result,
                                            JavaCompletionSession session, PrefixMatcher matcher) {
    session.registerBatchItems(getFastIdentifierVariants(parameters, position, matcher, position.getParent(), session));

    if (JavaSmartCompletionContributor.AFTER_NEW.accepts(position)) {
      session.flushBatchItems();

      boolean smart = parameters.getCompletionType() == CompletionType.SMART;
      ConstructorInsertHandler handler = smart
                                         ? ConstructorInsertHandler.SMART_INSTANCE
                                         : ConstructorInsertHandler.BASIC_INSTANCE;
      ExpectedTypeInfo[] types = JavaSmartCompletionContributor.getExpectedTypes(parameters);
      new JavaInheritorsGetter(handler).generateVariants(parameters, matcher, types, lookupElement -> {
        if ((smart || !isSuggestedByKeywordCompletion(lookupElement)) && result.getPrefixMatcher().prefixMatches(lookupElement)) {
          session.registerClassFrom(lookupElement);
          result.addElement(smart
                            ? JavaSmartCompletionContributor.decorate(lookupElement, Arrays.asList(types))
                            : AutoCompletionPolicy.NEVER_AUTOCOMPLETE.applyPolicy(lookupElement));
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
    boolean smart = parameters.getCompletionType() == CompletionType.SMART;

    List<LookupElement> items = new ArrayList<>();
    if (TypeArgumentCompletionProvider.IN_TYPE_ARGS.accepts(position)) {
      new TypeArgumentCompletionProvider(smart, session).addTypeArgumentVariants(parameters, items::add, matcher);
    }

    FunctionalExpressionCompletionProvider.addFunctionalVariants(parameters, false, matcher, items::add);

    if (MethodReturnTypeProvider.IN_METHOD_RETURN_TYPE.accepts(position)) {
      MethodReturnTypeProvider.addProbableReturnTypes(position, element -> {
        registerClassFromTypeElement(element, session);
        items.add(element);
      });
    }

    suggestSmartCast(parameters, session, true, items::add);

    if (parent instanceof PsiReferenceExpression && !(parent instanceof PsiMethodReferenceExpression)) {
      final List<ExpectedTypeInfo> expected = Arrays.asList(ExpectedTypesProvider.getExpectedTypes((PsiExpression)parent, true));
      StreamConversion.addCollectConversion((PsiReferenceExpression)parent, expected,
                                             lookupElement -> items.add(JavaSmartCompletionContributor.decorate(lookupElement, expected)));
      if (!smart) {
        items.addAll(StreamConversion.addToStreamConversion((PsiReferenceExpression)parent, parameters));
      }
      items.addAll(ArgumentSuggester.suggestArgument((PsiReferenceExpression)parent, smart ? expected : Collections.emptyList()));
    }

    if (IMPORT_REFERENCE.accepts(position)) {
      items.add(LookupElementBuilder.create("*"));
    }

    if (!smart && findAnnotationWhoseAttributeIsCompleted(position) == null) {
      items.addAll(new JavaKeywordCompletion(parameters, session).getResults());
    }

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

  private static boolean shouldAddExpressionVariants(CompletionParameters parameters) {
    return JavaSmartCompletionContributor.INSIDE_EXPRESSION.accepts(parameters.getPosition()) &&
           !JavaKeywordCompletion.AFTER_DOT.accepts(parameters.getPosition()) &&
           !SmartCastProvider.inCastContext(parameters);
  }

  private static void addExpressionVariants(@NotNull CompletionParameters parameters, PsiElement position, Consumer<? super LookupElement> result) {
    if (shouldAddExpressionVariants(parameters)) {
      if (SameSignatureCallParametersProvider.IN_CALL_ARGUMENT.accepts(position)) {
        new SameSignatureCallParametersProvider().addSignatureItems(position, result);
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
      JavaNoVariantsDelegator.suggestNonImportedClasses(parameters, result, session);
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

  private static List<LookupElement> completeReference(CompletionParameters parameters,
                                                       PsiJavaCodeReferenceElement ref,
                                                       JavaCompletionSession session,
                                                       Set<? extends ExpectedTypeInfo> expectedTypes,
                                                       Condition<? super String> nameCondition) {
    PsiElement position = parameters.getPosition();
    ElementFilter filter = getReferenceFilter(position);
    if (filter == null) return Collections.emptyList();
    if (parameters.getInvocationCount() <= 1 && JavaClassNameCompletionContributor.AFTER_NEW.accepts(position)) {
      filter = new AndFilter(filter, new ElementFilter() {
        @Override
        public boolean isAcceptable(Object element, @Nullable PsiElement context) {
          return !JavaPsiClassReferenceElement.isInaccessibleConstructorSuggestion(position, ObjectUtils.tryCast(element, PsiClass.class));
        }

        @Override
        public boolean isClassAcceptable(Class hintClass) {
          return true;
        }
      });
    }

    boolean smart = parameters.getCompletionType() == CompletionType.SMART;
    if (smart) {
      if (JavaSmartCompletionContributor.INSIDE_TYPECAST_EXPRESSION.accepts(position) || SmartCastProvider.inCastContext(parameters)) {
        return Collections.emptyList();
      }

      ElementFilter smartRestriction = ReferenceExpressionCompletionContributor.getReferenceFilter(position, false);
      if (smartRestriction != TrueFilter.INSTANCE) {
        filter = new AndFilter(filter, smartRestriction);
      }
    }

    boolean inSwitchLabel = IN_SWITCH_LABEL.accepts(position);
    TailType forcedTail = null;
    if (!smart) {
      if (inSwitchLabel) {
        forcedTail = TailTypes.forSwitchLabel(Objects.requireNonNull(PsiTreeUtil.getParentOfType(position, PsiSwitchBlock.class)));
      }
      else if (shouldInsertSemicolon(position)) {
        forcedTail = TailType.SEMICOLON;
      }
    }

    List<LookupElement> items = new ArrayList<>();
    if (INSIDE_CONSTRUCTOR.accepts(position) &&
        (parameters.getInvocationCount() <= 1 || CheckInitialized.isInsideConstructorCall(position))) {
      filter = new AndFilter(filter, new CheckInitialized(position));
    }
    PsiFile originalFile = parameters.getOriginalFile();

    boolean first = parameters.getInvocationCount() <= 1;
    JavaCompletionProcessor.Options options =
      JavaCompletionProcessor.Options.DEFAULT_OPTIONS
        .withCheckAccess(first)
        .withFilterStaticAfterInstance(first)
        .withShowInstanceInStaticContext(!first && !smart);

    for (LookupElement element : JavaCompletionUtil.processJavaReference(position,
                                                                         ref,
                                                                         new ElementExtractorFilter(filter),
                                                                         options,
                                                                         nameCondition, parameters)) {
      if (session.alreadyProcessed(element)) {
        continue;
      }

      LookupItem<?> item = element.as(LookupItem.CLASS_CONDITION_KEY);

      if (forcedTail != null && !(element instanceof JavaPsiClassReferenceElement)) {
        element = TailTypeDecorator.withTail(element, forcedTail);
      }

      if (inSwitchLabel && !smart) {
        element = new IndentingDecorator(element);
      }
      if (originalFile instanceof PsiJavaCodeReferenceCodeFragment &&
          !((PsiJavaCodeReferenceCodeFragment)originalFile).isClassesAccepted() && item != null) {
        item.setTailType(TailType.NONE);
      }
      if (item instanceof JavaMethodCallElement) {
        JavaMethodCallElement call = (JavaMethodCallElement)item;
        final PsiMethod method = call.getObject();
        if (method.getTypeParameters().length > 0) {
          PsiType returned = TypeConversionUtil.erasure(method.getReturnType());
          ExpectedTypeInfo matchingExpectation = returned == null ? null : ContainerUtil.find(expectedTypes, info ->
            info.getDefaultType().isAssignableFrom(returned) ||
            AssignableFromFilter.isAcceptable(method, position, info.getDefaultType(), call.getSubstitutor()));
          if (matchingExpectation != null) {
            call.setInferenceSubstitutorFromExpectedType(position, matchingExpectation.getDefaultType());
          }
        }
      }
      items.add(element);

      ContainerUtil.addIfNotNull(items, ArrayMemberAccess.accessFirstElement(position, element));
    }
    if (parameters.getInvocationCount() > 0) {
      items.addAll(getInnerScopeVariables(parameters, position));
    }
    return items;
  }

  private static Collection<LookupElement> getInnerScopeVariables(CompletionParameters parameters, PsiElement position) {
    PsiElement container = BringVariableIntoScopeFix.getContainer(position);
    if (container == null) return Collections.emptyList();
    Map<String, Optional<PsiLocalVariable>> variableMap =
      EntryStream.ofTree(container, (depth, element) -> depth > 2 ? null : StreamEx.of(element.getChildren()))
      .values()
      .select(PsiCodeBlock.class)
      .flatArray(PsiCodeBlock::getStatements)
      .select(PsiDeclarationStatement.class)
      .flatArray(PsiDeclarationStatement::getDeclaredElements)
      .select(PsiLocalVariable.class)
      .remove(var -> PsiTreeUtil.isAncestor(var, position, true))
      .toMap(PsiLocalVariable::getName, Optional::of, (v1, v2) -> Optional.empty());
    PsiResolveHelper helper = JavaPsiFacade.getInstance(parameters.getOriginalFile().getProject()).getResolveHelper();
    variableMap.values().removeAll(Collections.singleton(Optional.<PsiLocalVariable>empty()));
    variableMap.keySet().removeIf(name -> helper.resolveReferencedVariable(name, position) != null);
    int offset = position.getTextRange().getStartOffset();
    variableMap.values().removeIf(v -> v.orElseThrow().getTextRange().getStartOffset() > offset);
    if (variableMap.isEmpty()) return Collections.emptyList();
    return ContainerUtil.map(variableMap.values(), optVar -> {
      assert optVar.isPresent();
      PsiLocalVariable variable = optVar.get();
      String place = getPlace(variable);
      return new VariableLookupItem(variable, JavaBundle.message("completion.inner.scope.tail.text", place)).setPriority(-1);
    });
  }

  @Nls
  @NotNull
  private static String getPlace(PsiLocalVariable variable) {
    String place = JavaBundle.message("completion.inner.scope");
    PsiCodeBlock block = PsiTreeUtil.getParentOfType(variable, PsiCodeBlock.class);
    PsiElement statement = block == null ? null : block.getParent();
    if (statement instanceof PsiTryStatement) {
      place = ((PsiTryStatement)statement).getFinallyBlock() == block ? PsiKeyword.TRY + "-" + PsiKeyword.FINALLY : PsiKeyword.TRY;
    }
    else if (statement instanceof PsiCatchSection) {
      place = PsiKeyword.CATCH;
    }
    else if (statement instanceof PsiSynchronizedStatement) {
      place = PsiKeyword.SYNCHRONIZED;
    }
    else if (statement instanceof PsiBlockStatement) {
      PsiElement parent = statement.getParent();
      if (parent instanceof PsiWhileStatement) {
        place = PsiKeyword.WHILE;
      }
      else if (parent instanceof PsiIfStatement) {
        place = ((PsiIfStatement)parent).getThenBranch() == statement ? PsiKeyword.IF + "-then" : PsiKeyword.IF + "-" + PsiKeyword.ELSE;
      }
    }
    return place;
  }

  private static @NotNull List<LookupElement> completePermitsListReference(@NotNull CompletionParameters parameters,
                                                                           @NotNull PsiJavaCodeReferenceElement referenceElement,
                                                                           @NotNull PrefixMatcher prefixMatcher) {
    List<LookupElement> lookupElements = new SmartList<>();
    PsiJavaFile psiJavaFile = ObjectUtils.tryCast(referenceElement.getContainingFile(), PsiJavaFile.class);
    if (psiJavaFile == null) return lookupElements;
    PsiJavaModule javaModule = JavaModuleGraphUtil.findDescriptorByElement(psiJavaFile.getOriginalElement());
    if (javaModule == null) {
      String packageName = psiJavaFile.getPackageName();
      PsiPackage psiPackage = JavaPsiFacade.getInstance(psiJavaFile.getProject()).findPackage(packageName);
      if (psiPackage == null) return lookupElements;
      for (PsiClass psiClass : psiPackage.getClasses(referenceElement.getResolveScope())) {
        CompletionElement completionElement = new CompletionElement(psiClass, PsiSubstitutor.EMPTY);
        JavaCompletionUtil.createLookupElements(completionElement, referenceElement).forEach(lookupElements::add);
      }
    }
    else {
      JavaClassNameCompletionContributor.addAllClasses(parameters, true, prefixMatcher, lookupElements::add);
    }
    return lookupElements;
  }

  static boolean shouldInsertSemicolon(PsiElement position) {
    return position.getParent() instanceof PsiMethodReferenceExpression &&
           LambdaHighlightingUtil.insertSemicolon(position.getParent().getParent());
  }

  private static List<LookupElement> processLabelReference(PsiLabelReference reference) {
    return ContainerUtil.map(reference.getVariants(), s -> TailTypeDecorator.withTail(LookupElementBuilder.create(s), TailType.SEMICOLON));
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

    return !JavaKeywordCompletion.isAfterPrimitiveOrArrayType(position);
  }

  public static boolean mayStartClassName(CompletionResultSet result) {
    return InternalCompletionSettings.getInstance().mayStartClassNameCompletion(result);
  }

  private static void completeAnnotationAttributeName(CompletionResultSet result,
                                                      PsiElement position,
                                                      PsiAnnotation anno,
                                                      PsiClass annoClass) {
    PsiNameValuePair[] existingPairs = anno.getParameterList().getAttributes();

    methods: for (PsiMethod method : annoClass.getMethods()) {
      if (!(method instanceof PsiAnnotationMethod)) continue;

      final String attrName = method.getName();
      for (PsiNameValuePair existingAttr : existingPairs) {
        if (PsiTreeUtil.isAncestor(existingAttr, position, false)) break;
        if (Objects.equals(existingAttr.getName(), attrName) ||
            PsiAnnotation.DEFAULT_REFERENCED_METHOD_NAME.equals(attrName) && existingAttr.getName() == null) continue methods;
      }

      PsiAnnotationMemberValue defaultValue = ((PsiAnnotationMethod)method).getDefaultValue();
      String defText = defaultValue == null ? null : defaultValue.getText();
      if (PsiKeyword.TRUE.equals(defText) || PsiKeyword.FALSE.equals(defText)) {
        result.addElement(createAnnotationAttributeElement(method, PsiKeyword.TRUE.equals(defText) ? PsiKeyword.FALSE : PsiKeyword.TRUE));
        result.addElement(PrioritizedLookupElement.withPriority(createAnnotationAttributeElement(method, defText).withTailText(" (default)", true), -1));
      } else {
        LookupElementBuilder element = createAnnotationAttributeElement(method, null);
        if (defText != null) {
          element = element.withTailText(" default " + defText, true);
        }
        result.addElement(element);
      }
    }
  }

  @NotNull
  private static LookupElementBuilder createAnnotationAttributeElement(PsiMethod annoMethod, @Nullable String value) {
    String space = ReferenceExpressionCompletionContributor.getSpace(CodeStyle.getLanguageSettings(annoMethod.getContainingFile()).SPACE_AROUND_ASSIGNMENT_OPERATORS);
    String lookupString = annoMethod.getName() + (value == null ? "" : space + "=" + space + value);
    return LookupElementBuilder.create(annoMethod, lookupString).withIcon(annoMethod.getIcon(0))
      .withStrikeoutness(PsiImplUtil.isDeprecated(annoMethod))
      .withInsertHandler(new InsertHandler<>() {
        @Override
        public void handleInsert(@NotNull InsertionContext context, @NotNull LookupElement item) {
          final Editor editor = context.getEditor();
          if (value == null) {
            EqTailType.INSTANCE.processTail(editor, editor.getCaretModel().getOffset());
          }
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
  public @NlsContexts.HintText String handleEmptyLookup(@NotNull final CompletionParameters parameters, final Editor editor) {
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

    if (context.getCompletionType() == CompletionType.SMART) {
      JavaSmartCompletionContributor.beforeSmartCompletion(context);
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

  private static boolean addExpectedTypeMembers(CompletionParameters parameters,
                                                boolean searchInheritors,
                                                Collection<? extends ExpectedTypeInfo> types,
                                                Consumer<? super LookupElement> result) {
    boolean[] added = new boolean[1];
    boolean smart = parameters.getCompletionType() == CompletionType.SMART;
    if (smart || parameters.getInvocationCount() <= 1) { // on second basic completion, StaticMemberProcessor will suggest those
      Consumer<LookupElement> consumer = e -> {
        added[0] = true;
        result.consume(smart ? JavaSmartCompletionContributor.decorate(e, types) : e);
      };
      for (ExpectedTypeInfo info : types) {
        new JavaMembersGetter(info.getType(), parameters).addMembers(searchInheritors, consumer);
        if (!info.getType().equals(info.getDefaultType())) {
          new JavaMembersGetter(info.getDefaultType(), parameters).addMembers(searchInheritors, consumer);
        }
      }
    }
    return added[0];
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

  private static void addAutoModuleReference(String name, PsiElement parent, Set<? super String> filter, CompletionResultSet result) {
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

  private static class EnumStaticFieldsFilter implements ElementFilter {
    private final PsiClass myEnumClass;

    private EnumStaticFieldsFilter(PsiClass enumClass) { myEnumClass = enumClass;}

    @Override
    public boolean isAcceptable(Object element, @Nullable PsiElement context) {
      return !(element instanceof PsiField) || !GenericsHighlightUtil.isRestrictedStaticEnumField((PsiField)element, myEnumClass);
    }

    @Override
    public boolean isClassAcceptable(Class hintClass) {
      return true;
    }
  }
}