/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
import com.intellij.lang.Language;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.navigation.ItemPresentation;
import com.intellij.navigation.ItemPresentationProviders;
import com.intellij.openapi.ui.Queryable;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.impl.JavaPsiFacadeImpl;
import com.intellij.psi.impl.source.tree.java.PsiCompositeModifierList;
import com.intellij.psi.scope.ElementClassHint;
import com.intellij.psi.scope.NameHint;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.search.DelegatingGlobalSearchScope;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.*;
import com.intellij.reference.SoftReference;
import com.intellij.util.CommonProcessors;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

public class PsiPackageImpl extends PsiPackageBase implements PsiPackage, Queryable {
  public static boolean DEBUG = false;
  private volatile CachedValue<PsiModifierList> myAnnotationList;
  private volatile CachedValue<Collection<PsiDirectory>> myDirectories;
  private volatile CachedValue<Collection<PsiDirectory>> myDirectoriesWithLibSources;
  private volatile SoftReference<Set<String>> myPublicClassNamesCache;

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

  private CachedValue<Collection<PsiDirectory>> createCachedDirectories(final boolean includeLibrarySources) {
    return CachedValuesManager.getManager(myManager.getProject()).createCachedValue(new CachedValueProvider<Collection<PsiDirectory>>() {
      @Override
      public Result<Collection<PsiDirectory>> compute() {
        final CommonProcessors.CollectProcessor<PsiDirectory> processor = new CommonProcessors.CollectProcessor<PsiDirectory>();
        getFacade().processPackageDirectories(PsiPackageImpl.this, allScope(), processor, includeLibrarySources);
        return Result.create(processor.getResults(), PsiPackageImplementationHelper.getInstance().getDirectoryCachedValueDependencies(
          PsiPackageImpl.this));
      }
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
    return PsiPackageImplementationHelper.getInstance().packagePrefixExists(this) || !getAllDirectories(true).isEmpty();
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

  public String toString() {
    return "PsiPackage:" + getQualifiedName();
  }

  @Override
  @NotNull
  public PsiClass[] getClasses() {
    return getClasses(allScope());
  }

  protected GlobalSearchScope allScope() {
    return PsiPackageImplementationHelper.getInstance().adjustAllScope(this, GlobalSearchScope.allScope(getProject()));
  }

  @Override
  @NotNull
  public PsiClass[] getClasses(@NotNull GlobalSearchScope scope) {
    return getFacade().getClasses(this, scope);
  }

  @Override
  @Nullable
  public PsiModifierList getAnnotationList() {
    if (myAnnotationList == null) {
      myAnnotationList = CachedValuesManager.getManager(myManager.getProject()).createCachedValue(new PackageAnnotationValueProvider());
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

  private Set<String> getClassNamesCache() {
    SoftReference<Set<String>> ref = myPublicClassNamesCache;
    Set<String> cache = ref == null ? null : ref.get();
    if (cache == null) {
      GlobalSearchScope scope = allScope();

      if (!scope.isForceSearchingInLibrarySources()) {
        scope = new DelegatingGlobalSearchScope(scope) {
          @Override
          public boolean isForceSearchingInLibrarySources() {
            return true;
          }
        };
      }
      cache = getFacade().getClassNames(this, scope);
      myPublicClassNamesCache = new SoftReference<Set<String>>(cache);
    }

    return cache;
  }

  @NotNull
  private PsiClass[] findClassesByName(String name, GlobalSearchScope scope) {
    final String qName = getQualifiedName();
    final String classQName = !qName.isEmpty() ? qName + "." + name : name;
    return getFacade().findClasses(classQName, scope);
  }

  @Override
  public boolean containsClassNamed(String name) {
    return getClassNamesCache().contains(name);
  }

  @NotNull
  @Override
  public PsiClass[] findClassByShortName(@NotNull String name, @NotNull GlobalSearchScope scope) {
    if (!containsClassNamed(name)) return PsiClass.EMPTY_ARRAY;
    return getFacade().findClassByShortName(name, this, scope);
  }

  @Nullable
  private PsiPackage findSubPackageByName(String name) {
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

    final JavaPsiFacade facade = getFacade();
    final Condition<String> prefixMatcher = processor.getHint(JavaCompletionHints.NAME_FILTER);

    if (classHint == null || classHint.shouldProcess(ElementClassHint.DeclarationKind.CLASS)) {
      NameHint nameHint = processor.getHint(NameHint.KEY);
      if (nameHint != null) {
        final String shortName = nameHint.getName(state);
        if (containsClassNamed(shortName) && processClassesByName(processor, state, scope, shortName)) return false;
      }
      else if (prefixMatcher != null) {
        for (String className : getClassNamesCache()) {
          if (prefixMatcher.value(className)) {
            if (processClassesByName(processor, state, scope, className)) return false;
          }
        }
      }
      else {
        PsiClass[] classes = getClasses(scope);
        if (!processClasses(processor, state, classes)) return false;
      }
    }
    if (classHint == null || classHint.shouldProcess(ElementClassHint.DeclarationKind.PACKAGE)) {
      NameHint nameHint = processor.getHint(NameHint.KEY);
      if (nameHint != null) {
        PsiPackage aPackage = findSubPackageByName(nameHint.getName(state));
        if (aPackage != null) {
          if (!processor.execute(aPackage, state)) return false;
        }
      }
      else {
        PsiPackage[] packs = getSubPackages(scope);
        for (PsiPackage pack : packs) {
          final String packageName = pack.getName();
          if (packageName == null) continue;
          if (!facade.getNameHelper().isIdentifier(packageName, PsiUtil.getLanguageLevel(this))) {
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

  private boolean processClassesByName(PsiScopeProcessor processor,
                                       ResolveState state,
                                       GlobalSearchScope scope,
                                       String className) {
    final PsiClass[] classes = findClassesByName(className, scope);
    return !processClasses(processor, state, classes);
  }

  private static boolean processClasses(PsiScopeProcessor processor, ResolveState state, PsiClass[] classes) {
    for (PsiClass aClass : classes) {
      if (!processor.execute(aClass, state)) return false;
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
    private final Object[] OOCB_DEPENDENCY = { PsiModificationTracker.OUT_OF_CODE_BLOCK_MODIFICATION_COUNT };

    @Override
    public Result<PsiModifierList> compute() {
      List<PsiModifierList> list = new ArrayList<PsiModifierList>();
      for(PsiDirectory directory: getDirectories()) {
        PsiFile file = directory.findFile(PACKAGE_INFO_FILE);
        if (file != null) {
          PsiPackageStatement stmt = PsiTreeUtil.getChildOfType(file, PsiPackageStatement.class);
          if (stmt != null) {
            final PsiModifierList modifierList = stmt.getAnnotationList();
            if (modifierList != null) {
              list.add(modifierList);
            }
          }
        }
      }

      final JavaPsiFacade facade = getFacade();
      final GlobalSearchScope scope = allScope();
      for (PsiClass aClass : facade.findClasses(getQualifiedName() + ".package-info", scope)) {
        ContainerUtil.addIfNotNull(aClass.getModifierList(), list);
      }

      return new Result<PsiModifierList>(list.isEmpty() ? null : new PsiCompositeModifierList(getManager(), list), OOCB_DEPENDENCY);
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

  @Override
  public PsiQualifiedNamedElement getContainer() {
    return getParentPackage();
  }
}
