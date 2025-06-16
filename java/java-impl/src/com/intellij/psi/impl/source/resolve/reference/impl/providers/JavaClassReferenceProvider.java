// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.source.resolve.reference.impl.providers;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.psi.*;
import com.intellij.psi.scope.ElementClassHint;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.*;
import com.intellij.util.ProcessingContext;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.VisibleForTesting;

import java.util.*;

public class JavaClassReferenceProvider extends GenericReferenceProvider implements CustomizableReferenceProvider {
  /** Tells reference provider to process only qualified class references (e.g. not resolve String as java.lang.String) */
  public static final CustomizationKey<Boolean> RESOLVE_QUALIFIED_CLASS_NAME =
    new CustomizationKey<>("RESOLVE_QUALIFIED_CLASS_NAME");
  public static final CustomizationKey<List<String>> SUPER_CLASSES = new CustomizationKey<>("SUPER_CLASSES");
  public static final CustomizationKey<List<String>> IMPORTS = new CustomizationKey<>("IMPORTS");
  public static final CustomizationKey<String> CLASS_TEMPLATE = new CustomizationKey<>("CLASS_TEMPLATE");
  public static final CustomizationKey<ClassKind> CLASS_KIND = new CustomizationKey<>("CLASS_KIND");
  public static final CustomizationKey<Boolean> INSTANTIATABLE = new CustomizationKey<>("INSTANTIATABLE");
  public static final CustomizationKey<Boolean> CONCRETE = new CustomizationKey<>("CONCRETE");
  public static final CustomizationKey<Boolean> NOT_INTERFACE = new CustomizationKey<>("NOT_INTERFACE");
  public static final CustomizationKey<Boolean> NOT_ENUM = new CustomizationKey<>("NOT_ENUM");
  public static final CustomizationKey<Boolean> ADVANCED_RESOLVE = new CustomizationKey<>("RESOLVE_ONLY_CLASSES");
  public static final CustomizationKey<Boolean> JVM_FORMAT = new CustomizationKey<>("JVM_FORMAT");
  public static final CustomizationKey<Boolean> ALLOW_DOLLAR_NAMES = new CustomizationKey<>("ALLOW_DOLLAR_NAMES");
  public static final CustomizationKey<Boolean> ALLOW_WILDCARDS = new CustomizationKey<>("ALLOW_WILDCARDS");

  /** @deprecated use {@link #SUPER_CLASSES} instead */
  @Deprecated
  public static final CustomizationKey<String[]> EXTEND_CLASS_NAMES = new CustomizationKey<>("EXTEND_CLASS_NAMES");
  /** @deprecated use {@link #IMPORTS} instead */
  @Deprecated(forRemoval = true)
  public static final CustomizationKey<String> DEFAULT_PACKAGE = new CustomizationKey<>("DEFAULT_PACKAGE");

  private @Nullable Map<CustomizationKey, Object> myOptions;

  private boolean myAllowEmpty;

  private static final ParameterizedCachedValueProvider<List<PsiPackage>, Project> ourPackagesProvider = project -> {
    PsiNameHelper nameHelper = PsiNameHelper.getInstance(project);
    PsiPackage root = JavaPsiFacade.getInstance(project).findPackage("");
    List<PsiPackage> psiPackages = root == null ? Collections.emptyList() :
                                   ContainerUtil.filter(root.getSubPackages(),
                                                        p -> nameHelper.isIdentifier(p.getName(), PsiUtil.getLanguageLevel(p)));
    return CachedValueProvider.Result.createSingleDependency(psiPackages, VirtualFileManager.VFS_STRUCTURE_MODIFICATIONS);
  };

  private static final Key<ParameterizedCachedValue<List<PsiPackage>, Project>> ourPackagesKey = Key.create("default packages");

  public <T> void setOption(CustomizationKey<T> option, T value) {
    if (myOptions == null) {
      myOptions = new HashMap<>();
    }
    if (option == EXTEND_CLASS_NAMES) {
      SUPER_CLASSES.putValue(myOptions, List.of((String[])value));
    }
    else if (option == DEFAULT_PACKAGE) {
      IMPORTS.putValue(myOptions, Collections.singletonList((String)value));
    }
    else {
      option.putValue(myOptions, value);
    }
  }

  public @Nullable <T> T getOption(@NotNull CustomizationKey<T> option) {
    return myOptions == null ? null : option.getValue(myOptions);
  }

  public @Nullable GlobalSearchScope getScope(@NotNull Project project) {
    return null;
  }

  public @NotNull PsiFile getContextFile(@NotNull PsiElement element) {
    return element.getContainingFile();
  }

  public @Nullable PsiClass getContextClass(@NotNull PsiElement element) {
    return null;
  }

  @Override
  public PsiReference @NotNull [] getReferencesByElement(@NotNull PsiElement element, @NotNull ProcessingContext context) {
    return getReferencesByElement(element);
  }

  public PsiReference @NotNull [] getReferencesByElement(@NotNull PsiElement element) {
    final int offsetInElement = ElementManipulators.getOffsetInElement(element);
    final String text = ElementManipulators.getValueText(element);
    return getReferencesByString(text, element, offsetInElement);
  }

  public PsiReference @NotNull [] getReferencesByString(String str, @NotNull PsiElement position, int offsetInPosition) {
    if (myAllowEmpty && StringUtil.isEmpty(str)) {
      return PsiReference.EMPTY_ARRAY;
    }
    boolean allowDollars = Boolean.TRUE.equals(getOption(ALLOW_DOLLAR_NAMES));
    return new JavaClassReferenceSet(str, position, offsetInPosition, allowDollars, this).getAllReferences();
  }

  @Override
  public void handleEmptyContext(PsiScopeProcessor processor, PsiElement position) {
    ElementClassHint hint = processor.getHint(ElementClassHint.KEY);
    if (position == null) return;
    if (hint == null ||
        hint.shouldProcess(ElementClassHint.DeclarationKind.PACKAGE) ||
        hint.shouldProcess(ElementClassHint.DeclarationKind.CLASS)) {
      List<PsiPackage> cachedPackages = getDefaultPackages(position.getProject());
      for (PsiElement psiPackage : cachedPackages) {
        if (!processor.execute(psiPackage, ResolveState.initial())) return;
      }
    }
  }

  static @NotNull List<PsiPackage> getDefaultPackages(@NotNull Project project) {
    return CachedValuesManager.getManager(project).getParameterizedCachedValue(project, ourPackagesKey, ourPackagesProvider, false, project);
  }

  @ApiStatus.Internal
  @VisibleForTesting
  public static @NotNull Set<String> getDefaultPackagesNames(@NotNull Project project) {
    return CachedValuesManager.getManager(project)
      .getCachedValue(project, 
                      () -> CachedValueProvider.Result.create(ContainerUtil.map2Set(getDefaultPackages(project), PsiPackage::getName), 
                                                              VirtualFileManager.VFS_STRUCTURE_MODIFICATIONS));
  }

  @Override
  public @Nullable Map<CustomizationKey, Object> getOptions() {
    return myOptions;
  }

  public void setAllowEmpty(boolean allowEmpty) {
    myAllowEmpty = allowEmpty;
  }
}
