// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.augment;

import com.intellij.diagnostic.PluginException;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiTypeElement;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.Processor;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Some code is not what it seems to be!
 * This extension allows plugins <strike>augment a reality</strike> alter a behavior of Java PSI elements.
 * To get an insight of how the extension may be used see {@code PsiAugmentProviderTest}.
 * <p>
 * N.B. during indexing, only {@link DumbAware} providers are run.
 */
public abstract class PsiAugmentProvider {
  public static final ExtensionPointName<PsiAugmentProvider> EP_NAME = ExtensionPointName.create("com.intellij.lang.psiAugmentProvider");

  //<editor-fold desc="Methods to override in implementations.">

  /**
   * An extension that enables one to add children to some PSI elements, e.g. methods to Java classes.
   * The class code remains the same, but its method accessors also include the results returned from {@link PsiAugmentProvider}s.
   */
  @NotNull
  protected <Psi extends PsiElement> List<Psi> getAugments(@NotNull PsiElement element, @NotNull Class<Psi> type) {
    return Collections.emptyList();
  }

  /**
   * Extends {@link PsiTypeElement#getType()} so that a type could be retrieved from external place
   * (e.g. inferred from a variable initializer).
   */
  @Nullable
  protected PsiType inferType(@NotNull PsiTypeElement typeElement) {
    return null;
  }

  /**
   * Intercepts {@link PsiModifierList#hasModifierProperty(String)}, so that plugins can add imaginary modifiers or hide existing ones.
   */
  @NotNull
  protected Set<String> transformModifiers(@NotNull PsiModifierList modifierList, @NotNull Set<String> modifiers) {
    return modifiers;
  }

  //</editor-fold>

  //<editor-fold desc="API and the inner kitchen.">

  @NotNull
  public static <Psi extends PsiElement> List<Psi> collectAugments(@NotNull PsiElement element, @NotNull Class<? extends Psi> type) {
    List<Psi> result = new SmartList<>();

    forEach(element.getProject(), provider -> {
      result.addAll(provider.getAugments(element, type));
      return true;
    });

    return result;
  }

  @Nullable
  public static PsiType getInferredType(@NotNull PsiTypeElement typeElement) {
    Ref<PsiType> result = Ref.create();

    forEach(typeElement.getProject(), provider -> {
      PsiType type = provider.inferType(typeElement);
      if (type != null) {
        try {
          PsiUtil.ensureValidType(type);
        }
        catch (ProcessCanceledException e) {
          throw e;
        }
        catch (Throwable e) {
          throw PluginException.createByClass(e, provider.getClass());
        }
        result.set(type);
        return false;
      }
      else {
        return true;
      }
    });

    return result.get();
  }

  @NotNull
  public static Set<String> transformModifierProperties(@NotNull PsiModifierList modifierList,
                                                        @NotNull Project project,
                                                        @NotNull Set<String> modifiers) {
    Ref<Set<String>> result = Ref.create(modifiers);

    forEach(project, provider -> {
      result.set(provider.transformModifiers(modifierList, Collections.unmodifiableSet(result.get())));
      return true;
    });

    return result.get();
  }

  private static void forEach(Project project, Processor<? super PsiAugmentProvider> processor) {
    boolean dumb = DumbService.isDumb(project);
    for (PsiAugmentProvider provider : EP_NAME.getExtensionList()) {
      if (!dumb || DumbService.isDumbAware(provider)) {
        try {
          boolean goOn = processor.process(provider);
          if (!goOn) break;
        }
        catch (ProcessCanceledException e) {
          throw e;
        }
        catch (Exception e) {
          Logger.getInstance(PsiAugmentProvider.class).error("provider: " + provider, e);
        }
      }
    }
  }

  //</editor-fold>
}