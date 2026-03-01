// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.completion.modcommand;

import com.intellij.codeInsight.ExpectedTypeInfo;
import com.intellij.codeInsight.ExpectedTypesProvider;
import com.intellij.codeInsight.completion.AllClassesGetter;
import com.intellij.codeInsight.completion.JavaClassNameCompletionContributor;
import com.intellij.codeInsight.completion.JavaClassReferenceCompletionContributor;
import com.intellij.codeInsight.completion.JavaCompletionContributor;
import com.intellij.codeInsight.completion.JavaCompletionUtil;
import com.intellij.codeInsight.completion.JavaKeywordCompletion;
import com.intellij.codeInsight.completion.JavaMemberNameCompletionContributor;
import com.intellij.codeInsight.completion.JavaPatternCompletionUtil;
import com.intellij.codeInsight.completion.LimitedAccessibleClassPreprocessor;
import com.intellij.codeInsight.completion.PrefixMatcher;
import com.intellij.java.syntax.parser.JavaKeywords;
import com.intellij.modcompletion.ModCompletionResult;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.patterns.ElementPattern;
import com.intellij.patterns.PsiJavaElementPattern;
import com.intellij.pom.java.JavaFeature;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.PsiAnonymousClass;
import com.intellij.psi.PsiCaseLabelElementList;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiEnumConstant;
import com.intellij.psi.PsiErrorElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiExpressionList;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiIdentifier;
import com.intellij.psi.PsiImportStatement;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.psi.PsiKeyword;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiNewExpression;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiParameterList;
import com.intellij.psi.PsiReferenceList;
import com.intellij.psi.PsiSwitchBlock;
import com.intellij.psi.PsiSwitchLabelStatementBase;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiTypeElement;
import com.intellij.psi.filters.ElementFilter;
import com.intellij.psi.impl.source.PsiJavaCodeReferenceElementImpl;
import com.intellij.psi.impl.source.resolve.reference.impl.providers.JavaClassReference;
import com.intellij.psi.impl.source.tree.JavaElementType;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.Processor;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

import static com.intellij.codeInsight.completion.JavaClassNameCompletionContributor.getAllAnnotationClasses;
import static com.intellij.patterns.PsiJavaPatterns.psiElement;
import static com.intellij.patterns.PsiJavaPatterns.psiExpressionStatement;
import static com.intellij.patterns.StandardPatterns.or;
import static com.intellij.patterns.StandardPatterns.string;

@NotNullByDefault
final class NonImportedClassProvider extends JavaModCompletionItemProvider {
  private static final PsiJavaElementPattern.Capture<PsiElement> AFTER_NEW = psiElement().afterLeaf(JavaKeywords.NEW);
  private static final ElementPattern<PsiElement> IN_PERMITS_LIST = psiElement().afterLeaf(
    psiElement()
      .withText(string().oneOf(JavaKeywords.PERMITS, ","))
      .withParent(psiElement(PsiReferenceList.class).withFirstChild(psiElement(PsiKeyword.class).withText(JavaKeywords.PERMITS))));
  private static final ElementPattern<PsiElement> UNEXPECTED_REFERENCE_AFTER_DOT = or(
    // dot at the statement beginning
    psiElement().afterLeaf(".").insideStarting(psiExpressionStatement()),
    //example: class A{ .something }
    psiElement().afterLeaf(".")
      .insideStarting(psiElement(JavaElementType.TYPE))
      .afterLeafSkipping(psiElement().andOr(
                           psiElement().whitespace(),
                           psiElement().withText("")),
                         psiElement().withParent(PsiErrorElement.class))
      .withParent(PsiJavaCodeReferenceElement.class)
      .withSuperParent(2, PsiTypeElement.class)
      .withSuperParent(3, PsiClass.class),
    //example: void test(String p.<caret>)
    psiElement().afterLeaf(".")
      .afterLeafSkipping(psiElement().andOr(
                           psiElement().whitespace(),
                           psiElement().withText("")),
                         psiElement().withParent(PsiErrorElement.class))
      .afterLeafSkipping(psiElement().withParent(PsiErrorElement.class),
                         psiElement().withElementType(JavaTokenType.IDENTIFIER)
                           .withParent(PsiParameter.class)
                           .withSuperParent(2, PsiParameterList.class)),
    // like `call(Cls::methodRef.<caret>`
    psiElement().afterLeaf(psiElement(JavaTokenType.DOT).afterSibling(psiElement(PsiMethodCallExpression.class).withLastChild(
      psiElement(PsiExpressionList.class).withLastChild(psiElement(PsiErrorElement.class))))),
    // dot after primitive type `int.<caret>` or dot after dot `Object..<caret>`
    psiElement().afterLeaf(psiElement(JavaTokenType.DOT).withParent(
      psiElement(PsiErrorElement.class).afterSibling(psiElement(PsiErrorElement.class)))));
  private static final ElementPattern<PsiElement> AFTER_ENUM_CONSTANT =
    psiElement().inside(PsiTypeElement.class).afterLeaf(
      psiElement().inside(true, psiElement(PsiEnumConstant.class), psiElement(PsiClass.class, PsiExpressionList.class)));
  private static final ElementPattern<PsiElement> IN_SWITCH_LABEL =
    psiElement().withSuperParent(2, psiElement(PsiCaseLabelElementList.class)
      .withParent(psiElement(PsiSwitchLabelStatementBase.class).withSuperParent(2, PsiSwitchBlock.class)));

  @Override
  public void provideItems(CompletionContext context, ModCompletionResult sink) {
    if (!isClassNamePossible(context) || context.matcher().getPrefix().isEmpty()) {
      return;
    }
    suggestNonImportedClasses(context, sink);
  }

  private static void suggestNonImportedClasses(CompletionContext parameters,
                                                ModCompletionResult sink) {
    if (UNEXPECTED_REFERENCE_AFTER_DOT.accepts(parameters.getPosition())) return;
    addAllClasses(parameters, parameters.invocationCount() <= 2, parameters.matcher(), sink);
  }

  public static void addAllClasses(CompletionContext parameters,
                                   boolean filterByScope,
                                   PrefixMatcher matcher,
                                   ModCompletionResult sink) {
    final PsiElement insertedElement = parameters.getPosition();
    final PsiFile psiFile = insertedElement.getContainingFile();

    JavaClassReference ref = JavaClassReferenceCompletionContributor.findJavaClassReference(psiFile, parameters.getOffset());
    if (ref != null && ref.getContext() instanceof PsiClass) {
      return;
    }

    if (JavaCompletionContributor.getAnnotationNameIfInside(insertedElement) != null) {
      MultiMap<String, PsiClass> annoMap = getAllAnnotationClasses(insertedElement, matcher);
      Processor<PsiClass> processor = new LimitedAccessibleClassPreprocessor(parameters, filterByScope, anno -> {
        sink.accept(new ClassReferenceCompletionItem(anno));
        return true;
      });
      for (String name : matcher.sortMatching(annoMap.keySet())) {
        if (!ContainerUtil.process(annoMap.get(name), processor)) break;
      }
      return;
    }

    final boolean inPermitsList = IN_PERMITS_LIST.accepts(insertedElement);
    final ElementFilter filter = JavaCompletionContributor.getReferenceFilter(insertedElement);
    if (filter == null) return;

    final boolean inJavaContext = insertedElement instanceof PsiIdentifier;
    final boolean afterNew = AFTER_NEW.accepts(insertedElement);
    if (afterNew) {
      final PsiExpression expr = PsiTreeUtil.getContextOfType(insertedElement, PsiExpression.class, true);
      for (final ExpectedTypeInfo info : ExpectedTypesProvider.getExpectedTypes(expr, true)) {
        final PsiType type = info.getType();
        final PsiClass psiClass = PsiUtil.resolveClassInType(type);
        if (psiClass != null && psiClass.getName() != null && !(psiClass.hasModifierProperty(PsiModifier.SEALED) && psiClass.hasModifierProperty(PsiModifier.ABSTRACT))) {
          sink.accept(new ClassReferenceCompletionItem(psiClass));
        }
        final PsiType defaultType = info.getDefaultType();
        if (!defaultType.equals(type)) {
          final PsiClass defClass = PsiUtil.resolveClassInType(defaultType);
          if (defClass != null && defClass.getName() != null) {
            sink.accept(new ClassReferenceCompletionItem(defClass));
          }
        }
      }
    }

    boolean pkgContext = JavaCompletionUtil.inSomePackage(insertedElement);
    GlobalSearchScope scope = JavaClassNameCompletionContributor.getReferenceScope(parameters, filterByScope, inPermitsList);

    boolean patternContext = JavaPatternCompletionUtil.isPatternContext(psiFile, insertedElement);

    Processor<PsiClass> classProcessor = new Processor<>() {
      @Override
      public boolean process(PsiClass psiClass) {
        processClass(psiClass, null, "");
        return true;
      }

      private void processClass(PsiClass psiClass, @Nullable Set<? super PsiClass> visited, String prefix) {
        boolean isInnerClass = StringUtil.isNotEmpty(prefix);
        if (isInnerClass && isProcessedIndependently(psiClass)) {
          return;
        }

        if (filter.isAcceptable(psiClass, insertedElement)) {
          if (!inJavaContext) {
            sink.accept(new ClassReferenceCompletionItem(psiClass));
          }
          else {
            Predicate<PsiClass> condition = eachClass ->
              filter.isAcceptable(eachClass, insertedElement) &&
              AllClassesGetter.isAcceptableInContext(insertedElement, eachClass, filterByScope, pkgContext) &&
              (!afterNew || !(eachClass.hasModifierProperty(PsiModifier.ABSTRACT) && eachClass.hasModifierProperty(PsiModifier.SEALED)));
            for (ClassReferenceCompletionItem element : createClassLookupItems(psiClass, afterNew, condition)) {
              ConstructorCallCompletionItem.tryWrap(element, parameters.getPosition()).forEach(sink);
              //if (patternContext) {
              //  JavaPatternCompletionUtil.addPatterns(sink::accept, insertedElement, element.getObject(), false);
              //}
            }
          }
        }
        else {
          String name = psiClass.getName();
          if (name != null) {
            PsiClass[] innerClasses = psiClass.getInnerClasses();
            if (innerClasses.length > 0) {
              if (visited == null) visited = new HashSet<>();

              for (PsiClass innerClass : innerClasses) {
                if (visited.add(innerClass)) {
                  processClass(innerClass, visited, prefix + name + ".");
                }
              }
            }
          }
        }
      }

      private boolean isProcessedIndependently(PsiClass psiClass) {
        String innerName = psiClass.getName();
        return innerName != null && matcher.prefixMatches(innerName);
      }
    };
    final Project project = insertedElement.getProject();
    AllClassesGetter.processJavaClasses(matcher, project, scope,
                                        new LimitedAccessibleClassPreprocessor(parameters, filterByScope, classProcessor));
  }

  private static boolean isClassNamePossible(CompletionContext context) {
    boolean isSecondCompletion = context.invocationCount() >= 2;

    PsiElement position = context.getPosition();
    if (JavaKeywordCompletion.isInstanceofPlace(position) ||
        JavaMemberNameCompletionContributor.INSIDE_TYPE_PARAMS_PATTERN.accepts(position) ||
        AFTER_ENUM_CONSTANT.accepts(position)) {
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

    return !JavaKeywordCompletion.isAfterPrimitiveOrArrayType(position);
  }

  static List<ClassReferenceCompletionItem> createClassLookupItems(final PsiClass psiClass,
                                                                   boolean withInners,
                                                                   Predicate<? super PsiClass> condition) {
    String name = psiClass.getName();
    if (name == null) return Collections.emptyList();
    List<ClassReferenceCompletionItem> result = new ArrayList<>();
    if (condition.test(psiClass)) {
      result.add(new ClassReferenceCompletionItem(psiClass));
    }
    if (withInners) {
      for (PsiClass inner : psiClass.getInnerClasses()) {
        if (inner.hasModifierProperty(PsiModifier.STATIC)) {
          for (ClassReferenceCompletionItem item : createClassLookupItems(inner, true, condition)) {
            String forced = item.getForcedPresentableName();
            String qualifiedName = name + "." + (forced != null ? forced : inner.getName());
            result.add(item.withPresentableName(qualifiedName));
          }
        }
      }
    }
    return result;
  }

}
