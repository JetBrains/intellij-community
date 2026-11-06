// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.completion.modcommand;

import com.intellij.codeInsight.ExpectedTypesProvider;
import com.intellij.codeInsight.completion.JavaCompletionUtil;
import com.intellij.modcompletion.ModCompletionItem;
import com.intellij.modcompletion.ModCompletionItemFilter;
import com.intellij.modcompletion.ModCompletionItemProvider;
import com.intellij.modcompletion.ModCompletionResult;
import com.intellij.patterns.ElementPattern;
import com.intellij.psi.CommonClassNames;
import com.intellij.psi.GenericsUtil;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiIntersectionType;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiPrimitiveType;
import com.intellij.psi.PsiReturnStatement;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiTypeElement;
import com.intellij.psi.PsiTypeVisitor;
import com.intellij.psi.PsiTypes;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiTypesUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.ObjIntConsumer;
import java.util.function.Predicate;

import static com.intellij.patterns.PsiJavaPatterns.psiElement;

/**
 * Completes the return type of a method with the types of the values it returns and their supertypes:
 * {@code Char<caret> foo() { return "x"; }} gets {@code CharSequence}.
 */
@ApiStatus.Internal
@NotNullByDefault
public final class MethodReturnTypeItemProvider extends JavaModCompletionItemProvider {
  public static final ElementPattern<PsiElement> IN_METHOD_RETURN_TYPE =
    psiElement().withParents(PsiJavaCodeReferenceElement.class, PsiTypeElement.class, PsiMethod.class)
      .andNot(JavaCompletionUtil.AFTER_DOT);

  @Override
  public void provideItems(CompletionContext context, ModCompletionResult sink) {
    getProbableReturnTypes(context.getPosition()).forEach(
      (type, priority) -> sink.accept(PsiTypeCompletionItem.create(type).withPriority(priority)));
  }

  /**
   * @return the probable return types of the method whose return type holds {@code position}, each with its priority;
   * empty for any other position
   */
  private static Map<PsiType, Integer> getProbableReturnTypes(PsiElement position) {
    if (!IN_METHOD_RETURN_TYPE.accepts(position)) return Map.of();
    PsiMethod method = Objects.requireNonNull(PsiTreeUtil.getParentOfType(position, PsiMethod.class));
    return CachedValuesManager.getCachedValue(method, () -> {
      Map<PsiType, Integer> types = new LinkedHashMap<>();
      processProbableReturnTypes(Objects.requireNonNull(method.getReturnTypeElement()), types::put);
      return CachedValueProvider.Result.create(types, method);
    });
  }

  /**
   * Passes the types of the values that the method at {@code position} returns, and their supertypes, each with its priority.
   */
  public static void processProbableReturnTypes(PsiElement position, ObjIntConsumer<? super PsiType> consumer) {
    PsiMethod method = PsiTreeUtil.getParentOfType(position, PsiMethod.class);
    assert method != null;

    final PsiTypeVisitor<PsiType> eachProcessor = new PsiTypeVisitor<>() {
      private final Set<PsiType> myProcessed = new HashSet<>();

      @Override
      public PsiType visitType(PsiType type) {
        if (!(type instanceof PsiPrimitiveType) && PsiTypesUtil.isDenotableType(type, position) && myProcessed.add(type)) {
          int priority = type.equalsToText(CommonClassNames.JAVA_LANG_OBJECT) ? 1 : 1000 - myProcessed.size();
          consumer.accept(type, priority);
        }
        return type;
      }
    };
    for (PsiType type : getReturnTypeCandidates(method)) {
      eachProcessor.visitType(type);
      ExpectedTypesProvider.processAllSuperTypes(type, eachProcessor, position.getProject(), new HashSet<>(), new HashSet<>());
    }
  }

  private static PsiType[] getReturnTypeCandidates(PsiMethod method) {
    PsiType lub = null;
    boolean hasVoid = false;
    for (PsiReturnStatement statement : PsiUtil.findReturnStatements(method)) {
      PsiExpression value = statement.getReturnValue();
      if (value == null) {
        hasVoid = true;
      }
      else {
        PsiType type = value.getType();
        if (lub == null) {
          lub = type;
        }
        else if (type != null) {
          lub = GenericsUtil.getLeastUpperBound(lub, type, method.getManager());
        }
      }
    }
    if (hasVoid && lub == null) {
      lub = PsiTypes.voidType();
    }
    if (lub instanceof PsiIntersectionType intersectionType) {
      return intersectionType.getConjuncts();
    }
    return lub == null ? PsiType.EMPTY_ARRAY : new PsiType[]{lub};
  }

  /**
   * Removes the class items of {@link ReferenceItemProvider} and {@link NonImportedClassProvider} that duplicate
   * a probable return type without type arguments, as the legacy completion does.
   */
  static final class DuplicateFilter implements ModCompletionItemFilter {
    @Override
    public @Nullable Predicate<ModCompletionItem> createFilter(ModCompletionItemProvider provider, CompletionContext context) {
      if (!(provider instanceof ReferenceItemProvider || provider instanceof NonImportedClassProvider)) return null;
      Set<String> classNames = new HashSet<>();
      for (PsiType type : getProbableReturnTypes(context.getPosition()).keySet()) {
        if (type instanceof PsiClassType classType && classType.getParameterCount() == 0) {
          PsiClass psiClass = classType.resolve();
          if (psiClass != null) {
            ContainerUtil.addIfNotNull(classNames, psiClass.getQualifiedName());
          }
        }
      }
      if (classNames.isEmpty()) return null;
      return item -> !(item instanceof ClassReferenceCompletionItem classItem && classNames.contains(classItem.getQualifiedName()));
    }
  }
}
