// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.file;

import com.intellij.codeInsight.completion.scope.JavaCompletionHints;
import com.intellij.codeInsight.multiverse.CodeInsightContexts;
import com.intellij.core.CoreJavaDirectoryService;
import com.intellij.lang.Language;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.navigation.ItemPresentation;
import com.intellij.navigation.ItemPresentationProviders;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.openapi.ui.Queryable;
import com.intellij.openapi.util.*;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.impl.JavaPsiFacadeImpl;
import com.intellij.psi.impl.source.tree.java.PsiCompositeModifierList;
import com.intellij.psi.scope.ElementClassHint;
import com.intellij.psi.scope.NameHint;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiSearchScopeUtil;
import com.intellij.psi.util.*;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Processor;
import com.intellij.util.Processors;
import com.intellij.util.containers.ContainerUtil;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.lang.ref.SoftReference;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static com.intellij.reference.SoftReference.dereference;

public class PsiPackageImpl extends PsiPackageBase implements PsiPackage, Queryable {
  private static final Logger LOG = Logger.getInstance(PsiPackageImpl.class);

  private volatile CachedValue<PsiModifierList> myAnnotationList;
  private volatile SoftReference<Map<String, PsiClass[]>> myClassCache;
  private volatile CachedValue<Collection<PsiDirectory>> myDirectories;
  private volatile CachedValue<Collection<PsiDirectory>> myDirectoriesWithLibSources;
  private volatile CachedValue<Collection<PsiFile>> myFiles;
  private volatile SoftReference<Map<GlobalSearchScope, Map<String, PsiClass[]>>> myDumbModeFullCache;
  private volatile SoftReference<Map<Pair<GlobalSearchScope, String>, PsiClass[]>> myDumbModePartialCache;

  public PsiPackageImpl(PsiManager manager, String qualifiedName) {
    super(manager, qualifiedName);
  }

  @Override
  protected @Unmodifiable Collection<PsiDirectory> getAllDirectories(@NotNull GlobalSearchScope scope) {
    if (scope.isForceSearchingInLibrarySources()) {
      if (myDirectoriesWithLibSources == null) {
        myDirectoriesWithLibSources = createCachedDirectories(true);
      }
      return ContainerUtil.filter(myDirectoriesWithLibSources.getValue(), d -> scope.contains(d.getVirtualFile()));
    }
    else {
      if (myDirectories == null) {
        myDirectories = createCachedDirectories(false);
      }
      return ContainerUtil.filter(myDirectories.getValue(), d -> scope.contains(d.getVirtualFile()));
    }
  }

  @Override
  public @Unmodifiable @NotNull Collection<@NotNull PsiFile> getIndividualFiles(@NotNull GlobalSearchScope scope) {
    if (myFiles == null) {
      myFiles = createCachedFiles();
    }
    return ContainerUtil.filter(myFiles.getValue(), d -> scope.contains(d.getVirtualFile()));
  }

  private @NotNull CachedValue<Collection<PsiDirectory>> createCachedDirectories(final boolean includeLibrarySources) {
    return CachedValuesManager.getManager(getProject()).createCachedValue(() -> {
      Collection<PsiDirectory> result = new ArrayList<>();
      Processor<PsiDirectory> processor = Processors.cancelableCollectProcessor(result);
      getFacade().processPackageDirectories(this, allScope(), processor, includeLibrarySources);
      return CachedValueProvider.Result.create(result, PsiPackageImplementationHelper.getInstance().getDirectoryCachedValueDependencies(this));
    }, false);
  }

  private @NotNull CachedValue<Collection<PsiFile>> createCachedFiles() {
    return CachedValuesManager.getManager(getProject()).createCachedValue(() -> {
      Collection<PsiFile> result = new ArrayList<>();
      Processor<PsiFile> processor = Processors.cancelableCollectProcessor(result);
      getFacade().processPackageFiles(this, allScope(), processor);
      return CachedValueProvider.Result.create(result, PsiPackageImplementationHelper.getInstance().getDirectoryCachedValueDependencies(this));
    }, false);
  }

  @Override
  protected PsiPackageImpl findPackage(@NotNull String qName) {
    return (PsiPackageImpl)getFacade().findPackage(qName);
  }

  @Override
  public void handleQualifiedNameChange(final @NotNull String newQualifiedName) {
    PsiPackageImplementationHelper.getInstance().handleQualifiedNameChange(this, newQualifiedName);
  }

  @Override
  public VirtualFile @NotNull [] occursInPackagePrefixes() {
    return PsiPackageImplementationHelper.getInstance().occursInPackagePrefixes(this);
  }

  @Override
  public PsiPackageImpl getParentPackage() {
    return (PsiPackageImpl)super.getParentPackage();
  }

  @Override
  public @NotNull Language getLanguage() {
    return JavaLanguage.INSTANCE;
  }

  @Override
  public boolean isValid() {
    return !getProject().isDisposed();
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor)visitor).visitPackage(this);
    }
    else {
      visitor.visitElement(this);
    }
  }

  @Override
  public String toString() {
    return "PsiPackage:" + getQualifiedName();
  }

  @Override
  public PsiClass @NotNull [] getClasses() {
    return getClasses(allScope());
  }

  protected @NotNull GlobalSearchScope allScope() {
    return PsiPackageImplementationHelper.getInstance().adjustAllScope(this, GlobalSearchScope.allScope(getProject()));
  }

  @Override
  public PsiClass @NotNull [] getClasses(@NotNull GlobalSearchScope scope) {
    return getFacade().getClasses(this, scope);
  }

  @Override
  public PsiFile @NotNull [] getFiles(@NotNull GlobalSearchScope scope) {
    return getFacade().getPackageFiles(this, scope);
  }

  @Override
  public @Nullable PsiModifierList getAnnotationList() {
    if (myAnnotationList == null) {
      myAnnotationList = CachedValuesManager.getManager(getProject()).createCachedValue(new PackageAnnotationValueProvider(), false);
    }
    return myAnnotationList.getValue();
  }

  @Override
  public PsiPackage @NotNull [] getSubPackages() {
    return getSubPackages(allScope());
  }

  @Override
  public PsiPackage @NotNull [] getSubPackages(@NotNull GlobalSearchScope scope) {
    return getFacade().getSubPackages(this, scope);
  }

  private JavaPsiFacadeImpl getFacade() {
    return (JavaPsiFacadeImpl)JavaPsiFacade.getInstance(getProject());
  }

  private PsiClass @NotNull [] getCachedClassesByName(@NotNull String name, GlobalSearchScope scope) {
    DumbService dumbService = DumbService.getInstance(getProject());
    if (dumbService.isAlternativeResolveEnabled()) {
      return getCachedClassesInDumbMode(name, scope);
    }

    if (CodeInsightContexts.isSharedSourceSupportEnabled(getProject())) {
      // todo IJPL-339 this line introduces performance degradation, see IDEA-367535
      return findAllClasses(name, scope);
    }
    else {
      Map<String, PsiClass[]> map = dereference(myClassCache);
      if (map == null) {
        myClassCache = new SoftReference<>(map = ContainerUtil.createConcurrentSoftValueMap());
      }
      PsiClass[] classes = map.get(name);
      if (classes != null) {
        return classes;
      }

      RecursionGuard.StackStamp stamp = RecursionManager.markStack();
      classes = findAllClasses(name, GlobalSearchScope.everythingScope(getProject()));
      if (stamp.mayCacheNow()) {
        map.put(name, classes);
      }
      return classes;
    }
  }

  private PsiClass @NotNull [] findAllClasses(@NotNull String shortName, @NotNull GlobalSearchScope scope) {
    String qName = getQualifiedName();
    String classQName = !qName.isEmpty() ? qName + "." + shortName : shortName;
    return getFacade().findClasses(classQName, scope);
  }

  private PsiClass @NotNull [] getCachedClassesInDumbMode(String name, GlobalSearchScope scope) {
    Map<GlobalSearchScope, Map<String, PsiClass[]>> scopeMap = dereference(myDumbModeFullCache);
    if (scopeMap == null) {
      myDumbModeFullCache = new SoftReference<>(scopeMap = new ConcurrentHashMap<>());
    }
    Map<String, PsiClass[]> map = scopeMap.get(scope);
    if (map == null) {
      // before parsing all files in this package, try cheap heuristics: check if 'name' is a subpackage, check files named like 'name'
      PsiClass[] array = findClassesHeuristically(name, scope);
      if (array != null) return array;

      RecursionGuard.StackStamp stamp = RecursionManager.markStack();
      map = new HashMap<>();
      for (PsiClass psiClass : getClasses(scope)) {
        String psiClassName = psiClass.getName();
        if (psiClassName != null) {
          PsiClass[] existing = map.get(psiClassName);
          map.put(psiClassName, existing == null ? new PsiClass[]{psiClass} : ArrayUtil.append(existing, psiClass));
        }
      }
      if (stamp.mayCacheNow()) {
        scopeMap.put(scope, map);
      }
    }
    PsiClass[] classes = map.get(name);
    return classes == null ? PsiClass.EMPTY_ARRAY : classes;
  }

  private PsiClass @Nullable [] findClassesHeuristically(final String name, GlobalSearchScope scope) {
    if (findSubPackageByName(name) != null) {
      return PsiClass.EMPTY_ARRAY;
    }

    Map<Pair<GlobalSearchScope, String>, PsiClass[]> partial = dereference(myDumbModePartialCache);
    if (partial == null) {
      myDumbModePartialCache = new SoftReference<>(partial = new ConcurrentHashMap<>());
    }
    PsiClass[] result = partial.get(Pair.create(scope, name));
    if (result == null) {
      RecursionGuard.StackStamp stamp = RecursionManager.markStack();
      List<PsiClass> fastClasses = new ArrayList<>();
      for (PsiDirectory directory : getDirectories(scope)) {
        List<PsiFile> sameNamed = ContainerUtil.filter(directory.getFiles(scope), file -> file.getName().contains(name));
        PsiClass[] classes = CoreJavaDirectoryService.getPsiClasses(directory, sameNamed.toArray(PsiFile.EMPTY_ARRAY));
        for (PsiClass aClass : classes) {
          if (name.equals(aClass.getName())) {
            fastClasses.add(aClass);
          }
        }
      }
      if (!fastClasses.isEmpty() && stamp.mayCacheNow()) {
        partial.put(Pair.create(scope, name), result = fastClasses.toArray(PsiClass.EMPTY_ARRAY));
      }
    }
    return result;
  }

  @Override
  public boolean containsClassNamed(@NotNull String name) {
    return getCachedClassesByName(name, GlobalSearchScope.everythingScope(getProject())).length > 0;
  }

  @Override
  public PsiClass @NotNull [] findClassByShortName(@NotNull String name, @NotNull GlobalSearchScope scope) {
    PsiClass[] allClasses = getCachedClassesByName(name, scope);
    if (allClasses.length == 0) return allClasses;
    if (allClasses.length == 1) {
      return PsiSearchScopeUtil.isInScope(scope, allClasses[0]) ? allClasses.clone() : PsiClass.EMPTY_ARRAY;
    }
    return StreamEx.of(allClasses)
      .filter(aClass -> PsiSearchScopeUtil.isInScope(scope, aClass) && aClass.getContainingClass() == null)
      .sorted(PsiClassUtil
                .createScopeComparator(scope)
                .thenComparing(c -> c.getQualifiedName(), Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(c -> {
                  PsiFile file = c.getContainingFile();
                  return file instanceof PsiClassOwner ? ((PsiClassOwner)file).getPackageName() : "";
                }))
      .toArray(PsiClass.EMPTY_ARRAY);
  }

  @Override
  public boolean hasClassWithShortName(@NotNull String name, @NotNull GlobalSearchScope scope) {
    PsiClass[] classes = getCachedClassesByName(name, scope);
    return ContainerUtil.exists(classes, aClass -> PsiSearchScopeUtil.isInScope(scope, aClass));
  }

  private @Nullable PsiPackage findSubPackageByName(@NotNull String name) {
    final String qName = getQualifiedName();
    final String subpackageQName = qName.isEmpty() ? name : qName + "." + name;
    return getFacade().findPackage(subpackageQName);
  }

  @Override
  public boolean processDeclarations(@NotNull PsiScopeProcessor processor,
                                     @NotNull ResolveState state,
                                     PsiElement lastParent,
                                     @NotNull PsiElement place) {
    GlobalSearchScope scope = place.getResolveScope();

    processor.handleEvent(PsiScopeProcessor.Event.SET_DECLARATION_HOLDER, this);
    ElementClassHint classHint = processor.getHint(ElementClassHint.KEY);

    final Condition<String> nameCondition = processor.getHint(JavaCompletionHints.NAME_FILTER);

    NameHint providedNameHint = processor.getHint(NameHint.KEY);
    final String providedName = providedNameHint == null ? null : providedNameHint.getName(state);

    if (classHint == null || classHint.shouldProcess(ElementClassHint.DeclarationKind.CLASS)) {
      if (providedName != null) {
        PsiClass[] classes = findClassByShortName(providedName, scope);
        if (classes.length == 0 && PsiUtil.isInsideJavadocComment(place)) {
          //extend scope for javadoc references when no classes are found in the resolve scope:

          //always replacing the scope works bad for the case when multiple classes with the same FQName exist,
          //because the index returns them in unpredictable order,
          //so class not accessible from `place` may be used instead of another accessible class
          classes = findClassByShortName(providedName, allScope());
        }
        if (!processClasses(processor, state, classes, Conditions.alwaysTrue())) return false;
      }
      else {
        PsiClass[] classes = getClasses(scope);
        if (classes.length == 0 && PsiUtil.isInsideJavadocComment(place)) {
          classes = getClasses(allScope());
        }
        if (!processClasses(processor, state, classes, nameCondition != null ? nameCondition : Conditions.alwaysTrue())) return false;
      }
    }
    if (classHint == null || classHint.shouldProcess(ElementClassHint.DeclarationKind.PACKAGE)) {
      if (providedName != null) {
        PsiPackage aPackage = findSubPackageByName(providedName);
        if (aPackage != null) {
          if (!processor.execute(aPackage, state)) return false;
        }
      }
      else {
        PsiPackage[] packs = getSubPackages(scope);
        for (PsiPackage pack : packs) {
          final String packageName = pack.getName();
          if (packageName == null) continue;
          if (!PsiNameHelper.getInstance(getProject()).isIdentifier(packageName, PsiUtil.getLanguageLevel(this))) {
            continue;
          }
          if (!processor.execute(pack, state)) {
            return false;
          }
        }
      }
    }
    return true;
  }

  private static boolean processClasses(@NotNull PsiScopeProcessor processor,
                                        @NotNull ResolveState state,
                                        PsiClass @NotNull [] classes,
                                        @NotNull Condition<? super String> nameCondition) {
    for (PsiClass aClass : classes) {
      if (aClass instanceof PsiImplicitClass) continue;
      String name = aClass.getName();
      if (name != null && nameCondition.value(name)) {
        try {
          if (!processor.execute(aClass, state)) return false;
        }
        catch (ProcessCanceledException | IndexNotReadyException e) {
          throw e;
        }
        catch (Exception e) {
          LOG.error(e);
        }
      }
    }
    return true;
  }

  @Override
  public ItemPresentation getPresentation() {
    return ItemPresentationProviders.getItemPresentation(this);
  }

  @Override
  public void navigate(final boolean requestFocus) {
    PsiPackageImplementationHelper.getInstance().navigate(this, requestFocus);
  }

  public boolean mayHaveContentInScope(@NotNull GlobalSearchScope scope) {
    return getDirectories(scope).length > 0 ||
           getClasses(scope).length > 0 ||
           ContainerUtil.exists(occursInPackagePrefixes(), scope::contains);
  }

  private class PackageAnnotationValueProvider implements CachedValueProvider<PsiModifierList> {
    @Override
    public Result<PsiModifierList> compute() {
      List<PsiModifierList> modifiers = new ArrayList<>();
      for(PsiDirectory directory: getDirectories()) {
        PsiFile file = directory.findFile(PACKAGE_INFO_FILE);
        PsiPackageStatement stmt = file == null ? null : PsiTreeUtil.getChildOfType(file, PsiPackageStatement.class);
        PsiModifierList modifierList = stmt == null ? null : stmt.getAnnotationList();
        ContainerUtil.addIfNotNull(modifiers, modifierList);
      }

      for (PsiClass aClass : getFacade().findClasses(getQualifiedName() + ".package-info", allScope())) {
        ContainerUtil.addIfNotNull(modifiers, aClass.getModifierList());
      }

      PsiCompositeModifierList result = modifiers.isEmpty() ? null : new PsiCompositeModifierList(getManager(), modifiers);
      return new Result<>(result, PsiModificationTracker.MODIFICATION_COUNT);
    }
  }

  @Override
  public @Nullable PsiModifierList getModifierList() {
    return getAnnotationList();
  }

  @Override
  public boolean hasModifierProperty(final @NonNls @NotNull String name) {
    return false;
  }
}
