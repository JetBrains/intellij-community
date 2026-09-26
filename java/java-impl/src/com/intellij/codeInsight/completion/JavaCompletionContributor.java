// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.ExpectedTypeInfo;
import com.intellij.codeInsight.ExpectedTypesProvider;
import com.intellij.codeInsight.JavaTailTypes;
import com.intellij.codeInsight.TailType;
import com.intellij.codeInsight.TailTypes;
import com.intellij.codeInsight.completion.scope.CompletionElement;
import com.intellij.codeInsight.completion.scope.JavaCompletionProcessor;
import com.intellij.codeInsight.lookup.AutoCompletionPolicy;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.codeInsight.lookup.LookupElementDecorator;
import com.intellij.codeInsight.lookup.LookupItem;
import com.intellij.codeInsight.lookup.PsiTypeLookupItem;
import com.intellij.codeInsight.lookup.TailTypeDecorator;
import com.intellij.featureStatistics.FeatureUsageTracker;
import com.intellij.java.JavaBundle;
import com.intellij.java.codeserver.core.JavaPsiEnumUtil;
import com.intellij.java.codeserver.core.JavaPsiModuleUtil;
import com.intellij.java.syntax.parser.JavaKeywords;
import com.intellij.lang.LangBundle;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.lang.jvm.types.JvmPrimitiveTypeKind;
import com.intellij.modcompletion.ModCompletionItemProvider;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.options.advanced.AdvancedSettings;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.patterns.ElementPattern;
import com.intellij.patterns.PatternCondition;
import com.intellij.patterns.PsiElementPattern;
import com.intellij.patterns.PsiJavaElementPattern;
import com.intellij.patterns.PsiNameValuePairPattern;
import com.intellij.pom.java.JavaFeature;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiAnnotationParameterList;
import com.intellij.psi.PsiAnonymousClass;
import com.intellij.psi.PsiArrayType;
import com.intellij.psi.PsiCaseLabelElementList;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiCodeBlock;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiConditionalExpression;
import com.intellij.psi.PsiDeclarationStatement;
import com.intellij.psi.PsiDeconstructionList;
import com.intellij.psi.PsiDirectoryContainer;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiEnumConstant;
import com.intellij.psi.PsiErrorElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiExpressionList;
import com.intellij.psi.PsiExpressionStatement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiForStatement;
import com.intellij.psi.PsiForeachStatement;
import com.intellij.psi.PsiIdentifier;
import com.intellij.psi.PsiImplicitClass;
import com.intellij.psi.PsiImportStatement;
import com.intellij.psi.PsiImportStatementBase;
import com.intellij.psi.PsiInstanceOfExpression;
import com.intellij.psi.PsiJavaCodeReferenceCodeFragment;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiJavaModule;
import com.intellij.psi.PsiKeyword;
import com.intellij.psi.PsiLiteralExpression;
import com.intellij.psi.PsiLocalVariable;
import com.intellij.psi.PsiMember;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiMethodReferenceExpression;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiNewExpression;
import com.intellij.psi.PsiPackage;
import com.intellij.psi.PsiPackageAccessibilityStatement;
import com.intellij.psi.PsiPatternVariable;
import com.intellij.psi.PsiPrimitiveType;
import com.intellij.psi.PsiProvidesStatement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.PsiReferenceList;
import com.intellij.psi.PsiReferenceParameterList;
import com.intellij.psi.PsiSubstitutor;
import com.intellij.psi.PsiSwitchBlock;
import com.intellij.psi.PsiSwitchLabelStatementBase;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiTypeElement;
import com.intellij.psi.PsiTypeParameter;
import com.intellij.psi.PsiTypes;
import com.intellij.psi.PsiUsesStatement;
import com.intellij.psi.PsiVariable;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.filters.AndFilter;
import com.intellij.psi.filters.ClassFilter;
import com.intellij.psi.filters.ElementExtractorFilter;
import com.intellij.psi.filters.ElementFilter;
import com.intellij.psi.filters.NotFilter;
import com.intellij.psi.filters.OrFilter;
import com.intellij.psi.filters.TrueFilter;
import com.intellij.psi.filters.classes.AnnotationTypeFilter;
import com.intellij.psi.filters.classes.AssignableFromContextFilter;
import com.intellij.psi.filters.classes.NoFinalLibraryClassesFilter;
import com.intellij.psi.filters.element.ExcludeDeclaredFilter;
import com.intellij.psi.filters.element.ModifierFilter;
import com.intellij.psi.filters.getters.ClassLiteralGetter;
import com.intellij.psi.filters.getters.ExpectedTypesGetter;
import com.intellij.psi.filters.getters.JavaMembersGetter;
import com.intellij.psi.filters.types.AssignableFromFilter;
import com.intellij.psi.impl.source.PsiJavaCodeReferenceElementImpl;
import com.intellij.psi.impl.source.resolve.JavaResolveUtil;
import com.intellij.psi.scope.ElementClassFilter;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.Consumer;
import com.intellij.util.DocumentUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.ProcessingContext;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.ig.psiutils.TypeUtils;
import it.unimi.dsi.fastutil.objects.ObjectOpenCustomHashSet;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.VisibleForTesting;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import static com.intellij.codeInsight.completion.JavaQualifierAsArgumentContributor.JavaQualifierAsArgumentStaticMembersProcessor;
import static com.intellij.patterns.PsiJavaPatterns.or;
import static com.intellij.patterns.PsiJavaPatterns.psiAnnotation;
import static com.intellij.patterns.PsiJavaPatterns.psiClass;
import static com.intellij.patterns.PsiJavaPatterns.psiElement;
import static com.intellij.patterns.PsiJavaPatterns.psiMethod;
import static com.intellij.patterns.PsiJavaPatterns.psiNameValuePair;
import static com.intellij.patterns.PsiJavaPatterns.psiParameter;
import static com.intellij.patterns.PsiJavaPatterns.psiReferenceExpression;
import static com.intellij.patterns.PsiJavaPatterns.string;
import static com.intellij.patterns.PsiJavaPatterns.virtualFile;
import static com.intellij.psi.CommonClassNames.JAVA_LANG_COMPARABLE;
import static com.intellij.psi.CommonClassNames.JAVA_LANG_OBJECT;

public final class JavaCompletionContributor extends CompletionContributor implements DumbAware {
  private static final PsiElementPattern<PsiElement, ?> START_FOR = psiElement().afterLeaf(
    psiElement().withText("(").afterLeaf(JavaKeywords.FOR));
  private static final ElementPattern<PsiElement> START_SWITCH =
    psiElement().afterLeaf(psiElement().withText("{").withParents(PsiCodeBlock.class, PsiSwitchBlock.class));
  private static final ElementPattern<PsiElement> VARIABLE_AFTER_FINAL =
    psiElement().afterLeaf(JavaKeywords.FINAL).inside(PsiDeclarationStatement.class);
  private static final PsiNameValuePairPattern NAME_VALUE_PAIR =
    psiNameValuePair().withSuperParent(2, psiElement(PsiAnnotation.class));
  private static final ElementPattern<PsiElement> ANNOTATION_ATTRIBUTE_NAME =
    or(psiElement(PsiIdentifier.class).withParent(NAME_VALUE_PAIR),
       psiElement().afterLeaf("(").withParent(psiReferenceExpression().withParent(NAME_VALUE_PAIR)),
       psiElement().afterLeaf(",").withParent(psiReferenceExpression().withParent(NAME_VALUE_PAIR)));
  private static final PsiJavaElementPattern.Capture<PsiElement> IN_TYPE_PARAMETER =
    psiElement().afterLeaf(JavaKeywords.EXTENDS, JavaKeywords.SUPER, "&").withParent(
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
  static final PsiJavaElementPattern.Capture<PsiElement> IN_CASE_LABEL_ELEMENT_LIST =
    psiElement().withSuperParent(2, psiElement(PsiCaseLabelElementList.class));

  private static final ElementPattern<PsiElement> IMPORT_REFERENCE =
    psiElement().withParent(psiElement(PsiJavaCodeReferenceElement.class).withParent(PsiImportStatementBase.class));
  private static final ElementPattern<PsiElement> CATCH_OR_FINALLY = psiElement().afterLeaf(
    psiElement().withText("}").withParent(
      psiElement(PsiCodeBlock.class).afterLeaf(JavaKeywords.TRY)));
  private static final ElementPattern<PsiElement> INSIDE_CONSTRUCTOR = psiElement().inside(psiMethod().constructor(true));
  static final ElementPattern<PsiElement> IN_EXTENDS_OR_IMPLEMENTS = psiElement().afterLeaf(
    psiElement()
      .withText(string().oneOf(JavaKeywords.EXTENDS, JavaKeywords.IMPLEMENTS, ",", "&"))
      .withParent(PsiReferenceList.class));
  static final ElementPattern<PsiElement> IN_PERMITS_LIST = psiElement().afterLeaf(
    psiElement()
      .withText(string().oneOf(JavaKeywords.PERMITS, ","))
      .withParent(psiElement(PsiReferenceList.class).withFirstChild(psiElement(PsiKeyword.class).withText(JavaKeywords.PERMITS))));
  private static final ElementPattern<PsiElement> IN_VARIABLE_TYPE = psiElement()
    .withParents(PsiJavaCodeReferenceElement.class, PsiTypeElement.class, PsiDeclarationStatement.class)
    .afterLeaf(psiElement().inside(psiAnnotation()));
  private static final ElementPattern<PsiElement> TOP_LEVEL_VAR_IN_MODULE = psiElement().withSuperParent(3, PsiJavaFile.class)
    .inVirtualFile(virtualFile().withName("module-info.java"));

  /**
   * @param position completion invocation position
   * @return filter for acceptable references; if null then no references are accepted at a given position
   */
  public static @Nullable ElementFilter getReferenceFilter(PsiElement position) {
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

    if (JavaCompletionUtil.isDeclarationStart(position) ||
        JavaCompletionUtil.isInsideParameterList(position) ||
        isInsideAnnotationName(position) ||
        PsiTreeUtil.getParentOfType(position, PsiReferenceParameterList.class, false, PsiAnnotation.class) != null ||
        IN_VARIABLE_TYPE.accepts(position)) {
      return new OrFilter(ElementClassFilter.CLASS, ElementClassFilter.PACKAGE);
    }

    if (psiElement().afterLeaf(JavaKeywords.INSTANCEOF).accepts(position) ||
        position.getParent() instanceof PsiJavaCodeReferenceElement ref &&
        ref.getParent() instanceof PsiTypeElement typeElement &&
        (typeElement.getParent() instanceof PsiPatternVariable ||
         typeElement.getParent() instanceof PsiDeconstructionList)) {
      return new ElementFilter() {
        @Override
        public boolean isAcceptable(Object element, @Nullable PsiElement context) {
          return element instanceof PsiClass && !(element instanceof PsiTypeParameter);
        }

        @Override
        public boolean isClassAcceptable(Class<?> hintClass) {
          return PsiClass.class.isAssignableFrom(hintClass) && !PsiTypeParameter.class.isAssignableFrom(hintClass);
        }
      };
    }

    if (VARIABLE_AFTER_FINAL.accepts(position)) {
      return ElementClassFilter.CLASS;
    }

    if (CATCH_OR_FINALLY.accepts(position) ||
        START_SWITCH.accepts(position) ||
        JavaCompletionUtil.isInstanceofPlace(position) ||
        JavaCompletionUtil.isAfterPrimitiveOrArrayType(position)) {
      return null;
    }

    if (START_FOR.withParents(PsiJavaCodeReferenceElement.class, PsiExpressionStatement.class, PsiForStatement.class).accepts(position)) {
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
      PsiClass enumClass = JavaPsiEnumUtil.getEnumClassForExpressionInInitializer((PsiReferenceExpression)position.getParent());
      if (enumClass != null) {
        return new EnumStaticFieldsFilter(enumClass);
      }
    }

    return TrueFilter.INSTANCE;
  }

  @Contract(pure = true)
  private static @NotNull ElementFilter getCaseLabelElementListFilter(@NotNull PsiElement position) {
    PsiSwitchBlock switchBlock = PsiTreeUtil.getParentOfType(position, PsiSwitchBlock.class);
    if (switchBlock == null) return TrueFilter.INSTANCE;

    PsiExpression selector = switchBlock.getExpression();
    if (selector == null || selector.getType() == null) return TrueFilter.INSTANCE;

    PsiType selectorType = selector.getType();

    if (IN_ENUM_SWITCH_LABEL.accepts(position)) {
      ClassFilter enumClassFilter = new ClassFilter(PsiField.class) {
        @Override
        public boolean isAcceptable(Object element, PsiElement context) {
          return element instanceof PsiEnumConstant;
        }
      };
      if (!PsiUtil.isAvailable(JavaFeature.PATTERNS_IN_SWITCH, position)) {
        return enumClassFilter;
      }

      ClassFilter inheritorsEnumFilter = new ClassFilter(PsiClass.class) {
        @Override
        public boolean isAcceptable(Object element, PsiElement context) {
          return element instanceof PsiClass psiClass &&
                 (JAVA_LANG_OBJECT.equals(psiClass.getQualifiedName()) || psiClass.isEnum() || psiClass.isInterface()) &&
                 //it will be covered by enum class, and it looks like noise
                 !JAVA_LANG_COMPARABLE.equals(psiClass.getQualifiedName()) &&
                 TypeConversionUtil.areTypesConvertible(TypeUtils.getType(psiClass), selectorType);
        }
      };

      return new OrFilter(enumClassFilter, inheritorsEnumFilter);
    }

    ClassFilter constantVariablesFilter = new ClassFilter(PsiVariable.class) {
      @Override
      public boolean isAcceptable(Object element, PsiElement context) {
        PsiVariable variable;
        if (element instanceof PsiField field) {
          if (!field.hasModifierProperty(PsiModifier.FINAL) || !field.hasModifierProperty(PsiModifier.STATIC) ||
              !JavaResolveUtil.isAccessible(field, field.getContainingClass(), field.getModifierList(), context, null, null)) {
            return false;
          }

          PsiClass aClass = PsiUtil.resolveClassInClassTypeOnly(field.getType());
          if (aClass != null && aClass.isEnum()) {
            return false;
          }
          variable = field;
        }
        else if (element instanceof PsiLocalVariable local) {
          if (!local.hasModifierProperty(PsiModifier.FINAL)) {
            return false;
          }
          variable = local;
        }
        else {
          return false;
        }
        return TypeConversionUtil.isAssignable(selectorType, variable.getType());
      }
    };

    if (!PsiUtil.isAvailable(JavaFeature.PATTERNS_IN_SWITCH, position) ||
        isPrimitive(selectorType) || TypeUtils.isJavaLangString(selectorType)) {
      ClassFilter classFilter = new ClassFilter(PsiClass.class) {
        @Override
        public boolean isAcceptable(Object element, PsiElement context) {
          // Accept only classes with inner classes or with suitable fields
          if (!(element instanceof PsiClass psiClass)) return false;
          for (PsiClass aClass : psiClass.getInnerClasses()) {
            if (JavaResolveUtil.isAccessible(aClass, psiClass, aClass.getModifierList(), context, null, null)) {
              return true;
            }
          }
          return ContainerUtil.exists(psiClass.getAllFields(), field -> constantVariablesFilter.isAcceptable(field, context));
        }
      };
      return new OrFilter(classFilter, constantVariablesFilter);
    }

    ClassFilter enumInheritorsFilter = new ClassFilter(PsiField.class) {
      @Override
      public boolean isAcceptable(Object element, PsiElement context) {
        return element instanceof PsiEnumConstant enumConstant && TypeConversionUtil.areTypesConvertible(enumConstant.getType(), selectorType);
      }
    };

    if (TypeUtils.isJavaLangObject(selectorType)) {
      return new OrFilter(new ClassFilter(PsiClass.class), constantVariablesFilter, enumInheritorsFilter);
    }

    ClassFilter inheritorsFilter = new ClassFilter(PsiClass.class) {
      @Override
      public boolean isAcceptable(Object element, PsiElement context) {
        return element instanceof PsiClass psiClass &&
               TypeConversionUtil.areTypesConvertible(TypeUtils.getType(psiClass), selectorType);
      }
    };

    if (TypeUtils.isJavaLangString(selectorType)) {
      return new OrFilter(constantVariablesFilter, inheritorsFilter);
    }

    return selectorType instanceof PsiClassType
           ? new OrFilter(inheritorsFilter, enumInheritorsFilter)
           : TrueFilter.INSTANCE;
  }

  @Contract(pure = true)
  private static boolean isPrimitive(@NotNull PsiType type) {
    if (type instanceof PsiPrimitiveType) return true;
    if (!(type instanceof PsiClassType)) return false;

    PsiClass aClass = ((PsiClassType)type).resolve();
    Collection<String> boxedPrimitiveTypes = JvmPrimitiveTypeKind.getBoxedFqns();
    return aClass != null && boxedPrimitiveTypes.contains(aClass.getQualifiedName());
  }

  static @NotNull AndFilter createPermitsListFilter() {
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
  public void fillCompletionVariants(@NotNull CompletionParameters parameters, @NotNull CompletionResultSet _result) {
    if (ModCompletionItemProvider.modCommandCompletionEnabled()) return;
    PsiElement position = parameters.getPosition();
    if (!isInJavaContext(position)) {
      return;
    }

    if (JavaCompletionUtil.isAfterNumberLiteral(position) ||
        JavaCompletionUtil.isUnexpectedReferenceAfterDot(position) ||
        JavaCompletionUtil.isAfterEnumConstant(position)) {
      _result.stopHere();
      return;
    }

    boolean smart = parameters.getCompletionType() == CompletionType.SMART;

    CompletionResultSet result = JavaCompletionSorting.addJavaSorting(parameters, _result);
    JavaCompletionSession session = new JavaCompletionSession(result);

    PrefixMatcher matcher = result.getPrefixMatcher();
    PsiElement parent = position.getParent();


    boolean mayCompleteReference = true;

    if (position instanceof PsiIdentifier) {
      addIdentifierVariants(parameters, position, result, session, matcher);
      if (JavaMemberNameCompletionContributor.INSIDE_TYPE_PARAMS_PATTERN.accepts(position)) {
        session.flushBatchItems();
        result.stopHere();
        return;
      }

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
        }
      }

      List<LookupElement> refSuggestions = Collections.emptyList();
      if (parent instanceof PsiJavaCodeReferenceElement parentRef && mayCompleteReference) {
        if (IN_PERMITS_LIST.accepts(parent) && parameters.getInvocationCount() <= 1 && !parentRef.isQualified()) {
          refSuggestions = completePermitsListReference(parameters, parentRef, matcher);
        }
        else {
          if (parameters.getInvocationCount() >= 2 && Registry.is("java.completion.methods.use.tags")) {
            MethodTags.TagMatcher tagMatcher = new MethodTags.TagMatcher(matcher);
            List<LookupElement> proposedElements = completeReference(parameters, parentRef, session, expectedInfos, tagMatcher::prefixMatches);
            refSuggestions = ContainerUtil.map(proposedElements, element -> {
              LookupElement withTags = MethodTags.wrapLookupWithTags(element, matcher::prefixMatches, matcher.getPrefix(), parameters.getCompletionType());
              return withTags != null ? withTags : element;
            });
          }
          else {
            refSuggestions = completeReference(parameters, parentRef, session, expectedInfos, matcher::prefixMatches);
            if (refSuggestions
              .stream()
              .map(lookupElement -> MethodTags.collectTags(lookupElement, matcher::prefixMatches))
              .anyMatch(t -> t != null && !t.isEmpty())) {
              //it is possible to propose some tags, let's try to do this
              _result.restartCompletionWhenNothingMatches();
            }
          }
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
      } else {
        if (!JavaCompletionUtil.AFTER_DOT.accepts(position)) {
          for (ExpectedTypeInfo info : expectedInfos) {
            ClassLiteralGetter.addCompletions(new JavaSmartCompletionParameters(parameters, info), result, matcher);
          }
        }
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

    if (JavaPatternCompletionUtil.insideDeconstructionList(parameters.getPosition())) {
      JavaPatternCompletionUtil.suggestFullDeconstructionList(parameters, result);
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
        PsiType psiType = JavaCompletionUtil.getLookupElementType(lookupElement);
        if (psiType != null && info.getType().isAssignableFrom(psiType)) {
          hadItems[0] = true;
          result.addElement(JavaSmartCompletionContributor.decorate(lookupElement, infos));
        }
      }, result.getPrefixMatcher());
    }
    return hadItems[0];
  }

  public static @Nullable PsiAnnotation findAnnotationWhoseAttributeIsCompleted(@NotNull PsiElement position) {
    return ANNOTATION_ATTRIBUTE_NAME.accepts(position) && !JavaCompletionUtil.isAfterPrimitiveOrArrayType(position)
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
      List<ExpectedTypeInfo> expected = Arrays.asList(ExpectedTypesProvider.getExpectedTypes((PsiExpression)parent, true));
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

    addExpressionVariants(parameters, position, items::add);

    return items;
  }

  private static void registerClassFromTypeElement(LookupElement element, JavaCompletionSession session) {
    PsiType type = Objects.requireNonNull(element.as(PsiTypeLookupItem.CLASS_CONDITION_KEY)).getType();
    if (type instanceof PsiClassType && ((PsiClassType)type).getParameterCount() == 0) {
      PsiClass aClass = ((PsiClassType)type).resolve();
      if (aClass != null) {
        session.registerClass(aClass);
      }
    }
  }

  private static boolean shouldAddExpressionVariants(CompletionParameters parameters) {
    return JavaSmartCompletionContributor.INSIDE_EXPRESSION.accepts(parameters.getPosition()) &&
           !JavaCompletionUtil.AFTER_DOT.accepts(parameters.getPosition()) &&
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

  private static @NotNull List<LookupElement> completeReference(@NotNull CompletionParameters parameters,
                                                                PsiJavaCodeReferenceElement ref,
                                                                JavaCompletionSession session,
                                                                Set<? extends ExpectedTypeInfo> expectedTypes,
                                                                Condition<? super String> nameCondition) {
    PsiElement position = parameters.getPosition();
    if (TOP_LEVEL_VAR_IN_MODULE.accepts(position)) return Collections.emptyList();
    ElementFilter filter = getReferenceFilter(position);
    if (filter == null) return Collections.emptyList();
    if (parameters.getInvocationCount() <= 1 && JavaClassNameCompletionContributor.AFTER_NEW.accepts(position)) {
      filter = new AndFilter(filter, new ElementFilter() {
        @Override
        public boolean isAcceptable(Object element, @Nullable PsiElement context) {
          return !JavaPsiClassReferenceElement.isInaccessibleConstructorSuggestion(position, ObjectUtils.tryCast(element, PsiClass.class));
        }

        @Override
        public boolean isClassAcceptable(Class<?> hintClass) {
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
    TailType forcedTail = getTailType(smart, inSwitchLabel, position);

    List<LookupElement> items = new ArrayList<>();
    if (INSIDE_CONSTRUCTOR.accepts(position) &&
        (parameters.getInvocationCount() <= 1 || CheckInitialized.isInsideConstructorCall(position))) {
      filter = new AndFilter(filter, new CheckInitialized(position));
    }
    PsiFile originalFile = parameters.getOriginalFile();

    boolean first = parameters.getInvocationCount() <= 1;
    boolean instantiableOnly = JavaSmartCompletionContributor.AFTER_NEW.accepts(position);
    JavaCompletionProcessor.Options options =
      JavaCompletionProcessor.Options.DEFAULT_OPTIONS
        .withCheckAccess(first)
        .withFilterStaticAfterInstance(first)
        .withInstantiableOnly(instantiableOnly)
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
        element = new TailTypeDecorator<>(element) {
          @Override
          protected TailType computeTailType(@NotNull InsertionContext context) {
            if (context.getCompletionChar() == ':' && forcedTail == JavaTailTypes.CASE_ARROW) {
              return TailTypes.caseColonType();
            }
            return forcedTail;
          }
        };
      }

      if (inSwitchLabel && !smart) {
        element = new IndentingDecorator(element);
      }
      if (originalFile instanceof PsiJavaCodeReferenceCodeFragment &&
          !((PsiJavaCodeReferenceCodeFragment)originalFile).isClassesAccepted() && item != null) {
        item.setTailType(TailTypes.noneType());
      }
      if (item instanceof JavaMethodCallElement call) {
        prepareMethodCallForExpectedTypes(call, position, expectedTypes);
      }
      items.add(element);

      ContainerUtil.addIfNotNull(items, ArrayMemberAccess.accessFirstElement(position, element));
    }

    if (ref.getQualifier() instanceof PsiExpression qualifierExpression &&
        parameters.getInvocationCount() <= 2 &&
        AdvancedSettings.getBoolean("java.completion.qualifier.as.argument")) {
      JavaQualifierAsArgumentStaticMembersProcessor processor =
        new JavaQualifierAsArgumentStaticMembersProcessor(parameters, qualifierExpression);

      items = ContainerUtil.map(items, lookupItem -> {
        JavaMethodCallElement javaMethodCallElement = lookupItem.as(JavaMethodCallElement.class);
        if (javaMethodCallElement != null) {
          PsiMethod psiMethod = javaMethodCallElement.getObject();
          Ref<LookupElement> staticref = new Ref<>();
          processor.processStaticMember(staticItem -> staticref.set(staticItem), psiMethod, new HashSet<>());
          if (!staticref.isNull()) {
            PrioritizedLookupElement<?> prioritizedLookupElement = lookupItem.as(PrioritizedLookupElement.class);
            if (prioritizedLookupElement != null) {
              if (prioritizedLookupElement.getExplicitProximity() != 0) {
                return PrioritizedLookupElement.withExplicitProximity(staticref.get(), prioritizedLookupElement.getExplicitProximity());
              }
              else {
                return PrioritizedLookupElement.withPriority(staticref.get(), prioritizedLookupElement.getPriority());
              }
            }
            return staticref.get();
          }
        }
        return lookupItem;
      });
    }

    return items;
  }

  private static @Nullable TailType getTailType(boolean smart, boolean inSwitchLabel, PsiElement position) {
    if (!smart && inSwitchLabel) {
      if (position instanceof PsiClass) {
        return TailTypes.insertSpaceType();
      }
      return JavaTailTypes.forSwitchLabel(Objects.requireNonNull(PsiTreeUtil.getParentOfType(position, PsiSwitchBlock.class)));
    }
    if (!smart && shouldInsertSemicolon(position)) {
      return TailTypes.semicolonType();
    }
    return null;
  }

  private static @NotNull List<LookupElement> completePermitsListReference(@NotNull CompletionParameters parameters,
                                                                           @NotNull PsiJavaCodeReferenceElement referenceElement,
                                                                           @NotNull PrefixMatcher prefixMatcher) {
    List<LookupElement> lookupElements = new SmartList<>();
    PsiJavaFile psiJavaFile = ObjectUtils.tryCast(referenceElement.getContainingFile(), PsiJavaFile.class);
    if (psiJavaFile == null) return lookupElements;
    PsiJavaModule javaModule = JavaPsiModuleUtil.findDescriptorByElement(psiJavaFile.getOriginalElement());
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
           JavaFrontendCompletionUtil.insertSemicolon(position.getParent().getParent());
  }

  static boolean isClassNamePossible(CompletionParameters parameters) {
    boolean isSecondCompletion = parameters.getInvocationCount() >= 2;

    PsiElement position = parameters.getPosition();
    if (JavaCompletionUtil.isInstanceofPlace(position) ||
        JavaMemberNameCompletionContributor.INSIDE_TYPE_PARAMS_PATTERN.accepts(position) ||
        JavaCompletionUtil.isAfterEnumConstant(position)) {
      return false;
    }

    PsiElement parent = position.getParent();
    if (!(parent instanceof PsiJavaCodeReferenceElement)) return isSecondCompletion;
    if (((PsiJavaCodeReferenceElement)parent).getQualifier() != null) return isSecondCompletion;

    if (parent instanceof PsiJavaCodeReferenceElementImpl &&
        ((PsiJavaCodeReferenceElementImpl)parent).getKindEnum(parent.getContainingFile()) == PsiJavaCodeReferenceElementImpl.Kind.PACKAGE_NAME_KIND) {
      return false;
    }

    if (IN_SWITCH_LABEL.accepts(position)) {
      return PsiUtil.isAvailable(JavaFeature.PATTERNS_IN_SWITCH, parent);
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

    return !JavaCompletionUtil.isAfterPrimitiveOrArrayType(position);
  }

  public static boolean mayStartClassName(CompletionResultSet result) {
    return InternalCompletionSettings.getInstance().mayStartClassNameCompletion(result);
  }

  @Override
  public String advertise(@NotNull CompletionParameters parameters) {
    if (!(parameters.getOriginalFile() instanceof PsiJavaFile)) return null;

    if (parameters.getCompletionType() == CompletionType.BASIC && parameters.getInvocationCount() > 0) {
      PsiElement position = parameters.getPosition();
      if (psiElement().withParent(psiReferenceExpression().withFirstChild(psiReferenceExpression().referencing(psiClass()))).accepts(position)) {
        if (CompletionUtil.shouldShowFeature(parameters, JavaCompletionFeatures.GLOBAL_MEMBER_NAME)) {
          String shortcut = KeymapUtil.getFirstKeyboardShortcutText(IdeActions.ACTION_CODE_COMPLETION);
          if (StringUtil.isNotEmpty(shortcut)) {
            return JavaBundle.message("pressing.0.twice.without.a.class.qualifier", shortcut);
          }
        }
      }
    }

    if (parameters.getCompletionType() != CompletionType.SMART && shouldSuggestSmartCompletion(parameters.getPosition())) {
      if (CompletionUtil.shouldShowFeature(parameters, CodeCompletionFeatures.EDITING_COMPLETION_SMARTTYPE_GENERAL)) {
        String shortcut = KeymapUtil.getFirstKeyboardShortcutText(IdeActions.ACTION_SMART_TYPE_COMPLETION);
        if (StringUtil.isNotEmpty(shortcut)) {
          return JavaBundle.message("completion.smart.hint", shortcut);
        }
      }
    }

    if (parameters.getCompletionType() == CompletionType.SMART && parameters.getInvocationCount() == 1) {
      PsiType[] psiTypes = ExpectedTypesGetter.getExpectedTypes(parameters.getPosition(), true);
      if (psiTypes.length > 0) {
        if (CompletionUtil.shouldShowFeature(parameters, JavaCompletionFeatures.SECOND_SMART_COMPLETION_TOAR)) {
          String shortcut = KeymapUtil.getFirstKeyboardShortcutText(IdeActions.ACTION_SMART_TYPE_COMPLETION);
          if (StringUtil.isNotEmpty(shortcut)) {
            for (PsiType psiType : psiTypes) {
              PsiType type = PsiUtil.extractIterableTypeParameter(psiType, false);
              if (type != null) {
                return JavaBundle.message("completion.smart.aslist.hint", shortcut, type.getPresentableText());
              }
            }
          }
        }
        if (CompletionUtil.shouldShowFeature(parameters, JavaCompletionFeatures.SECOND_SMART_COMPLETION_ASLIST)) {
          String shortcut = KeymapUtil.getFirstKeyboardShortcutText(IdeActions.ACTION_SMART_TYPE_COMPLETION);
          if (StringUtil.isNotEmpty(shortcut)) {
            for (PsiType psiType : psiTypes) {
              if (psiType instanceof PsiArrayType) {
                PsiType componentType = ((PsiArrayType)psiType).getComponentType();
                if (!(componentType instanceof PsiPrimitiveType)) {
                  return JavaBundle.message("completion.smart.toar.hint", shortcut, componentType.getPresentableText());
                }
              }
            }
          }
        }

        if (CompletionUtil.shouldShowFeature(parameters, JavaCompletionFeatures.SECOND_SMART_COMPLETION_CHAIN)) {
          String shortcut = KeymapUtil.getFirstKeyboardShortcutText(IdeActions.ACTION_SMART_TYPE_COMPLETION);
          if (StringUtil.isNotEmpty(shortcut)) {
            return JavaBundle.message("completion.smart.chain.hint", shortcut);
          }
        }
      }
    }
    return null;
  }

  @Override
  public @NlsContexts.HintText String handleEmptyLookup(@NotNull CompletionParameters parameters, @NotNull Editor editor) {
    if (!(parameters.getOriginalFile() instanceof PsiJavaFile)) return null;

    String ad = advertise(parameters);
    String suffix = ad == null ? "" : "; " + StringUtil.decapitalize(ad);
    if (parameters.getCompletionType() == CompletionType.SMART) {
      PsiExpression expression = PsiTreeUtil.getContextOfType(parameters.getPosition(), PsiExpression.class, true);
      if (expression instanceof PsiLiteralExpression) {
        return LangBundle.message("completion.no.suggestions") + suffix;
      }

      if (expression instanceof PsiInstanceOfExpression instanceOfExpression &&
          PsiTreeUtil.isAncestor(instanceOfExpression.getCheckType(), parameters.getPosition(), false)) {
        return LangBundle.message("completion.no.suggestions") + suffix;
      }

      Set<PsiType> expectedTypes = JavaCompletionUtil.getExpectedTypes(parameters);
      if (expectedTypes != null) {
        PsiType type = expectedTypes.size() == 1 ? expectedTypes.iterator().next() : null;
        if (type != null) {
          PsiType deepComponentType = type.getDeepComponentType();
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
          if (!PsiTypes.nullType().equals(type)) {
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

  private static boolean shouldSuggestSmartCompletion(PsiElement element) {
    if (shouldSuggestClassNameCompletion(element)) return false;

    PsiElement parent = element.getParent();
    if (parent instanceof PsiReferenceExpression && ((PsiReferenceExpression)parent).getQualifier() != null) return false;
    if (parent instanceof PsiReferenceExpression && parent.getParent() instanceof PsiReferenceExpression) return true;

    return ExpectedTypesGetter.getExpectedTypes(element, false).length > 0;
  }

  private static boolean shouldSuggestClassNameCompletion(PsiElement element) {
    if (element == null) return false;
    PsiElement parent = element.getParent();
    if (parent == null) return false;
    return parent.getParent() instanceof PsiTypeElement || parent.getParent() instanceof PsiExpressionStatement ||
           parent.getParent() instanceof PsiReferenceList;
  }

  @Override
  public void beforeCompletion(@NotNull CompletionInitializationContext context) {
    PsiFile file = context.getFile();

    if (file instanceof PsiJavaFile) {
      String dummyIdentifier = customizeDummyIdentifier(context, file);
      if (dummyIdentifier != null) {
        context.setDummyIdentifier(dummyIdentifier);
      }

      PsiElement element = file.findElementAt(context.getStartOffset());
      if (file.getName().equals(PsiJavaModule.MODULE_INFO_FILE)) {
        if (element instanceof PsiWhiteSpace &&
            element.textContains('\n') &&
            element.getTextRange().getStartOffset() == context.getStartOffset()) {
          context.setReplacementOffset(context.getStartOffset());
        }
      }

      PsiLiteralExpression literal =
        PsiTreeUtil.findElementOfClassAtOffset(file, context.getStartOffset(), PsiLiteralExpression.class, false);
      if (literal != null) {
        TextRange range = literal.getTextRange();
        if (range.getStartOffset() == context.getStartOffset()) {
          context.setReplacementOffset(range.getEndOffset());
        }
      }

      PsiJavaCodeReferenceElement ref = getAnnotationNameIfInside(element);
      if (ref != null) {
        context.setReplacementOffset(ref.getTextRange().getEndOffset());
      }
    }

    if (context.getCompletionType() == CompletionType.SMART) {
      JavaSmartCompletionContributor.beforeSmartCompletion(context);
    }
  }

  @ApiStatus.Internal
  public static @Nullable PsiJavaCodeReferenceElement getAnnotationNameIfInside(@Nullable PsiElement position) {
    PsiAnnotation anno = PsiTreeUtil.getParentOfType(position, PsiAnnotation.class);
    PsiJavaCodeReferenceElement ref = anno == null ? null : anno.getNameReferenceElement();
    return ref != null && PsiTreeUtil.isAncestor(ref, position, false) ? ref : null;
  }

  private static @Nullable String customizeDummyIdentifier(@NotNull CompletionInitializationContext context, PsiFile file) {
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
        PsiTreeUtil.getParentOfType(ref, PsiExpression.class, PsiClass.class) == null &&
        PsiTreeUtil.getParentOfType(at, PsiImplicitClass.class) == null) { // TODO check before it that there is record
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

  private static @Nullable PsiElement skipWhitespacesAndComments(@Nullable PsiElement at) {
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

  /**
   * Prepares a method call element with type inference based on the expected types provided.
   *
   * @param call The method call element to be prepared.
   * @param position The PSI element representing the position in the code where the method call occurs.
   * @param infos A collection of expected type information that the method call should satisfy.
   */
  public static void prepareMethodCallForExpectedTypes(@NotNull JavaMethodCallElement call,
                                                       @NotNull PsiElement position,
                                                       @NotNull Collection<? extends ExpectedTypeInfo> infos) {
    PsiMethod method = call.getObject();
    if (method.getTypeParameters().length > 0) {
      PsiType returned = TypeConversionUtil.erasure(method.getReturnType());
      ExpectedTypeInfo matchingExpectation = returned == null ? null : ContainerUtil.find(infos, info ->
        info.getDefaultType().isAssignableFrom(returned) ||
        AssignableFromFilter.isAcceptable(method, position, info.getDefaultType(), call.getSubstitutor()));
      if (matchingExpectation != null) {
        call.setInferenceSubstitutorFromExpectedType(position, matchingExpectation.getDefaultType());
      }
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
    public boolean isClassAcceptable(Class<?> hintClass) {
      return true;
    }
  }

  private static class EnumStaticFieldsFilter implements ElementFilter {
    private final PsiClass myEnumClass;

    private EnumStaticFieldsFilter(PsiClass enumClass) { myEnumClass = enumClass;}

    @Override
    public boolean isAcceptable(Object element, @Nullable PsiElement context) {
      return !(element instanceof PsiField) || !JavaPsiEnumUtil.isRestrictedStaticEnumField((PsiField)element, myEnumClass);
    }

    @Override
    public boolean isClassAcceptable(Class<?> hintClass) {
      return true;
    }
  }
}