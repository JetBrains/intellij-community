/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.ArrayUtil;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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
  public static final String TARGET_ANNOTATION_FQ_NAME = "java.lang.annotation.Target";

  static {
    ALL_ANNOTATIONS = new HashSet<String>(2);
    ALL_ANNOTATIONS.add(NULLABLE);
    ALL_ANNOTATIONS.add(NOT_NULL);
  }

  public static boolean isNullable(@NotNull PsiModifierListOwner owner) {
    return !isNotNull(owner) && isAnnotated(owner, NULLABLE, true);
  }

  public static boolean isNotNull(@NotNull PsiModifierListOwner owner) {
    return isAnnotated(owner, NOT_NULL, true);
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
  public static PsiAnnotation findAnnotation(PsiModifierListOwner listOwner, @NotNull Set<String> annotationNames) {
    return findAnnotation(listOwner, (Collection<String>)annotationNames);
  }

  @Nullable
  public static PsiAnnotation findAnnotation(@Nullable PsiModifierListOwner listOwner, Collection<String> annotationNames) {
    return findAnnotation(listOwner, annotationNames, false);
  }

  @Nullable
  public static PsiAnnotation findAnnotation(@Nullable PsiModifierListOwner listOwner, Collection<String> annotationNames,
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
  public static PsiAnnotation findAnnotationInHierarchy(PsiModifierListOwner listOwner, Set<String> annotationNames) {
    PsiAnnotation directAnnotation = findAnnotation(listOwner, annotationNames);
    if (directAnnotation != null) return directAnnotation;
    if (listOwner instanceof PsiMethod) {
      PsiMethod method = (PsiMethod)listOwner;
      PsiClass aClass = method.getContainingClass();
      if (aClass == null) return null;
      HierarchicalMethodSignature methodSignature = method.getHierarchicalMethodSignature();
      return findAnnotationInHierarchy(methodSignature, annotationNames, method, null);
    } else if (listOwner instanceof PsiClass) {
      return findAnnotationInHierarchy(((PsiClass)listOwner), annotationNames, null);
    }
    return null;
  }

  @Nullable
  private static PsiAnnotation findAnnotationInHierarchy(final @NotNull PsiClass psiClass, final Set<String> annotationNames, Set<PsiClass> processed) {
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
  private static PsiAnnotation findAnnotationInHierarchy(HierarchicalMethodSignature signature,
                                                         Set<String> annotationNames,
                                                         PsiElement place,
                                                         Set<PsiMethod> processed) {
    final List<HierarchicalMethodSignature> superSignatures = signature.getSuperSignatures();
    final PsiResolveHelper resolveHelper = JavaPsiFacade.getInstance(place.getProject()).getResolveHelper();
    for (final HierarchicalMethodSignature superSignature : superSignatures) {
      final PsiMethod superMethod = superSignature.getMethod();
      if (processed == null) processed = new THashSet<PsiMethod>();
      if (!processed.add(superMethod)) continue;
      if (!resolveHelper.isAccessible(superMethod, place, null)) continue;
      PsiAnnotation direct = findAnnotation(superMethod, annotationNames);
      if (direct != null) return direct;
      PsiAnnotation superResult = findAnnotationInHierarchy(superSignature, annotationNames, place, processed);
      if (superResult != null) return superResult;
    }

    return null;
  }

  public static boolean isAnnotated(PsiModifierListOwner listOwner, Collection<String> annotations) {
    for (String annotation : annotations) {
      if (isAnnotated(listOwner, annotation, false)) return true;
    }
    return false;
  }

  public static boolean isAnnotated(@NotNull PsiModifierListOwner listOwner, @NonNls String annotationFQN, boolean checkHierarchy) {
    return isAnnotated(listOwner, annotationFQN, checkHierarchy, false, null);
  }

  public static boolean isAnnotated(@NotNull PsiModifierListOwner listOwner, @NonNls String annotationFQN, boolean checkHierarchy,
                                    boolean skipExternal) {
    return isAnnotated(listOwner, annotationFQN, checkHierarchy, skipExternal, null);
  }

  private static boolean isAnnotated(@NotNull PsiModifierListOwner listOwner,
                                     @NonNls String annotationFQN,
                                     boolean checkHierarchy, final boolean skipExternal, Set<PsiMember> processed) {
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

  public static boolean isAnnotatingApplicable(PsiElement elt) {
    return PsiUtil.isLanguageLevel5OrHigher(elt) &&
           JavaPsiFacade.getInstance(elt.getProject()).findClass(NULLABLE, elt.getResolveScope()) != null;
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
    List<String> fqns = null;
    final PsiModifierList modList;
    if (owner == null || (modList = owner.getModifierList()) == null) return false;

    for (String fqn : annotations) {
      if (! fqn.endsWith("*") && isAnnotated(owner, fqn, false)) {
        return true;
      } else {
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
}
