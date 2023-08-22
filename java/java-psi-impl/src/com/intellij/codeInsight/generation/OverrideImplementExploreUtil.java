// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.generation;

import com.intellij.codeInsight.MemberImplementorExplorer;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.NullableLazyValue;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.infos.CandidateInfo;
import com.intellij.psi.util.*;
import org.jetbrains.annotations.NotNull;

import java.util.*;

import static com.intellij.openapi.util.NullableLazyValue.volatileLazyNullable;

public class OverrideImplementExploreUtil {
  @NotNull
  public static Collection<CandidateInfo> getMethodsToOverrideImplement(@NotNull PsiClass aClass, boolean toImplement) {
    return getMapToOverrideImplement(aClass, toImplement).values();
  }

  @NotNull
  public static Collection<MethodSignature> getMethodSignaturesToImplement(@NotNull PsiClass aClass) {
    return getMapToOverrideImplement(aClass, true).keySet();
  }

  @NotNull
  public static Collection<MethodSignature> getMethodSignaturesToOverride(@NotNull PsiClass aClass) {
    if (aClass.isAnnotationType()) return Collections.emptySet();
    return getMapToOverrideImplement(aClass, false).keySet();
  }

  @NotNull
  public static Map<MethodSignature, CandidateInfo> getMapToOverrideImplement(@NotNull PsiClass aClass, boolean toImplement) {
    return getMapToOverrideImplement(aClass, toImplement, true);
  }

  @NotNull
  public static Map<MethodSignature, CandidateInfo> getMapToOverrideImplement(@NotNull PsiClass aClass, boolean toImplement, boolean skipImplemented) {
    if (aClass.isAnnotationType() || aClass instanceof PsiTypeParameter) return Collections.emptyMap();

    PsiUtilCore.ensureValid(aClass);
    Collection<HierarchicalMethodSignature> allMethodSigs = aClass.getVisibleSignatures();
    PsiResolveHelper resolveHelper = JavaPsiFacade.getInstance(aClass.getProject()).getResolveHelper();
    Map<MethodSignature, PsiMethod> abstracts = new LinkedHashMap<>();
    Map<MethodSignature, PsiMethod> finals = new LinkedHashMap<>();
    Map<MethodSignature, PsiMethod> concretes = new LinkedHashMap<>();
    for (HierarchicalMethodSignature signature : allMethodSigs) {
      PsiMethod method = signature.getMethod();
      PsiUtilCore.ensureValid(method);

      if (method.hasModifierProperty(PsiModifier.STATIC) || !resolveHelper.isAccessible(method, aClass, aClass)) continue;
      //broken super
      if (method.isConstructor() && method.hasModifierProperty(PsiModifier.ABSTRACT)) continue;
      for (HierarchicalMethodSignature superMethodSignature : signature.getSuperSignatures()) {
        final PsiMethod superMethod = superMethodSignature.getMethod();
        if (PsiUtil.getAccessLevel(superMethod.getModifierList()) > PsiUtil.getAccessLevel(method.getModifierList())) {
          method = superMethod;
          break;
        }
      }

      PsiClass hisClass = method.getContainingClass();
      if (hisClass == null) continue;
      // filter non-immediate super constructors
      if (method.isConstructor() && (!aClass.isInheritor(hisClass, false) || aClass instanceof PsiAnonymousClass || aClass.isEnum())) {
        continue;
      }
      // filter already implemented
      if (skipImplemented && MethodSignatureUtil.findMethodBySignature(aClass, signature, false) != null) {
        continue;
      }

      if (method.hasModifierProperty(PsiModifier.FINAL)) {
        finals.put(signature, method);
        continue;
      }

      Map<MethodSignature, PsiMethod> map = hisClass.isInterface() || method.hasModifierProperty(PsiModifier.ABSTRACT) ? abstracts : concretes;
      fillMap(signature, method, map);
      if (shouldAppearInOverrideList(aClass, method)) {
        fillMap(signature, method, concretes);
      }
    }

    final Map<MethodSignature, CandidateInfo> result = new TreeMap<>(new MethodSignatureComparator());
    if (toImplement || aClass.isInterface()) {
      collectMethodsToImplement(aClass, abstracts, finals, concretes, result);
    }
    else {
      for (Map.Entry<MethodSignature, PsiMethod> entry : concretes.entrySet()) {
        MethodSignature signature = entry.getKey();
        PsiMethod concrete = entry.getValue();
        if (finals.get(signature) == null) {
          PsiMethod abstractOne = abstracts.get(signature);
          if (abstractOne == null || !abstractOne.getContainingClass().isInheritor(concrete.getContainingClass(), true) ||
              CommonClassNames.JAVA_LANG_OBJECT.equals(concrete.getContainingClass().getQualifiedName())) {
            PsiSubstitutor subst = correctSubstitutor(concrete, signature.getSubstitutor());
            CandidateInfo info = new CandidateInfo(concrete, subst);
            result.put(signature, info);
          }
        }
      }
    }

    return result;
  }

  private static boolean isDefaultMethod(@NotNull PsiClass aClass, @NotNull PsiMethod method) {
    return method.hasModifierProperty(PsiModifier.DEFAULT) &&
           PsiUtil.getLanguageLevel(aClass).isAtLeast(LanguageLevel.JDK_1_8);
  }

  private static void fillMap(@NotNull HierarchicalMethodSignature signature, @NotNull PsiMethod method, @NotNull Map<MethodSignature, PsiMethod> map) {
    final PsiMethod other = map.get(signature);
    if (other == null || preferLeftForImplement(method, other)) {
      map.put(signature, method);
    }
  }

  @FunctionalInterface
  public interface MemberImplementorExplorersProvider {
    @NotNull
    List<? extends MemberImplementorExplorer> getExplorers();
  }

  private static final NullableLazyValue<MemberImplementorExplorersProvider> ourExplorerProvider =
    volatileLazyNullable(() -> ApplicationManager.getApplication().getService(MemberImplementorExplorersProvider.class));

  private static void collectMethodsToImplement(@NotNull PsiClass aClass,
                                                @NotNull Map<MethodSignature, PsiMethod> abstracts,
                                                @NotNull Map<MethodSignature, PsiMethod> finals,
                                                @NotNull Map<MethodSignature, PsiMethod> concretes,
                                                @NotNull Map<MethodSignature, CandidateInfo> result) {
    for (Map.Entry<MethodSignature, PsiMethod> entry : abstracts.entrySet()) {
      MethodSignature signature = entry.getKey();
      PsiMethod abstractOne = entry.getValue();
      PsiMethod concrete = concretes.get(signature);
      if (concrete == null
          || PsiUtil.getAccessLevel(concrete.getModifierList()) < PsiUtil.getAccessLevel(abstractOne.getModifierList())
          || !abstractOne.getContainingClass().isInterface() && abstractOne.getContainingClass().isInheritor(concrete.getContainingClass(), true)
          || shouldAppearInOverrideList(aClass, abstractOne)) {
        if (finals.get(signature) == null) {
          PsiSubstitutor subst = correctSubstitutor(abstractOne, signature.getSubstitutor());
          CandidateInfo info = new CandidateInfo(abstractOne, subst);
          result.put(signature, info);
        }
      }
    }

    MemberImplementorExplorersProvider explorersProvider = ourExplorerProvider.getValue();
    if (explorersProvider != null) {
      for (final MemberImplementorExplorer implementor : explorersProvider.getExplorers()) {
        for (final PsiMethod method : implementor.getMethodsToImplement(aClass)) {
          MethodSignature signature = MethodSignatureBackedByPsiMethod.create(method, PsiSubstitutor.EMPTY);
          CandidateInfo info = new CandidateInfo(method, PsiSubstitutor.EMPTY);
          result.put(signature, info);
        }
      }
    }
  }

  private static boolean shouldAppearInOverrideList(@NotNull PsiClass aClass, PsiMethod abstractOne) {
    return isDefaultMethod(aClass, abstractOne) ||
           // abstract methods from java.lang.Record (equals/hashCode/toString) are implicitly implemented in subclasses
           // so it could be reasonable to expect them in 'override' method dialog
           belongsToRecord(abstractOne);
  }

  static boolean belongsToRecord(@NotNull PsiMethod method) {
    return CommonClassNames.JAVA_LANG_RECORD.equals(Objects.requireNonNull(method.getContainingClass()).getQualifiedName());
  }

  private static boolean preferLeftForImplement(@NotNull PsiMethod left, @NotNull PsiMethod right) {
    if (PsiUtil.getAccessLevel(left.getModifierList()) > PsiUtil.getAccessLevel(right.getModifierList())) return true;
    if (!left.getContainingClass().isInterface()) return true;
    if (!right.getContainingClass().isInterface()) return false;
    // implement annotated method
    PsiAnnotation[] leftAnnotations = left.getModifierList().getAnnotations();
    PsiAnnotation[] rightAnnotations = right.getModifierList().getAnnotations();
    return leftAnnotations.length > rightAnnotations.length;
  }

  public static class MethodSignatureComparator implements Comparator<MethodSignature> {
    // signatures should appear in the order of declaration
    @Override
    public int compare(MethodSignature o1, MethodSignature o2) {
      if (o1 instanceof MethodSignatureBackedByPsiMethod && o2 instanceof MethodSignatureBackedByPsiMethod) {
        PsiMethod m1 = ((MethodSignatureBackedByPsiMethod)o1).getMethod();
        PsiMethod m2 = ((MethodSignatureBackedByPsiMethod)o2).getMethod();
        PsiClass c1 = m1.getContainingClass();
        PsiClass c2 = m2.getContainingClass();
        if (c1 != null && c2 != null) {
          if (c1 == c2) {
            final List<PsiMethod> methods = Arrays.asList(c1.getMethods());
            return methods.indexOf(m1) - methods.indexOf(m2);
          }

          if (c1.isInheritor(c2, true)) return -1;
          if (c2.isInheritor(c1, true)) return 1;

          return StringUtil.notNullize(c1.getQualifiedName()).compareTo(StringUtil.notNullize(c2.getQualifiedName()));
        }
        return m1.getTextOffset() - m2.getTextOffset();
      }
      return 0;
    }
  }

  @NotNull
  public static PsiSubstitutor correctSubstitutor(@NotNull PsiMethod method, @NotNull PsiSubstitutor substitutor) {
    PsiClass hisClass = method.getContainingClass();
    PsiTypeParameter[] typeParameters = method.getTypeParameters();
    if (typeParameters.length > 0) {
      if (PsiUtil.isRawSubstitutor(hisClass, substitutor)) {
        substitutor = JavaPsiFacade.getElementFactory(method.getProject()).createRawSubstitutor(substitutor, typeParameters);
      }
    }
    return substitutor;
  }
}
