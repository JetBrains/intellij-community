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

/*
 * @author max
 */
package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.daemon.DaemonBundle;
import com.intellij.codeInsight.daemon.GutterIconNavigationHandler;
import com.intellij.codeInsight.navigation.ListBackgroundUpdaterTask;
import com.intellij.ide.util.*;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.actionSystem.Shortcut;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.util.Computable;
import com.intellij.psi.*;
import com.intellij.psi.search.PsiElementProcessor;
import com.intellij.psi.search.PsiElementProcessorAdapter;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.search.searches.ClassInheritorsSearch;
import com.intellij.psi.search.searches.FunctionalExpressionSearch;
import com.intellij.psi.search.searches.OverridingMethodsSearch;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.CommonProcessors;
import com.intellij.util.Function;
import com.intellij.util.NullableFunction;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.MouseEvent;
import java.util.Arrays;
import java.util.Comparator;

public class MarkerType {

  private final GutterIconNavigationHandler<PsiElement> handler;
  private final Function<PsiElement, String> myTooltip;

  public MarkerType(@NotNull Function<PsiElement, String> tooltip, @NotNull final LineMarkerNavigator navigator) {
    myTooltip = tooltip;
    handler = new GutterIconNavigationHandler<PsiElement>() {
      @Override
      public void navigate(final MouseEvent e, final PsiElement elt) {
        DumbService.getInstance(elt.getProject()).withAlternativeResolveEnabled(new Runnable() {
          @Override
          public void run() {
            navigator.browse(e, elt);
          }
        });
      }
    };
  }

  @NotNull
  public GutterIconNavigationHandler<PsiElement> getNavigationHandler() {
    return handler;
  }

  @NotNull
  public Function<PsiElement, String> getTooltip() {
    return myTooltip;
  }

  static final MarkerType OVERRIDING_METHOD = new MarkerType(new NullableFunction<PsiElement, String>() {
    @Override
    public String fun(PsiElement element) {
      PsiElement parent = getParentMethod(element);
      if (!(parent instanceof PsiMethod)) return null;
      PsiMethod method = (PsiMethod)parent;

      return calculateOverridingMethodTooltip(method, method != element.getParent());
    }
  }, new LineMarkerNavigator() {
    @Override
    public void browse(MouseEvent e, PsiElement element) {
      PsiElement parent = getParentMethod(element);
      if (!(parent instanceof PsiMethod)) return;
      PsiMethod method = (PsiMethod)parent;
      navigateToOverridingMethod(e, method, method != element.getParent());
    }
  });

  @Nullable
  public static String calculateOverridingMethodTooltip(PsiMethod method, boolean acceptSelf) {
    PsiMethod[] superMethods = composeSuperMethods(method, acceptSelf);
    if (superMethods == null) return null;

    PsiMethod superMethod = superMethods[0];
    boolean isAbstract = method.hasModifierProperty(PsiModifier.ABSTRACT);
    boolean isSuperAbstract = superMethod.hasModifierProperty(PsiModifier.ABSTRACT);

    final boolean sameSignature = superMethod.getSignature(PsiSubstitutor.EMPTY).equals(method.getSignature(PsiSubstitutor.EMPTY));
    @NonNls final String key;
    if (isSuperAbstract && !isAbstract){
      key = sameSignature ? "method.implements" : "method.implements.in";
    }
    else{
      key = sameSignature ? "method.overrides" : "method.overrides.in";
    }
    return composeText(superMethods, "", DaemonBundle.message(key), "GotoSuperMethod");
  }

  private static String composeText(PsiElement[] methods, String start, String pattern, String actionId) {
    Shortcut[] shortcuts = ActionManager.getInstance().getAction(actionId).getShortcutSet().getShortcuts();
    Shortcut shortcut = ArrayUtil.getFirstElement(shortcuts);
    String postfix = "<br><div style='margin-top: 5px'><font size='2'>Click";
    if (shortcut != null) postfix += " or press " + KeymapUtil.getShortcutText(shortcut);
    postfix += " to navigate</font></div>";
    return GutterIconTooltipHelper.composeText(Arrays.asList(methods), start, pattern, postfix);
  }

  public static void navigateToOverridingMethod(MouseEvent e, PsiMethod method, boolean acceptSelf) {
    PsiMethod[] superMethods = composeSuperMethods(method, acceptSelf);
    if (superMethods == null) return;
    boolean showMethodNames = !PsiUtil.allMethodsHaveSameSignature(superMethods);
    PsiElementListNavigator.openTargets(e, superMethods,
                                        DaemonBundle.message("navigation.title.super.method", method.getName()),
                                        DaemonBundle.message("navigation.findUsages.title.super.method", method.getName()),
                                        new MethodCellRenderer(showMethodNames));
  }

  @Nullable
  private static PsiMethod[] composeSuperMethods(PsiMethod method, boolean acceptSelf) {
    PsiMethod[] superMethods = method.findSuperMethods(false);
    if (acceptSelf) {
      superMethods = ArrayUtil.prepend(method, superMethods);
    }
    if (superMethods.length == 0) return null;
    return superMethods;
  }

  private static PsiElement getParentMethod(PsiElement element) {
    final PsiElement parent = element.getParent();
    final PsiMethod interfaceMethod = LambdaUtil.getFunctionalInterfaceMethod(parent);
    return interfaceMethod != null ? interfaceMethod : parent;
  }

  public static final String SEARCHING_FOR_OVERRIDING_METHODS = "Searching for Overriding Methods";
  static final MarkerType OVERRIDDEN_METHOD = new MarkerType(new NullableFunction<PsiElement, String>() {
    @Override
    public String fun(PsiElement element) {
      PsiElement parent = element.getParent();
      if (!(parent instanceof PsiMethod)) return null;
      PsiMethod method = (PsiMethod)parent;

      return getOverriddenMethodTooltip(method);
    }
  }, new LineMarkerNavigator(){
    @Override
    public void browse(MouseEvent e, PsiElement element) {
      PsiElement parent = element.getParent();
      if (!(parent instanceof PsiMethod)) return;
      navigateToOverriddenMethod(e, (PsiMethod)parent);

    }
  });

  public static String getOverriddenMethodTooltip(final PsiMethod method) {
    PsiElementProcessor.CollectElementsWithLimit<PsiMethod> processor = new PsiElementProcessor.CollectElementsWithLimit<PsiMethod>(5);
    OverridingMethodsSearch.search(method, true).forEach(new PsiElementProcessorAdapter<PsiMethod>(processor));

    boolean isAbstract = method.hasModifierProperty(PsiModifier.ABSTRACT);

    if (processor.isOverflow()){
      return isAbstract ? DaemonBundle.message("method.is.implemented.too.many") : DaemonBundle.message("method.is.overridden.too.many");
    }

    PsiMethod[] overridings = processor.toArray(PsiMethod.EMPTY_ARRAY);
    if (overridings.length == 0) {
      final PsiClass aClass = method.getContainingClass();
      if (aClass != null && FunctionalExpressionSearch.search(aClass).findFirst() != null) {
        return "Has functional implementations";
      }
      return null;
    }

    Comparator<PsiMethod> comparator = new MethodCellRenderer(false).getComparator();
    Arrays.sort(overridings, comparator);

    String start = isAbstract ? DaemonBundle.message("method.is.implemented.header") : DaemonBundle.message("method.is.overriden.header");
    @NonNls String pattern = "&nbsp;&nbsp;&nbsp;&nbsp;<a href=\"#javaClass/{1}\">{1}</a>";
    return composeText(overridings, start, pattern, IdeActions.ACTION_GOTO_IMPLEMENTATION);
  }

  public static void navigateToOverriddenMethod(MouseEvent e, final PsiMethod method) {
    if (DumbService.isDumb(method.getProject())) {
      DumbService.getInstance(method.getProject()).showDumbModeNotification(
        "Navigation to overriding classes is not possible during index update");
      return;
    }

    final PsiElementProcessor.CollectElementsWithLimit<PsiMethod> collectProcessor =
      new PsiElementProcessor.CollectElementsWithLimit<PsiMethod>(2, new THashSet<PsiMethod>());
    final PsiElementProcessor.CollectElementsWithLimit<PsiFunctionalExpression> collectExprProcessor = 
      new PsiElementProcessor.CollectElementsWithLimit<PsiFunctionalExpression>(2, new THashSet<PsiFunctionalExpression>());
    if (!ProgressManager.getInstance().runProcessWithProgressSynchronously(new Runnable() {
      @Override
      public void run() {
        OverridingMethodsSearch.search(method, true).forEach(new PsiElementProcessorAdapter<PsiMethod>(collectProcessor));
        if (collectProcessor.getCollection().isEmpty()) {
          final PsiClass aClass = ApplicationManager.getApplication().runReadAction(new Computable<PsiClass>() {
            @Override
            public PsiClass compute() {
              return method.getContainingClass();
            }
          });
          if (aClass != null) {
            FunctionalExpressionSearch.search(aClass).forEach(new PsiElementProcessorAdapter<PsiFunctionalExpression>(collectExprProcessor));
          }
        }
      }
    }, SEARCHING_FOR_OVERRIDING_METHODS, true, method.getProject(), (JComponent)e.getComponent())) {
      return;
    }

    final PsiMethod[] methodOverriders = collectProcessor.toArray(PsiMethod.EMPTY_ARRAY);
    NavigatablePsiElement[] overridings = ArrayUtil.mergeArrays(methodOverriders, collectExprProcessor.toArray(PsiFunctionalExpression.EMPTY_ARRAY));
    if (overridings.length == 0) return;
    boolean showMethodNames = !PsiUtil.allMethodsHaveSameSignature(methodOverriders);
    MethodOrFunctionalExpressionCellRenderer renderer = new MethodOrFunctionalExpressionCellRenderer(showMethodNames);
    Arrays.sort(overridings, renderer.getComparator());
    final OverridingMethodsUpdater methodsUpdater = new OverridingMethodsUpdater(method, renderer);
    PsiElementListNavigator.openTargets(e, overridings, methodsUpdater.getCaption(overridings.length), "Overriding methods of " + method.getName(), renderer, methodsUpdater);
  }

  private static final String SEARCHING_FOR_OVERRIDDEN_METHODS = "Searching for Overridden Methods";
  static final MarkerType SUBCLASSED_CLASS = new MarkerType(new NullableFunction<PsiElement, String>() {
    @Override
    public String fun(PsiElement element) {
      PsiElement parent = element.getParent();
      if (!(parent instanceof PsiClass)) return null;
      PsiClass aClass = (PsiClass)parent;
      return getSubclassedClassTooltip(aClass);
    }
  }, new LineMarkerNavigator() {
    @Override
    public void browse(MouseEvent e, PsiElement element) {
      final PsiElement parent = element.getParent();
      if (!(parent instanceof PsiClass)) return;
      final PsiClass aClass = (PsiClass)parent;

      navigateToSubclassedClass(e, aClass);
    }
  });

  public static String getSubclassedClassTooltip(PsiClass aClass) {
    PsiElementProcessor.CollectElementsWithLimit<PsiClass> processor = new PsiElementProcessor.CollectElementsWithLimit<PsiClass>(5, new THashSet<PsiClass>());
    ClassInheritorsSearch.search(aClass, true).forEach(new PsiElementProcessorAdapter<PsiClass>(processor));

    if (processor.isOverflow()) {
      return aClass.isInterface()
             ? DaemonBundle.message("interface.is.implemented.too.many")
             : DaemonBundle.message("class.is.subclassed.too.many");
    }

    PsiClass[] subclasses = processor.toArray(PsiClass.EMPTY_ARRAY);
    if (subclasses.length == 0) {
      final PsiElementProcessor.CollectElementsWithLimit<PsiFunctionalExpression> functionalImplementations =
        new PsiElementProcessor.CollectElementsWithLimit<PsiFunctionalExpression>(2, new THashSet<PsiFunctionalExpression>());
      FunctionalExpressionSearch.search(aClass).forEach(new PsiElementProcessorAdapter<PsiFunctionalExpression>(functionalImplementations));
      if (!functionalImplementations.getCollection().isEmpty()) {
        return "Has functional implementations";
      }
      return null;
    }

    Comparator<PsiClass> comparator = new PsiClassListCellRenderer().getComparator();
    Arrays.sort(subclasses, comparator);

    String start = aClass.isInterface()
                   ? DaemonBundle.message("interface.is.implemented.by.header")
                   : DaemonBundle.message("class.is.subclassed.by.header");
    @NonNls String pattern = "&nbsp;&nbsp;&nbsp;&nbsp;<a href=\"#javaClass/{0}\">{0}</a>";
    return composeText(subclasses, start, pattern, IdeActions.ACTION_GOTO_IMPLEMENTATION);
  }

  public static void navigateToSubclassedClass(MouseEvent e, final PsiClass aClass) {
    if (DumbService.isDumb(aClass.getProject())) {
      DumbService.getInstance(aClass.getProject()).showDumbModeNotification("Navigation to overriding methods is not possible during index update");
      return;
    }

    final PsiElementProcessor.CollectElementsWithLimit<PsiClass> collectProcessor = new PsiElementProcessor.CollectElementsWithLimit<PsiClass>(2, new THashSet<PsiClass>());
    final PsiElementProcessor.CollectElementsWithLimit<PsiFunctionalExpression> collectExprProcessor = new PsiElementProcessor.CollectElementsWithLimit<PsiFunctionalExpression>(2, new THashSet<PsiFunctionalExpression>());
    if (!ProgressManager.getInstance().runProcessWithProgressSynchronously(new Runnable() {
      @Override
      public void run() {
        ClassInheritorsSearch.search(aClass, true).forEach(new PsiElementProcessorAdapter<PsiClass>(collectProcessor));
        if (collectProcessor.getCollection().isEmpty()) {
          FunctionalExpressionSearch.search(aClass).forEach(new PsiElementProcessorAdapter<PsiFunctionalExpression>(collectExprProcessor));
        }
      }
    }, SEARCHING_FOR_OVERRIDDEN_METHODS, true, aClass.getProject(), (JComponent)e.getComponent())) {
      return;
    }

    NavigatablePsiElement[] inheritors = ArrayUtil.mergeArrays(collectProcessor.toArray(PsiClass.EMPTY_ARRAY),
                                                               collectExprProcessor.toArray(PsiFunctionalExpression.EMPTY_ARRAY));
    if (inheritors.length == 0) return;
    final PsiClassOrFunctionalExpressionListCellRenderer renderer = new PsiClassOrFunctionalExpressionListCellRenderer();
    final SubclassUpdater subclassUpdater = new SubclassUpdater(aClass, renderer);
    Arrays.sort(inheritors, renderer.getComparator());
    PsiElementListNavigator.openTargets(e, inheritors, subclassUpdater.getCaption(inheritors.length), CodeInsightBundle.message("goto.implementation.findUsages.title", aClass.getName()), renderer, subclassUpdater);
  }

  private static class SubclassUpdater extends ListBackgroundUpdaterTask {
    private final PsiClass myClass;
    private final PsiClassOrFunctionalExpressionListCellRenderer myRenderer;

    public SubclassUpdater(PsiClass aClass, PsiClassOrFunctionalExpressionListCellRenderer renderer) {
      super(aClass.getProject(), SEARCHING_FOR_OVERRIDDEN_METHODS);
      myClass = aClass;
      myRenderer = renderer;
    }

    @Override
    public String getCaption(int size) {
      return myClass.isInterface()
             ? CodeInsightBundle.message("goto.implementation.chooserTitle", myClass.getName(), size)
             : DaemonBundle.message("navigation.title.subclass", myClass.getName(), size);
    }

    @Override
    public void run(@NotNull final ProgressIndicator indicator) {
      super.run(indicator);
      ClassInheritorsSearch.search(myClass, ApplicationManager.getApplication().runReadAction(new Computable<SearchScope>() {
        @Override
        public SearchScope compute() {
          return myClass.getUseScope();
        }
      }), true).forEach(new CommonProcessors.CollectProcessor<PsiClass>() {
        @Override
        public boolean process(final PsiClass o) {
          if (!updateComponent(o, myRenderer.getComparator())) {
            indicator.cancel();
          }
          indicator.checkCanceled();
          return super.process(o);
        }
      });

      FunctionalExpressionSearch.search(myClass).forEach(new CommonProcessors.CollectProcessor<PsiFunctionalExpression>() {
        @Override
        public boolean process(final PsiFunctionalExpression expr) {
          if (!updateComponent(expr, myRenderer.getComparator())) {
            indicator.cancel();
          }
          indicator.checkCanceled();
          return super.process(expr);
        }
      });
    }

  }

  private static class OverridingMethodsUpdater extends ListBackgroundUpdaterTask {
    private final PsiMethod myMethod;
    private final PsiElementListCellRenderer myRenderer;

    public OverridingMethodsUpdater(PsiMethod method, PsiElementListCellRenderer renderer) {
      super(method.getProject(), SEARCHING_FOR_OVERRIDING_METHODS);
      myMethod = method;
      myRenderer = renderer;
    }

    @Override
    public String getCaption(int size) {
      return myMethod.hasModifierProperty(PsiModifier.ABSTRACT) ?
             DaemonBundle.message("navigation.title.implementation.method", myMethod.getName(), size) :
             DaemonBundle.message("navigation.title.overrider.method", myMethod.getName(), size);
    }

    @Override
    public void run(@NotNull final ProgressIndicator indicator) {
      super.run(indicator);
      OverridingMethodsSearch.search(myMethod, true).forEach(
        new CommonProcessors.CollectProcessor<PsiMethod>() {
          @Override
          public boolean process(PsiMethod psiMethod) {
            if (!updateComponent(psiMethod, myRenderer.getComparator())) {
              indicator.cancel();
            }
            indicator.checkCanceled();
            return super.process(psiMethod);
          }
        });
      final PsiClass psiClass = ApplicationManager.getApplication().runReadAction(new Computable<PsiClass>() {
        @Override
        public PsiClass compute() {
          return myMethod.getContainingClass();
        }
      });
      FunctionalExpressionSearch.search(psiClass).forEach(new CommonProcessors.CollectProcessor<PsiFunctionalExpression>() {
        @Override
        public boolean process(final PsiFunctionalExpression expr) {
          if (!updateComponent(expr, myRenderer.getComparator())) {
            indicator.cancel();
          }
          indicator.checkCanceled();
          return super.process(expr);
        }
      });
    }
  }
}
