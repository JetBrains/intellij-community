/*
 * @author max
 */
package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeInsight.daemon.GutterIconNavigationHandler;
import com.intellij.codeInsight.daemon.DaemonBundle;
import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.search.PsiElementProcessor;
import com.intellij.psi.search.PsiElementProcessorAdapter;
import com.intellij.psi.search.searches.OverridingMethodsSearch;
import com.intellij.psi.search.searches.ClassInheritorsSearch;
import com.intellij.util.Function;
import com.intellij.util.NullableFunction;
import com.intellij.ide.util.MethodCellRenderer;
import com.intellij.ide.util.PsiClassListCellRenderer;
import org.jetbrains.annotations.NonNls;

import java.util.Comparator;
import java.util.Arrays;
import java.awt.event.MouseEvent;

public enum MarkerType {
  OVERRIDING_METHOD(new NullableFunction<PsiElement, String>() {
    public String fun(PsiElement element) {
      if (!(element instanceof PsiMethod)) return null;
      PsiMethod method = (PsiMethod)element;

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
      if (!(element instanceof PsiMethod)) return;
      PsiMethod method = (PsiMethod)element;
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
      if (!(element instanceof PsiMethod)) return null;
      PsiMethod method = (PsiMethod)element;

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
      if (!(element instanceof PsiMethod)) return;
      PsiMethod method = (PsiMethod)element;
      PsiMethod[] overridings = OverridingMethodsSearch.search(method, method.getUseScope(), true).toArray(PsiMethod.EMPTY_ARRAY);
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
  METHOD_SEPARATOR(Function.NULL, new LineMarkerNavigator(){
    public void browse(MouseEvent e, PsiElement element) {

    }
  }),
  SUBCLASSED_CLASS(new NullableFunction<PsiElement, String>() {
    public String fun(PsiElement element) {
      if (!(element instanceof PsiClass)) return null;
      PsiClass aClass = (PsiClass)element;
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
      if (!(element instanceof PsiClass)) return;
      PsiClass aClass = (PsiClass)element;
      PsiClass[] inheritors = ClassInheritorsSearch.search(aClass, aClass.getUseScope(), true).toArray(PsiClass.EMPTY_ARRAY);
      if (inheritors.length == 0) return;
      String title = aClass.isInterface()
                     ? CodeInsightBundle.message("goto.implementation.chooser.title", aClass.getName(), inheritors.length)
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