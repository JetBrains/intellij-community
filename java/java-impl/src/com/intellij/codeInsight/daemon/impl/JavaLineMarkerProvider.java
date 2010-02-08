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
package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeHighlighting.Pass;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzerSettings;
import com.intellij.codeInsight.daemon.LineMarkerInfo;
import com.intellij.codeInsight.daemon.LineMarkerProvider;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.colors.CodeInsightColors;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.openapi.editor.markup.SeparatorPlacement;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.*;
import com.intellij.psi.search.searches.AllOverridingMethodsSearch;
import com.intellij.psi.search.searches.ClassInheritorsSearch;
import com.intellij.psi.search.searches.SuperMethodsSearch;
import com.intellij.psi.util.MethodSignatureBackedByPsiMethod;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.NullableFunction;
import com.intellij.util.Processor;
import com.intellij.util.containers.HashSet;
import gnu.trove.THashSet;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Collection;
import java.util.List;
import java.util.Set;

public class JavaLineMarkerProvider implements LineMarkerProvider, DumbAware {
  private static final Icon OVERRIDING_METHOD_ICON = IconLoader.getIcon("/gutter/overridingMethod.png");
  private static final Icon IMPLEMENTING_METHOD_ICON = IconLoader.getIcon("/gutter/implementingMethod.png");

  private static final Icon OVERRIDEN_METHOD_MARKER_RENDERER = IconLoader.getIcon("/gutter/overridenMethod.png");
  private static final Icon IMPLEMENTED_METHOD_MARKER_RENDERER = IconLoader.getIcon("/gutter/implementedMethod.png");
  private static final Icon IMPLEMENTED_INTERFACE_MARKER_RENDERER = IMPLEMENTED_METHOD_MARKER_RENDERER;
  private static final Icon SUBCLASSED_CLASS_MARKER_RENDERER = OVERRIDEN_METHOD_MARKER_RENDERER;

  protected final DaemonCodeAnalyzerSettings myDaemonSettings;
  protected final EditorColorsManager myColorsManager;

  public JavaLineMarkerProvider(DaemonCodeAnalyzerSettings daemonSettings, EditorColorsManager colorsManager) {
    myDaemonSettings = daemonSettings;
    myColorsManager = colorsManager;
  }

  @Nullable
  public LineMarkerInfo getLineMarkerInfo(final PsiElement element) {
    if (element instanceof PsiIdentifier && element.getParent() instanceof PsiMethod) {
      PsiMethod method = (PsiMethod)element.getParent();
      MethodSignatureBackedByPsiMethod superSignature = null;
      try {
        superSignature = SuperMethodsSearch.search(method, null, true, false).findFirst();
      }
      catch (IndexNotReadyException e) {
        //some searchers (EJB) require indices. What shall we do?
      }
      if (superSignature != null) {
        boolean overrides =
          method.hasModifierProperty(PsiModifier.ABSTRACT) == superSignature.getMethod().hasModifierProperty(PsiModifier.ABSTRACT);

        final Icon icon = overrides ? OVERRIDING_METHOD_ICON : IMPLEMENTING_METHOD_ICON;
        final MarkerType type = MarkerType.OVERRIDING_METHOD;
        return new LineMarkerInfo<PsiElement>(element, element.getTextRange(), icon, Pass.UPDATE_ALL, type.getTooltip(), type.getNavigationHandler(), GutterIconRenderer.Alignment.LEFT);
      }
    }

    if (myDaemonSettings.SHOW_METHOD_SEPARATORS && element.getFirstChild() == null) {
      PsiElement element1 = element;
      boolean isMember = false;
      while (element1 != null && !(element1 instanceof PsiFile) && element1.getPrevSibling() == null) {
        element1 = element1.getParent();
        if (element1 instanceof PsiMember) {
          isMember = true;
          break;
        }
      }
      if (isMember && !(element1 instanceof PsiAnonymousClass || element1.getParent() instanceof PsiAnonymousClass)) {
        boolean drawSeparator = false;
        int category = getCategory(element1);
        for (PsiElement child = element1.getPrevSibling(); child != null; child = child.getPrevSibling()) {
          int category1 = getCategory(child);
          if (category1 == 0) continue;
          drawSeparator = category != 1 || category1 != 1;
          break;
        }

        if (drawSeparator) {
          LineMarkerInfo info = new LineMarkerInfo<PsiElement>(element, element.getTextRange(), null, Pass.UPDATE_ALL, NullableFunction.NULL, null, GutterIconRenderer.Alignment.RIGHT);
          EditorColorsScheme scheme = myColorsManager.getGlobalScheme();
          info.separatorColor = scheme.getColor(CodeInsightColors.METHOD_SEPARATORS_COLOR);
          info.separatorPlacement = SeparatorPlacement.TOP;
          return info;
        }
      }
    }

    return null;
  }

  protected static int getCategory(PsiElement element) {
    if (element instanceof PsiField) return 1;
    if (element instanceof PsiClass || element instanceof PsiClassInitializer) return 2;
    if (element instanceof PsiMethod) {
      if (((PsiMethod)element).hasModifierProperty(PsiModifier.ABSTRACT)) {
        return 1;
      }
      String text = element.getText();
      if (text.indexOf('\n') < 0 && text.indexOf('\r') < 0) {
        return 1;
      }
      else {
        return 2;
      }
    }
    return 0;
  }

  public void collectSlowLineMarkers(final List<PsiElement> elements, final Collection<LineMarkerInfo> result) {
    ApplicationManager.getApplication().assertReadAccessAllowed();

    if (elements.isEmpty() || DumbService.getInstance(elements.get(0).getProject()).isDumb()) {
      return;
    }

    Set<PsiMethod> methods = new HashSet<PsiMethod>();
    for (PsiElement element : elements) {
      ProgressManager.checkCanceled();
      if (element instanceof PsiMethod) {
        final PsiMethod method = (PsiMethod)element;
        if (PsiUtil.canBeOverriden(method)) {
          methods.add(method);
        }
      }
      else if (element instanceof PsiClass && !(element instanceof PsiTypeParameter)) {
        collectInheritingClasses((PsiClass)element, result);
      }
    }
    if (!methods.isEmpty()) {
      collectOverridingMethods(methods, result);
    }
  }

  private static void collectInheritingClasses(PsiClass aClass, Collection<LineMarkerInfo> result) {
    if (!aClass.hasModifierProperty(PsiModifier.FINAL)) {
      if ("java.lang.Object".equals(aClass.getQualifiedName())) return; // It's useless to have overriden markers for object.

      final PsiClass inheritor = ClassInheritorsSearch.search(aClass, false).findFirst();
      if (inheritor != null) {
        final Icon icon = aClass.isInterface() ? IMPLEMENTED_INTERFACE_MARKER_RENDERER : SUBCLASSED_CLASS_MARKER_RENDERER;
        final MarkerType type = MarkerType.SUBCLASSED_CLASS;
        PsiElement range = aClass.getNameIdentifier();
        if (range == null) range = aClass;
        LineMarkerInfo info = new LineMarkerInfo<PsiElement>(range, range.getTextRange(), icon, Pass.UPDATE_OVERRIDEN_MARKERS, type.getTooltip(), type.getNavigationHandler(),
                                                           GutterIconRenderer.Alignment.RIGHT);
        result.add(info);
      }
    }
  }

  private static void collectOverridingMethods(final Set<PsiMethod> methods, Collection<LineMarkerInfo> result) {
    final Set<PsiMethod> overridden = new HashSet<PsiMethod>();
    Set<PsiClass> classes = new THashSet<PsiClass>();
    for (PsiMethod method : methods) {
      ProgressManager.checkCanceled();
      final PsiClass parentClass = method.getContainingClass();
      if (!"java.lang.Object".equals(parentClass.getQualifiedName())) {
        classes.add(parentClass);
      }
    }

    for (final PsiClass aClass : classes) {
      AllOverridingMethodsSearch.search(aClass).forEach(new Processor<Pair<PsiMethod, PsiMethod>>() {
        public boolean process(final Pair<PsiMethod, PsiMethod> pair) {
          final PsiMethod superMethod = pair.getFirst();
          if (superMethod.isPhysical() && pair.getSecond().isPhysical() //groovy, scala
              && methods.remove(superMethod)) {
            overridden.add(superMethod);
          }
          return !methods.isEmpty();
        }
      });
    }

    for (PsiMethod method : overridden) {
      boolean overrides = !method.hasModifierProperty(PsiModifier.ABSTRACT);

      final Icon icon = overrides ? OVERRIDEN_METHOD_MARKER_RENDERER : IMPLEMENTED_METHOD_MARKER_RENDERER;
      PsiElement range = method.getNameIdentifier();
      if (range == null) range = method;
      final MarkerType type = MarkerType.OVERRIDEN_METHOD;
      LineMarkerInfo info = new LineMarkerInfo<PsiElement>(range, range.getTextRange(), icon, Pass.UPDATE_OVERRIDEN_MARKERS, type.getTooltip(), type.getNavigationHandler(),
                                                          GutterIconRenderer.Alignment.RIGHT);
      result.add(info);
    }
  }
}
