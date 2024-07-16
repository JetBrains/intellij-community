// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.filters.getters;

import com.intellij.codeInsight.CodeInsightUtil;
import com.intellij.codeInsight.completion.CompletionUtil;
import com.intellij.codeInsight.completion.JavaCompletionUtil;
import com.intellij.codeInsight.completion.PrefixMatcher;
import com.intellij.codeInsight.completion.StaticMemberProcessor;
import com.intellij.codeInsight.lookup.AutoCompletionPolicy;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Conditions;
import com.intellij.openapi.util.Key;
import com.intellij.pom.java.JavaFeature;
import com.intellij.psi.*;
import com.intellij.psi.filters.TrueFilter;
import com.intellij.psi.impl.java.stubs.index.JavaStaticMemberTypeIndex;
import com.intellij.psi.scope.processor.FilterScopeProcessor;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.Consumer;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * @author ik
 */
public abstract class MembersGetter {
  private static final Map<String, List<String>> COMMON_INHERITORS =
    Map.of("java.util.Collection", List.of("java.util.List", "java.util.Set"),
           "java.util.List", List.of("java.util.ArrayList"),
           "java.util.Set", List.of("java.util.HashSet", "java.util.TreeSet"),
           "java.util.Map", List.of("java.util.HashMap", "java.util.TreeMap"));

  public static final Key<Boolean> EXPECTED_TYPE_MEMBER = Key.create("EXPECTED_TYPE_MEMBER");
  private final Set<PsiMember> myImportedStatically = new HashSet<>();
  private final List<PsiClass> myPlaceClasses = new ArrayList<>();
  private final List<PsiMethod> myPlaceMethods = new ArrayList<>();
  protected final PsiElement myPlace;

  protected MembersGetter(StaticMemberProcessor processor, final @NotNull PsiElement place) {
    myPlace = place;
    processor.processMembersOfRegisteredClasses(Conditions.alwaysTrue(), (member, psiClass) -> myImportedStatically.add(member));

    PsiClass current = PsiTreeUtil.getContextOfType(place, PsiClass.class);
    while (current != null) {
      current = CompletionUtil.getOriginalOrSelf(current);
      myPlaceClasses.add(current);
      current = PsiTreeUtil.getContextOfType(current, PsiClass.class);
    }

    PsiMethod eachMethod = PsiTreeUtil.getContextOfType(place, PsiMethod.class);
    while (eachMethod != null) {
      eachMethod = CompletionUtil.getOriginalOrSelf(eachMethod);
      myPlaceMethods.add(eachMethod);
      eachMethod = PsiTreeUtil.getContextOfType(eachMethod, PsiMethod.class);
    }

  }

  private boolean mayProcessMembers(@Nullable PsiClass psiClass) {
    if (psiClass == null) {
      return false;
    }

    for (PsiClass placeClass : myPlaceClasses) {
      if (InheritanceUtil.isInheritorOrSelf(placeClass, psiClass, true)) {
        return false;
      }
    }
    return true;
  }

  public void processMembers(final Consumer<? super LookupElement> results, final @Nullable PsiClass where,
                             final boolean acceptMethods, final boolean searchInheritors) {
    if (where == null || isPrimitiveClass(where)) return;

    String qualifiedName = where.getQualifiedName();
    final boolean searchFactoryMethods = searchInheritors &&
                                         !CommonClassNames.JAVA_LANG_OBJECT.equals(qualifiedName) &&
                                         !isPrimitiveClass(where);

    final Project project = myPlace.getProject();
    final GlobalSearchScope scope = myPlace.getResolveScope();

    final PsiClassType baseType = JavaPsiFacade.getElementFactory(project).createType(where);
    Consumer<PsiType> consumer = psiType -> {
      PsiClass psiClass = PsiUtil.resolveClassInType(psiType);
      if (psiClass == null) {
        return;
      }
      psiClass = CompletionUtil.getOriginalOrSelf(psiClass);
      if (mayProcessMembers(psiClass)) {
        final FilterScopeProcessor<PsiElement> declProcessor = new FilterScopeProcessor<>(TrueFilter.INSTANCE);
        psiClass.processDeclarations(declProcessor, ResolveState.initial(), null, myPlace);
        doProcessMembers(acceptMethods, results, psiType == baseType, psiClass, declProcessor.getResults());

        String name = psiClass.getName();
        if (name != null && searchFactoryMethods) {
          Collection<PsiMember> factoryMethods = JavaStaticMemberTypeIndex.getInstance().getStaticMembers(name, project, scope);
          doProcessMembers(acceptMethods, results, false, psiClass, factoryMethods);
        }
      }
    };
    consumer.consume(baseType);
    if (searchInheritors && !CommonClassNames.JAVA_LANG_OBJECT.equals(qualifiedName)) {
      CodeInsightUtil.processSubTypes(baseType, myPlace, true, PrefixMatcher.ALWAYS_TRUE, consumer);
    } else if (qualifiedName != null) {
      // If we don't search inheritors, we still process some known very common ones
      StreamEx.ofTree(qualifiedName, cls -> StreamEx.of(COMMON_INHERITORS.getOrDefault(cls, List.of()))).skip(1)
        .map(className -> JavaPsiFacade.getElementFactory(project).createTypeByFQClassName(className, myPlace.getResolveScope()))
        .forEach(consumer::consume);
    }
  }

  private static boolean isPrimitiveClass(PsiClass where) {
    String qname = where.getQualifiedName();
    if (qname == null || !qname.startsWith("java.lang.")) return false;
    return CommonClassNames.JAVA_LANG_STRING.equals(qname) || InheritanceUtil.isInheritor(where, CommonClassNames.JAVA_LANG_NUMBER);
  }

  private void doProcessMembers(boolean acceptMethods,
                                Consumer<? super LookupElement> results,
                                boolean isExpectedTypeMember,
                                PsiClass origClass,
                                Collection<? extends PsiElement> declarations) {
    for (final PsiElement result : declarations) {
      if (result instanceof PsiMember member && !(result instanceof PsiClass)) {
        if (member instanceof PsiMethod method && method.isConstructor()) {
          PsiClass aClass = member.getContainingClass();
          if (aClass == null || aClass.hasModifierProperty(PsiModifier.ABSTRACT)) continue;
          PsiClass enclosingClass = aClass.hasModifierProperty(PsiModifier.STATIC) ? null : aClass.getContainingClass();
          if (enclosingClass != null &&
              !InheritanceUtil.hasEnclosingInstanceInScope(enclosingClass, myPlace, true, false)) {
            continue;
          }
          // For parameterized class constructors, we add a diamond. Do not suggest constructors for parameterized classes
          // in Java 6 or older when diamond was not supported
          if (aClass.getTypeParameters().length > 0 && !PsiUtil.isAvailable(JavaFeature.DIAMOND_TYPES, myPlace)) continue;
          // Constructor type parameters aren't supported yet
          if (method.getTypeParameters().length > 0) continue;
        }
        else if (!(member.hasModifierProperty(PsiModifier.STATIC))) {
          continue;
        }
        if (!(result instanceof PsiField) && !(result instanceof PsiMethod)) continue;
        if (result instanceof PsiField && !member.hasModifierProperty(PsiModifier.FINAL)) continue;
        if (result instanceof PsiMethod && (!acceptMethods || myPlaceMethods.contains(result))) continue;
        if (JavaCompletionUtil.isInExcludedPackage(member, false) || myImportedStatically.contains(member)) continue;

        if (!JavaPsiFacade.getInstance(myPlace.getProject()).getResolveHelper().isAccessible(member, myPlace, null)) {
          continue;
        }

        final LookupElement item = result instanceof PsiMethod method ? createMethodElement(method, origClass) :
                                   createFieldElement((PsiField)result, origClass);
        if (item != null) {
          item.putUserData(EXPECTED_TYPE_MEMBER, isExpectedTypeMember);
          results.consume(AutoCompletionPolicy.NEVER_AUTOCOMPLETE.applyPolicy(item));
        }
      }
    }
  }

  protected abstract @Nullable LookupElement createFieldElement(@NotNull PsiField field, @NotNull PsiClass origClass);

  protected abstract @Nullable LookupElement createMethodElement(@NotNull PsiMethod method, @NotNull PsiClass origClass);
}
