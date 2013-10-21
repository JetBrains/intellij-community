/*
 * Copyright 2000-2013 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.codeInsight;

import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.ArrayUtil;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.annotation.Annotation;
import java.lang.reflect.Proxy;
import java.util.*;

/**
 * @author max
 */
public class AnnotationUtil {
  /**
   * The full qualified name of the standard Nullable annotation.
   */
  public static final String NULLABLE = "org.jetbrains.annotations.Nullable";

  /**
   * The full qualified name of the standard NotNull annotation.
   */
  public static final String NOT_NULL = "org.jetbrains.annotations.NotNull";

  @NonNls public static final String NOT_NULL_SIMPLE_NAME = "NotNull";

  @NonNls public static final String NULLABLE_SIMPLE_NAME = "Nullable";

  /**
   * The full qualified name of the standard NonNls annotation.
   *
   * @since 5.0.1
   */
  public static final String NON_NLS = "org.jetbrains.annotations.NonNls";
  public static final String NLS = "org.jetbrains.annotations.Nls";
  public static final String PROPERTY_KEY = "org.jetbrains.annotations.PropertyKey";
  @NonNls public static final String PROPERTY_KEY_RESOURCE_BUNDLE_PARAMETER = "resourceBundle";

  @NonNls public static final String NON_NLS_SIMPLE_NAME = "NonNls";
  @NonNls public static final String PROPERTY_KEY_SIMPLE_NAME = "PropertyKey";

  public static final String TEST_ONLY = "org.jetbrains.annotations.TestOnly";
  @NonNls public static final String TEST_ONLY_SIMPLE_NAME = "TestOnly";

  public static final String LANGUAGE = "org.intellij.lang.annotations.Language";

  public static final Set<String> ALL_ANNOTATIONS;

  @NonNls private static final String[] SIMPLE_NAMES =
    {NOT_NULL_SIMPLE_NAME, NULLABLE_SIMPLE_NAME, NON_NLS_SIMPLE_NAME, PROPERTY_KEY_SIMPLE_NAME, TEST_ONLY_SIMPLE_NAME,
      "Language", "Identifier", "Pattern", "PrintFormat", "RegExp", "Subst"};

  static {
    ALL_ANNOTATIONS = new HashSet<String>(2);
    ALL_ANNOTATIONS.add(NULLABLE);
    ALL_ANNOTATIONS.add(NOT_NULL);
  }

  @Nullable
  public static PsiAnnotation findAnnotation(PsiModifierListOwner listOwner, @NotNull String... annotationNames) {
    return findAnnotation(listOwner, false, annotationNames);
  }

  @Nullable
  public static PsiAnnotation findAnnotation(PsiModifierListOwner listOwner, final boolean skipExternal, @NotNull String... annotationNames) {
    if (annotationNames.length == 0) return null;
    Set<String> set = annotationNames.length == 1 ? Collections.singleton(annotationNames[0]) : new HashSet<String>(Arrays.asList(annotationNames));
    return findAnnotation(listOwner, set, skipExternal);
  }

  @Nullable
  public static PsiAnnotation findAnnotation(@Nullable PsiModifierListOwner listOwner, @NotNull Set<String> annotationNames) {
    return findAnnotation(listOwner, (Collection<String>)annotationNames);
  }

  @Nullable
  public static PsiAnnotation findAnnotation(@Nullable PsiModifierListOwner listOwner, Collection<String> annotationNames) {
    return findAnnotation(listOwner, annotationNames, false);
  }

  @Nullable
  public static PsiAnnotation findAnnotation(@Nullable PsiModifierListOwner listOwner, @NotNull Collection<String> annotationNames,
                                             final boolean skipExternal) {
    if (listOwner == null) return null;
    final PsiModifierList list = listOwner.getModifierList();
    if (list == null) return null;
    final PsiAnnotation[] allAnnotations = list.getAnnotations();
    for (PsiAnnotation annotation : allAnnotations) {
      String qualifiedName = annotation.getQualifiedName();
      if (annotationNames.contains(qualifiedName)) {
        return annotation;
      }
    }
    if (!skipExternal) {
      final ExternalAnnotationsManager annotationsManager = ExternalAnnotationsManager.getInstance(listOwner.getProject());
      for (String annotationName : annotationNames) {
        final PsiAnnotation annotation = annotationsManager.findExternalAnnotation(listOwner, annotationName);
        if (annotation != null) {
          return annotation;
        }
      }
    }
    return null;
  }

  @NotNull
  public static PsiAnnotation[] findAnnotations(final PsiModifierListOwner modifierListOwner, @NotNull Collection<String> annotationNames) {
    if (modifierListOwner == null) return PsiAnnotation.EMPTY_ARRAY;
    final PsiModifierList modifierList = modifierListOwner.getModifierList();
    if (modifierList == null) return PsiAnnotation.EMPTY_ARRAY;
    final PsiAnnotation[] annotations = modifierList.getAnnotations();
    ArrayList<PsiAnnotation> result = null;
    for (final PsiAnnotation psiAnnotation : annotations) {
      if (annotationNames.contains(psiAnnotation.getQualifiedName())) {
        if (result == null) result = new ArrayList<PsiAnnotation>();
        result.add(psiAnnotation);
      }
    }
    return result == null ? PsiAnnotation.EMPTY_ARRAY : result.toArray(new PsiAnnotation[result.size()]);
  }

  @Nullable
  public static PsiAnnotation findAnnotationInHierarchy(PsiModifierListOwner listOwner, @NotNull Set<String> annotationNames) {
    PsiAnnotation directAnnotation = findAnnotation(listOwner, annotationNames);
    if (directAnnotation != null) return directAnnotation;
    if (listOwner instanceof PsiMethod) {
      PsiMethod method = (PsiMethod)listOwner;
      PsiClass aClass = method.getContainingClass();
      if (aClass == null) return null;
      HierarchicalMethodSignature methodSignature = method.getHierarchicalMethodSignature();
      return findAnnotationInHierarchy(methodSignature, annotationNames, method, null,
                                       JavaPsiFacade.getInstance(method.getProject()).getResolveHelper());
    }
    if (listOwner instanceof PsiClass) {
      return findAnnotationInHierarchy((PsiClass)listOwner, annotationNames, null);
    }
    if (listOwner instanceof PsiParameter) {
      PsiParameter parameter = (PsiParameter)listOwner;
      return doFindAnnotationInHierarchy(parameter, annotationNames, null);
    }
    return null;
  }

  @Nullable
  private static PsiAnnotation doFindAnnotationInHierarchy(PsiParameter parameter,
                                                           Set<String> annotationNames,
                                                           @Nullable Set<PsiModifierListOwner> visited) {
    PsiAnnotation annotation = findAnnotation(parameter, annotationNames);
    if (annotation != null) return annotation;
    PsiElement scope = parameter.getDeclarationScope();
    if (!(scope instanceof PsiMethod)) {
      return null;
    }
    PsiMethod method = (PsiMethod)scope;
    PsiClass aClass = method.getContainingClass();
    PsiElement parent = parameter.getParent();
    if (aClass == null || !(parent instanceof PsiParameterList)) {
      return null;
    }
    int index = ((PsiParameterList)parent).getParameterIndex(parameter);
    HierarchicalMethodSignature methodSignature = method.getHierarchicalMethodSignature();

    final List<HierarchicalMethodSignature> superSignatures = methodSignature.getSuperSignatures();
    PsiResolveHelper resolveHelper = PsiResolveHelper.SERVICE.getInstance(aClass.getProject());
    for (final HierarchicalMethodSignature superSignature : superSignatures) {
      final PsiMethod superMethod = superSignature.getMethod();
      if (visited == null) visited = new THashSet<PsiModifierListOwner>();
      if (!visited.add(superMethod)) continue;
      if (!resolveHelper.isAccessible(superMethod, parameter, null)) continue;
      PsiParameter[] superParameters = superMethod.getParameterList().getParameters();
      if (index < superParameters.length) {
        PsiAnnotation insuper = doFindAnnotationInHierarchy(superParameters[index], annotationNames, visited);
        if (insuper != null) return insuper;
      }
    }
    return null;
  }

  @Nullable
  private static PsiAnnotation findAnnotationInHierarchy(@NotNull final PsiClass psiClass, final Set<String> annotationNames, @Nullable Set<PsiClass> processed) {
    final PsiClass[] superClasses = psiClass.getSupers();
    for (final PsiClass superClass : superClasses) {
      if (processed == null) processed = new THashSet<PsiClass>();
      if (!processed.add(superClass)) return null;
      final PsiAnnotation annotation = findAnnotation(superClass, annotationNames);
      if (annotation != null) return annotation;
      final PsiAnnotation annotationInHierarchy = findAnnotationInHierarchy(superClass, annotationNames, processed);
      if (annotationInHierarchy != null) return annotationInHierarchy;
    }
    return null;
  }

  @Nullable
  private static PsiAnnotation findAnnotationInHierarchy(@NotNull HierarchicalMethodSignature signature,
                                                         @NotNull Set<String> annotationNames,
                                                         @NotNull PsiElement place,
                                                         @Nullable Set<PsiMethod> processed,
                                                         @NotNull PsiResolveHelper resolveHelper) {
    final List<HierarchicalMethodSignature> superSignatures = signature.getSuperSignatures();
    for (final HierarchicalMethodSignature superSignature : superSignatures) {
      final PsiMethod superMethod = superSignature.getMethod();
      if (processed == null) processed = new THashSet<PsiMethod>();
      if (!processed.add(superMethod)) continue;
      if (!resolveHelper.isAccessible(superMethod, place, null)) continue;
      PsiAnnotation direct = findAnnotation(superMethod, annotationNames);
      if (direct != null) return direct;
      PsiAnnotation superResult = findAnnotationInHierarchy(superSignature, annotationNames, place, processed, resolveHelper);
      if (superResult != null) return superResult;
    }

    return null;
  }

  public static boolean isAnnotated(@NotNull PsiModifierListOwner listOwner, Collection<String> annotations) {
    return isAnnotated(listOwner, annotations, false);
  }

  public static boolean isAnnotated(@NotNull PsiModifierListOwner listOwner,
                                    Collection<String> annotations,
                                    final boolean checkHierarchy) {
    return isAnnotated(listOwner, annotations, checkHierarchy, true);
  }

  public static boolean isAnnotated(@NotNull PsiModifierListOwner listOwner,
                                    Collection<String> annotations,
                                    final boolean checkHierarchy,
                                    boolean skipExternal) {
    for (String annotation : annotations) {
      if (isAnnotated(listOwner, annotation, checkHierarchy, skipExternal)) return true;
    }
    return false;
  }

  public static boolean isAnnotated(@NotNull PsiModifierListOwner listOwner, @NonNls String annotationFQN, boolean checkHierarchy) {
    return isAnnotated(listOwner, annotationFQN, checkHierarchy, true, null);
  }

  public static boolean isAnnotated(@NotNull PsiModifierListOwner listOwner, @NonNls String annotationFQN, boolean checkHierarchy,
                                    boolean skipExternal) {
    return isAnnotated(listOwner, annotationFQN, checkHierarchy, skipExternal, null);
  }

  private static boolean isAnnotated(@NotNull PsiModifierListOwner listOwner,
                                     @NonNls String annotationFQN,
                                     boolean checkHierarchy, final boolean skipExternal, @Nullable Set<PsiMember> processed) {
    if (!listOwner.isValid()) return false;
    final PsiModifierList modifierList = listOwner.getModifierList();
    if (modifierList == null) return false;
    PsiAnnotation annotation = modifierList.findAnnotation(annotationFQN);
    if (annotation != null) return true;
    if (!skipExternal && ExternalAnnotationsManager.getInstance(listOwner.getProject()).findExternalAnnotation(listOwner, annotationFQN) != null) {
      return true;
    }
    if (checkHierarchy) {
      if (listOwner instanceof PsiMethod) {
        PsiMethod method = (PsiMethod)listOwner;
        if (processed == null) processed = new THashSet<PsiMember>();
        if (!processed.add(method)) return false;
        final PsiMethod[] superMethods = method.findSuperMethods();
        for (PsiMethod superMethod : superMethods) {
          if (isAnnotated(superMethod, annotationFQN, checkHierarchy, skipExternal, processed)) return true;
        }
      } else if (listOwner instanceof PsiClass) {
        final PsiClass clazz = (PsiClass)listOwner;
        if (processed == null) processed = new THashSet<PsiMember>();
        if (!processed.add(clazz)) return false;
        final PsiClass[] superClasses = clazz.getSupers();
        for (PsiClass superClass : superClasses) {
          if (isAnnotated(superClass, annotationFQN, checkHierarchy, skipExternal, processed)) return true;
        }
      }
    }
    return false;
  }

  public static boolean isAnnotatingApplicable(@NotNull PsiElement elt) {
    return isAnnotatingApplicable(elt, NullableNotNullManager.getInstance(elt.getProject()).getDefaultNullable());
  }

  public static boolean isAnnotatingApplicable(@NotNull PsiElement elt, final String annotationFQN) {
    final Project project = elt.getProject();
    return PsiUtil.isLanguageLevel5OrHigher(elt) &&
           JavaPsiFacade.getInstance(project).findClass(annotationFQN, elt.getResolveScope()) != null;
  }

  public static boolean isJetbrainsAnnotation(@NonNls final String simpleName) {
    return ArrayUtil.find(SIMPLE_NAMES, simpleName) != -1;
  }

  /**
   * Works similar to #isAnnotated(PsiModifierListOwner, Collection<String>) but supports FQN patters
   * like "javax.ws.rs.*". Supports ending "*" only.
   *
   * @param owner modifier list
   * @param annotations annotations qualified names or patterns. Patterns can have '*' at the end
   * @return <code>true</code> if annotated of at least one annotation from the annotations list
   */
  public static boolean checkAnnotatedUsingPatterns(PsiModifierListOwner owner, Collection<String> annotations) {
    final PsiModifierList modList;
    if (owner == null || (modList = owner.getModifierList()) == null) return false;

    List<String> fqns = null;
    for (String fqn : annotations) {
      boolean isPattern = fqn.endsWith("*");
      if (!isPattern && isAnnotated(owner, fqn, false)) {
        return true;
      } else if (isPattern) {
        if (fqns == null) {
          fqns = new ArrayList<String>();
          final PsiAnnotation[] annos = modList.getAnnotations();
          for (PsiAnnotation anno : annos) {
            final String qName = anno.getQualifiedName();
            if (qName != null) {
              fqns.add(qName);
            }
          }
          if (fqns.isEmpty()) return false;
        }
        fqn = fqn.substring(0, fqn.length() - 2);
        for (String annoFQN : fqns) {
          if (annoFQN.startsWith(fqn)) {
            return true;
          }
        }
      }
    }
    return false;
  }

  @Nullable
  public static PsiMethod getAnnotationMethod(PsiNameValuePair pair) {
    final PsiAnnotation annotation = PsiTreeUtil.getParentOfType(pair.getParent(), PsiAnnotation.class);
    assert annotation != null;

    final String fqn = annotation.getQualifiedName();
    if (fqn == null) return null;

    final PsiClass psiClass = JavaPsiFacade.getInstance(pair.getProject()).findClass(fqn, pair.getResolveScope());
    if (psiClass != null && psiClass.isAnnotationType()) {
      final String name = pair.getName();
      return ArrayUtil.getFirstElement(psiClass.findMethodsByName(name != null ? name : "value", false));
    }
    return null;
  }

  @NotNull
  public static PsiAnnotation[] getAllAnnotations(@NotNull PsiModifierListOwner owner, boolean inHierarchy, Set<PsiModifierListOwner> visited) {
    final PsiModifierList list = owner.getModifierList();
    PsiAnnotation[] annotations = PsiAnnotation.EMPTY_ARRAY;
    if (list != null) {
      annotations = list.getAnnotations();
    }

    final PsiAnnotation[] externalAnnotations = ExternalAnnotationsManager.getInstance(owner.getProject()).findExternalAnnotations(owner);
    if (externalAnnotations != null) {
      annotations = ArrayUtil.mergeArrays(annotations, externalAnnotations, PsiAnnotation.ARRAY_FACTORY);
    }

    if (inHierarchy) {
      if (owner instanceof PsiClass) {
        for (PsiClass superClass : ((PsiClass)owner).getSupers()) {
          if (visited == null) visited = new THashSet<PsiModifierListOwner>();
          if (visited.add(superClass)) annotations = ArrayUtil.mergeArrays(annotations, getAllAnnotations(superClass, inHierarchy, visited));
        }
      }
      else if (owner instanceof PsiMethod) {
        PsiMethod method = (PsiMethod)owner;
        PsiClass aClass = method.getContainingClass();
        if (aClass != null) {
          HierarchicalMethodSignature methodSignature = method.getHierarchicalMethodSignature();

          final List<HierarchicalMethodSignature> superSignatures = methodSignature.getSuperSignatures();
          PsiResolveHelper resolveHelper = PsiResolveHelper.SERVICE.getInstance(aClass.getProject());
          for (final HierarchicalMethodSignature superSignature : superSignatures) {
            final PsiMethod superMethod = superSignature.getMethod();
            if (visited == null) visited = new THashSet<PsiModifierListOwner>();
            if (!visited.add(superMethod)) continue;
            if (!resolveHelper.isAccessible(superMethod, owner, null)) continue;
            annotations = ArrayUtil.mergeArrays(annotations, getAllAnnotations(superMethod, inHierarchy, visited));
          }
        }
      }
      else if (owner instanceof PsiParameter) {
        PsiParameter parameter = (PsiParameter)owner;
        PsiElement scope = parameter.getDeclarationScope();
        if (scope instanceof PsiMethod) {
          PsiMethod method = (PsiMethod)scope;
          PsiClass aClass = method.getContainingClass();
          PsiElement parent = parameter.getParent();
          if (aClass != null && parent instanceof PsiParameterList) {
            int index = ((PsiParameterList)parent).getParameterIndex(parameter);
            HierarchicalMethodSignature methodSignature = method.getHierarchicalMethodSignature();

            final List<HierarchicalMethodSignature> superSignatures = methodSignature.getSuperSignatures();
            PsiResolveHelper resolveHelper = PsiResolveHelper.SERVICE.getInstance(aClass.getProject());
            for (final HierarchicalMethodSignature superSignature : superSignatures) {
              final PsiMethod superMethod = superSignature.getMethod();
              if (visited == null) visited = new THashSet<PsiModifierListOwner>();
              if (!visited.add(superMethod)) continue;
              if (!resolveHelper.isAccessible(superMethod, owner, null)) continue;
              PsiParameter[] superParameters = superMethod.getParameterList().getParameters();
              if (index < superParameters.length) {
                annotations = ArrayUtil.mergeArrays(annotations, getAllAnnotations(superParameters[index], inHierarchy, visited));
              }
            }
          }
        }
      }
    }
    return annotations;
  }

  public static boolean isInsideAnnotation(PsiElement element) {
    return PsiTreeUtil.getParentOfType(element, PsiNameValuePair.class, PsiArrayInitializerMemberValue.class) != null;
  }

  @Nullable
  public static String getStringAttributeValue(PsiAnnotation anno, @Nullable final String attributeName) {
    PsiAnnotationMemberValue attrValue = anno.findAttributeValue(attributeName);
    Object constValue = JavaPsiFacade.getInstance(anno.getProject()).getConstantEvaluationHelper().computeConstantExpression(attrValue);
    return constValue instanceof String ? (String)constValue : null;
  }
  @Nullable
  public static Boolean getBooleanAttributeValue(PsiAnnotation anno, @Nullable final String attributeName) {
    PsiAnnotationMemberValue attrValue = anno.findAttributeValue(attributeName);
    Object constValue = JavaPsiFacade.getInstance(anno.getProject()).getConstantEvaluationHelper().computeConstantExpression(attrValue);
    return constValue instanceof Boolean ? (Boolean)constValue : null;
  }

  @Nullable
  public static <T extends Annotation> T findAnnotationInHierarchy(@NotNull PsiModifierListOwner listOwner, @NotNull Class<T> annotationClass) {
    PsiAnnotation annotation = findAnnotationInHierarchy(listOwner, Collections.singleton(annotationClass.getName()));
    if (annotation == null) return null;
    return (T)Proxy.newProxyInstance(
      annotationClass.getClassLoader(), new Class<?>[]{annotationClass}, new AnnotationInvocationHandler(annotationClass, annotation));
  }
}
