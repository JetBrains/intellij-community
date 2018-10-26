// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

/*
 * @author max
 */
package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.daemon.DaemonBundle;
import com.intellij.codeInsight.daemon.GutterIconNavigationHandler;
import com.intellij.codeInsight.navigation.BackgroundUpdaterTask;
import com.intellij.ide.util.*;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
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
import com.intellij.util.CommonProcessors;
import com.intellij.util.Function;
import com.intellij.util.NullableFunction;
import com.intellij.util.containers.ContainerUtil;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.MouseEvent;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

public class MarkerType {
  private final GutterIconNavigationHandler<PsiElement> handler;
  private final Function<PsiElement, String> myTooltip;
  @NotNull private final String myDebugName;

  /**
   * @deprecated use {@link #MarkerType(String, Function, LineMarkerNavigator)} instead
   */
  @Deprecated
  public MarkerType(@NotNull Function<PsiElement, String> tooltip, @NotNull final LineMarkerNavigator navigator) {
    this("Unknown", tooltip, navigator);
  }

  public MarkerType(@NotNull String debugName, @NotNull Function<PsiElement, String> tooltip, @NotNull final LineMarkerNavigator navigator) {
    myTooltip = tooltip;
    myDebugName = debugName;
    handler = (e, elt) -> DumbService.getInstance(elt.getProject()).withAlternativeResolveEnabled(() -> navigator.browse(e, elt));
  }

  @Override
  public String toString() {
    return myDebugName;
  }

  @NotNull
  public GutterIconNavigationHandler<PsiElement> getNavigationHandler() {
    return handler;
  }

  @NotNull
  public Function<PsiElement, String> getTooltip() {
    return myTooltip;
  }

  public static final MarkerType OVERRIDING_METHOD = new MarkerType("OVERRIDING_METHOD", (NullableFunction<PsiElement, String>)element -> {
    PsiElement parent = getParentMethod(element);
    if (!(parent instanceof PsiMethod)) return null;
    PsiMethod method = (PsiMethod)parent;

    return calculateOverridingMethodTooltip(method, method != element.getParent());
  }, new LineMarkerNavigator() {
    @Override
    public void browse(MouseEvent e, PsiElement element) {
      PsiElement parent = getParentMethod(element);
      if (!(parent instanceof PsiMethod)) return;
      PsiMethod method = (PsiMethod)parent;
      navigateToOverridingMethod(e, method, method != element.getParent());
    }
  });
  public static final MarkerType SIBLING_OVERRIDING_METHOD = new MarkerType("SIBLING_OVERRIDING_METHOD",
                                                                            (NullableFunction<PsiElement, String>)element -> {
                                                                       PsiElement parent = getParentMethod(element);
                                                                       if (!(parent instanceof PsiMethod)) return null;
                                                                       PsiMethod method = (PsiMethod)parent;

                                                                       return calculateOverridingSiblingMethodTooltip(method);
                                                                     }, new LineMarkerNavigator() {
    @Override
    public void browse(MouseEvent e, PsiElement element) {
      PsiElement parent = getParentMethod(element);
      if (!(parent instanceof PsiMethod)) return;
      PsiMethod method = (PsiMethod)parent;
      navigateToSiblingOverridingMethod(e, method);
    }
  });

  @Nullable
  private static String calculateOverridingMethodTooltip(@NotNull PsiMethod method, boolean acceptSelf) {
    PsiMethod[] superMethods = composeSuperMethods(method, acceptSelf);
    if (superMethods.length == 0) return null;

    String divider = GutterTooltipHelper.getElementDivider(false, false, superMethods.length);
    AtomicReference<String> reference = new AtomicReference<>(""); // optimization: calculate next divider only once
    return GutterTooltipHelper.getTooltipText(
      Arrays.asList(superMethods),
      superMethod -> getTooltipPrefix(method, superMethod, reference.getAndSet(divider)),
      superMethod -> isSameSignature(method, superMethod),
      IdeActions.ACTION_GOTO_SUPER);
  }

  @Nullable
  private static String calculateOverridingSiblingMethodTooltip(@NotNull PsiMethod method) {
    FindSuperElementsHelper.SiblingInfo pair = FindSuperElementsHelper.getSiblingInfoInheritedViaSubClass(method);
    if (pair == null) return null;

    return GutterTooltipHelper.getTooltipText(
      Arrays.asList(pair.superMethod, pair.subClass),
      element -> element instanceof PsiMethod ? getTooltipPrefix(method, (PsiMethod)element, "") : " via sub-class ",
      element -> element instanceof PsiMethod && isSameSignature(method, (PsiMethod)element),
      IdeActions.ACTION_GOTO_SUPER);
  }

  @NotNull
  private static String getTooltipPrefix(@NotNull PsiMethod method, @NotNull PsiMethod superMethod, @NotNull String prefix) {
    StringBuilder sb = new StringBuilder(prefix);
    boolean isAbstract = method.hasModifierProperty(PsiModifier.ABSTRACT);
    boolean isSuperAbstract = superMethod.hasModifierProperty(PsiModifier.ABSTRACT);
    sb.append(isSuperAbstract && !isAbstract ? "Implements method " : "Overrides method ");
    if (isSameSignature(method, superMethod)) sb.append("in ");
    return sb.toString();
  }

  private static boolean isSameSignature(@NotNull PsiMethod method, @NotNull PsiMethod superMethod) {
    return method.getSignature(PsiSubstitutor.EMPTY).equals(superMethod.getSignature(PsiSubstitutor.EMPTY));
  }

  @NotNull
  private static String getImplementationTooltip(@NotNull String prefix, @NotNull PsiElement... elements) {
    return GutterTooltipHelper.getTooltipText(Arrays.asList(elements), prefix, true, IdeActions.ACTION_GOTO_IMPLEMENTATION);
  }

  private static void navigateToOverridingMethod(MouseEvent e, @NotNull PsiMethod method, boolean acceptSelf) {
    PsiMethod[] superMethods = composeSuperMethods(method, acceptSelf);
    if (superMethods.length == 0) return;
    boolean showMethodNames = !PsiUtil.allMethodsHaveSameSignature(superMethods);
    PsiElementListNavigator.openTargets(e, superMethods,
                                        DaemonBundle.message("navigation.title.super.method", method.getName()),
                                        DaemonBundle.message("navigation.findUsages.title.super.method", method.getName()),
                                        new MethodCellRenderer(showMethodNames));
  }

  private static void navigateToSiblingOverridingMethod(MouseEvent e, @NotNull PsiMethod method) {
    PsiMethod superMethod = FindSuperElementsHelper.getSiblingInheritedViaSubClass(method);
    if (superMethod == null) return;
    PsiElementListNavigator.openTargets(e, new NavigatablePsiElement[]{superMethod},
                                        DaemonBundle.message("navigation.title.super.method", method.getName()),
                                        DaemonBundle.message("navigation.findUsages.title.super.method", method.getName()),
                                        new MethodCellRenderer(false));
  }

  @NotNull
  private static PsiMethod[] composeSuperMethods(@NotNull PsiMethod method, boolean acceptSelf) {
    PsiElement[] superElements = FindSuperElementsHelper.findSuperElements(method);

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

  public static final String SEARCHING_FOR_OVERRIDING_METHODS = "Searching for Overriding Methods";
  public static final MarkerType OVERRIDDEN_METHOD = new MarkerType("OVERRIDDEN_METHOD", (NullableFunction<PsiElement, String>)element -> {
    PsiElement parent = element.getParent();
    if (!(parent instanceof PsiMethod)) return null;
    PsiMethod method = (PsiMethod)parent;

    return getOverriddenMethodTooltip(method);
  }, new LineMarkerNavigator(){
    @Override
    public void browse(MouseEvent e, PsiElement element) {
      PsiElement parent = element.getParent();
      if (!(parent instanceof PsiMethod)) return;
      navigateToOverriddenMethod(e, (PsiMethod)parent);
    }
  });

  private static String getOverriddenMethodTooltip(@NotNull PsiMethod method) {
    final PsiClass aClass = method.getContainingClass();
    if (aClass != null && CommonClassNames.JAVA_LANG_OBJECT.equals(aClass.getQualifiedName())) {
      return getImplementationTooltip("Has implementations");
    }

    PsiElementProcessor.CollectElementsWithLimit<PsiMethod> processor = new PsiElementProcessor.CollectElementsWithLimit<>(5);
    GlobalSearchScope scope = GlobalSearchScope.allScope(PsiUtilCore.getProjectInReadAction(method));
    OverridingMethodsSearch.search(method, scope, true).forEach(new PsiElementProcessorAdapter<>(processor));

    boolean isAbstract = method.hasModifierProperty(PsiModifier.ABSTRACT);

    if (processor.isOverflow()){
      return getImplementationTooltip(isAbstract ? "Has implementations" : "Is overridden in subclasses");
    }

    PsiMethod[] overridings = processor.toArray(PsiMethod.EMPTY_ARRAY);
    if (overridings.length == 0) {
      if (aClass != null && isAbstract && FunctionalExpressionSearch.search(aClass).findFirst() != null) {
        return getImplementationTooltip("Has functional implementations");
      }
      return null;
    }

    Comparator<PsiMethod> comparator = new MethodCellRenderer(false).getComparator();
    Arrays.sort(overridings, comparator);

    return getImplementationTooltip(isAbstract ? "Is implemented in" : "Is overridden in", overridings);
  }

  private static void navigateToOverriddenMethod(MouseEvent e, @NotNull final PsiMethod method) {
    if (DumbService.isDumb(method.getProject())) {
      DumbService.getInstance(method.getProject()).showDumbModeNotification(
        "Navigation to overriding classes is not possible during index update");
      return;
    }

    PsiElementProcessor.CollectElementsWithLimit<PsiMethod> collectProcessor = new PsiElementProcessor.CollectElementsWithLimit<>(2, new THashSet<>());
    PsiElementProcessor.CollectElementsWithLimit<PsiFunctionalExpression> collectExprProcessor = new PsiElementProcessor.CollectElementsWithLimit<>(2, new THashSet<>());
    final boolean isAbstract = method.hasModifierProperty(PsiModifier.ABSTRACT);
    if (!ProgressManager.getInstance().runProcessWithProgressSynchronously(() -> {
      GlobalSearchScope scope = GlobalSearchScope.allScope(PsiUtilCore.getProjectInReadAction(method));
      OverridingMethodsSearch.search(method, scope,true).forEach(new PsiElementProcessorAdapter<>(collectProcessor));
      if (isAbstract && collectProcessor.getCollection().size() < 2) {
        final PsiClass aClass = ReadAction.compute(method::getContainingClass);
        if (aClass != null) {
          FunctionalExpressionSearch.search(aClass).forEach(new PsiElementProcessorAdapter<>(collectExprProcessor));
        }
      }
    }, SEARCHING_FOR_OVERRIDING_METHODS, true, method.getProject(), (JComponent)e.getComponent())) {
      return;
    }

    final PsiMethod[] methodOverriders = collectProcessor.toArray(PsiMethod.EMPTY_ARRAY);
    final List<NavigatablePsiElement> overridings = new ArrayList<>();
    overridings.addAll(collectProcessor.getCollection());
    overridings.addAll(collectExprProcessor.getCollection());
    if (overridings.isEmpty()) return;
    boolean showMethodNames = !PsiUtil.allMethodsHaveSameSignature(methodOverriders);
    MethodOrFunctionalExpressionCellRenderer renderer = new MethodOrFunctionalExpressionCellRenderer(showMethodNames);
    Collections.sort(overridings, renderer.getComparator());
    final OverridingMethodsUpdater methodsUpdater = new OverridingMethodsUpdater(method, renderer);
    PsiElementListNavigator.openTargets(e, overridings.toArray(NavigatablePsiElement.EMPTY_NAVIGATABLE_ELEMENT_ARRAY), methodsUpdater.getCaption(overridings.size()), "Overriding methods of " + method.getName(), renderer, methodsUpdater);
  }

  private static final String SEARCHING_FOR_OVERRIDDEN_METHODS = "Searching for Overridden Methods";
  public static final MarkerType SUBCLASSED_CLASS = new MarkerType("SUBCLASSED_CLASS", (NullableFunction<PsiElement, String>)element -> {
    PsiElement parent = element.getParent();
    if (!(parent instanceof PsiClass)) return null;
    PsiClass aClass = (PsiClass)parent;
    return getSubclassedClassTooltip(aClass);
  }, new LineMarkerNavigator() {
    @Override
    public void browse(MouseEvent e, PsiElement element) {
      final PsiElement parent = element.getParent();
      if (!(parent instanceof PsiClass)) return;
      final PsiClass aClass = (PsiClass)parent;

      navigateToSubclassedClass(e, aClass);
    }
  });

  // Used in Kotlin, please don't make private
  public static String getSubclassedClassTooltip(@NotNull PsiClass aClass) {
    PsiElementProcessor.CollectElementsWithLimit<PsiClass> processor = new PsiElementProcessor.CollectElementsWithLimit<>(5,
                                                                                                                          new THashSet<>());
    ClassInheritorsSearch.search(aClass).forEach(new PsiElementProcessorAdapter<>(processor));

    if (processor.isOverflow()) {
      return getImplementationTooltip(aClass.isInterface() ? "Has implementations" : "Has subclasses");
    }

    PsiClass[] subclasses = processor.toArray(PsiClass.EMPTY_ARRAY);
    if (subclasses.length == 0) {
      final PsiElementProcessor.CollectElementsWithLimit<PsiFunctionalExpression> functionalImplementations =
        new PsiElementProcessor.CollectElementsWithLimit<>(2, new THashSet<>());
      FunctionalExpressionSearch.search(aClass).forEach(new PsiElementProcessorAdapter<>(functionalImplementations));
      if (!functionalImplementations.getCollection().isEmpty()) {
        return getImplementationTooltip("Has functional implementations");
      }
      return null;
    }

    Comparator<PsiClass> comparator = new PsiClassListCellRenderer().getComparator();
    Arrays.sort(subclasses, comparator);

    return getImplementationTooltip(aClass.isInterface() ? "Is implemented by" : "Is subclassed by", subclasses);
  }

  // Used in Kotlin, please don't make private
  public static void navigateToSubclassedClass(MouseEvent e,
                                               @NotNull final PsiClass aClass) {
    navigateToSubclassedClass(e, aClass, new PsiClassOrFunctionalExpressionListCellRenderer());
  }

  // Used in Kotlin, please don't make private
  public static void navigateToSubclassedClass(MouseEvent e,
                                               @NotNull final PsiClass aClass,
                                               PsiElementListCellRenderer<NavigatablePsiElement> renderer) {
    if (DumbService.isDumb(aClass.getProject())) {
      DumbService.getInstance(aClass.getProject()).showDumbModeNotification("Navigation to overriding methods is not possible during index update");
      return;
    }

    final PsiElementProcessor.FindElement<PsiClass> collectProcessor = new PsiElementProcessor.FindElement<>();
    final PsiElementProcessor.FindElement<PsiFunctionalExpression> collectExprProcessor = new PsiElementProcessor.FindElement<>();
    if (!ProgressManager.getInstance().runProcessWithProgressSynchronously(() -> {
      ClassInheritorsSearch.search(aClass).forEach(new PsiElementProcessorAdapter<>(collectProcessor));
      if (collectProcessor.getFoundElement() == null) {
        FunctionalExpressionSearch.search(aClass).forEach(new PsiElementProcessorAdapter<>(collectExprProcessor));
      }
    }, SEARCHING_FOR_OVERRIDDEN_METHODS, true, aClass.getProject(), (JComponent)e.getComponent())) {
      return;
    }

    final List<NavigatablePsiElement> inheritors = new ArrayList<>();
    ContainerUtil.addIfNotNull(inheritors, collectProcessor.getFoundElement());
    ContainerUtil.addIfNotNull(inheritors, collectExprProcessor.getFoundElement());
    if (inheritors.isEmpty()) return;
    final SubclassUpdater subclassUpdater = new SubclassUpdater(aClass, renderer);
    Collections.sort(inheritors, renderer.getComparator());
    PsiElementListNavigator.openTargets(e, inheritors.toArray(NavigatablePsiElement.EMPTY_NAVIGATABLE_ELEMENT_ARRAY), subclassUpdater.getCaption(inheritors.size()), CodeInsightBundle.message("goto.implementation.findUsages.title", aClass.getName()), renderer, subclassUpdater);
  }

  private static class SubclassUpdater extends BackgroundUpdaterTask {
    private final PsiClass myClass;

    private SubclassUpdater(@NotNull PsiClass aClass, @NotNull PsiElementListCellRenderer<NavigatablePsiElement> renderer) {
      super(aClass.getProject(), SEARCHING_FOR_OVERRIDDEN_METHODS, createComparatorWrapper((Comparator)renderer.getComparator()));
      myClass = aClass;
    }

    @Override
    public String getCaption(int size) {
      String suffix = isFinished() ? "" : " so far";
      return myClass.isInterface()
             ? CodeInsightBundle.message("goto.implementation.chooserTitle", myClass.getName(), size, suffix)
             : DaemonBundle.message("navigation.title.subclass", myClass.getName(), size, suffix);
    }

    @Override
    public void onSuccess() {
      super.onSuccess();
      PsiElement oneElement = getTheOnlyOneElement();
      if (oneElement instanceof NavigatablePsiElement) {
        ((NavigatablePsiElement)oneElement).navigate(true);
        myPopup.cancel();
      }
    }

    @Override
    public void run(@NotNull final ProgressIndicator indicator) {
      super.run(indicator);
      ClassInheritorsSearch.search(myClass, ReadAction.compute(myClass::getUseScope), true).forEach(new CommonProcessors.CollectProcessor<PsiClass>() {
        @Override
        public boolean process(final PsiClass o) {
          if (!updateComponent(o)) {
            indicator.cancel();
          }
          ProgressManager.checkCanceled();
          return super.process(o);
        }
      });

      FunctionalExpressionSearch.search(myClass).forEach(new CommonProcessors.CollectProcessor<PsiFunctionalExpression>() {
        @Override
        public boolean process(final PsiFunctionalExpression expr) {
          if (!updateComponent(expr)) {
            indicator.cancel();
          }
          ProgressManager.checkCanceled();
          return super.process(expr);
        }
      });
    }
  }

  private static class OverridingMethodsUpdater extends BackgroundUpdaterTask {
    private final PsiMethod myMethod;

    private OverridingMethodsUpdater(@NotNull PsiMethod method, @NotNull PsiElementListCellRenderer renderer) {
      super(method.getProject(), SEARCHING_FOR_OVERRIDING_METHODS, createComparatorWrapper(renderer.getComparator()));
      myMethod = method;
    }

    @Override
    public String getCaption(int size) {
      return DaemonBundle.message(myMethod.hasModifierProperty(PsiModifier.ABSTRACT) ?
                                  "navigation.title.implementation.method" :
                                  "navigation.title.overrider.method", myMethod.getName(), size);
    }

    @Override
    public void onSuccess() {
      super.onSuccess();
      PsiElement oneElement = getTheOnlyOneElement();
      if (oneElement instanceof NavigatablePsiElement) {
        ((NavigatablePsiElement)oneElement).navigate(true);
        myPopup.cancel();
      }
    }

    @Override
    public void run(@NotNull final ProgressIndicator indicator) {
      super.run(indicator);
      GlobalSearchScope scope = GlobalSearchScope.allScope(PsiUtilCore.getProjectInReadAction(myMethod));
      OverridingMethodsSearch.search(myMethod, scope, true).forEach(
        new CommonProcessors.CollectProcessor<PsiMethod>() {
          @Override
          public boolean process(PsiMethod psiMethod) {
            if (!updateComponent(psiMethod)) {
              indicator.cancel();
            }
            ProgressManager.checkCanceled();
            return super.process(psiMethod);
          }
        });
      if (ReadAction.compute(() -> myMethod.hasModifierProperty(PsiModifier.ABSTRACT))) {
        PsiClass psiClass = ReadAction.compute(myMethod::getContainingClass);
        FunctionalExpressionSearch.search(psiClass).forEach(new CommonProcessors.CollectProcessor<PsiFunctionalExpression>() {
          @Override
          public boolean process(final PsiFunctionalExpression expr) {
            if (!updateComponent(expr)) {
              indicator.cancel();
            }
            ProgressManager.checkCanceled();
            return super.process(expr);
          }
        });
      }
    }
  }
}
