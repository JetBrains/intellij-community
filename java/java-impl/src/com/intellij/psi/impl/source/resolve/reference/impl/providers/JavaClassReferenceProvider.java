/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.psi.impl.source.resolve.reference.impl.providers;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.scope.ElementClassHint;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.*;
import com.intellij.util.NullableFunction;
import com.intellij.util.ProcessingContext;
import com.intellij.util.containers.ContainerUtil;
import gnu.trove.THashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Created by IntelliJ IDEA.
 * User: ik
 * Date: 27.03.2003
 * Time: 17:30:38
 * To change this template use Options | File Templates.
 */
public class JavaClassReferenceProvider extends GenericReferenceProvider implements CustomizableReferenceProvider {

  public static final CustomizationKey<Boolean> RESOLVE_QUALIFIED_CLASS_NAME =
    new CustomizationKey<>(PsiBundle.message("qualified.resolve.class.reference.provider.option"));
  public static final CustomizationKey<String[]> EXTEND_CLASS_NAMES = new CustomizationKey<>("EXTEND_CLASS_NAMES");
  public static final CustomizationKey<String> CLASS_TEMPLATE = new CustomizationKey<>("CLASS_TEMPLATE");
  public static final CustomizationKey<ClassKind> CLASS_KIND = new CustomizationKey<>("CLASS_KIND");
  public static final CustomizationKey<Boolean> INSTANTIATABLE = new CustomizationKey<>("INSTANTIATABLE");
  public static final CustomizationKey<Boolean> CONCRETE = new CustomizationKey<>("CONCRETE");
  public static final CustomizationKey<Boolean> NOT_INTERFACE = new CustomizationKey<>("NOT_INTERFACE");
  public static final CustomizationKey<Boolean> NOT_ENUM= new CustomizationKey<>("NOT_ENUM");
  public static final CustomizationKey<Boolean> ADVANCED_RESOLVE = new CustomizationKey<>("RESOLVE_ONLY_CLASSES");
  public static final CustomizationKey<Boolean> JVM_FORMAT = new CustomizationKey<>("JVM_FORMAT");
  public static final CustomizationKey<Boolean> ALLOW_DOLLAR_NAMES = new CustomizationKey<>("ALLOW_DOLLAR_NAMES");
  public static final CustomizationKey<String> DEFAULT_PACKAGE = new CustomizationKey<>("DEFAULT_PACKAGE");
  @Nullable private Map<CustomizationKey, Object> myOptions;

  private boolean myAllowEmpty;

  private final ParameterizedCachedValueProvider<List<PsiElement>, Project> myProvider = new ParameterizedCachedValueProvider<List<PsiElement>, Project>() {
      @Override
      public CachedValueProvider.Result<List<PsiElement>> compute(Project project) {
        final List<PsiElement> psiPackages = new ArrayList<>();
        final String defPackageName = DEFAULT_PACKAGE.getValue(myOptions);
        if (StringUtil.isNotEmpty(defPackageName)) {
          final PsiPackage defaultPackage = JavaPsiFacade.getInstance(project).findPackage(defPackageName);
          if (defaultPackage != null) {
            psiPackages.addAll(getSubPackages(defaultPackage));
          }
        }
        final PsiPackage rootPackage = JavaPsiFacade.getInstance(project).findPackage("");
        if (rootPackage != null) {
          psiPackages.addAll(getSubPackages(rootPackage));
        }
        return CachedValueProvider.Result.createSingleDependency(psiPackages, PsiModificationTracker.MODIFICATION_COUNT);
      }
    };

  private final Key<ParameterizedCachedValue<List<PsiElement>, Project>> myKey = Key.create("default packages");

  public <T> void setOption(CustomizationKey<T> option, T value) {
    if (myOptions == null) {
      myOptions = new THashMap<>();
    }
    option.putValue(myOptions, value);
  }

  @Nullable
  public <T> T getOption(CustomizationKey<T> option) {
    return myOptions == null ? null : (T)myOptions.get(option);
  }

  @Nullable
  public GlobalSearchScope getScope(Project project) {
    return null;
  }

  @NotNull
  public PsiFile getContextFile(@NotNull PsiElement element) {
    return element.getContainingFile();
  }

  @Nullable
  public PsiClass getContextClass(@NotNull PsiElement element) {
    return null;
  }

  @Override
  @NotNull
  public PsiReference[] getReferencesByElement(@NotNull PsiElement element, @NotNull final ProcessingContext context) {
    return getReferencesByElement(element);
  }

  public PsiReference[] getReferencesByElement(@NotNull PsiElement element) {
    final int offsetInElement = ElementManipulators.getOffsetInElement(element);
    final String text = ElementManipulators.getValueText(element);
    return getReferencesByString(text, element, offsetInElement);
  }

  @NotNull
  public PsiReference[] getReferencesByString(String str, @NotNull PsiElement position, int offsetInPosition) {
    if (myAllowEmpty && StringUtil.isEmpty(str)) {
      return PsiReference.EMPTY_ARRAY;
    }
    boolean allowDollars = Boolean.TRUE.equals(getOption(ALLOW_DOLLAR_NAMES));
    return new JavaClassReferenceSet(str, position, offsetInPosition, allowDollars, this).getAllReferences();
  }

  @Override
  public void handleEmptyContext(PsiScopeProcessor processor, PsiElement position) {
    final ElementClassHint hint = processor.getHint(ElementClassHint.KEY);
    if (position == null) return;
    if (hint == null || hint.shouldProcess(ElementClassHint.DeclarationKind.PACKAGE) || hint.shouldProcess(ElementClassHint.DeclarationKind.CLASS)) {
      final List<PsiElement> cachedPackages = getDefaultPackages(position.getProject());
      for (final PsiElement psiPackage : cachedPackages) {
        if (!processor.execute(psiPackage, ResolveState.initial())) return;
      }
    }
  }

  protected List<PsiElement> getDefaultPackages(Project project) {
    return CachedValuesManager.getManager(project).getParameterizedCachedValue(project, myKey, myProvider, false, project);
  }

  private static Collection<PsiPackage> getSubPackages(final PsiPackage defaultPackage) {
    return ContainerUtil.mapNotNull(defaultPackage.getSubPackages(), (NullableFunction<PsiPackage, PsiPackage>)psiPackage -> {
      final String packageName = psiPackage.getName();
      return PsiNameHelper.getInstance(psiPackage.getProject())
               .isIdentifier(packageName, PsiUtil.getLanguageLevel(psiPackage)) ? psiPackage : null;
    });
  }

  @Override
  public void setOptions(@Nullable Map<CustomizationKey, Object> options) {
    myOptions = options;
  }

  @Override
  @Nullable
  public Map<CustomizationKey, Object> getOptions() {
    return myOptions;
  }

  public void setAllowEmpty(final boolean allowEmpty) {
    myAllowEmpty = allowEmpty;
  }
}
