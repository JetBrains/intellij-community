// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.psiutils;

import com.intellij.java.JavaBundle;
import com.intellij.java.codeserver.core.JavaPsiModuleUtil;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.DirectClassInheritorsSearch;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.ContainerUtil;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.PropertyKey;
import org.jetbrains.annotations.Unmodifiable;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Stream;

import static com.intellij.util.ObjectUtils.tryCast;

public final class SealedUtils {

  private SealedUtils() {}

  /**
   * Add a new subclass to existing class permits list. If permits list was absent, it's created with same-file inheritors,
   * in order to avoid new compilation errors.
   *
   * @param psiClass a sealed class to add a new permitted subclass
   * @param fqn fully-qualified name of the new permitted subclass
   */
  public static void addClassToPermitsList(@NotNull PsiClass psiClass, @NotNull String fqn) {
    Set<String> missingInheritors = new HashSet<>();
    missingInheritors.add(fqn);
    if (psiClass.getPermitsList() == null) {
      missingInheritors.addAll(findSameFileInheritors(psiClass));
    }
    fillPermitsList(psiClass, missingInheritors);
  }

  /**
   * Add specified classes to permits list; creating one if it hasn't existed before
   * @param psiClass class to add permits to
   * @param missingInheritors collection of fully-qualified names to add to permits list
   */
  public static void fillPermitsList(@NotNull PsiClass psiClass, @NotNull @Unmodifiable Collection<String> missingInheritors) {
    PsiReferenceList permitsList = psiClass.getPermitsList();
    PsiFileFactory factory = PsiFileFactory.getInstance(psiClass.getProject());
    if (permitsList == null) {
      PsiReferenceList implementsList = Objects.requireNonNull(psiClass.getImplementsList());
      String permitsClause = StreamEx.of(missingInheritors).sorted().joining(",", "permits ", "");
      psiClass.addAfter(createPermitsClause(factory, permitsClause), implementsList);
    }
    else {
      Stream<String> curClasses = Arrays.stream(permitsList.getReferenceElements()).map(PsiJavaCodeReferenceElement::getQualifiedName);
      String permitsClause = StreamEx.of(missingInheritors).append(curClasses).sorted().joining(",", "permits ", "");
      permitsList.replace(createPermitsClause(factory, permitsClause));
    }
  }

  private static @NotNull PsiReferenceList createPermitsClause(@NotNull PsiFileFactory factory, @NotNull String permitsClause) {
    PsiJavaFile javaFile = (PsiJavaFile)factory.createFileFromText(JavaLanguage.INSTANCE, "class __Dummy " + permitsClause + "{}");
    PsiClass newClass = javaFile.getClasses()[0];
    return Objects.requireNonNull(newClass.getPermitsList());
  }

  public static boolean hasSealedParent(@NotNull PsiClass psiClass) {
    return StreamEx.of(psiClass.getExtendsListTypes())
      .append(psiClass.getImplementsListTypes())
      .map(r -> r.resolve())
      .anyMatch(parent -> parent != null && parent.hasModifierProperty(PsiModifier.SEALED));
  }

  public static @Unmodifiable Collection<PsiClass> findSameFileInheritorsClasses(@NotNull PsiClass psiClass, PsiClass @NotNull ... classesToExclude) {
    return getClasses(psiClass, Function.identity(), classesToExclude);
  }

  public static @Unmodifiable Collection<String> findSameFileInheritors(@NotNull PsiClass psiClass, PsiClass @NotNull ... classesToExclude) {
    return getClasses(psiClass, PsiClass::getQualifiedName, classesToExclude);
  }

  private static @NotNull @Unmodifiable <T> Collection<T> getClasses(@NotNull PsiClass psiClass,
                                                                     Function<? super PsiClass, T> mapper,
                                                                     PsiClass @NotNull ... classesToExclude) {
    GlobalSearchScope fileScope = GlobalSearchScope.fileScope(psiClass.getContainingFile().getOriginalFile());
    return DirectClassInheritorsSearch.search(psiClass, fileScope)
      .filtering(inheritor -> !ArrayUtil.contains(inheritor, classesToExclude))
      //local classes and anonymous classes must not extend sealed
      .filtering(cls -> !(cls instanceof PsiAnonymousClass || PsiUtil.isLocalClass(cls)))
      .mapping(mapper)
      .findAll();
  }

  /**
   * Removes exChild class reference from permits list of a parent.
   * If this was the last element in permits list then sealed modifier of parent class is removed.
   */
  public static void removeFromPermitsList(@NotNull PsiClass parent, @NotNull PsiClass exChild) {
    PsiReferenceList permitsList = parent.getPermitsList();
    if (permitsList == null) return;
    PsiJavaCodeReferenceElement[] childRefs = permitsList.getReferenceElements();
    PsiJavaCodeReferenceElement exChildRef = ContainerUtil.find(childRefs, ref -> ref.resolve() == exChild);
    if (exChildRef == null) return;
    exChildRef.delete();
    if (childRefs.length != 1) return;
    PsiModifierList modifiers = parent.getModifierList();
    if (modifiers == null) return;
    modifiers.setModifierProperty(PsiModifier.SEALED, false);
  }

  public static @Nullable @PropertyKey(resourceBundle = JavaBundle.BUNDLE) String checkInheritor(
    @NotNull PsiJavaFile parentFile, @Nullable PsiJavaModule module, @NotNull PsiClass inheritor) {
    @PropertyKey(resourceBundle = JavaBundle.BUNDLE)
    String result = null;
    if (PsiUtil.isLocalOrAnonymousClass(inheritor)) {
      result = "intention.error.make.sealed.class.has.anonymous.or.local.inheritors";
    }
    else if (module == null) {
      PsiJavaFile file = tryCast(inheritor.getContainingFile(), PsiJavaFile.class);
      if (file == null || file.getOriginalFile() instanceof PsiCompiledElement) {
        result = "intention.error.make.sealed.class.inheritors.not.in.java.file";
      }
      else if (!parentFile.getPackageName().equals(file.getPackageName())) {
        result = "intention.error.make.sealed.class.different.packages";
      }
    }
    else if (JavaPsiModuleUtil.findDescriptorByElement(inheritor) != module) {
      result = "intention.error.make.sealed.class.different.modules";
    }
    return result;
  }
}
