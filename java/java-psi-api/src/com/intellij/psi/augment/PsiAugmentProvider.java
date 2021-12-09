// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.augment;

import com.intellij.diagnostic.PluginException;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.ExtensionPoint;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.NotNullLazyValue;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.*;
import com.intellij.psi.util.*;
import com.intellij.util.Processor;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ConcurrentFactoryMap;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Some code is not what it seems to be!
 * This extension allows plugins <strike>augment a reality</strike> alter a behavior of Java PSI elements.
 * To get an insight of how the extension may be used see {@code PsiAugmentProviderTest}.
 * <p>
 * N.B. during indexing, only {@link DumbAware} providers are run.
 */
public abstract class PsiAugmentProvider {
  private static final Logger LOG = Logger.getInstance(PsiAugmentProvider.class);
  public static final ExtensionPointName<PsiAugmentProvider> EP_NAME = ExtensionPointName.create("com.intellij.lang.psiAugmentProvider");
  private static final @NotNull NotNullLazyValue<ExtensionPoint<PsiAugmentProvider>> EP = NotNullLazyValue.lazy(() -> EP_NAME.getPoint());
  @SuppressWarnings("rawtypes")
  private /* non-static */ final Key<CachedValue<Map<Class, List>>> myCacheKey = Key.create(getClass().getName());

  //<editor-fold desc="Methods to override in implementations.">

  /**
   * An extension that enables one to add children to some PSI elements, e.g. methods to Java classes.
   * The class code remains the same, but its method accessors also include the results returned from {@link PsiAugmentProvider}s.
   * An augmenter can be called several times with the same parameters in the same state of the code model,
   * and the PSI returned from these invocations must be equal and implement {@link #equals}/{@link #hashCode()} accordingly.
   * @param nameHint the expected name of the requested augmented members, or null if all members of the specified class are to be returned.
   *                 Implementations can ignore this parameter or use it for optimizations.
   */
  @SuppressWarnings({"unchecked", "rawtypes"})
  @NotNull
  protected <Psi extends PsiElement> List<Psi> getAugments(@NotNull PsiElement element,
                                                           @NotNull Class<Psi> type,
                                                           @Nullable String nameHint) {
    if (nameHint == null) return getAugments(element, type);

    // cache to emulate previous behavior where augmenters were called just once, not for each name hint separately
    Map<Class, List> cache = CachedValuesManager.getCachedValue(element, myCacheKey, () -> {
      Map<Class, List> map = ConcurrentFactoryMap.createMap(c -> getAugments(element, c));
      return CachedValueProvider.Result.create(map, PsiModificationTracker.MODIFICATION_COUNT);
    });
    return (List<Psi>)cache.get(type);
  }

  /**
   * An extension which enables one to inject extension methods with name {@code nameHint} in class {@code aClass} in context `{@code context}`
   * @param aClass    where extension methods would be injected
   * @param nameHint  name of the method which is requested.
   *                  Implementations are supposed to use this parameter as no additional name check would be performed
   * @param context   context where extension methods should be applicable
   */
  @ApiStatus.Experimental
  protected List<PsiExtensionMethod> getExtensionMethods(@NotNull PsiClass aClass, @NotNull String nameHint, @NotNull PsiElement context) {
    return Collections.emptyList();
  }

  /**
   * @deprecated invoke and override {@link #getAugments(PsiElement, Class, String)}.
   */
  @Deprecated
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
   * @return whether this extension might infer the type for the given PSI,
   * preferably checked in a lightweight way without actually inferring the type.
   */
  protected boolean canInferType(@NotNull PsiTypeElement typeElement) {
    return inferType(typeElement) != null;
  }

  /**
   * @param field field to check
   * @return true if this field initializer can be changed due to extra-linguistic extensions
   * (e.g., it's annotated via some annotation and annotation processor will transform the field to be non-constant)
   */
  protected boolean fieldInitializerMightBeChanged(@NotNull PsiField field) {
    return false;
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
  public static <Psi extends PsiElement> List<Psi> collectAugments(@NotNull PsiElement element, @NotNull Class<? extends Psi> type,
                                                                   @Nullable String nameHint) {
    List<Psi> result = new SmartList<>();

    forEach(element.getProject(), provider -> {
      List<? extends Psi> augments = provider.getAugments(element, type, nameHint);
      for (Psi augment : augments) {
        if (nameHint == null || !(augment instanceof PsiNamedElement) || nameHint.equals(((PsiNamedElement)augment).getName())) {
          try {
            PsiUtilCore.ensureValid(augment);
            result.add(augment);
          }
          catch (ProcessCanceledException e) {
            throw e;
          }
          catch (Throwable e) {
            LOG.error(PluginException.createByClass(e, provider.getClass()));
          }
        }
      }
      return true;
    });

    return result;
  }

  @ApiStatus.Experimental
  @NotNull
  public static List<PsiExtensionMethod> collectExtensionMethods(PsiClass aClass, @NotNull String nameHint, PsiElement context) {
    List<PsiExtensionMethod> extensionMethods = new SmartList<>();
    forEach(aClass.getProject(), provider -> {
      List<PsiExtensionMethod> methods = provider.getExtensionMethods(aClass, nameHint, context);
      for (PsiExtensionMethod method : methods) {
        try {
          PsiUtilCore.ensureValid(method);
          extensionMethods.add(method);
        }
        catch (ProcessCanceledException e) {
          throw e;
        }
        catch (Throwable e) {
          LOG.error(PluginException.createByClass(e, provider.getClass()));
        }
      }
      return true;
    });
    return extensionMethods;
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

  public static boolean isInferredType(@NotNull PsiTypeElement typeElement) {
    AtomicBoolean result = new AtomicBoolean();

    forEach(typeElement.getProject(), provider -> {
      boolean canInfer = provider.canInferType(typeElement);
      if (canInfer) {
        result.set(true);
      }
      return !canInfer;
    });

    return result.get();
  }

  /**
   * @param field field to check
   * @return true if we can trust the field initializer;
   * false if any of providers reported that the initializer might be changed
   */
  public static boolean canTrustFieldInitializer(@NotNull PsiField field) {
    AtomicBoolean result = new AtomicBoolean(true);

    forEach(field.getProject(), provider -> {
      boolean mightBeReplaced = provider.fieldInitializerMightBeChanged(field);
      if (mightBeReplaced) {
        result.set(false);
      }
      return !mightBeReplaced;
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
    for (PsiAugmentProvider provider : EP.getValue().getExtensionList()) {
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