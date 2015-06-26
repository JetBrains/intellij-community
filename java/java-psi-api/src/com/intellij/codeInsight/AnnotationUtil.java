/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
import com.intellij.psi.util.*;
import com.intellij.util.ArrayUtil;
import com.intellij.util.CommonProcessors;
import com.intellij.util.containers.ConcurrentFactoryMap;
import com.intellij.util.containers.ContainerUtil;
import gnu.trove.THashSet;
import org.jetbrains.annotations.Contract;
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
  public static PsiAnnotation findAnnotation(@Nullable PsiModifierListOwner listOwner, @NotNull String... annotationNames) {
    return findAnnotation(listOwner, false, annotationNames);
  }

  @Nullable
  public static PsiAnnotation findAnnotation(@Nullable PsiModifierListOwner listOwner, final boolean skipExternal, @NotNull String... annotationNames) {
    if (annotationNames.length == 0) return null;
    Set<String> set = annotationNames.length == 1 ? Collections.singleton(annotationNames[0]) : new HashSet<String>(Arrays.asList(annotationNames));
    return findAnnotation(listOwner, set, skipExternal);
  }

  @Nullable
  public static PsiAnnotation findAnnotation(@Nullable PsiModifierListOwner listOwner, @NotNull Set<String> annotationNames) {
    return findAnnotation(listOwner, (Collection<String>)annotationNames);
  }

  @Nullable
  public static PsiAnnotation findAnnotation(@Nullable PsiModifierListOwner listOwner, @NotNull Collection<String> annotationNames) {
    return findAnnotation(listOwner, annotationNames, false);
  }

  @Nullable
  public static PsiAnnotation findAnnotation(@Nullable PsiModifierListOwner listOwner, @NotNull Collection<String> annotationNames,
                                             final boolean skipExternal) {
    if (listOwner == null) return null;

    PsiAnnotation annotation = findOwnAnnotation(listOwner, annotationNames);
    if (annotation != null) {
      return annotation;
    }
    return skipExternal ? null : findNonCodeAnnotation(listOwner, annotationNames);
  }

  private static PsiAnnotation findOwnAnnotation(final PsiModifierListOwner listOwner, Collection<String> annotationNames) {
    ConcurrentFactoryMap<Collection<String>, PsiAnnotation> map = CachedValuesManager.getCachedValue(
      listOwner,
      new CachedValueProvider<ConcurrentFactoryMap<Collection<String>, PsiAnnotation>>() {
        @Nullable
        @Override
        public Result<ConcurrentFactoryMap<Collection<String>, PsiAnnotation>> compute() {
          ConcurrentFactoryMap<Collection<String>, PsiAnnotation> value = new ConcurrentFactoryMap<Collection<String>, PsiAnnotation>() {
            @Nullable
            @Override
            protected PsiAnnotation create(Collection<String> annotationNames) {
              final PsiModifierList list = listOwner.getModifierList();
              if (list == null) return null;
              for (PsiAnnotation annotation : list.getAnnotations()) {
                if (annotationNames.contains(annotation.getQualifiedName())) {
                  return annotation;
                }
              }
              return null;
            }
          };
          return Result.create(value, PsiModificationTracker.MODIFICATION_COUNT);
        }
      });
    return map.get(annotationNames);
  }

  private static PsiAnnotation findNonCodeAnnotation(final PsiModifierListOwner listOwner, Collection<String> annotationNames) {
    ConcurrentFactoryMap<Collection<String>, PsiAnnotation> map = CachedValuesManager.getCachedValue(
      listOwner,
      new CachedValueProvider<ConcurrentFactoryMap<Collection<String>, PsiAnnotation>>() {
        @Nullable
        @Override
        public Result<ConcurrentFactoryMap<Collection<String>, PsiAnnotation>> compute() {
          ConcurrentFactoryMap<Collection<String>, PsiAnnotation> value = new ConcurrentFactoryMap<Collection<String>, PsiAnnotation>() {
            @Nullable
            @Override
            protected PsiAnnotation create(Collection<String> annotationNames) {
              final Project project = listOwner.getProject();
              final ExternalAnnotationsManager annotationsManager = ExternalAnnotationsManager.getInstance(project);
              for (String annotationName : annotationNames) {
                final PsiAnnotation annotation = annotationsManager.findExternalAnnotation(listOwner, annotationName);
                if (annotation != null) {
                  return annotation;
                }
              }
              final InferredAnnotationsManager inferredAnnotationsManager = InferredAnnotationsManager.getInstance(project);
              for (String annotationName : annotationNames) {
                final PsiAnnotation annotation = inferredAnnotationsManager.findInferredAnnotation(listOwner, annotationName);
                if (annotation != null) {
                  return annotation;
                }
              }
              return null;

            }
          };
          return Result.create(value, PsiModificationTracker.MODIFICATION_COUNT);
        }
      });
    return map.get(annotationNames);
  }

  @NotNull
  public static PsiAnnotation[] findAnnotations(@Nullable PsiModifierListOwner modifierListOwner, @NotNull Collection<String> annotationNames) {
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

  public static <T extends PsiModifierListOwner> List<T> getSuperAnnotationOwners(final T element) {
    return CachedValuesManager.getCachedValue(element, new CachedValueProvider<List<T>>() {
      @Nullable
      @Override
      public Result<List<T>> compute() {
        LinkedHashSet<PsiModifierListOwner> result = ContainerUtil.newLinkedHashSet();
        if (element instanceof PsiMethod) {
          collectSuperMethods(result, ((PsiMethod)element).getHierarchicalMethodSignature(), element,
                              JavaPsiFacade.getInstance(element.getProject()).getResolveHelper());
        } else if (element instanceof PsiClass) {
          //noinspection unchecked
          InheritanceUtil.processSupers((PsiClass)element, false, new CommonProcessors.CollectProcessor<PsiClass>((Set)result));
        } else if (element instanceof PsiParameter) {
          collectSuperParameters(result, (PsiParameter)element);
        }

        List<T> list = new ArrayList<T>();
        //noinspection unchecked
        list.addAll((Collection<? extends T>)result);
        
        List<Object> dependencies = ContainerUtil.<Object>newArrayList(result);
        dependencies.add(PsiModificationTracker.MODIFICATION_COUNT);
        return Result.create(list, dependencies);
      }
    });
  }

  @Nullable
  public static PsiAnnotation findAnnotationInHierarchy(@NotNull final PsiModifierListOwner listOwner, @NotNull Set<String> annotationNames) {
    PsiAnnotation directAnnotation = findAnnotation(listOwner, annotationNames);
    if (directAnnotation != null) return directAnnotation;

    ConcurrentFactoryMap<Set<String>, PsiAnnotation> map = CachedValuesManager.getCachedValue(
      listOwner,
      new CachedValueProvider<ConcurrentFactoryMap<Set<String>, PsiAnnotation>>() {
        @Nullable
        @Override
        public Result<ConcurrentFactoryMap<Set<String>, PsiAnnotation>> compute() {
          ConcurrentFactoryMap<Set<String>, PsiAnnotation> value = new ConcurrentFactoryMap<Set<String>, PsiAnnotation>() {
            @Nullable
            @Override
            protected PsiAnnotation create(Set<String> annotationNames) {
              for (PsiModifierListOwner superOwner : getSuperAnnotationOwners(listOwner)) {
                PsiAnnotation annotation = findAnnotation(superOwner, annotationNames);
                if (annotation != null) {
                  return annotation;
                }
              }
              return null;
            }
          };
          return Result.create(value, PsiModificationTracker.MODIFICATION_COUNT);
        }
      });
    return map.get(annotationNames);
  }

  private static void collectSuperParameters(LinkedHashSet<PsiModifierListOwner> result, @NotNull PsiParameter parameter) {
    PsiElement scope = parameter.getDeclarationScope();
    if (!(scope instanceof PsiMethod)) {
      return;
    }
    PsiMethod method = (PsiMethod)scope;

    PsiElement parent = parameter.getParent();
    if (!(parent instanceof PsiParameterList)) {
      return;
    }
    int index = ((PsiParameterList)parent).getParameterIndex(parameter);
    for (PsiMethod superMethod : getSuperAnnotationOwners(method)) {
      PsiParameter[] superParameters = superMethod.getParameterList().getParameters();
      if (index < superParameters.length) {
        result.add(superParameters[index]);
      }
    }
  }

  private static void collectSuperMethods(LinkedHashSet<PsiModifierListOwner> result,
                                          @NotNull HierarchicalMethodSignature signature,
                                          @NotNull PsiElement place,
                                          @NotNull PsiResolveHelper resolveHelper) {
    for (final HierarchicalMethodSignature superSignature : signature.getSuperSignatures()) {
      final PsiMethod superMethod = superSignature.getMethod();
      if (!resolveHelper.isAccessible(superMethod, place, null)) continue;
      if (!result.add(superMethod)) continue;
      collectSuperMethods(result, superSignature, place, resolveHelper);
    }
  }

  public static boolean isAnnotated(@NotNull PsiModifierListOwner listOwner, @NotNull Collection<String> annotations) {
    return isAnnotated(listOwner, annotations, false);
  }

  public static boolean isAnnotated(@NotNull PsiModifierListOwner listOwner,
                                    @NotNull Collection<String> annotations,
                                    final boolean checkHierarchy) {
    return isAnnotated(listOwner, annotations, checkHierarchy, true);
  }

  public static boolean isAnnotated(@NotNull PsiModifierListOwner listOwner,
                                    @NotNull Collection<String> annotations,
                                    final boolean checkHierarchy,
                                    boolean skipExternal) {
    for (String annotation : annotations) {
      if (isAnnotated(listOwner, annotation, checkHierarchy, skipExternal)) return true;
    }
    return false;
  }

  public static boolean isAnnotated(@NotNull PsiModifierListOwner listOwner, @NonNls @NotNull String annotationFQN, boolean checkHierarchy) {
    return isAnnotated(listOwner, annotationFQN, checkHierarchy, true, null);
  }

  public static boolean isAnnotated(@NotNull PsiModifierListOwner listOwner, @NonNls @NotNull String annotationFQN, boolean checkHierarchy,
                                    boolean skipExternal) {
    return isAnnotated(listOwner, annotationFQN, checkHierarchy, skipExternal, null);
  }

  private static boolean isAnnotated(@NotNull PsiModifierListOwner listOwner,
                                     @NonNls @NotNull String annotationFQN,
                                     boolean checkHierarchy, final boolean skipExternal, @Nullable Set<PsiMember> processed) {
    if (!listOwner.isValid()) return false;
    final PsiModifierList modifierList = listOwner.getModifierList();
    if (modifierList == null) return false;
    PsiAnnotation annotation = modifierList.findAnnotation(annotationFQN);
    if (annotation != null) return true;
    if (!skipExternal) {
      final Project project = listOwner.getProject();
      if (ExternalAnnotationsManager.getInstance(project).findExternalAnnotation(listOwner, annotationFQN) != null ||
          InferredAnnotationsManager.getInstance(project).findInferredAnnotation(listOwner, annotationFQN) != null) {
        return true;
      }
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

  public static boolean isAnnotatingApplicable(@NotNull PsiElement elt, @NotNull String annotationFQN) {
    final Project project = elt.getProject();
    return PsiUtil.isLanguageLevel5OrHigher(elt) &&
           JavaPsiFacade.getInstance(project).findClass(annotationFQN, elt.getResolveScope()) != null;
  }

  public static boolean isJetbrainsAnnotation(@NonNls @NotNull String simpleName) {
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
  @Contract("null,_ -> false")
  public static boolean checkAnnotatedUsingPatterns(@Nullable PsiModifierListOwner owner, @NotNull Collection<String> annotations) {
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
  public static PsiMethod getAnnotationMethod(@NotNull PsiNameValuePair pair) {
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
  public static PsiAnnotation[] getAllAnnotations(@NotNull PsiModifierListOwner owner,
                                                  boolean inHierarchy,
                                                  @Nullable Set<PsiModifierListOwner> visited) {
    return getAllAnnotations(owner, inHierarchy, visited, true);
  }

  @NotNull
  public static PsiAnnotation[] getAllAnnotations(@NotNull PsiModifierListOwner owner,
                                                  boolean inHierarchy,
                                                  @Nullable Set<PsiModifierListOwner> visited, boolean withInferred) {
    final PsiModifierList list = owner.getModifierList();
    PsiAnnotation[] annotations = PsiAnnotation.EMPTY_ARRAY;
    if (list != null) {
      annotations = list.getAnnotations();
    }

    final Project project = owner.getProject();
    final PsiAnnotation[] externalAnnotations = ExternalAnnotationsManager.getInstance(project).findExternalAnnotations(owner);
    if (externalAnnotations != null) {
      annotations = ArrayUtil.mergeArrays(annotations, externalAnnotations, PsiAnnotation.ARRAY_FACTORY);
    }
    if (withInferred) {
      final PsiAnnotation[] inferredAnnotations = InferredAnnotationsManager.getInstance(project).findInferredAnnotations(owner);
      annotations = ArrayUtil.mergeArrays(annotations, inferredAnnotations, PsiAnnotation.ARRAY_FACTORY);
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

  public static boolean isInferredAnnotation(@NotNull PsiAnnotation annotation) {
    return InferredAnnotationsManager.getInstance(annotation.getProject()).isInferredAnnotation(annotation);
  }

  public static boolean isExternalAnnotation(@NotNull PsiAnnotation annotation) {
    return ExternalAnnotationsManager.getInstance(annotation.getProject()).isExternalAnnotation(annotation);
  }

  @Nullable
  public static String getStringAttributeValue(@NotNull PsiAnnotation anno, @Nullable final String attributeName) {
    PsiAnnotationMemberValue attrValue = anno.findAttributeValue(attributeName);
    Object constValue = JavaPsiFacade.getInstance(anno.getProject()).getConstantEvaluationHelper().computeConstantExpression(attrValue);
    return constValue instanceof String ? (String)constValue : null;
  }
  @Nullable
  public static Boolean getBooleanAttributeValue(@NotNull PsiAnnotation anno, @Nullable final String attributeName) {
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
