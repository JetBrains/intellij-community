// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.ExpectedTypeInfo;
import com.intellij.codeInsight.ExpectedTypesProvider;
import com.intellij.codeInsight.completion.JavaCompletionUtil.JavaLookupElementHighlighter;
import com.intellij.codeInsight.daemon.impl.analysis.JavaModuleGraphUtil.JavaModuleScope;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.java.JavaBundle;
import com.intellij.java.codeserver.core.JavaPsiModuleUtil;
import com.intellij.java.syntax.parser.JavaKeywords;
import com.intellij.lang.LangBundle;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.patterns.PsiJavaElementPattern;
import com.intellij.psi.*;
import com.intellij.psi.filters.ElementFilter;
import com.intellij.psi.impl.source.resolve.reference.impl.providers.JavaClassReference;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.DirectClassInheritorsSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.Consumer;
import com.intellij.util.Processor;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.JBIterable;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static com.intellij.codeInsight.completion.JavaClassNameInsertHandler.JAVA_CLASS_INSERT_HANDLER;
import static com.intellij.patterns.PsiJavaPatterns.psiElement;

public class JavaClassNameCompletionContributor extends CompletionContributor implements DumbAware {
  public static final PsiJavaElementPattern.Capture<PsiElement> AFTER_NEW = psiElement().afterLeaf(JavaKeywords.NEW);

  @Override
  public void fillCompletionVariants(@NotNull CompletionParameters parameters, final @NotNull CompletionResultSet _result) {
    if (parameters.getCompletionType() == CompletionType.CLASS_NAME ||
      parameters.isExtendedCompletion() && mayContainClassName(parameters)) {
      addAllClasses(parameters, _result);
    }
  }

  static void addAllClasses(CompletionParameters parameters, final CompletionResultSet _result) {
    CompletionResultSet result = _result.withPrefixMatcher(CompletionUtil.findReferenceOrAlphanumericPrefix(parameters));
    addAllClasses(parameters, parameters.getInvocationCount() <= 1, result.getPrefixMatcher(), _result);
  }

  private static boolean mayContainClassName(CompletionParameters parameters) {
    PsiElement position = parameters.getPosition();
    if (SkipAutopopupInStrings.isInStringLiteral(position)) {
      return true;
    }
    PsiComment comment = PsiTreeUtil.getParentOfType(position, PsiComment.class, false);
    return comment != null && !(comment instanceof PsiDocComment);
  }

  public static void addAllClasses(@NotNull CompletionParameters parameters,
                                   final boolean filterByScope,
                                   final @NotNull PrefixMatcher matcher,
                                   final @NotNull Consumer<? super LookupElement> consumer) {
    final PsiElement insertedElement = parameters.getPosition();
    final PsiFile psiFile = insertedElement.getContainingFile();

    JavaClassReference ref = JavaClassReferenceCompletionContributor.findJavaClassReference(psiFile, parameters.getOffset());
    if (ref != null && ref.getContext() instanceof PsiClass) {
      return;
    }

    if (JavaCompletionContributor.getAnnotationNameIfInside(insertedElement) != null) {
      MultiMap<String, PsiClass> annoMap = getAllAnnotationClasses(insertedElement, matcher);
      Processor<PsiClass> processor = new LimitedAccessibleClassPreprocessor(parameters, filterByScope, anno -> {
        JavaPsiClassReferenceElement item = AllClassesGetter.createLookupItem(anno, JAVA_CLASS_INSERT_HANDLER);
        item.addLookupStrings(getClassNameWithContainers(anno));
        consumer.consume(item);
        return true;
      });
      for (String name : matcher.sortMatching(annoMap.keySet())) {
        if (!ContainerUtil.process(annoMap.get(name), processor)) break;
      }
      return;
    }

    final boolean inPermitsList = JavaCompletionContributor.IN_PERMITS_LIST.accepts(insertedElement);
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
          consumer.consume(createClassLookupItem(psiClass, inJavaContext));
        }
        final PsiType defaultType = info.getDefaultType();
        if (!defaultType.equals(type)) {
          final PsiClass defClass = PsiUtil.resolveClassInType(defaultType);
          if (defClass != null && defClass.getName() != null) {
            consumer.consume(createClassLookupItem(defClass, true));
          }
        }
      }
    }

    final boolean pkgContext = JavaCompletionUtil.inSomePackage(insertedElement);
    final GlobalSearchScope scope = getReferenceScope(parameters, filterByScope, inPermitsList);

    JavaLookupElementHighlighter highlighter = JavaCompletionUtil.getHighlighterForPlace(insertedElement, parameters.getOriginalFile().getVirtualFile());
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
            JavaPsiClassReferenceElement element = AllClassesGetter.createLookupItem(psiClass, AllClassesGetter.TRY_SHORTENING);
            element.setLookupString(prefix + element.getLookupString());
            consumer.consume(element);
          }
          else {
            Condition<PsiClass> condition = eachClass ->
              filter.isAcceptable(eachClass, insertedElement) &&
              AllClassesGetter.isAcceptableInContext(insertedElement, eachClass, filterByScope, pkgContext) &&
              (!afterNew || !(eachClass.hasModifierProperty(PsiModifier.ABSTRACT) && eachClass.hasModifierProperty(PsiModifier.SEALED)));
            for (JavaPsiClassReferenceElement element : createClassLookupItems(psiClass, afterNew, JAVA_CLASS_INSERT_HANDLER, condition)) {
              element.setLookupString(prefix + element.getLookupString());

              JavaConstructorCallElement.wrap(element, insertedElement).forEach(
                e -> consumer.consume(highlighter.highlightIfNeeded(null, e, e.getObject())));
              if (patternContext) {
                JavaPatternCompletionUtil.addPatterns(consumer::consume, insertedElement, element.getObject(), false);
              }
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

  private static @NotNull GlobalSearchScope getReferenceScope(@NotNull CompletionParameters parameters,
                                                              boolean filterByScope,
                                                              boolean inPermitsList) {
    PsiElement insertedElement = parameters.getPosition();
    PsiFile psiFile = insertedElement.getContainingFile().getOriginalFile();
    Project project = insertedElement.getProject();
    if (!inPermitsList) {
      return filterByScope ? psiFile.getResolveScope() : GlobalSearchScope.allScope(project);
    }
    if (parameters.getInvocationCount() >= 2) {
      return GlobalSearchScope.allScope(project);
    }
    PsiJavaModule javaModule = JavaPsiModuleUtil.findDescriptorByElement(psiFile.getOriginalElement());
    JavaModuleScope moduleScope = javaModule == null ? null : JavaModuleScope.moduleScope(javaModule);
    if (moduleScope != null) {
      return moduleScope;
    }
    PsiDirectory dir = psiFile.getParent();
    PsiPackage psiPackage = dir == null ? null : JavaDirectoryService.getInstance().getPackage(dir);
    if (psiPackage != null) {
      return GlobalSearchScope.filesScope(project, ContainerUtil.map(psiPackage.getFiles(GlobalSearchScope.allScope(project)),
                                                                     PsiFile::getVirtualFile));
    }
    return GlobalSearchScope.fileScope(psiFile);
  }

  private static @NotNull MultiMap<String, PsiClass> getAllAnnotationClasses(PsiElement context, PrefixMatcher matcher) {
    MultiMap<String, PsiClass> map = new MultiMap<>();
    GlobalSearchScope scope = context.getResolveScope();
    Project project = context.getProject();
    PsiClass[] annotations = JavaPsiFacade.getInstance(project).findClasses(
      CommonClassNames.JAVA_LANG_ANNOTATION_ANNOTATION, GlobalSearchScope.allScope(project));
    for (PsiClass annotation : annotations) {
      DirectClassInheritorsSearch.search(annotation, scope, false).forEach(psiClass -> {
        if (!psiClass.isAnnotationType() || psiClass.getQualifiedName() == null) return true;

        String name = Objects.requireNonNull(psiClass.getName());
        if (!matcher.prefixMatches(name)) {
          name = getClassNameWithContainers(psiClass);
          if (!matcher.prefixMatches(name)) return true;
        }
        map.putValue(name, psiClass);
        return true;
      });
    }
    return map;
  }

  private static @NotNull String getClassNameWithContainers(@NotNull PsiClass psiClass) {
    StringBuilder name = new StringBuilder(Objects.requireNonNull(psiClass.getName()));
    for (PsiClass parent : JBIterable.generate(psiClass.getContainingClass(), PsiClass::getContainingClass)) {
      name.insert(0, parent.getName() + ".");
    }
    return name.toString();
  }

  public static JavaPsiClassReferenceElement createClassLookupItem(final PsiClass psiClass, final boolean inJavaContext) {
    return AllClassesGetter.createLookupItem(psiClass, inJavaContext ? JAVA_CLASS_INSERT_HANDLER
                                                                     : AllClassesGetter.TRY_SHORTENING);
  }

  public static List<JavaPsiClassReferenceElement> createClassLookupItems(final PsiClass psiClass,
                                                                          boolean withInners,
                                                                          InsertHandler<JavaPsiClassReferenceElement> insertHandler,
                                                                          Condition<? super PsiClass> condition) {
    String name = psiClass.getName();
    if (name == null) return Collections.emptyList();
    List<JavaPsiClassReferenceElement> result = new SmartList<>();
    if (condition.value(psiClass)) {
      result.add(AllClassesGetter.createLookupItem(psiClass, insertHandler));
    }
    if (withInners) {
      for (PsiClass inner : psiClass.getInnerClasses()) {
        if (inner.hasModifierProperty(PsiModifier.STATIC)) {
          for (JavaPsiClassReferenceElement lookupInner : createClassLookupItems(inner, true, insertHandler, condition)) {
            String forced = lookupInner.getForcedPresentableName();
            String qualifiedName = name + "." + (forced != null ? forced : inner.getName());
            lookupInner.setForcedPresentableName(qualifiedName);
            lookupInner.setLookupString(qualifiedName);
            result.add(lookupInner);
          }
        }
      }
    }
    return result;
  }



  @Override
  public @NlsContexts.HintText String handleEmptyLookup(final @NotNull CompletionParameters parameters, final Editor editor) {
    if (!(parameters.getOriginalFile() instanceof PsiJavaFile)) return null;

    if (shouldShowSecondSmartCompletionHint(parameters)) {
      return LangBundle.message("completion.no.suggestions") +
             "; " +
             StringUtil.decapitalize(
                 JavaBundle.message("completion.class.name.hint.2", KeymapUtil.getFirstKeyboardShortcutText(IdeActions.ACTION_CODE_COMPLETION)));
    }

    return null;
  }

  private static boolean shouldShowSecondSmartCompletionHint(final CompletionParameters parameters) {
    return parameters.getCompletionType() == CompletionType.BASIC &&
           parameters.getInvocationCount() == 2 &&
           parameters.getOriginalFile().getLanguage().isKindOf(JavaLanguage.INSTANCE);
  }
}
