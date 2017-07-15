/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.psi.impl;

import com.intellij.lang.jvm.JvmClass;
import com.intellij.lang.jvm.facade.JvmFacade;
import com.intellij.lang.jvm.facade.JvmFacadeImpl;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.ExtensionPoint;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.extensions.SimpleSmartExtensionPoint;
import com.intellij.openapi.progress.ProgressIndicatorProvider;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.AtomicNotNullLazyValue;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Conditions;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.impl.file.impl.JavaFileManager;
import com.intellij.psi.impl.jvm2psi.JvmPsiConversionHelper;
import com.intellij.psi.impl.source.DummyHolderFactory;
import com.intellij.psi.impl.source.JavaDummyHolder;
import com.intellij.psi.impl.source.JavaDummyHolderFactory;
import com.intellij.psi.impl.source.resolve.FileContextUtil;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.util.ConcurrencyUtil;
import com.intellij.util.Processor;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.messages.MessageBus;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.ConcurrentMap;

/**
 * @author max
 */
public class JavaPsiFacadeImpl extends JavaPsiFacadeEx {
  private static final Logger LOG = Logger.getInstance(JavaPsiFacadeImpl.class);

  private final SimpleSmartExtensionPoint<PsiElementFinder> myElementFinders;
  private final PsiConstantEvaluationHelper myConstantEvaluationHelper;
  private final ConcurrentMap<String, PsiPackage> myPackageCache = ContainerUtil.createConcurrentSoftValueMap();
  private final ConcurrentMap<GlobalSearchScope, Map<String, PsiClass>> myClassCache = ContainerUtil.createConcurrentWeakKeySoftValueMap();
  private final Project myProject;
  private final JavaFileManager myFileManager;
  private final AtomicNotNullLazyValue<JvmFacadeImpl> myJvmFacade;
  private final JvmPsiConversionHelper myConversionHelper;

  public JavaPsiFacadeImpl(Project project,
                           PsiManager psiManager,
                           JavaFileManager javaFileManager,
                           MessageBus bus) {
    myProject = project;
    myFileManager = javaFileManager;
    myConstantEvaluationHelper = new PsiConstantEvaluationHelperImpl();
    myJvmFacade = AtomicNotNullLazyValue.createValue(() -> (JvmFacadeImpl)JvmFacade.getInstance(project));
    myConversionHelper = new JvmPsiConversionHelper(psiManager);

    final PsiModificationTracker modificationTracker = psiManager.getModificationTracker();

    if (bus != null) {
      bus.connect().subscribe(PsiModificationTracker.TOPIC, new PsiModificationTracker.Listener() {
        private long lastTimeSeen = -1L;

        @Override
        public void modificationCountChanged() {
          myClassCache.clear();
          final long now = modificationTracker.getJavaStructureModificationCount();
          if (lastTimeSeen != now) {
            lastTimeSeen = now;
            myPackageCache.clear();
          }
        }
      });
    }

    DummyHolderFactory.setFactory(new JavaDummyHolderFactory());
    myElementFinders = new SimpleSmartExtensionPoint<PsiElementFinder>(Collections.emptyList()) {
      @NotNull
      @Override
      protected ExtensionPoint<PsiElementFinder> getExtensionPoint() {
        return Extensions.getArea(myProject).getExtensionPoint(PsiElementFinder.EP_NAME);
      }
    };
  }

  @Override
  public PsiClass findClass(@NotNull final String qualifiedName, @NotNull GlobalSearchScope scope) {
    ProgressIndicatorProvider.checkCanceled(); // We hope this method is being called often enough to cancel daemon processes smoothly

    Map<String, PsiClass> map = myClassCache.get(scope);
    if (map == null) {
      map = ContainerUtil.createConcurrentWeakValueMap();
      map = ConcurrencyUtil.cacheOrGet(myClassCache, scope, map);
    }
    PsiClass result = map.get(qualifiedName);
    if (result == null) {
      result = doFindClass(qualifiedName, scope);
      if (result != null) {
        map.put(qualifiedName, result);
      }
    }

    return result;
  }

  @Nullable
  private PsiClass doFindClass(@NotNull String qualifiedName, @NotNull GlobalSearchScope scope) {
    if (shouldUseSlowResolve()) {
      PsiClass[] classes = findClassesInDumbMode(qualifiedName, scope);
      if (classes.length != 0) {
        return classes[0];
      }
      return null;
    }

    List<PsiElementFinder> finders = finders();
    Condition<PsiClass> classesFilter = getFilterFromFinders(scope, finders);

    for (PsiElementFinder finder : finders) {
      PsiClass aClass = finder.findClass(qualifiedName, scope);
      if (aClass != null && (classesFilter == null || classesFilter.value(aClass))) {
        return aClass;
      }
    }

    return null;
  }

  @NotNull
  private PsiClass[] findClassesInDumbMode(@NotNull String qualifiedName, @NotNull GlobalSearchScope scope) {
    final String packageName = StringUtil.getPackageName(qualifiedName);
    final PsiPackage pkg = findPackage(packageName);
    final String className = StringUtil.getShortName(qualifiedName);
    if (pkg == null && packageName.length() < qualifiedName.length()) {
      PsiClass[] containingClasses = findClassesInDumbMode(packageName, scope);
      if (containingClasses.length == 1) {
        return PsiElementFinder.filterByName(className, containingClasses[0].getInnerClasses());
      }

      return PsiClass.EMPTY_ARRAY;
    }

    if (pkg == null) {
      return PsiClass.EMPTY_ARRAY;
    }

    return pkg.findClassByShortName(className, scope);
  }

  @Override
  @NotNull
  public PsiClass[] findClasses(@NotNull String qualifiedName, @NotNull GlobalSearchScope scope) {
    List<PsiClass> allClasses = findClassesWithJvmFacade(qualifiedName, scope);
    return allClasses.isEmpty() ? PsiClass.EMPTY_ARRAY : allClasses.toArray(PsiClass.EMPTY_ARRAY);
  }

  @NotNull
  private List<PsiClass> findClassesWithJvmFacade(@NotNull String qualifiedName, @NotNull GlobalSearchScope scope) {
    List<PsiClass> result = null;

    final List<PsiClass> ownClasses = findClassesWithoutJvmFacade(qualifiedName, scope);
    if (!ownClasses.isEmpty()) {
      result = new ArrayList<>(ownClasses);
    }

    final List<JvmClass> jvmClasses = myJvmFacade.getValue().findClassesWithoutJavaFacade(qualifiedName, scope);
    if (!jvmClasses.isEmpty()) {
      final List<PsiClass> jvmPsiClasses = ContainerUtil.map(jvmClasses, it -> myConversionHelper.toPsiClass(it));
      if (result == null) {
        result = new ArrayList<>(jvmPsiClasses);
      }
      else {
        result.addAll(jvmPsiClasses);
      }
    }

    return result == null ? Collections.emptyList() : result;
  }

  @NotNull
  public List<PsiClass> findClassesWithoutJvmFacade(@NotNull String qualifiedName, @NotNull GlobalSearchScope scope) {
    if (shouldUseSlowResolve()) {
      return Arrays.asList(findClassesInDumbMode(qualifiedName, scope));
    }
    List<PsiElementFinder> finders = finders();
    Condition<PsiClass> classesFilter = getFilterFromFinders(scope, finders);

    List<PsiClass> result = null;
    for (PsiElementFinder finder : finders) {
      PsiClass[] finderClasses = finder.findClasses(qualifiedName, scope);
      if (finderClasses.length != 0) {
        if (result == null) result = new ArrayList<>(finderClasses.length);
        filterClassesAndAppend(finder, classesFilter, finderClasses, result);
      }
    }

    return result == null ? Collections.emptyList() : result;
  }

  private static Condition<PsiClass> getFilterFromFinders(@NotNull GlobalSearchScope scope, @NotNull List<PsiElementFinder> finders) {
    Condition<PsiClass> filter = null;
    for (PsiElementFinder finder : finders) {
      Condition<PsiClass> finderFilter = finder.getClassesFilter(scope);
      if (finderFilter != null) {
        filter = filter == null ? finderFilter : Conditions.and(filter, finderFilter);
      }
    }
    return filter;
  }

  private boolean shouldUseSlowResolve() {
    DumbService dumbService = DumbService.getInstance(getProject());
    return dumbService.isDumb() && dumbService.isAlternativeResolveEnabled();
  }

  private List<PsiElementFinder> finders() {
    return myElementFinders.getExtensions();
  }

  @Override
  @NotNull
  public PsiConstantEvaluationHelper getConstantEvaluationHelper() {
    return myConstantEvaluationHelper;
  }

  @Override
  public PsiPackage findPackage(@NotNull String qualifiedName) {
    PsiPackage aPackage = myPackageCache.get(qualifiedName);
    if (aPackage != null) {
      return aPackage;
    }

    for (PsiElementFinder finder : filteredFinders()) {
      aPackage = finder.findPackage(qualifiedName);
      if (aPackage != null) {
        return ConcurrencyUtil.cacheOrGet(myPackageCache, qualifiedName, aPackage);
      }
    }

    return null;
  }

  @NotNull
  private List<PsiElementFinder> filteredFinders() {
    return DumbService.getInstance(getProject()).filterByDumbAwareness(finders());
  }

  @Override
  @NotNull
  public PsiJavaParserFacade getParserFacade() {
    return getElementFactory(); // TODO: lighter implementation which doesn't mark all the elements as generated.
  }

  @Override
  @NotNull
  public PsiResolveHelper getResolveHelper() {
    return PsiResolveHelper.SERVICE.getInstance(myProject);
  }

  @Override
  @NotNull
  public PsiNameHelper getNameHelper() {
    return PsiNameHelper.getInstance(myProject);
  }

  @NotNull
  public Set<String> getClassNames(@NotNull PsiPackage psiPackage, @NotNull GlobalSearchScope scope) {
    Set<String> result = new THashSet<>();
    for (PsiElementFinder finder : filteredFinders()) {
      result.addAll(finder.getClassNames(psiPackage, scope));
    }
    return result;
  }

  @NotNull
  public PsiClass[] getClasses(@NotNull PsiPackage psiPackage, @NotNull GlobalSearchScope scope) {
    List<PsiElementFinder> finders = filteredFinders();
    Condition<PsiClass> classesFilter = getFilterFromFinders(scope, finders);

    List<PsiClass> result = null;
    for (PsiElementFinder finder : finders) {
      PsiClass[] classes = finder.getClasses(psiPackage, scope);
      if (classes.length == 0) continue;
      if (result == null) result = new ArrayList<>(classes.length);
      filterClassesAndAppend(finder, classesFilter, classes, result);
    }

    return result == null ? PsiClass.EMPTY_ARRAY : result.toArray(new PsiClass[result.size()]);
  }

  private static void filterClassesAndAppend(PsiElementFinder finder,
                                             @Nullable Condition<PsiClass> classesFilter,
                                             @NotNull PsiClass[] classes,
                                             @NotNull List<PsiClass> result) {
    for (PsiClass psiClass : classes) {
      if (psiClass == null) {
        LOG.error("Finder " + finder + " returned null PsiClass");
        continue;
      }
      if (classesFilter == null || classesFilter.value(psiClass)) {
        result.add(psiClass);
      }
    }
  }

  @NotNull
  public PsiFile[] getPackageFiles(@NotNull PsiPackage psiPackage, @NotNull GlobalSearchScope scope) {
    Condition<PsiFile> filter = null;

    for (PsiElementFinder finder : filteredFinders()) {
      Condition<PsiFile> finderFilter = finder.getPackageFilesFilter(psiPackage, scope);
      if (finderFilter != null) {
        if (filter == null) {
          filter = finderFilter;
        }
        else {
          filter = Conditions.and(filter, finderFilter);
        }
      }
    }

    Set<PsiFile> result = new LinkedHashSet<>();
    PsiDirectory[] directories = psiPackage.getDirectories(scope);
    for (PsiDirectory directory : directories) {
      for (PsiFile file : directory.getFiles()) {
        if (filter == null || filter.value(file)) {
          result.add(file);
        }
      }
    }

    for (PsiElementFinder finder : filteredFinders()) {
      Collections.addAll(result, finder.getPackageFiles(psiPackage, scope));
    }
    return result.toArray(new PsiFile[result.size()]);
  }

  public boolean processPackageDirectories(@NotNull PsiPackage psiPackage,
                                           @NotNull GlobalSearchScope scope,
                                           @NotNull Processor<PsiDirectory> consumer,
                                           boolean includeLibrarySources) {
    for (PsiElementFinder finder : filteredFinders()) {
      if (!finder.processPackageDirectories(psiPackage, scope, consumer, includeLibrarySources)) {
        return false;
      }
    }
    return true;
  }

  @NotNull
  public PsiPackage[] getSubPackages(@NotNull PsiPackage psiPackage, @NotNull GlobalSearchScope scope) {
    LinkedHashMap<String, PsiPackage> result = new LinkedHashMap<>();
    for (PsiElementFinder finder : filteredFinders()) {
      // Ensure uniqueness of names in the returned list of subpackages. If a plugin PsiElementFinder
      // returns the same package from its getSubPackages() implementation that Java already knows about
      // (the Kotlin plugin can do that), the Java package takes precedence.
      PsiPackage[] packages = finder.getSubPackages(psiPackage, scope);
      for (PsiPackage aPackage : packages) {
        if (result.get(aPackage.getName()) == null) {
          result.put(aPackage.getName(), aPackage);
        }
      }
    }
    return result.values().toArray(new PsiPackage[result.size()]);
  }

  @Override
  public boolean isPartOfPackagePrefix(@NotNull String packageName) {
    final Collection<String> packagePrefixes = myFileManager.getNonTrivialPackagePrefixes();
    for (final String subpackageName : packagePrefixes) {
      if (PsiNameHelper.isSubpackageOf(subpackageName, packageName)) return true;
    }
    return false;
  }

  @Override
  public boolean isInPackage(@NotNull PsiElement element, @NotNull PsiPackage aPackage) {
    final PsiFile file = FileContextUtil.getContextFile(element);
    if (file instanceof JavaDummyHolder) {
      return ((JavaDummyHolder) file).isInPackage(aPackage);
    }
    if (file instanceof PsiJavaFile) {
      final String packageName = ((PsiJavaFile) file).getPackageName();
      return packageName.equals(aPackage.getQualifiedName());
    }
    return false;
  }

  @Override
  public boolean arePackagesTheSame(@NotNull PsiElement element1, @NotNull PsiElement element2) {
    PsiFile file1 = FileContextUtil.getContextFile(element1);
    PsiFile file2 = FileContextUtil.getContextFile(element2);
    if (Comparing.equal(file1, file2)) return true;
    if (file1 instanceof JavaDummyHolder && file2 instanceof JavaDummyHolder) return true;
    if (file1 instanceof JavaDummyHolder || file2 instanceof JavaDummyHolder) {
      JavaDummyHolder dummyHolder = (JavaDummyHolder) (file1 instanceof JavaDummyHolder ? file1 : file2);
      PsiElement other = file1 instanceof JavaDummyHolder ? file2 : file1;
      return dummyHolder.isSamePackage(other);
    }
    if (!(file1 instanceof PsiClassOwner)) return false;
    if (!(file2 instanceof PsiClassOwner)) return false;
    String package1 = ((PsiClassOwner) file1).getPackageName();
    String package2 = ((PsiClassOwner) file2).getPackageName();
    return Comparing.equal(package1, package2);
  }

  @Override
  @NotNull
  public Project getProject() {
    return myProject;
  }

  @Override
  public boolean isConstantExpression(@NotNull PsiExpression expression) {
    IsConstantExpressionVisitor visitor = new IsConstantExpressionVisitor();
    expression.accept(visitor);
    return visitor.isConstant();
  }

  @Override
  @NotNull
  public PsiElementFactory getElementFactory() {
    return PsiElementFactory.SERVICE.getInstance(myProject);
  }
}
