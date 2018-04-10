// Copyright 2000-2017 JetBrains s.r.o.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
package com.intellij.codeInsight;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.AnnotatedElementsSearch;
import com.intellij.psi.search.searches.DirectClassInheritorsSearch;
import com.intellij.psi.util.*;
import com.intellij.util.containers.ConcurrentFactoryMap;
import com.intellij.util.containers.ContainerUtil;
import gnu.trove.THashSet;
import gnu.trove.TObjectHashingStrategy;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Stream;

import static com.intellij.openapi.util.Pair.pair;

/**
 * @since 2016.3
 */
public class MetaAnnotationUtil {
  private static final TObjectHashingStrategy<PsiClass> HASHING_STRATEGY = new TObjectHashingStrategy<PsiClass>() {
    @Override
    public int computeHashCode(PsiClass object) {
      String qualifiedName = object.getQualifiedName();
      return qualifiedName == null ? 0 : qualifiedName.hashCode();
    }

    @Override
    public boolean equals(PsiClass o1, PsiClass o2) {
      return Comparing.equal(o1.getQualifiedName(), o2.getQualifiedName());
    }
  };

  public static Collection<PsiClass> getAnnotationTypesWithChildren(@NotNull Module module, String annotationName, boolean includeTests) {
    Project project = module.getProject();

    Map<Pair<String, Boolean>, Collection<PsiClass>> map = CachedValuesManager.getManager(project).getCachedValue(module, () -> {
      Map<Pair<String, Boolean>, Collection<PsiClass>> factoryMap = ConcurrentFactoryMap.createMap(key -> {
        GlobalSearchScope moduleScope = GlobalSearchScope.moduleWithDependenciesAndLibrariesScope(module, key.getSecond());

        PsiClass annotationClass = JavaPsiFacade.getInstance(project).findClass(key.getFirst(), moduleScope);
        if (annotationClass == null || !annotationClass.isAnnotationType()) {
          return Collections.emptyList();
        }

        // limit search to files containing annotations
        GlobalSearchScope effectiveSearchScope = getAllAnnotationFilesScope(project).intersectWith(moduleScope);
        return getAnnotationTypesWithChildren(annotationClass, effectiveSearchScope);
      });
      return CachedValueProvider.Result.create(factoryMap, PsiModificationTracker.JAVA_STRUCTURE_MODIFICATION_COUNT);
    });

    return map.get(pair(annotationName, includeTests));
  }

  public static Set<PsiClass> getChildren(@NotNull PsiClass psiClass, @NotNull GlobalSearchScope scope) {
    if (AnnotationTargetUtil.findAnnotationTarget(psiClass, PsiAnnotation.TargetType.ANNOTATION_TYPE, PsiAnnotation.TargetType.TYPE) == null) {
      return Collections.emptySet();
    }

    String name = psiClass.getQualifiedName();
    if (name == null) return Collections.emptySet();

    Set<PsiClass> result = new THashSet<>(HASHING_STRATEGY);

    AnnotatedElementsSearch.searchPsiClasses(psiClass, scope).forEach(processorResult -> {
      if (processorResult.isAnnotationType()) {
        result.add(processorResult);
      }
      return true;
    });

    return result;
  }

  public static Collection<PsiClass> getAnnotatedTypes(@NotNull Module module,
                                                       @NotNull Key<CachedValue<Collection<PsiClass>>> key,
                                                       @NotNull String annotationName) {
    return CachedValuesManager.getManager(module.getProject()).getCachedValue(module, key, () -> {
      GlobalSearchScope scope = GlobalSearchScope.moduleWithDependenciesAndLibrariesScope(module, false);
      PsiClass psiClass = JavaPsiFacade.getInstance(module.getProject()).findClass(annotationName, scope);

      Collection<PsiClass> classes;
      if (psiClass == null || !psiClass.isAnnotationType()) {
        classes = Collections.emptyList();
      }
      else {
        classes = getChildren(psiClass, scope);
      }
      return new CachedValueProvider.Result<>(classes, PsiModificationTracker.OUT_OF_CODE_BLOCK_MODIFICATION_COUNT);
    }, false);
  }

  @NotNull
  private static Collection<PsiClass> getAnnotationTypesWithChildren(PsiClass annotationClass, GlobalSearchScope scope) {
    Set<PsiClass> classes = new THashSet<>(HASHING_STRATEGY);
    collectClassWithChildren(annotationClass, classes, scope);
    return classes;
  }

  private static GlobalSearchScope getAllAnnotationFilesScope(Project project) {
    return CachedValuesManager.getManager(project).getCachedValue(project, () -> {
      GlobalSearchScope scope = GlobalSearchScope.allScope(project);
      Set<VirtualFile> allAnnotationFiles = new HashSet<>();
      for (PsiClass javaLangAnnotation : JavaPsiFacade.getInstance(project).findClasses(CommonClassNames.JAVA_LANG_ANNOTATION_ANNOTATION, scope)) {
        DirectClassInheritorsSearch.search(javaLangAnnotation, scope, false).forEach(annotationClass -> {
          ContainerUtil.addIfNotNull(allAnnotationFiles, PsiUtilCore.getVirtualFile(annotationClass));
          return true;
        });
      }

      scope = GlobalSearchScope.filesWithLibrariesScope(project, allAnnotationFiles);
      return CachedValueProvider.Result.createSingleDependency(scope, PsiModificationTracker.JAVA_STRUCTURE_MODIFICATION_COUNT);
    });
  }

  private static void collectClassWithChildren(PsiClass psiClass, Set<PsiClass> classes, GlobalSearchScope scope) {
    classes.add(psiClass);

    for (PsiClass aClass : getChildren(psiClass, scope)) {
      if (!classes.contains(aClass)) {
        collectClassWithChildren(aClass, classes, scope);
      }
    }
  }

  /**
   * Checks if listOwner is annotated with annotations or listOwner's annotations contain given annotations.
   */
  public static boolean isMetaAnnotated(@NotNull PsiModifierListOwner listOwner, @NotNull Collection<String> annotations) {
    if (AnnotationUtil.isAnnotated(listOwner, annotations, 0)) {
      return true;
    }

    List<PsiClass> resolvedAnnotations = getResolvedClassesInAnnotationsList(listOwner);
    for (String annotationFQN : annotations) {
      for (PsiClass resolvedAnnotation : resolvedAnnotations) {
        if (metaAnnotationCached(resolvedAnnotation, annotationFQN) != null) return true;
      }
    }

    return false;
  }

  public static boolean isMetaAnnotatedInHierarchy(@NotNull PsiModifierListOwner listOwner,
                                                   @NotNull Collection<String> annotations) {
    return isMetaAnnotatedInHierarchy(listOwner, annotations, new HashSet<>());
  }

  private static boolean isMetaAnnotatedInHierarchy(@NotNull PsiModifierListOwner listOwner,
                                                    @NotNull Collection<String> annotations,
                                                    Set<PsiMember> visited) {
    if (isMetaAnnotated(listOwner, annotations)) return true;
    if (listOwner instanceof PsiClass) {
      for (PsiClass superClass : ((PsiClass)listOwner).getSupers()) {
        if (visited.add(superClass) && isMetaAnnotatedInHierarchy(superClass, annotations, visited)) return true;
      }
    }
    else if (listOwner instanceof PsiMethod) {
      for (PsiMethod method : ((PsiMethod)listOwner).findSuperMethods()) {
        if (visited.add(method) && isMetaAnnotatedInHierarchy(method, annotations, visited)) return true;
      }
    }
    return false;
  }

  @Nullable
  private static PsiAnnotation metaAnnotationCached(PsiClass subjectAnnotation, String annotationToFind) {
    return CachedValuesManager.getCachedValue(subjectAnnotation, () -> {
      ConcurrentMap<String, PsiAnnotation> metaAnnotationsMap = ConcurrentFactoryMap.createMap(
        anno -> findMetaAnnotation(subjectAnnotation, anno, new HashSet<>()));
      return new CachedValueProvider.Result<>(metaAnnotationsMap, PsiModificationTracker.JAVA_STRUCTURE_MODIFICATION_COUNT);
    }).get(annotationToFind);
  }

  @Nullable
  private static PsiAnnotation findMetaAnnotation(PsiClass aClass, String annotation, Set<PsiClass> visited) {
    PsiAnnotation directAnnotation = AnnotationUtil.findAnnotation(aClass, annotation);
    if (directAnnotation != null) {
      return directAnnotation;
    }

    List<PsiClass> resolvedAnnotations = getResolvedClassesInAnnotationsList(aClass);
    for (PsiClass resolvedAnnotation : resolvedAnnotations) {
      if (visited.add(resolvedAnnotation)) {
        PsiAnnotation annotated = findMetaAnnotation(resolvedAnnotation, annotation, visited);
        if (annotated != null) {
          return annotated;
        }
      }
    }

    return null;
  }

  @NotNull
  public static Stream<PsiAnnotation> findMetaAnnotations(@NotNull PsiModifierListOwner listOwner, @NotNull Collection<String> annotations) {
    Stream<PsiAnnotation> directAnnotations = Stream.of(AnnotationUtil.findAnnotations(listOwner, annotations));

    Stream<PsiClass> lazyResolvedAnnotations =
      Stream.generate(() -> getResolvedClassesInAnnotationsList(listOwner)).limit(1)
        .flatMap(it -> it.stream());

    Stream<PsiAnnotation> metaAnnotations =
      lazyResolvedAnnotations
        .flatMap(psiClass -> annotations.stream()
          .map(annotationFQN -> metaAnnotationCached(psiClass, annotationFQN)))
        .filter(Objects::nonNull);

    return Stream.concat(directAnnotations, metaAnnotations);
  }

  private static List<PsiClass> getResolvedClassesInAnnotationsList(PsiModifierListOwner owner) {
    PsiModifierList modifierList = owner.getModifierList();
    if (modifierList != null) {
      return ContainerUtil.mapNotNull(modifierList.getApplicableAnnotations(), psiAnnotation -> {
        PsiJavaCodeReferenceElement nameReferenceElement = psiAnnotation.getNameReferenceElement();
        PsiElement resolve = nameReferenceElement != null ? nameReferenceElement.resolve() : null;
        return resolve instanceof PsiClass && ((PsiClass)resolve).isAnnotationType() ? (PsiClass)resolve : null;
      });
    }
    return Collections.emptyList();
  }
}