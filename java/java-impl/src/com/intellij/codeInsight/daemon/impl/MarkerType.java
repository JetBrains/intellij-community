// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeInsight.daemon.DaemonBundle;
import com.intellij.codeInsight.daemon.GutterIconNavigationHandler;
import com.intellij.codeInsight.navigation.GotoTargetHandler;
import com.intellij.codeInsight.navigation.PsiTargetNavigator;
import com.intellij.ide.util.PsiClassRenderingInfo;
import com.intellij.ide.util.PsiElementRenderingInfo;
import com.intellij.ide.util.PsiMethodRenderingInfo;
import com.intellij.java.JavaBundle;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.DumbService;
import com.intellij.psi.*;
import com.intellij.psi.impl.FindSuperElementsHelper;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiElementProcessor;
import com.intellij.psi.search.PsiElementProcessorAdapter;
import com.intellij.psi.search.searches.ClassInheritorsSearch;
import com.intellij.psi.search.searches.FunctionalExpressionSearch;
import com.intellij.psi.search.searches.OverridingMethodsSearch;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Function;
import com.intellij.util.NullableFunction;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.event.MouseEvent;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

public class MarkerType {
  private final GutterIconNavigationHandler<PsiElement> handler;
  private final Function<? super PsiElement, String> myTooltip;
  private final @NotNull String myDebugName;

  public MarkerType(@NotNull String debugName, @NotNull Function<? super PsiElement, String> tooltip, final @NotNull LineMarkerNavigator navigator) {
    myTooltip = tooltip;
    myDebugName = debugName;
    if (ApplicationManager.getApplication().isUnitTestMode() && navigator instanceof GutterIconNavigationHandler<?>) {
      //noinspection unchecked
      handler = (GutterIconNavigationHandler<PsiElement>)navigator;
    }
    else {
      handler = (e, elt) -> DumbService.getInstance(elt.getProject()).withAlternativeResolveEnabled(() -> navigator.browse(e, elt));
    }
  }

  @Override
  public String toString() {
    return myDebugName;
  }

  public @NotNull GutterIconNavigationHandler<PsiElement> getNavigationHandler() {
    return handler;
  }

  public @NotNull Function<? super PsiElement, String> getTooltip() {
    return myTooltip;
  }

  public static final MarkerType OVERRIDING_METHOD = new MarkerType("OVERRIDING_METHOD", (NullableFunction<PsiElement, String>)element -> {
    PsiElement parent = getParentMethod(element);
    if (!(parent instanceof PsiMethod method)) return null;

    return calculateOverridingMethodTooltip(method, method != element.getParent());
  }, new LineMarkerNavigator() {
    @Override
    public void browse(MouseEvent e, PsiElement element) {
      PsiElement parent = getParentMethod(element);
      if (!(parent instanceof PsiMethod method)) return;
      navigateToOverridingMethod(e, method, method != element.getParent());
    }
  });
  public static final MarkerType SIBLING_OVERRIDING_METHOD =
    new MarkerType("SIBLING_OVERRIDING_METHOD", (NullableFunction<PsiElement, String>)element -> {
      PsiElement parent = getParentMethod(element);
      if (!(parent instanceof PsiMethod method)) return null;
      return calculateOverridingSiblingMethodTooltip(method);
    }, new LineMarkerNavigator() {
      @Override
      public void browse(MouseEvent e, PsiElement element) {
        PsiElement parent = getParentMethod(element);
        if (!(parent instanceof PsiMethod method)) return;
        navigateToSiblingOverridingMethod(e, method);
      }
    });

  private static @Nullable String calculateOverridingMethodTooltip(@NotNull PsiMethod method, boolean acceptSelf) {
    PsiMethod[] superMethods = composeSuperMethods(method, acceptSelf);
    if (superMethods.length == 0) return null;

    String divider = GutterTooltipBuilder.getElementDivider(false, false, superMethods.length);
    AtomicReference<String> reference = new AtomicReference<>(""); // optimization: calculate next divider only once
    return GutterTooltipHelper.getTooltipText(
      Arrays.asList(superMethods),
      superMethod -> getTooltipPrefix(method, superMethod, reference.getAndSet(divider)),
      superMethod -> isSameSignature(method, superMethod),
      IdeActions.ACTION_GOTO_SUPER);
  }

  private static @Nullable String calculateOverridingSiblingMethodTooltip(@NotNull PsiMethod method) {
    FindSuperElementsHelper.SiblingInfo pair = FindSuperElementsHelper.getSiblingInfoInheritedViaSubClass(method);
    if (pair == null) return null;

    return GutterTooltipHelper.getTooltipText(
      Arrays.asList(pair.superMethod, pair.subClass),
      element -> element instanceof PsiMethod ? getTooltipPrefix(method, (PsiMethod)element, "") : " " + JavaBundle.message("tooltip.via.subclass") + " ",
      element -> element instanceof PsiMethod && isSameSignature(method, (PsiMethod)element),
      IdeActions.ACTION_GOTO_SUPER);
  }

  private static @NotNull String getTooltipPrefix(@NotNull PsiMethod method, @NotNull PsiMethod superMethod, @NotNull String prefix) {
    StringBuilder sb = new StringBuilder(prefix);
    boolean isAbstract = method.hasModifierProperty(PsiModifier.ABSTRACT);
    boolean isSuperAbstract = superMethod.hasModifierProperty(PsiModifier.ABSTRACT);
    String key = isSameSignature(method, superMethod)
                 ? (isSuperAbstract && !isAbstract ? "tooltip.implements.method.in" : "tooltip.overrides.method.in")
                 : (isSuperAbstract && !isAbstract ? "tooltip.implements.method" : "tooltip.overrides.method");
    return sb.append(JavaBundle.message(key)).append(" ").toString();
  }

  private static boolean isSameSignature(@NotNull PsiMethod method, @NotNull PsiMethod superMethod) {
    return method.getSignature(PsiSubstitutor.EMPTY).equals(superMethod.getSignature(PsiSubstitutor.EMPTY));
  }

  private static @NotNull <E extends PsiElement> PsiElementProcessor.CollectElementsWithLimit<E> getProcessor(int limit, boolean set) {
    return set ? new PsiElementProcessor.CollectElementsWithLimit<>(limit, new HashSet<>())
               : new PsiElementProcessor.CollectElementsWithLimit<>(limit);
  }

  private static String getFunctionalImplementationTooltip(@NotNull PsiClass psiClass) {
    PsiElementProcessor.CollectElementsWithLimit<PsiFunctionalExpression> processor = getProcessor(5, true);
    FunctionalExpressionSearch.search(psiClass).forEach(new PsiElementProcessorAdapter<>(processor));
    if (processor.isOverflow()) return getImplementationTooltip("tooltip.has.several.functional.implementations");
    if (processor.getCollection().isEmpty()) return null;
    return getImplementationTooltip(processor.getCollection(), JavaBundle.message("tooltip.is.functionally.implemented.in"));
  }

  private static @NotNull String getImplementationTooltip(@NotNull String prefixKey, PsiElement @NotNull ... elements) {
    return getImplementationTooltip(Arrays.asList(elements), JavaBundle.message(prefixKey));
  }

  private static @NotNull String getImplementationTooltip(@NotNull Collection<? extends PsiElement> elements, @NotNull String prefix) {
    return GutterTooltipHelper.getTooltipText(elements, prefix, true, IdeActions.ACTION_GOTO_IMPLEMENTATION);
  }

  private static void navigateToOverridingMethod(MouseEvent e, @NotNull PsiMethod method, boolean acceptSelf) {
    navigate(e, method, () -> Arrays.asList(composeSuperMethods(method, acceptSelf)));
  }

  private static void navigateToSiblingOverridingMethod(MouseEvent e, @NotNull PsiMethod method) {
    PsiMethod superMethod = FindSuperElementsHelper.getSiblingInheritedViaSubClass(method);
    if (superMethod == null) return;
    navigate(e, method, () -> Collections.singletonList(method));
  }

  private static void navigate(MouseEvent e, @NotNull PsiMethod method, Supplier<Collection<PsiMethod>> supplier) {
    new PsiTargetNavigator<>(supplier)
      .tabTitle(DaemonBundle.message("navigation.findUsages.title.super.method", method.getName()))
      .elementsConsumer((methods, navigator) -> {
        if (!methods.isEmpty()) {
          boolean showMethodNames = !PsiUtil.allMethodsHaveSameSignature(methods.toArray(PsiMethod.EMPTY_ARRAY));
          navigator.presentationProvider(element -> {
            if (element instanceof PsiCompiledElement) {
              ((PsiCompiledElement)element).getMirror(); // load decompiler in background thread
            }
            return GotoTargetHandler.computePresentation(element, showMethodNames);
          });
        }
      })
      .navigate(e, DaemonBundle.message("navigation.title.super.method", method.getName()), method.getProject());
  }

  private static PsiMethod @NotNull [] composeSuperMethods(@NotNull PsiMethod method, boolean acceptSelf) {
    PsiElement[] superElements = DumbService.getInstance(method.getProject()).computeWithAlternativeResolveEnabled(
      () -> FindSuperElementsHelper.findSuperElements(method));

    PsiMethod[] superMethods = ContainerUtil.map(superElements, element -> (PsiMethod)element, PsiMethod.EMPTY_ARRAY);
    if (acceptSelf) {
      superMethods = ArrayUtil.prepend(method, superMethods);
    }
    return superMethods;
  }

  private static PsiElement getParentMethod(@NotNull PsiElement element) {
    final PsiElement parent = element.getParent();
    final PsiMethod interfaceMethod = LambdaUtil.getFunctionalInterfaceMethod(parent);
    return interfaceMethod != null ? interfaceMethod : parent;
  }

  public static final MarkerType OVERRIDDEN_METHOD = new MarkerType("OVERRIDDEN_METHOD", (NullableFunction<PsiElement, String>)element -> {
    PsiElement parent = element.getParent();
    if (!(parent instanceof PsiMethod method)) return null;
    return getOverriddenMethodTooltip(method);
  }, new InheritorsLineMarkerNavigator() {
    @Override
    protected String getMessageForDumbMode() {
      return JavaBundle.message("notification.navigation.to.overriding.methods");
    }
  });

  private static String getOverriddenMethodTooltip(@NotNull PsiMethod method) {
    final PsiClass aClass = method.getContainingClass();
    if (aClass != null && CommonClassNames.JAVA_LANG_OBJECT.equals(aClass.getQualifiedName())) {
      return getImplementationTooltip("tooltip.is.implemented.in.several.subclasses");
    }

    PsiElementProcessor.CollectElementsWithLimit<PsiMethod> processor = getProcessor(5, false);
    GlobalSearchScope scope = GlobalSearchScope.allScope(PsiUtilCore.getProjectInReadAction(method));
    OverridingMethodsSearch.search(method, scope, true).forEach(new PsiElementProcessorAdapter<>(processor));

    boolean isAbstract = method.hasModifierProperty(PsiModifier.ABSTRACT);

    if (processor.isOverflow()){
      return getImplementationTooltip(isAbstract ? "tooltip.is.implemented.in.several.subclasses" : "tooltip.is.overridden.in.several.subclasses");
    }

    PsiMethod[] overridings = processor.toArray(PsiMethod.EMPTY_ARRAY);
    if (overridings.length == 0) {
      return !isAbstract || aClass == null ? null : getFunctionalImplementationTooltip(aClass);
    }

    Comparator<PsiMethod> comparator = PsiElementRenderingInfo.getComparator(new PsiMethodRenderingInfo(false));
    Arrays.sort(overridings, comparator);

    return getImplementationTooltip(isAbstract ? "tooltip.is.implemented.in" : "tooltip.is.overridden.in", overridings);
  }

  public static final MarkerType SUBCLASSED_CLASS = new MarkerType("SUBCLASSED_CLASS", (NullableFunction<PsiElement, String>)element -> {
    PsiElement parent = element.getParent();
    if (!(parent instanceof PsiClass aClass)) return null;
    return getSubclassedClassTooltip(aClass);
  }, new InheritorsLineMarkerNavigator() {
    @Override
    protected String getMessageForDumbMode() {
      return JavaBundle.message("notification.navigation.to.overriding.classes");
    }
  });

  public static String getSubclassedClassTooltip(@NotNull PsiClass aClass) {
    PsiElementProcessor.CollectElementsWithLimit<PsiClass> processor = getProcessor(5, true);
    ClassInheritorsSearch.search(aClass).forEach(new PsiElementProcessorAdapter<>(processor));

    if (processor.isOverflow()) {
      return getImplementationTooltip(aClass.isInterface() ? "tooltip.is.implemented.by.several.subclasses" : "tooltip.is.overridden.by.several.subclasses");
    }

    PsiClass[] subclasses = processor.toArray(PsiClass.EMPTY_ARRAY);
    if (subclasses.length == 0) return getFunctionalImplementationTooltip(aClass);

    Comparator<PsiClass> comparator = PsiElementRenderingInfo.getComparator(PsiClassRenderingInfo.INSTANCE);
    Arrays.sort(subclasses, comparator);

    return getImplementationTooltip(aClass.isInterface() ? "tooltip.is.implemented.by" : "tooltip.is.subclassed.by", subclasses);
  }
}
