/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
import com.intellij.ide.util.MethodCellRenderer;
import com.intellij.ide.util.PsiClassListCellRenderer;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.psi.*;
import com.intellij.psi.search.PsiElementProcessor;
import com.intellij.psi.search.PsiElementProcessorAdapter;
import com.intellij.psi.search.searches.ClassInheritorsSearch;
import com.intellij.psi.search.searches.OverridingMethodsSearch;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.CommonProcessors;
import com.intellij.util.Function;
import com.intellij.util.NullableFunction;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;
import java.awt.event.MouseEvent;
import java.util.Arrays;
import java.util.Comparator;

public enum MarkerType {
  OVERRIDING_METHOD(new NullableFunction<PsiElement, String>() {
    public String fun(PsiElement element) {
      PsiElement parent = element.getParent();
      if (!(parent instanceof PsiMethod)) return null;
      PsiMethod method = (PsiMethod)parent;

      PsiMethod[] superMethods = method.findSuperMethods(false);
      if (superMethods.length == 0) return null;

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
      return GutterIconTooltipHelper.composeText(superMethods, "", DaemonBundle.message(key));
    }
  }, new LineMarkerNavigator(){
    public void browse(MouseEvent e, PsiElement element) {
      PsiElement parent = element.getParent();
      if (!(parent instanceof PsiMethod)) return;
      PsiMethod method = (PsiMethod)parent;
      PsiMethod[] superMethods = method.findSuperMethods(false);
      if (superMethods.length == 0) return;
      boolean showMethodNames = !PsiUtil.allMethodsHaveSameSignature(superMethods);
      PsiElementListNavigator.openTargets(e, superMethods,
                  DaemonBundle.message("navigation.title.super.method", method.getName()),
                  new MethodCellRenderer(showMethodNames));

    }
  }),
  OVERRIDEN_METHOD(new NullableFunction<PsiElement, String>() {
    public String fun(PsiElement element) {
      PsiElement parent = element.getParent();
      if (!(parent instanceof PsiMethod)) return null;
      PsiMethod method = (PsiMethod)parent;

      PsiElementProcessor.CollectElementsWithLimit<PsiMethod> processor = new PsiElementProcessor.CollectElementsWithLimit<PsiMethod>(5);
      OverridingMethodsSearch.search(method, method.getUseScope(), true).forEach(new PsiElementProcessorAdapter<PsiMethod>(processor));

      boolean isAbstract = method.hasModifierProperty(PsiModifier.ABSTRACT);

      if (processor.isOverflow()){
        return isAbstract ? DaemonBundle.message("method.is.implemented.too.many") : DaemonBundle.message("method.is.overridden.too.many");
      }

      PsiMethod[] overridings = processor.toArray(new PsiMethod[processor.getCollection().size()]);
      if (overridings.length == 0) return null;

      Comparator<PsiMethod> comparator = new MethodCellRenderer(false).getComparator();
      Arrays.sort(overridings, comparator);

      String start = isAbstract ? DaemonBundle.message("method.is.implemented.header") : DaemonBundle.message("method.is.overriden.header");
      @NonNls String pattern = "&nbsp;&nbsp;&nbsp;&nbsp;{1}";
      return GutterIconTooltipHelper.composeText(overridings, start, pattern);
    }
  }, new LineMarkerNavigator(){
    public void browse(MouseEvent e, PsiElement element) {
      PsiElement parent = element.getParent();
      if (!(parent instanceof PsiMethod)) return;

      final PsiMethod method = (PsiMethod)parent;
      final CommonProcessors.CollectProcessor<PsiMethod> collectProcessor = new CommonProcessors.CollectProcessor<PsiMethod>();
      if (!ProgressManager.getInstance().runProcessWithProgressSynchronously(new Runnable() {
        public void run() {
          OverridingMethodsSearch.search(method, method.getUseScope(), true).forEach(collectProcessor);
        }
      }, "Searching for overridding methods", true, method.getProject(), (JComponent)e.getComponent())) {
        return;
      }

      PsiMethod[] overridings = collectProcessor.toArray(PsiMethod.EMPTY_ARRAY);
      if (overridings.length == 0) return;
      String title = method.hasModifierProperty(PsiModifier.ABSTRACT) ?
                     DaemonBundle .message("navigation.title.implementation.method", method.getName(), overridings.length) :
                     DaemonBundle.message("navigation.title.overrider.method", method.getName(), overridings.length);
      boolean showMethodNames = !PsiUtil.allMethodsHaveSameSignature(overridings);
      MethodCellRenderer renderer = new MethodCellRenderer(showMethodNames);
      Arrays.sort(overridings, renderer.getComparator());
      PsiElementListNavigator.openTargets(e, overridings, title, renderer);

    }
  }),
  SUBCLASSED_CLASS(new NullableFunction<PsiElement, String>() {
    public String fun(PsiElement element) {
      PsiElement parent = element.getParent();
      if (!(parent instanceof PsiClass)) return null;
      PsiClass aClass = (PsiClass)parent;
      PsiElementProcessor.CollectElementsWithLimit<PsiClass> processor = new PsiElementProcessor.CollectElementsWithLimit<PsiClass>(5);
      ClassInheritorsSearch.search(aClass, aClass.getUseScope(), true).forEach(new PsiElementProcessorAdapter<PsiClass>(processor));

      if (processor.isOverflow()) {
        return aClass.isInterface()
               ? DaemonBundle.message("interface.is.implemented.too.many")
               : DaemonBundle.message("class.is.subclassed.too.many");
      }

      PsiClass[] subclasses = processor.toArray(new PsiClass[processor.getCollection().size()]);
      if (subclasses.length == 0) return null;

      Comparator<PsiClass> comparator = new PsiClassListCellRenderer().getComparator();
      Arrays.sort(subclasses, comparator);

      String start = aClass.isInterface()
                     ? DaemonBundle.message("interface.is.implemented.by.header")
                     : DaemonBundle.message("class.is.subclassed.by.header");
      @NonNls String pattern = "&nbsp;&nbsp;&nbsp;&nbsp;{0}";
      return GutterIconTooltipHelper.composeText(subclasses, start, pattern);
    }
  }, new LineMarkerNavigator(){
    public void browse(MouseEvent e, PsiElement element) {
      PsiElement parent = element.getParent();
      if (!(parent instanceof PsiClass)) return;

      final PsiClass aClass = (PsiClass)parent;
      final CommonProcessors.CollectProcessor<PsiClass> collectProcessor = new CommonProcessors.CollectProcessor<PsiClass>();
      if (!ProgressManager.getInstance().runProcessWithProgressSynchronously(new Runnable() {
        public void run() {
          ClassInheritorsSearch.search(aClass, aClass.getUseScope(), true).forEach(collectProcessor);
        }
      }, "Searching for overridden methods", true, aClass.getProject(), (JComponent)e.getComponent())) {
        return;
      }

      PsiClass[] inheritors = collectProcessor.toArray(PsiClass.EMPTY_ARRAY);
      if (inheritors.length == 0) return;
      String title = aClass.isInterface()
                     ? CodeInsightBundle.message("goto.implementation.chooserTitle", aClass.getName(), inheritors.length)
                     : DaemonBundle.message("navigation.title.subclass", aClass.getName(), inheritors.length);
      PsiClassListCellRenderer renderer = new PsiClassListCellRenderer();
      Arrays.sort(inheritors, renderer.getComparator());
      PsiElementListNavigator.openTargets(e, inheritors, title, renderer);
    }
  });

  private final GutterIconNavigationHandler handler;
  private final Function<? super PsiElement, String> myTooltip;

  MarkerType(Function<? super PsiElement, String> tooltip, final LineMarkerNavigator navigator) {
    myTooltip = tooltip;
    handler = new GutterIconNavigationHandler() {
      public void navigate(MouseEvent e, PsiElement elt) {
        navigator.browse(e, elt);
      }
    };
  }

  public <T extends PsiElement> GutterIconNavigationHandler<T> getNavigationHandler() {
    return handler;
  }

  public <T extends PsiElement> Function<T, String> getTooltip() {
    return (Function<T, String>)myTooltip;
  }
}