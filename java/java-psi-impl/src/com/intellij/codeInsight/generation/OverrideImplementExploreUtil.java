package com.intellij.codeInsight.generation;

import com.intellij.codeInsight.MemberImplementorExplorer;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.NullableLazyValue;
import com.intellij.openapi.util.VolatileNullableLazyValue;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.infos.CandidateInfo;
import com.intellij.psi.util.*;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class OverrideImplementExploreUtil {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.generation.OverrideImplementExploreUtil");

  @NotNull
  public static Collection<CandidateInfo> getMethodsToOverrideImplement(PsiClass aClass, boolean toImplement) {
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
  public static Map<MethodSignature, CandidateInfo> getMapToOverrideImplement(PsiClass aClass, boolean toImplement) {
    return getMapToOverrideImplement(aClass, toImplement, true);
  }

  @NotNull
  public static Map<MethodSignature, CandidateInfo> getMapToOverrideImplement(PsiClass aClass, boolean toImplement, boolean skipImplemented) {
    Map<MethodSignature, PsiMethod> abstracts = new LinkedHashMap<>();
    Map<MethodSignature, PsiMethod> finals = new LinkedHashMap<>();
    Map<MethodSignature, PsiMethod> concretes = new LinkedHashMap<>();

    if (aClass.isAnnotationType() || aClass instanceof PsiTypeParameter) return Collections.emptyMap();

    PsiUtilCore.ensureValid(aClass);
    Collection<HierarchicalMethodSignature> allMethodSigs = aClass.getVisibleSignatures();
    PsiResolveHelper resolveHelper = JavaPsiFacade.getInstance(aClass.getProject()).getResolveHelper();
    for (HierarchicalMethodSignature signature : allMethodSigs) {
      PsiMethod method = signature.getMethod();
      PsiUtilCore.ensureValid(method);

      if (method.hasModifierProperty(PsiModifier.STATIC) || !resolveHelper.isAccessible(method, aClass, aClass)) continue;
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
      if (isDefaultMethod(aClass, method)) {
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

  private static boolean isDefaultMethod(PsiClass aClass, PsiMethod method) {
    return method.hasModifierProperty(PsiModifier.DEFAULT) &&
           PsiUtil.getLanguageLevel(aClass).isAtLeast(LanguageLevel.JDK_1_8);
  }

  private static void fillMap(HierarchicalMethodSignature signature, PsiMethod method, Map<MethodSignature, PsiMethod> map) {
    final PsiMethod other = map.get(signature);
    if (other == null || preferLeftForImplement(method, other)) {
      map.put(signature, method);
    }
  }

  public interface MemberImplementorExplorersProvider {
    MemberImplementorExplorer[] getExplorers();
  }

  private static final NullableLazyValue<MemberImplementorExplorersProvider> ourExplorersProvider = new VolatileNullableLazyValue<MemberImplementorExplorersProvider>() {
    @Override
    protected MemberImplementorExplorersProvider compute() {
      return ServiceManager.getService(MemberImplementorExplorersProvider.class);
    }
  };

  public static void collectMethodsToImplement(PsiClass aClass,
                                               Map<MethodSignature, PsiMethod> abstracts,
                                               Map<MethodSignature, PsiMethod> finals,
                                               Map<MethodSignature, PsiMethod> concretes,
                                               Map<MethodSignature, CandidateInfo> result) {
    for (Map.Entry<MethodSignature, PsiMethod> entry : abstracts.entrySet()) {
      MethodSignature signature = entry.getKey();
      PsiMethod abstractOne = entry.getValue();
      PsiMethod concrete = concretes.get(signature);
      if (concrete == null
          || PsiUtil.getAccessLevel(concrete.getModifierList()) < PsiUtil.getAccessLevel(abstractOne.getModifierList())
          || !abstractOne.getContainingClass().isInterface() && abstractOne.getContainingClass().isInheritor(concrete.getContainingClass(), true)
          || isDefaultMethod(aClass, abstractOne)) {
        if (finals.get(signature) == null) {
          PsiSubstitutor subst = correctSubstitutor(abstractOne, signature.getSubstitutor());
          CandidateInfo info = new CandidateInfo(abstractOne, subst);
          result.put(signature, info);
        }
      }
    }

    MemberImplementorExplorersProvider explorersProvider = ourExplorersProvider.getValue();
    if (explorersProvider != null) {
      for (final MemberImplementorExplorer implementor : explorersProvider.getExplorers()) {
        for (final PsiMethod method : implementor.getMethodsToImplement(aClass)) {
          MethodSignature signature = MethodSignatureUtil.createMethodSignature(method.getName(), method.getParameterList(),
                                                                                method.getTypeParameterList(), PsiSubstitutor.EMPTY,
                                                                                method.isConstructor());
          CandidateInfo info = new CandidateInfo(method, PsiSubstitutor.EMPTY);
          result.put(signature, info);
        }
      }
    }
  }

  private static boolean preferLeftForImplement(PsiMethod left, PsiMethod right) {
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

  public static PsiSubstitutor correctSubstitutor(PsiMethod method, PsiSubstitutor substitutor) {
    PsiClass hisClass = method.getContainingClass();
    PsiTypeParameter[] typeParameters = method.getTypeParameters();
    if (typeParameters.length > 0) {
      if (PsiUtil.isRawSubstitutor(hisClass, substitutor)) {
        substitutor = JavaPsiFacade.getInstance(method.getProject()).getElementFactory().createRawSubstitutor(substitutor, typeParameters);
      }
    }
    return substitutor;
  }
}
