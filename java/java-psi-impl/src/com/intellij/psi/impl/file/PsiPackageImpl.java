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
package com.intellij.psi.impl.file;

import com.intellij.codeInsight.completion.scope.JavaCompletionHints;
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
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Conditions;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.impl.JavaPsiFacadeImpl;
import com.intellij.psi.impl.source.tree.java.PsiCompositeModifierList;
import com.intellij.psi.scope.ElementClassHint;
import com.intellij.psi.scope.NameHint;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.search.EverythingGlobalScope;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiSearchScopeUtil;
import com.intellij.psi.util.*;
import com.intellij.reference.SoftReference;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Processor;
import com.intellij.util.Processors;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class PsiPackageImpl extends PsiPackageBase implements PsiPackage, Queryable {
  private static final Logger LOG = Logger.getInstance(PsiPackageImpl.class);

  private volatile CachedValue<PsiModifierList> myAnnotationList;
  private volatile CachedValue<Collection<PsiDirectory>> myDirectories;
  private volatile CachedValue<Collection<PsiDirectory>> myDirectoriesWithLibSources;
  private volatile SoftReference<Map<String, PsiClass[]>> myClassCache;
  private volatile SoftReference<Map<GlobalSearchScope, Map<String, PsiClass[]>>> myDumbModeFullCache;
  private volatile SoftReference<Map<Pair<GlobalSearchScope, String>, PsiClass[]>> myDumbModePartialCache;

  public PsiPackageImpl(PsiManager manager, String qualifiedName) {
    super(manager, qualifiedName);
  }

  @Override
  protected Collection<PsiDirectory> getAllDirectories(boolean includeLibrarySources) {
    if (includeLibrarySources) {
      if (myDirectoriesWithLibSources == null) {
        myDirectoriesWithLibSources = createCachedDirectories(true);
      }
      return myDirectoriesWithLibSources.getValue();
    }
    else {
      if (myDirectories == null) {
        myDirectories = createCachedDirectories(false);
      }
      return myDirectories.getValue();
    }
  }

  @NotNull
  private CachedValue<Collection<PsiDirectory>> createCachedDirectories(final boolean includeLibrarySources) {
    return CachedValuesManager.getManager(myManager.getProject()).createCachedValue(() -> {
      Collection<PsiDirectory> result = new ArrayList<>();
      Processor<PsiDirectory> processor = Processors.cancelableCollectProcessor(result);
      getFacade().processPackageDirectories(this, allScope(), processor, includeLibrarySources);
      return CachedValueProvider.Result.create(result, PsiPackageImplementationHelper.getInstance().getDirectoryCachedValueDependencies(this));
    }, false);
  }

  @Override
  protected PsiElement findPackage(String qName) {
    return getFacade().findPackage(qName);
  }

  @Override
  public void handleQualifiedNameChange(@NotNull final String newQualifiedName) {
    PsiPackageImplementationHelper.getInstance().handleQualifiedNameChange(this, newQualifiedName);
  }

  @NotNull
  @Override
  public VirtualFile[] occursInPackagePrefixes() {
    return PsiPackageImplementationHelper.getInstance().occursInPackagePrefixes(this);
  }

  @Override
  public PsiPackageImpl getParentPackage() {
    return (PsiPackageImpl)super.getParentPackage();
  }

  @Override
  protected PsiPackageImpl createInstance(PsiManager manager, String qName) {
    return new PsiPackageImpl(myManager, qName);
  }

  @Override
  @NotNull
  public Language getLanguage() {
    return JavaLanguage.INSTANCE;
  }

  @Override
  public boolean isValid() {
    return !myManager.getProject().isDisposed() &&
           (PsiPackageImplementationHelper.getInstance().packagePrefixExists(this) ||
            !getAllDirectories(true).isEmpty());
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
  @NotNull
  public PsiClass[] getClasses() {
    return getClasses(allScope());
  }

  @NotNull
  protected GlobalSearchScope allScope() {
    return PsiPackageImplementationHelper.getInstance().adjustAllScope(this, GlobalSearchScope.allScope(getProject()));
  }

  @Override
  @NotNull
  public PsiClass[] getClasses(@NotNull GlobalSearchScope scope) {
    return getFacade().getClasses(this, scope);
  }

  @NotNull
  @Override
  public PsiFile[] getFiles(@NotNull GlobalSearchScope scope) {
    return getFacade().getPackageFiles(this, scope);
  }

  @Override
  @Nullable
  public PsiModifierList getAnnotationList() {
    if (myAnnotationList == null) {
      myAnnotationList = CachedValuesManager.getManager(myManager.getProject()).createCachedValue(new PackageAnnotationValueProvider(), false);
    }
    return myAnnotationList.getValue();
  }

  @Override
  @NotNull
  public PsiPackage[] getSubPackages() {
    return getSubPackages(allScope());
  }

  @Override
  @NotNull
  public PsiPackage[] getSubPackages(@NotNull GlobalSearchScope scope) {
    return getFacade().getSubPackages(this, scope);
  }

  private JavaPsiFacadeImpl getFacade() {
    return (JavaPsiFacadeImpl)JavaPsiFacade.getInstance(myManager.getProject());
  }

  @NotNull
  private PsiClass[] getCachedClassesByName(@NotNull String name, GlobalSearchScope scope) {
    if (DumbService.getInstance(getProject()).isDumb()) {
      return getCachedClassInDumbMode(name, scope);
    }

    Map<String, PsiClass[]> map = SoftReference.dereference(myClassCache);
    if (map == null) {
      myClassCache = new SoftReference<>(map = ContainerUtil.createConcurrentSoftValueMap());
    }
    PsiClass[] classes = map.get(name);
    if (classes != null) {
      return classes;
    }

    final String qName = getQualifiedName();
    final String classQName = !qName.isEmpty() ? qName + "." + name : name;
    map.put(name, classes = getFacade().findClasses(classQName, new EverythingGlobalScope(getProject())));
    return classes;
  }

  @NotNull
  private PsiClass[] getCachedClassInDumbMode(final String name, GlobalSearchScope scope) {
    Map<GlobalSearchScope, Map<String, PsiClass[]>> scopeMap = SoftReference.dereference(myDumbModeFullCache);
    if (scopeMap == null) {
      myDumbModeFullCache = new SoftReference<>(scopeMap = ContainerUtil.newConcurrentMap());
    }
    Map<String, PsiClass[]> map = scopeMap.get(scope);
    if (map == null) {
      // before parsing all files in this package, try cheap heuristics: check if 'name' is a subpackage, check files named like 'name'
      PsiClass[] array = findClassesHeuristically(name, scope);
      if (array != null) return array;

      map = new HashMap<>();
      for (PsiClass psiClass : getClasses(scope)) {
        String psiClassName = psiClass.getName();
        if (psiClassName != null) {
          PsiClass[] existing = map.get(psiClassName);
          map.put(psiClassName, existing == null ? new PsiClass[]{psiClass} : ArrayUtil.append(existing, psiClass));
        }
      }
      scopeMap.put(scope, map);
    }
    PsiClass[] classes = map.get(name);
    return classes == null ? PsiClass.EMPTY_ARRAY : classes;
  }

  @Nullable
  private PsiClass[] findClassesHeuristically(final String name, GlobalSearchScope scope) {
    if (findSubPackageByName(name) != null) {
      return PsiClass.EMPTY_ARRAY;
    }

    Map<Pair<GlobalSearchScope, String>, PsiClass[]> partial = SoftReference.dereference(myDumbModePartialCache);
    if (partial == null) {
      myDumbModePartialCache = new SoftReference<>(partial = ContainerUtil.newConcurrentMap());
    }
    PsiClass[] result = partial.get(Pair.create(scope, name));
    if (result == null) {
      List<PsiClass> fastClasses = ContainerUtil.newArrayList();
      for (PsiDirectory directory : getDirectories(scope)) {
        List<PsiFile> sameNamed = ContainerUtil.filter(directory.getFiles(), file -> file.getName().contains(name));
        Collections.addAll(fastClasses, CoreJavaDirectoryService.getPsiClasses(directory, sameNamed.toArray(new PsiFile[sameNamed.size()])));
      }
      if (!fastClasses.isEmpty()) {
        partial.put(Pair.create(scope, name), result = fastClasses.toArray(new PsiClass[fastClasses.size()]));
      }
    }
    return result;
  }

  @Override
  public boolean containsClassNamed(@NotNull String name) {
    return getCachedClassesByName(name, new EverythingGlobalScope(getProject())).length > 0;
  }

  @NotNull
  @Override
  public PsiClass[] findClassByShortName(@NotNull String name, @NotNull final GlobalSearchScope scope) {
    PsiClass[] allClasses = getCachedClassesByName(name, scope);
    if (allClasses.length == 0) return allClasses;
    if (allClasses.length == 1) {
      return PsiSearchScopeUtil.isInScope(scope, allClasses[0]) ? allClasses : PsiClass.EMPTY_ARRAY;
    }
    PsiClass[] array = ContainerUtil.findAllAsArray(allClasses, aClass -> PsiSearchScopeUtil.isInScope(scope, aClass));
    Arrays.sort(array, PsiClassUtil.createScopeComparator(scope));
    return array;
  }

  @Nullable
  private PsiPackage findSubPackageByName(@NotNull String name) {
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
        final PsiClass[] classes = findClassByShortName(providedName, scope);
        if (!processClasses(processor, state, classes, Conditions.alwaysTrue())) return false;
      }
      else {
        PsiClass[] classes = getClasses(scope);
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
          if (!PsiNameHelper.getInstance(myManager.getProject()).isIdentifier(packageName, PsiUtil.getLanguageLevel(this))) {
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
                                        @NotNull PsiClass[] classes,
                                        @NotNull Condition<String> nameCondition) {
    for (PsiClass aClass : classes) {
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
  public boolean canNavigate() {
    return isValid();
  }

  @Override
  public ItemPresentation getPresentation() {
    return ItemPresentationProviders.getItemPresentation(this);
  }

  @Override
  public void navigate(final boolean requestFocus) {
    PsiPackageImplementationHelper.getInstance().navigate(this, requestFocus);
  }

  private class PackageAnnotationValueProvider implements CachedValueProvider<PsiModifierList> {
    @Override
    public Result<PsiModifierList> compute() {
      List<PsiModifierList> modifiers = ContainerUtil.newArrayList();
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
      return new Result<>(result, PsiModificationTracker.OUT_OF_CODE_BLOCK_MODIFICATION_COUNT);
    }
  }

  @Override
  @Nullable
  public PsiModifierList getModifierList() {
    return getAnnotationList();
  }

  @Override
  public boolean hasModifierProperty(@NonNls @NotNull final String name) {
    return false;
  }
}