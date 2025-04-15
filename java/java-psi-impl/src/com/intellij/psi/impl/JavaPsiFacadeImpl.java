// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl;

import com.intellij.lang.jvm.JvmClass;
import com.intellij.lang.jvm.facade.JvmFacade;
import com.intellij.lang.jvm.facade.JvmFacadeImpl;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicatorProvider;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.DumbUtil;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootModificationTracker;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.impl.file.impl.JavaFileManager;
import com.intellij.psi.impl.source.DummyHolderFactory;
import com.intellij.psi.impl.source.JavaDummyHolder;
import com.intellij.psi.impl.source.JavaDummyHolderFactory;
import com.intellij.psi.impl.source.resolve.FileContextUtil;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiClassUtil;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.util.ConcurrencyUtil;
import com.intellij.util.Processor;
import com.intellij.util.containers.CollectionFactory;
import com.intellij.util.containers.ConcurrentFactoryMap;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.messages.MessageBus;
import kotlinx.coroutines.CoroutineScope;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Predicate;

public final class JavaPsiFacadeImpl extends JavaPsiFacadeEx {
  private static final Logger LOG = Logger.getInstance(JavaPsiFacadeImpl.class);

  private final PsiConstantEvaluationHelper myConstantEvaluationHelper;
  private final ConcurrentMap<String, PsiPackage> myPackageCache = ContainerUtil.createConcurrentSoftValueMap();

  private final ConcurrentMap<GlobalSearchScope, Map<String, Optional<PsiClass>>> myClassCache = ContainerUtil.createConcurrentSoftKeySoftValueMap();
  private final Project myProject;
  private final JavaFileManager myFileManager;
  private final NotNullLazyValue<JvmFacadeImpl> myJvmFacade;
  private final JvmPsiConversionHelper myConversionHelper;

  private final ThreadLocal<Boolean> myTemporaryScopeCacheEnabled = ThreadLocal.withInitial(() -> Boolean.FALSE);
  @SuppressWarnings("SSBasedInspection")
  private final ThreadLocal<Set<GlobalSearchScope>> myCachedTemporaryScopes = ThreadLocal.withInitial(() -> new HashSet<>());

  public JavaPsiFacadeImpl(@NotNull Project project, @Nullable CoroutineScope coroutineScope) {
    myProject = project;
    myFileManager = JavaFileManager.getInstance(myProject);
    myConstantEvaluationHelper = new PsiConstantEvaluationHelperImpl();
    myJvmFacade = NotNullLazyValue.atomicLazy(() -> (JvmFacadeImpl)JvmFacade.getInstance(project));
    myConversionHelper = JvmPsiConversionHelper.getInstance(myProject);

    MessageBus bus = project.getMessageBus();
    (coroutineScope == null ? bus.simpleConnect() : bus.connect(coroutineScope)).subscribe(PsiModificationTracker.TOPIC, () -> {
      myClassCache.clear();
      myPackageCache.clear();
      myCachedTemporaryScopes.get().clear();
      myCachedTemporaryScopes.remove();
    });

    DummyHolderFactory.setFactory(new JavaDummyHolderFactory());
  }

  /**
   * @deprecated Use {@link JavaPsiFacade#getInstance(Project)}
   */
  @Deprecated
  public JavaPsiFacadeImpl(@NotNull Project project) {
    this(project, null);
  }

  @Override
  public PsiClass findClass(final @NotNull String qualifiedName, @NotNull GlobalSearchScope scope) {
    ProgressIndicatorProvider.checkCanceled(); // We hope this method is being called often enough to cancel daemon processes smoothly

    if (myTemporaryScopeCacheEnabled.get()) {
      myCachedTemporaryScopes.get().add(scope);
    }
    Map<String, Optional<PsiClass>> map = myClassCache.computeIfAbsent(scope, scope1 -> CollectionFactory.createConcurrentWeakValueMap());
    Optional<PsiClass> result = map.get(qualifiedName);
    if (result == null) {
      RecursionGuard.StackStamp stamp = RecursionManager.markStack();
      result = Optional.ofNullable(doFindClass(qualifiedName, scope));
      if (stamp.mayCacheNow()) {
        map.put(qualifiedName, result);
      }
    }

    return result.orElse(null);
  }

  private @Nullable PsiClass doFindClass(@NotNull String qualifiedName, @NotNull GlobalSearchScope scope) {
    if (shouldUseSlowResolve()) {
      PsiClass[] classes = findClassesInDumbMode(qualifiedName, scope);
      if (classes.length != 0) {
        return classes[0];
      }
      return null;
    }
    List<PsiElementFinder> finders = filteredFinders();
    Predicate<PsiClass> classesFilter = getFilterFromFinders(scope, finders);
    PsiClass bestClass = null;
    for (PsiElementFinder finder : finders) {
      try {
        PsiClass candidateClass = finder.findClass(qualifiedName, scope);
        if (candidateClass == null) continue;
        if (classesFilter != null && !classesFilter.test(candidateClass)) continue;
        if (bestClass != null && PsiClassUtil.createScopeComparator(scope).compare(candidateClass, bestClass) >= 0) continue;
        bestClass = candidateClass;
      }
      catch (IndexNotReadyException ex) {
        handleIndexNotReadyException(ex);
      }
    }
    return bestClass;
  }

  private PsiClass @NotNull [] findClassesInDumbMode(@NotNull String qualifiedName, @NotNull GlobalSearchScope scope) {
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
  
  private boolean hasClassInDumbMode(@NotNull String qualifiedName, @NotNull GlobalSearchScope scope) {
    final String packageName = StringUtil.getPackageName(qualifiedName);
    final PsiPackage pkg = findPackage(packageName);
    final String className = StringUtil.getShortName(qualifiedName);
    if (pkg != null) {
      return pkg.hasClassWithShortName(className, scope);
    }
    if (packageName.length() < qualifiedName.length()) {
      PsiClass[] containingClasses = findClassesInDumbMode(packageName, scope);
      for (PsiClass containingClass : containingClasses) {
        if (ContainerUtil.exists(containingClass.getInnerClasses(), innerClass -> className.equals(innerClass.getName()))) {
          return true;
        }
      }
    }
    return false;
  }

  @Override
  public PsiClass @NotNull [] findClasses(@NotNull String qualifiedName, @NotNull GlobalSearchScope scope) {
    List<PsiClass> allClasses = findClassesWithJvmFacade(qualifiedName, scope);
    return allClasses.isEmpty() ? PsiClass.EMPTY_ARRAY : allClasses.toArray(PsiClass.EMPTY_ARRAY);
  }

  @Override
  public boolean hasClass(@NotNull String qualifiedName, @NotNull GlobalSearchScope scope) {
    if (shouldUseSlowResolve()) {
      return hasClassInDumbMode(qualifiedName, scope);
    }
    List<PsiElementFinder> finders = filteredFinders();
    Predicate<PsiClass> classesFilter = getFilterFromFinders(scope, finders);
    if (classesFilter == null) {
      classesFilter = Predicates.alwaysTrue();
    }
    for (PsiElementFinder finder : finders) {
      if (finder.hasClass(qualifiedName, scope, classesFilter)) {
        return true;
      }
    }
    return !myJvmFacade.getValue().findClassesWithoutJavaFacade(qualifiedName, scope).isEmpty();
  }

  private @NotNull List<PsiClass> findClassesWithJvmFacade(@NotNull String qualifiedName, @NotNull GlobalSearchScope scope) {
    List<PsiClass> result = null;

    final List<PsiClass> ownClasses = findClassesWithoutJvmFacade(qualifiedName, scope);
    if (!ownClasses.isEmpty()) {
      result = new ArrayList<>(ownClasses);
    }

    final List<JvmClass> jvmClasses = myJvmFacade.getValue().findClassesWithoutJavaFacade(qualifiedName, scope);
    if (!jvmClasses.isEmpty()) {
      final List<PsiClass> jvmPsiClasses = ContainerUtil.map(jvmClasses, it -> myConversionHelper.convertTypeDeclaration(it));
      if (result == null) {
        result = new ArrayList<>(jvmPsiClasses);
      }
      else {
        result.addAll(jvmPsiClasses);
      }
    }

    return result == null ? Collections.emptyList() : result;
  }

  public @NotNull List<PsiClass> findClassesWithoutJvmFacade(@NotNull String qualifiedName, @NotNull GlobalSearchScope scope) {
    if (shouldUseSlowResolve()) {
      return Arrays.asList(findClassesInDumbMode(qualifiedName, scope));
    }
    List<PsiElementFinder> finders = filteredFinders();
    Predicate<PsiClass> classesFilter = getFilterFromFinders(scope, finders);

    List<PsiClass> result = null;
    for (PsiElementFinder finder : finders) {
      try {
        PsiClass[] finderClasses = finder.findClasses(qualifiedName, scope);
        if (finderClasses.length != 0) {
          if (result == null) result = new ArrayList<>(finderClasses.length);
          filterClassesAndAppend(finder, classesFilter, finderClasses, result);
        }
      }
      catch (IndexNotReadyException ex) {
        handleIndexNotReadyException(ex);
      }
    }
    return result == null ? Collections.emptyList() : result;
  }

  private static @Nullable Predicate<PsiClass> getFilterFromFinders(@NotNull GlobalSearchScope scope, @NotNull List<? extends PsiElementFinder> finders) {
    Predicate<PsiClass> filter = null;
    for (PsiElementFinder finder : finders) {
      try {
        Predicate<PsiClass> finderFilter = finder.getClassesFilter(scope);
        if (finderFilter != null) {
          filter = filter == null ? finderFilter : filter.and(finderFilter);
        }
      }
      catch (IndexNotReadyException ex) {
        handleIndexNotReadyException(ex);
      }
    }
    return filter;
  }

  private boolean shouldUseSlowResolve() {
    DumbService dumbService = DumbService.getInstance(getProject());
    return dumbService.isAlternativeResolveEnabled();
  }

  @Override
  public @NotNull PsiConstantEvaluationHelper getConstantEvaluationHelper() {
    return myConstantEvaluationHelper;
  }

  @Override
  public PsiPackage findPackage(@NotNull String qualifiedName) {
    PsiPackage aPackage = myPackageCache.get(qualifiedName);
    if (aPackage != null) {
      return aPackage;
    }
    for (PsiElementFinder finder : filteredFinders()) {
      try {
        aPackage = finder.findPackage(qualifiedName);
        if (aPackage != null) {
          return ConcurrencyUtil.cacheOrGet(myPackageCache, qualifiedName, aPackage);
        }
      }
      catch (IndexNotReadyException ex) {
        handleIndexNotReadyException(ex);
      }
    }
    return null;
  }

  @Override
  public PsiJavaModule findModule(@NotNull String moduleName, @NotNull GlobalSearchScope scope) {
    Collection<PsiJavaModule> modules = findModules(moduleName, scope);
    return modules.size() == 1 ? modules.iterator().next() : null;
  }

  @Override
  public @NotNull Collection<PsiJavaModule> findModules(@NotNull String moduleName, @NotNull GlobalSearchScope scope) {
    JavaFileManager javaFileManager = JavaFileManager.getInstance(myProject);
    //it can be called in dumb mode
    //see com.intellij.psi.impl.JavaPlatformModuleSystem.accessibleFromLoadedModules
    //but in this case it must not be cached!
    if (DumbService.isDumb(myProject)) {
      if (DumbUtil.getInstance(myProject).mayUseIndices()) {
        return javaFileManager.findModules(moduleName, scope);
      }
      else {
        //no options
        return Collections.emptyList();
      }
    }
    return CachedValuesManager.getManager(myProject)
      .getCachedValue(myProject, () -> {
        Map<GlobalSearchScope, Map<String, Collection<PsiJavaModule>>> scope2ModulesMap =
          ConcurrentFactoryMap.create(searchScope ->
                                        ConcurrentFactoryMap.create(name -> javaFileManager.findModules(name, searchScope), () -> CollectionFactory.createConcurrentWeakValueMap()),
                                      () -> ContainerUtil.createConcurrentSoftKeySoftValueMap());
        return new CachedValueProvider.Result<>(scope2ModulesMap, 
                                                PsiJavaModuleModificationTracker.getInstance(myProject),
                                                ProjectRootModificationTracker.getInstance(myProject));
      })
      .get(scope)
      .get(moduleName);
  }

  private @NotNull List<PsiElementFinder> filteredFinders() {
    return DumbService.getInstance(getProject()).filterByDumbAwareness(PsiElementFinder.EP.getPoint(myProject).getExtensionList());
  }

  @Override
  public @NotNull PsiJavaParserFacade getParserFacade() {
    return getElementFactory(); // TODO: lighter implementation which doesn't mark all the elements as generated.
  }

  @Override
  public @NotNull PsiResolveHelper getResolveHelper() {
    return PsiResolveHelper.getInstance(myProject);
  }

  @Override
  public @NotNull PsiNameHelper getNameHelper() {
    return PsiNameHelper.getInstance(myProject);
  }

  public @NotNull Set<String> getClassNames(@NotNull PsiPackage psiPackage, @NotNull GlobalSearchScope scope) {
    Set<String> result = new HashSet<>();
    for (PsiElementFinder finder : filteredFinders()) {
      try {
        result.addAll(finder.getClassNames(psiPackage, scope));
      }
      catch (IndexNotReadyException ex) {
        handleIndexNotReadyException(ex);
      }
    }
    return result;
  }

  public PsiClass @NotNull [] getClasses(@NotNull PsiPackage psiPackage, @NotNull GlobalSearchScope scope) {
    List<PsiElementFinder> finders = filteredFinders();
    Predicate<PsiClass> classesFilter = getFilterFromFinders(scope, finders);
    List<PsiClass> result = null;
    for (PsiElementFinder finder : finders) {
      try {
        PsiClass[] classes = finder.getClasses(psiPackage, scope);
        if (classes.length == 0) continue;
        if (result == null) result = new ArrayList<>(classes.length);
        filterClassesAndAppend(finder, classesFilter, classes, result);
      }
      catch (IndexNotReadyException ex) {
        handleIndexNotReadyException(ex);
      }
    }
    return result == null ? PsiClass.EMPTY_ARRAY : result.toArray(PsiClass.EMPTY_ARRAY);
  }

  private static void filterClassesAndAppend(PsiElementFinder finder,
                                             @Nullable Predicate<? super PsiClass> classesFilter,
                                             PsiClass @NotNull [] classes,
                                             @NotNull List<? super PsiClass> result) {
    for (PsiClass psiClass : classes) {
      if (psiClass == null) {
        LOG.error("Finder " + finder + " returned null PsiClass");
        continue;
      }
      if (classesFilter == null || classesFilter.test(psiClass)) {
        result.add(psiClass);
      }
    }
  }

  public PsiFile @NotNull [] getPackageFiles(@NotNull PsiPackage psiPackage, @NotNull GlobalSearchScope scope) {
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
      try {
        Collections.addAll(result, finder.getPackageFiles(psiPackage, scope));
      }
      catch (IndexNotReadyException ex) {
      }
    }
    return result.toArray(PsiFile.EMPTY_ARRAY);
  }

  public boolean processPackageDirectories(@NotNull PsiPackage psiPackage,
                                           @NotNull GlobalSearchScope scope,
                                           @NotNull Processor<? super PsiDirectory> consumer,
                                           boolean includeLibrarySources) {
    for (PsiElementFinder finder : filteredFinders()) {
      try {
        if (!finder.processPackageDirectories(psiPackage, scope, consumer, includeLibrarySources)) {
          return false;
        }
      }
      catch (IndexNotReadyException ex) {
        handleIndexNotReadyException(ex);
      }
    }
    return true;
  }

  public PsiPackage @NotNull [] getSubPackages(@NotNull PsiPackage psiPackage, @NotNull GlobalSearchScope scope) {
    Map<String, PsiPackage> result = new LinkedHashMap<>();
    for (PsiElementFinder finder : filteredFinders()) {
      // Ensure uniqueness of names in the returned list of subpackages. If a plugin PsiElementFinder
      // returns the same package from its getSubPackages() implementation that Java already knows about
      // (the Kotlin plugin can do that), the Java package takes precedence.
      try {
        PsiPackage[] packages = finder.getSubPackages(psiPackage, scope);
        for (PsiPackage aPackage : packages) {
          result.putIfAbsent(aPackage.getName(), aPackage);
        }
      }
      catch (IndexNotReadyException ex) {
        handleIndexNotReadyException(ex);
      }
    }
    return result.values().toArray(PsiPackage.EMPTY_ARRAY);
  }

  private static void handleIndexNotReadyException(@NotNull IndexNotReadyException ex) {
    if (Registry.is("ide.dumb.mode.check.awareness")) throw ex;
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
    return Objects.equals(package1, package2);
  }

  @Override
  public @NotNull Project getProject() {
    return myProject;
  }

  @Override
  public boolean isConstantExpression(@NotNull PsiExpression expression) {
    IsConstantExpressionVisitor visitor = new IsConstantExpressionVisitor();
    expression.accept(visitor);
    return visitor.isConstant();
  }

  @Override
  public @NotNull PsiElementFactory getElementFactory() {
    return PsiElementFactory.getInstance(myProject);
  }

  @ApiStatus.Internal
  @Override
  public <T> T withTemporaryScopeCaches(Callable<T> callable) {
    Boolean previousValue = myTemporaryScopeCacheEnabled.get();
    myTemporaryScopeCacheEnabled.set(Boolean.TRUE);
    try {
      return callable.call();
    }
    catch (Exception e) {
      throw new RuntimeException(e);
    }
    finally {
      for (GlobalSearchScope scope : myCachedTemporaryScopes.get()) {
        myClassCache.remove(scope);
      }
      myCachedTemporaryScopes.get().clear();
      myCachedTemporaryScopes.remove();
      myTemporaryScopeCacheEnabled.set(previousValue);
    }
  }

  @Override
  public boolean temporaryScopeCachesEnabled() {
    return myTemporaryScopeCacheEnabled.get();
  }
}
