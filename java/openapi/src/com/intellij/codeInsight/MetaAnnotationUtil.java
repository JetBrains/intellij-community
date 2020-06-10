// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight;

import com.intellij.lang.Language;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileWithId;
import com.intellij.psi.*;
import com.intellij.psi.impl.java.stubs.index.JavaStubIndexKeys;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.AnnotatedElementsSearch;
import com.intellij.psi.search.searches.DirectClassInheritorsSearch;
import com.intellij.psi.stubs.StubIndex;
import com.intellij.psi.util.CachedValue;
import com.intellij.psi.util.CachedValueProvider.Result;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.util.containers.ConcurrentFactoryMap;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.indexing.IdIterator;
import gnu.trove.THashSet;
import gnu.trove.TIntHashSet;
import gnu.trove.TObjectHashingStrategy;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Stream;

import static com.intellij.openapi.util.Pair.pair;
import static com.intellij.psi.search.GlobalSearchScope.moduleWithDependenciesAndLibrariesScope;
import static java.util.Collections.emptyList;

/**
 * NB: Supposed to be used for annotations used in libraries and frameworks only, external annotations are not considered.
 */
public abstract class MetaAnnotationUtil {
  private static final TObjectHashingStrategy<PsiClass> HASHING_STRATEGY = new TObjectHashingStrategy<PsiClass>() {
    @Override
    public int computeHashCode(PsiClass object) {
      String qualifiedName = object.getQualifiedName();
      return qualifiedName == null ? 0 : qualifiedName.hashCode();
    }

    @Override
    public boolean equals(PsiClass o1, PsiClass o2) {
      return Objects.equals(o1.getQualifiedName(), o2.getQualifiedName());
    }
  };

  public static Collection<PsiClass> getAnnotationTypesWithChildren(@NotNull Module module, String annotationName, boolean includeTests) {
    Project project = module.getProject();

    Map<Pair<String, Boolean>, Collection<PsiClass>> map = CachedValuesManager.getManager(project).getCachedValue(module, () -> {
      Map<Pair<String, Boolean>, Collection<PsiClass>> factoryMap = ConcurrentFactoryMap.createMap(key -> {
        GlobalSearchScope moduleScope = moduleWithDependenciesAndLibrariesScope(module, key.getSecond());

        PsiClass annotationClass = JavaPsiFacade.getInstance(project).findClass(key.getFirst(), moduleScope);
        if (annotationClass == null || !annotationClass.isAnnotationType()) {
          return emptyList();
        }

        // limit search to files containing annotations
        GlobalSearchScope effectiveSearchScope = getAllAnnotationFilesScope(project).intersectWith(moduleScope);
        return getAnnotationTypesWithChildren(annotationClass, effectiveSearchScope);
      });
      return Result.create(factoryMap, PsiModificationTracker.MODIFICATION_COUNT);
    });

    return map.get(pair(annotationName, includeTests));
  }

  public static Set<PsiClass> getChildren(@NotNull PsiClass psiClass, @NotNull GlobalSearchScope scope) {
    if (AnnotationTargetUtil.findAnnotationTarget(psiClass, PsiAnnotation.TargetType.ANNOTATION_TYPE, PsiAnnotation.TargetType.TYPE) ==
        null) {
      return Collections.emptySet();
    }

    String name = psiClass.getQualifiedName();
    if (name == null) return Collections.emptySet();

    Set<PsiClass> result = new THashSet<>(HASHING_STRATEGY);
    AnnotatedElementsSearch.searchPsiClasses(psiClass, scope).forEach(processorResult -> {
      ProgressManager.checkCanceled();
      if (processorResult.isAnnotationType()) {
        result.add(processorResult);
      }
      return true;
    });

    return result;
  }

  public static Collection<PsiClass> getAnnotatedTypes(@NotNull Module module, @NotNull String annotationName) {
    Map<String, Collection<PsiClass>> map = CachedValuesManager.getManager(module.getProject()).getCachedValue(module, () -> {
      Map<String, Collection<PsiClass>> factoryMap = ConcurrentFactoryMap.createMap(key -> {
        return findAnnotatedTypes(module, key);
      });

      return new Result<>(factoryMap, PsiModificationTracker.MODIFICATION_COUNT);
    });
    return map.getOrDefault(annotationName, emptyList());
  }

  /**
   * @deprecated Use {@link #getAnnotatedTypes(Module, String)} instead.
   */
  @Deprecated
  @ApiStatus.ScheduledForRemoval(inVersion = "2021.1")
  public static Collection<PsiClass> getAnnotatedTypes(@NotNull Module module,
                                                       @NotNull Key<CachedValue<Collection<PsiClass>>> key,
                                                       @NotNull String annotationName) {
    return getAnnotatedTypes(module, annotationName);
  }

  @NotNull
  private static Collection<PsiClass> findAnnotatedTypes(@NotNull Module module, @NotNull String annotationName) {
    GlobalSearchScope scope = moduleWithDependenciesAndLibrariesScope(module, false);
    PsiClass psiClass = JavaPsiFacade.getInstance(module.getProject()).findClass(annotationName, scope);
    if (psiClass == null || !psiClass.isAnnotationType()) {
      return emptyList();
    }
    return getChildren(psiClass, scope);
  }

  @NotNull
  private static Collection<PsiClass> getAnnotationTypesWithChildren(PsiClass annotationClass, GlobalSearchScope scope) {
    Set<PsiClass> classes = new THashSet<>(HASHING_STRATEGY);
    collectClassWithChildren(annotationClass, classes, scope);
    return classes;
  }

  private static GlobalSearchScope getAllAnnotationFilesScope(Project project) {
    return CachedValuesManager.getManager(project).getCachedValue(project, () -> {
      GlobalSearchScope javaScope = new FileIdScope(project, getJavaAnnotationInheritorIds(project));
      GlobalSearchScope otherScope = searchForAnnotationInheritorsInOtherLanguages(project);
      return Result.createSingleDependency(
        javaScope.uniteWith(otherScope),
        PsiModificationTracker.MODIFICATION_COUNT);
    });
  }

  @NotNull
  private static GlobalSearchScope searchForAnnotationInheritorsInOtherLanguages(Project project) {
    GlobalSearchScope allScope = GlobalSearchScope.allScope(project);
    Set<VirtualFile> allAnnotationFiles = new HashSet<>();
    for (PsiClass javaLangAnnotation : JavaPsiFacade.getInstance(project)
      .findClasses(CommonClassNames.JAVA_LANG_ANNOTATION_ANNOTATION, allScope)) {
      DirectClassInheritorsSearch.SearchParameters parameters =
        new DirectClassInheritorsSearch.SearchParameters(javaLangAnnotation, allScope, false, true) {
          @Override
          public boolean shouldSearchInLanguage(@NotNull Language language) {
            return language != JavaLanguage.INSTANCE;
          }
        };
      DirectClassInheritorsSearch.search(parameters).forEach(annotationClass -> {
        ProgressManager.checkCanceled();
        ContainerUtil.addIfNotNull(allAnnotationFiles, PsiUtilCore.getVirtualFile(annotationClass));
        return true;
      });
    }

    return GlobalSearchScope.filesWithLibrariesScope(project, allAnnotationFiles);
  }

  @NotNull
  private static TIntHashSet getJavaAnnotationInheritorIds(Project project) {
    IdIterator iterator = StubIndex.getInstance().getContainingIds(JavaStubIndexKeys.SUPER_CLASSES, "Annotation", project,
                                                                   GlobalSearchScope.allScope(project));
    TIntHashSet idSet = new TIntHashSet();
    while (iterator.hasNext()) {
      idSet.add(iterator.next());
    }
    return idSet;
  }

  private static class FileIdScope extends GlobalSearchScope {
    private final TIntHashSet myIdSet;

    FileIdScope(Project project, TIntHashSet idSet) {
      super(project);
      myIdSet = idSet;
    }

    @Override
    public boolean isSearchInModuleContent(@NotNull Module aModule) {
      return true;
    }

    @Override
    public boolean isSearchInLibraries() {
      return true;
    }

    @Override
    public boolean contains(@NotNull VirtualFile file) {
      return file instanceof VirtualFileWithId && myIdSet.contains(((VirtualFileWithId)file).getId());
    }
  }

  private static void collectClassWithChildren(PsiClass psiClass, Set<? super PsiClass> classes, GlobalSearchScope scope) {
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

  public static boolean hasMetaAnnotatedMethods(@NotNull PsiClass psiClass,
                                                @NotNull Collection<String> annotations) {
    return Stream.of(psiClass.getMethods())
      .anyMatch(psiMethod -> isMetaAnnotated(psiMethod, annotations));
  }

  private static boolean isMetaAnnotatedInHierarchy(@NotNull PsiModifierListOwner listOwner,
                                                    @NotNull Collection<String> annotations,
                                                    Set<? super PsiMember> visited) {
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
      return new Result<>(metaAnnotationsMap, PsiModificationTracker.MODIFICATION_COUNT);
    }).get(annotationToFind);
  }

  @Nullable
  private static PsiAnnotation findMetaAnnotation(PsiClass aClass, String annotation, Set<? super PsiClass> visited) {
    PsiAnnotation directAnnotation = AnnotationUtil.findAnnotation(aClass, true, annotation);
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
  public static Stream<PsiAnnotation> findMetaAnnotations(@NotNull PsiModifierListOwner listOwner,
                                                          @NotNull Collection<String> annotations) {
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
      return ContainerUtil.mapNotNull(modifierList.getApplicableAnnotations(), annotation -> annotation.resolveAnnotationType());
    }
    return emptyList();
  }
}