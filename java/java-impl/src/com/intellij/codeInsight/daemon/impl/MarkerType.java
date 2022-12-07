// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.daemon.DaemonBundle;
import com.intellij.codeInsight.daemon.GutterIconNavigationHandler;
import com.intellij.codeInsight.navigation.BackgroundUpdaterTask;
import com.intellij.ide.util.*;
import com.intellij.java.JavaBundle;
import com.intellij.java.analysis.JavaAnalysisBundle;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.IntellijInternalApi;
import com.intellij.openapi.util.NlsContexts.ProgressTitle;
import com.intellij.psi.*;
import com.intellij.psi.impl.FindSuperElementsHelper;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiElementProcessor;
import com.intellij.psi.search.PsiElementProcessorAdapter;
import com.intellij.psi.search.PsiSearchHelper;
import com.intellij.psi.search.searches.ClassInheritorsSearch;
import com.intellij.psi.search.searches.FunctionalExpressionSearch;
import com.intellij.psi.search.searches.OverridingMethodsSearch;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.util.*;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.MouseEvent;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

public class MarkerType {
  private final GutterIconNavigationHandler<PsiElement> handler;
  private final Function<? super PsiElement, String> myTooltip;
  @NotNull private final String myDebugName;

  public MarkerType(@NotNull String debugName, @NotNull Function<? super PsiElement, String> tooltip, @NotNull final LineMarkerNavigator navigator) {
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
  public Function<? super PsiElement, String> getTooltip() {
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

    String divider = GutterTooltipBuilder.getElementDivider(false, false, superMethods.length);
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
      element -> element instanceof PsiMethod ? getTooltipPrefix(method, (PsiMethod)element, "") : " " + JavaBundle.message("tooltip.via.subclass") + " ",
      element -> element instanceof PsiMethod && isSameSignature(method, (PsiMethod)element),
      IdeActions.ACTION_GOTO_SUPER);
  }

  @NotNull
  private static String getTooltipPrefix(@NotNull PsiMethod method, @NotNull PsiMethod superMethod, @NotNull String prefix) {
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

  @NotNull
  private static <E extends PsiElement> PsiElementProcessor.CollectElementsWithLimit<E> getProcessor(int limit, boolean set) {
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

  @NotNull
  private static String getImplementationTooltip(@NotNull String prefixKey, PsiElement @NotNull ... elements) {
    return getImplementationTooltip(Arrays.asList(elements), JavaBundle.message(prefixKey));
  }

  @NotNull
  private static String getImplementationTooltip(@NotNull Collection<? extends PsiElement> elements, @NotNull String prefix) {
    return GutterTooltipHelper.getTooltipText(elements, prefix, true, IdeActions.ACTION_GOTO_IMPLEMENTATION);
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
    PsiElementListNavigator.openTargets(e, new PsiMethod[]{superMethod},
                                        DaemonBundle.message("navigation.title.super.method", method.getName()),
                                        DaemonBundle.message("navigation.findUsages.title.super.method", method.getName()),
                                        new MethodCellRenderer(false));
  }

  private static PsiMethod @NotNull [] composeSuperMethods(@NotNull PsiMethod method, boolean acceptSelf) {
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

  private static void navigateToOverriddenMethod(MouseEvent e, @NotNull final PsiMethod method) {
    if (DumbService.isDumb(method.getProject())) {
      DumbService.getInstance(method.getProject()).showDumbModeNotification(
        JavaBundle.message("notification.navigation.to.overriding.classes"));
      return;
    }

    PsiElementProcessor.CollectElementsWithLimit<PsiMethod> collectProcessor = getProcessor(2, true);
    PsiElementProcessor.CollectElementsWithLimit<PsiFunctionalExpression> collectExprProcessor = getProcessor(2, true);
    final boolean isAbstract = method.hasModifierProperty(PsiModifier.ABSTRACT);
    if (!ProgressManager.getInstance().runProcessWithProgressSynchronously(() -> {
      GlobalSearchScope scope = GlobalSearchScope.allScope(PsiUtilCore.getProjectInReadAction(method));
      OverridingMethodsSearch.search(method, scope,true).forEach(new PsiElementProcessorAdapter<>(collectProcessor));
      if (isAbstract && collectProcessor.getCollection().size() < 2) {
        FunctionalExpressionSearch.search(method).forEach(new PsiElementProcessorAdapter<>(collectExprProcessor));
      }
    }, JavaAnalysisBundle.message("searching.for.overriding.methods"), true, method.getProject(), (JComponent)e.getComponent())) {
      return;
    }

    final PsiMethod[] methodOverriders = collectProcessor.toArray(PsiMethod.EMPTY_ARRAY);
    final List<NavigatablePsiElement> overridings = new ArrayList<>();
    overridings.addAll(collectProcessor.getCollection());
    overridings.addAll(collectExprProcessor.getCollection());
    if (overridings.isEmpty()) return;
    boolean showMethodNames = !PsiUtil.allMethodsHaveSameSignature(methodOverriders);
    MethodOrFunctionalExpressionCellRenderer renderer = new MethodOrFunctionalExpressionCellRenderer(showMethodNames);
    overridings.sort(renderer.getComparator());
    final OverridingMethodsUpdater methodsUpdater = new OverridingMethodsUpdater(method, renderer);
    PsiElementListNavigator.openTargets(e, overridings.toArray(NavigatablePsiElement.EMPTY_NAVIGATABLE_ELEMENT_ARRAY), methodsUpdater.getCaption(overridings.size()),
                                        JavaAnalysisBundle.message("navigate.to.overridden.methods.title", method.getName()), renderer, methodsUpdater);
  }

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

  public static void navigateToSubclassedClass(MouseEvent e,
                                               @NotNull final PsiClass aClass) {
    navigateToSubclassedClass(e, aClass, new PsiClassOrFunctionalExpressionListCellRenderer());
  }

  public static void navigateToSubclassedClass(MouseEvent e,
                                               @NotNull final PsiClass aClass,
                                               PsiElementListCellRenderer<NavigatablePsiElement> renderer) {
    if (DumbService.isDumb(aClass.getProject())) {
      DumbService.getInstance(aClass.getProject()).showDumbModeNotification(
        JavaBundle.message("notification.navigation.to.overriding.methods"));
      return;
    }

    final PsiElementProcessor.FindElement<PsiClass> collectProcessor = new PsiElementProcessor.FindElement<>();
    final PsiElementProcessor.FindElement<PsiFunctionalExpression> collectExprProcessor = new PsiElementProcessor.FindElement<>();
    if (!ProgressManager.getInstance().runProcessWithProgressSynchronously(() -> {
      ClassInheritorsSearch.search(aClass).forEach(new PsiElementProcessorAdapter<>(collectProcessor));
      if (collectProcessor.getFoundElement() == null) {
        FunctionalExpressionSearch.search(aClass).forEach(new PsiElementProcessorAdapter<>(collectExprProcessor));
      }
    }, JavaAnalysisBundle.message("progress.title.searching.for.overridden.methods"), true, aClass.getProject(), (JComponent)e.getComponent())) {
      return;
    }

    final List<NavigatablePsiElement> inheritors = new ArrayList<>();
    ContainerUtil.addIfNotNull(inheritors, collectProcessor.getFoundElement());
    ContainerUtil.addIfNotNull(inheritors, collectExprProcessor.getFoundElement());
    if (inheritors.isEmpty()) return;
    final SubclassUpdater subclassUpdater = new SubclassUpdater(aClass, renderer);
    inheritors.sort(renderer.getComparator());
    PsiElementListNavigator.openTargets(e, inheritors.toArray(NavigatablePsiElement.EMPTY_NAVIGATABLE_ELEMENT_ARRAY),
                                        subclassUpdater.getCaption(inheritors.size()), CodeInsightBundle.message("goto.implementation.findUsages.title", aClass.getName()), renderer, subclassUpdater);
  }

  @IntellijInternalApi
  public static abstract class OverridingMembersUpdater extends BackgroundUpdaterTask {
    public OverridingMembersUpdater(@Nullable Project project,
                                     @NotNull @ProgressTitle String title,
                                     @NotNull PsiElementListCellRenderer<NavigatablePsiElement> renderer) {
      super(project, title, createComparatorWrapper((Comparator)renderer.getComparator()));
    }

    void collectFunctionalInheritors(@NotNull ProgressIndicator indicator, PsiMember member) {
      Query<PsiFunctionalExpression> search = member instanceof PsiClass ? FunctionalExpressionSearch.search((PsiClass)member) 
                                                                         : FunctionalExpressionSearch.search((PsiMethod)member);
      search.forEach(expr -> {
        if (!updateComponent(expr)) {
          indicator.cancel();
        }
        ProgressManager.checkCanceled();
        return true;
      });
    }
  }

  @IntellijInternalApi
  public static final class SubclassUpdater extends OverridingMembersUpdater {
    private final PsiClass myClass;

    public SubclassUpdater(@NotNull PsiClass aClass, @NotNull PsiElementListCellRenderer<NavigatablePsiElement> renderer) {
      super(aClass.getProject(), JavaAnalysisBundle.message("subclasses.search.progress.title"), renderer);
      myClass = aClass;
    }

    @Override
    public @Nls String getCaption(int size) {
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
      ClassInheritorsSearch.search(myClass, ReadAction.compute(() -> PsiSearchHelper.getInstance(myProject).getUseScope(myClass)), true).forEach(
        o -> {
          if (!updateComponent(o)) {
            indicator.cancel();
          }
          ProgressManager.checkCanceled();
          return true;
        });

      collectFunctionalInheritors(indicator, myClass);
    }
  }

  private static final class OverridingMethodsUpdater extends OverridingMembersUpdater {
    private final PsiMethod myMethod;

    private OverridingMethodsUpdater(@NotNull PsiMethod method, @NotNull PsiElementListCellRenderer<NavigatablePsiElement> renderer) {
      super(method.getProject(), JavaAnalysisBundle.message("searching.for.overriding.methods"), renderer);
      myMethod = method;
    }

    @Override
    public @Nls String getCaption(int size) {
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
        new CommonProcessors.CollectProcessor<>() {
          @Override
          public boolean process(PsiMethod psiMethod) {
            if (!updateComponent(psiMethod)) {
              indicator.cancel();
            }
            ProgressManager.checkCanceled();
            return super.process(psiMethod);
          }
        });
      collectFunctionalInheritors(indicator, myMethod);
    }
  }
}
