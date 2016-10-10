/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.CommonClassNames;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
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

/**
 * @since 2016.3
 */
public class MetaAnnotationUtil {
  private static final TObjectHashingStrategy<PsiClass> HASHING_STRATEGY = new TObjectHashingStrategy<PsiClass>() {
    public int computeHashCode(final PsiClass object) {
      final String qualifiedName = object.getQualifiedName();
      return qualifiedName == null ? 0 : qualifiedName.hashCode();
    }

    public boolean equals(final PsiClass o1, final PsiClass o2) {
      return Comparing.equal(o1.getQualifiedName(), o2.getQualifiedName());
    }
  };

  public static Collection<PsiClass> getAnnotationTypesWithChildren(@NotNull final Module module,
                                                                    final String annotationName,
                                                                    final boolean includeTests) {
    Map<Pair<String, Boolean>, Collection<PsiClass>> map =
      CachedValuesManager.getManager(module.getProject())
        .getCachedValue(module, () -> {

          GlobalSearchScope allAnnotationFilesScope = getAllAnnotationFilesScope(module);

          Map<Pair<String, Boolean>, Collection<PsiClass>> factoryMap =
            new ConcurrentFactoryMap<Pair<String, Boolean>, Collection<PsiClass>>() {
              @Nullable
              @Override
              protected Collection<PsiClass> create(Pair<String, Boolean> key) {
                final GlobalSearchScope scope = GlobalSearchScope.moduleWithDependenciesAndLibrariesScope(module, key.getSecond());

                // limit search to files containing annotations
                final GlobalSearchScope effectiveSearchScope = allAnnotationFilesScope.intersectWith(scope);
                return getAnnotationTypesWithChildren(key.getFirst(), module.getProject(), effectiveSearchScope);
              }
            };
          return CachedValueProvider.Result.create(factoryMap, PsiModificationTracker.JAVA_STRUCTURE_MODIFICATION_COUNT);
        });

    return map.get(Pair.create(annotationName, includeTests));
  }

  public static Set<PsiClass> getChildren(final PsiClass psiClass, final GlobalSearchScope scope) {
    if (AnnotationTargetUtil.findAnnotationTarget(psiClass,
                                                  PsiAnnotation.TargetType.ANNOTATION_TYPE, PsiAnnotation.TargetType.TYPE) == null) {
      return Collections.emptySet();
    }

    final String name = psiClass.getQualifiedName();
    if (name == null) return Collections.emptySet();

    final Set<PsiClass> result = new THashSet<>(HASHING_STRATEGY);

    AnnotatedElementsSearch.searchPsiClasses(psiClass, scope).forEach(processorResult -> {
      if (processorResult.isAnnotationType()) {
        result.add(processorResult);
      }
      return true;
    });

    return result;
  }

  public static Collection<PsiClass> getAnnotatedTypes(final Module module,
                                                       final Key<CachedValue<Collection<PsiClass>>> key,
                                                       final String annotationName) {
    return CachedValuesManager.getManager(module.getProject()).getCachedValue(module, key, () -> {
      final GlobalSearchScope scope = GlobalSearchScope.moduleWithDependenciesAndLibrariesScope(module, false);
      final PsiClass psiClass = JavaPsiFacade.getInstance(module.getProject()).findClass(annotationName, scope);

      final Collection<PsiClass> classes;
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
  private static Collection<PsiClass> getAnnotationTypesWithChildren(final String annotationName,
                                                                     Project project,
                                                                     GlobalSearchScope scope) {
    final PsiClass psiClass = JavaPsiFacade.getInstance(project).findClass(annotationName, scope);

    if (psiClass == null || !psiClass.isAnnotationType()) return Collections.emptyList();

    final Set<PsiClass> classes = new THashSet<>(HASHING_STRATEGY);

    collectClassWithChildren(psiClass, classes, scope);

    return classes;
  }

  private static GlobalSearchScope getAllAnnotationFilesScope(Module module) {
    return CachedValuesManager.getManager(module.getProject()).getCachedValue(module, () -> {
      GlobalSearchScope scope = GlobalSearchScope.moduleWithDependenciesAndLibrariesScope(module, true);
      PsiClass javaLangAnnotation =
        JavaPsiFacade.getInstance(scope.getProject()).findClass(CommonClassNames.JAVA_LANG_ANNOTATION_ANNOTATION, scope);
      if (javaLangAnnotation == null) {
        return CachedValueProvider.Result.createSingleDependency(GlobalSearchScope.EMPTY_SCOPE, PsiModificationTracker.JAVA_STRUCTURE_MODIFICATION_COUNT);
      }

      Set<VirtualFile> allAnnotationFiles = new HashSet<>();
      DirectClassInheritorsSearch.search(javaLangAnnotation, scope, false).forEach(annotationClass -> {
        ContainerUtil.addIfNotNull(allAnnotationFiles, PsiUtilCore.getVirtualFile(annotationClass));
        return true;
      });

      return CachedValueProvider.Result
        .createSingleDependency(GlobalSearchScope.filesWithLibrariesScope(module.getProject(), allAnnotationFiles),
                                PsiModificationTracker.JAVA_STRUCTURE_MODIFICATION_COUNT);
    });
  }

  private static void collectClassWithChildren(final PsiClass psiClass, final Set<PsiClass> classes, final GlobalSearchScope scope) {
    classes.add(psiClass);

    for (PsiClass aClass : getChildren(psiClass, scope)) {
      if (!classes.contains(aClass)) {
        collectClassWithChildren(aClass, classes, scope);
      }
    }
  }
}
