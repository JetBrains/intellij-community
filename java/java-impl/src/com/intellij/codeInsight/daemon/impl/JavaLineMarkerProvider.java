/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
import com.intellij.codeInsight.daemon.*;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.colors.CodeInsightColors;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.openapi.editor.markup.SeparatorPlacement;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.impl.FindSuperElementsHelper;
import com.intellij.psi.search.searches.AllOverridingMethodsSearch;
import com.intellij.psi.search.searches.FunctionalExpressionSearch;
import com.intellij.psi.search.searches.SuperMethodsSearch;
import com.intellij.psi.util.MethodSignatureBackedByPsiMethod;
import com.intellij.psi.util.PsiExpressionTrimRenderer;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.Function;
import com.intellij.util.FunctionUtil;
import com.intellij.util.Processor;
import com.intellij.util.containers.HashSet;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class JavaLineMarkerProvider extends LineMarkerProviderDescriptor {
  private final DaemonCodeAnalyzerSettings myDaemonSettings;
  private final EditorColorsManager myColorsManager;
  private final Option myLambdaOption = new Option("java.lambda", "Lambda", AllIcons.Gutter.ImplementingFunctionalInterface);

  public JavaLineMarkerProvider(DaemonCodeAnalyzerSettings daemonSettings, EditorColorsManager colorsManager) {
    myDaemonSettings = daemonSettings;
    myColorsManager = colorsManager;
  }

  @Override
  @Nullable
  public LineMarkerInfo getLineMarkerInfo(@NotNull final PsiElement element) {
    PsiElement parent;
    if (element instanceof PsiIdentifier && (parent = element.getParent()) instanceof PsiMethod) {
      PsiMethod method = (PsiMethod)parent;
      MethodSignatureBackedByPsiMethod superSignature = SuperMethodsSearch.search(method, null, true, false).findFirst();
      if (superSignature != null) {
        boolean overrides =
          method.hasModifierProperty(PsiModifier.ABSTRACT) == superSignature.getMethod().hasModifierProperty(PsiModifier.ABSTRACT);

        final Icon icon = overrides ? AllIcons.Gutter.OverridingMethod : AllIcons.Gutter.ImplementingMethod;
        return createSuperMethodLineMarkerInfo(element, icon, Pass.UPDATE_ALL);
      }
    }

    final PsiMethod interfaceMethod = LambdaUtil.getFunctionalInterfaceMethod(element);
    final PsiElement firstChild = element.getFirstChild();
    if (interfaceMethod != null && firstChild != null && LineMarkerSettings.getSettings().isEnabled(myLambdaOption)) {
      return createSuperMethodLineMarkerInfo(firstChild, AllIcons.Gutter.ImplementingFunctionalInterface, Pass.UPDATE_ALL);
    }

    if (myDaemonSettings.SHOW_METHOD_SEPARATORS && firstChild == null) {
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
        PsiFile file = element1.getContainingFile();
        Document document = file == null ? null : PsiDocumentManager.getInstance(file.getProject()).getLastCommittedDocument(file);
        boolean drawSeparator = false;

        if (document != null) {
          CharSequence documentChars = document.getCharsSequence();
          int category = getCategory(element1, documentChars);
          for (PsiElement child = element1.getPrevSibling(); child != null; child = child.getPrevSibling()) {
            int category1 = getCategory(child, documentChars);
            if (category1 == 0) continue;
            drawSeparator = category != 1 || category1 != 1;
            break;
          }
        }

        if (drawSeparator) {
          LineMarkerInfo info = new LineMarkerInfo<PsiElement>(element, element.getTextRange(), null, Pass.UPDATE_ALL,
                                                               FunctionUtil.<Object, String>nullConstant(), null,
                                                               GutterIconRenderer.Alignment.RIGHT);
          EditorColorsScheme scheme = myColorsManager.getGlobalScheme();
          info.separatorColor = scheme.getColor(CodeInsightColors.METHOD_SEPARATORS_COLOR);
          info.separatorPlacement = SeparatorPlacement.TOP;
          return info;
        }
      }
    }

    return null;
  }

  @NotNull
  private static LineMarkerInfo createSuperMethodLineMarkerInfo(@NotNull PsiElement name, @NotNull Icon icon, int passId) {
    ArrowUpLineMarkerInfo info = new ArrowUpLineMarkerInfo(name, icon, MarkerType.OVERRIDING_METHOD, passId);
    return NavigateAction.setNavigateAction(info, "Go to super method", IdeActions.ACTION_GOTO_SUPER);
  }

  private static int getCategory(@NotNull PsiElement element, @NotNull CharSequence documentChars) {
    if (element instanceof PsiField || element instanceof PsiTypeParameter) return 1;
    if (element instanceof PsiClass || element instanceof PsiClassInitializer) return 2;
    if (element instanceof PsiMethod) {
      if (((PsiMethod)element).hasModifierProperty(PsiModifier.ABSTRACT)) {
        return 1;
      }
      TextRange textRange = element.getTextRange();
      int start = textRange.getStartOffset();
      int end = Math.min(documentChars.length(), textRange.getEndOffset());
      int crlf = StringUtil.getLineBreakCount(documentChars.subSequence(start, end));
      return crlf == 0 ? 1 : 2;
    }
    return 0;
  }

  @Override
  public void collectSlowLineMarkers(@NotNull final List<PsiElement> elements, @NotNull final Collection<LineMarkerInfo> result) {
    ApplicationManager.getApplication().assertReadAccessAllowed();
    Map<PsiClass, PsiClass> subClassCache = FindSuperElementsHelper.createSubClassCache();

    Collection<PsiMethod> methods = new THashSet<PsiMethod>();
    //noinspection ForLoopReplaceableByForEach
    for (int i = 0; i < elements.size(); i++) {
      PsiElement element = elements.get(i);
      ProgressManager.checkCanceled();
      if (!(element instanceof PsiIdentifier)) continue;
      PsiElement parent = element.getParent();
      if (parent instanceof PsiMethod) {
        final PsiMethod method = (PsiMethod)parent;
        if (PsiUtil.canBeOverriden(method)) {
          methods.add(method);
        }
      }
      else if (parent instanceof PsiClass && !(parent instanceof PsiTypeParameter)) {
        collectInheritingClasses((PsiClass)parent, result, subClassCache);
      }
    }
    if (!methods.isEmpty()) {
      collectOverridingMethods(methods, result);
      collectSiblingInheritedMethods(methods, result, subClassCache);
    }
  }

  private static void collectSiblingInheritedMethods(@NotNull final Collection<PsiMethod> methods,
                                                     @NotNull Collection<LineMarkerInfo> result,
                                                     @NotNull Map<PsiClass, PsiClass> subClassCache) {
    for (PsiMethod method : methods) {
      ProgressManager.checkCanceled();
      PsiClass aClass = method.getContainingClass();
      if (aClass == null || aClass.hasModifierProperty(PsiModifier.FINAL) || aClass.isInterface()) continue;

      boolean canHaveSiblingSuper = !method.hasModifierProperty(PsiModifier.ABSTRACT) && !method.hasModifierProperty(PsiModifier.STATIC) && method.hasModifierProperty(PsiModifier.PUBLIC)&& !method.hasModifierProperty(PsiModifier.FINAL)&& !method.hasModifierProperty(PsiModifier.NATIVE);
      if (!canHaveSiblingSuper) continue;

      PsiMethod siblingInheritedViaSubClass = Pair.getFirst(FindSuperElementsHelper.getSiblingInheritedViaSubClass(method, subClassCache));
      if (siblingInheritedViaSubClass == null) {
        continue;
      }
      PsiElement range = getMethodRange(method);
      ArrowUpLineMarkerInfo upInfo = new ArrowUpLineMarkerInfo(range, AllIcons.Gutter.ImplementingMethod, MarkerType.SIBLING_OVERRIDING_METHOD,
                                                              Pass.UPDATE_OVERRIDEN_MARKERS);
      LineMarkerInfo info = NavigateAction.setNavigateAction(upInfo, "Go to super method", IdeActions.ACTION_GOTO_SUPER);
      result.add(info);
    }
  }

  @NotNull
  private static PsiElement getMethodRange(@NotNull PsiMethod method) {
    PsiElement range;
    if (method.isPhysical()) {
      range = method.getNameIdentifier();
    }
    else {
      final PsiElement navigationElement = method.getNavigationElement();
      range = navigationElement instanceof PsiNameIdentifierOwner
              ? ((PsiNameIdentifierOwner)navigationElement).getNameIdentifier()
              : navigationElement;
    }
    if (range == null) {
      range = method;
    }
    return range;
  }

  public static void collectInheritingClasses(@NotNull PsiClass aClass,
                                              @NotNull Collection<LineMarkerInfo> result,
                                              @NotNull Map<PsiClass, PsiClass> subClassCache) {
    if (aClass.hasModifierProperty(PsiModifier.FINAL)) {
      return;
    }
    if (CommonClassNames.JAVA_LANG_OBJECT.equals(aClass.getQualifiedName())) return; // It's useless to have overridden markers for object.

    PsiClass subClass = subClassCache.get(aClass);
    if (subClass != null || FunctionalExpressionSearch.search(aClass).findFirst() != null) {
      final Icon icon = aClass.isInterface() ? AllIcons.Gutter.ImplementedMethod : AllIcons.Gutter.OverridenMethod;
      PsiElement range = aClass.getNameIdentifier();
      if (range == null) {
        range = aClass;
      }
      MarkerType type = MarkerType.SUBCLASSED_CLASS;
      LineMarkerInfo info = new LineMarkerInfo<PsiElement>(range, range.getTextRange(),
                                                           icon, Pass.UPDATE_OVERRIDEN_MARKERS, type.getTooltip(),
                                                           type.getNavigationHandler(),
                                                           GutterIconRenderer.Alignment.RIGHT);
      NavigateAction.setNavigateAction(info, aClass.isInterface() ? "Go to implementation(s)" : "Go to subclass(es)", IdeActions.ACTION_GOTO_IMPLEMENTATION);
      result.add(info);
    }
  }

  private static void collectOverridingMethods(@NotNull final Collection<PsiMethod> methods, @NotNull Collection<LineMarkerInfo> result) {
    final Set<PsiMethod> overridden = new HashSet<PsiMethod>();
    Set<PsiClass> classes = new THashSet<PsiClass>();
    for (PsiMethod method : methods) {
      ProgressManager.checkCanceled();
      final PsiClass parentClass = method.getContainingClass();
      if (!CommonClassNames.JAVA_LANG_OBJECT.equals(parentClass.getQualifiedName())) {
        classes.add(parentClass);
      }
    }

    for (final PsiClass aClass : classes) {
      AllOverridingMethodsSearch.search(aClass).forEach(new Processor<Pair<PsiMethod, PsiMethod>>() {
        @Override
        public boolean process(final Pair<PsiMethod, PsiMethod> pair) {
          ProgressManager.checkCanceled();

          final PsiMethod superMethod = pair.getFirst();
          if (methods.remove(superMethod)) {
            overridden.add(superMethod);
          }
          return !methods.isEmpty();
        }
      });
    }

    if (!methods.isEmpty()) {
      for (PsiClass aClass : classes) {
        final PsiMethod interfaceMethod = LambdaUtil.getFunctionalInterfaceMethod(aClass);
        if (interfaceMethod != null) {
          if (FunctionalExpressionSearch.search(aClass).findFirst() != null) {
            overridden.add(interfaceMethod);
          }
        }
      }
    }

    for (PsiMethod method : overridden) {
      ProgressManager.checkCanceled();
      boolean overrides = !method.hasModifierProperty(PsiModifier.ABSTRACT);

      PsiElement range = getMethodRange(method);
      final MarkerType type = MarkerType.OVERRIDDEN_METHOD;
      final Icon icon = overrides ? AllIcons.Gutter.OverridenMethod : AllIcons.Gutter.ImplementedMethod;
      LineMarkerInfo<PsiElement> info = new LineMarkerInfo<PsiElement>(range, range.getTextRange(),
                                                           icon, Pass.UPDATE_OVERRIDEN_MARKERS, type.getTooltip(),
                                                           type.getNavigationHandler(),
                                                           GutterIconRenderer.Alignment.RIGHT);
      NavigateAction.setNavigateAction(info, overrides ? "Go to overriding methods" : "Go to implementation(s)", IdeActions.ACTION_GOTO_IMPLEMENTATION);
      result.add(info);
    }
  }

  @Override
  public String getName() {
    return "Java line markers";
  }

  @Override
  public Option[] getOptions() {
    return new Option[] {myLambdaOption};
  }

  private static class ArrowUpLineMarkerInfo extends MergeableLineMarkerInfo<PsiElement> {
    private ArrowUpLineMarkerInfo(@NotNull PsiElement element, @NotNull Icon icon, @NotNull MarkerType markerType, int passId) {
      super(element, element.getTextRange(), icon, passId, markerType.getTooltip(),
            markerType.getNavigationHandler(), GutterIconRenderer.Alignment.LEFT);
    }

    @Override
    public boolean canMergeWith(@NotNull MergeableLineMarkerInfo<?> info) {
      if (!(info instanceof ArrowUpLineMarkerInfo)) return false;
      PsiElement otherElement = info.getElement();
      PsiElement myElement = getElement();
      return otherElement != null && myElement != null;
    }


    @Override
    public Icon getCommonIcon(@NotNull List<MergeableLineMarkerInfo> infos) {
      return myIcon;
    }

    @NotNull
    @Override
    public Function<? super PsiElement, String> getCommonTooltip(@NotNull List<MergeableLineMarkerInfo> infos) {
      return new Function<PsiElement, String>() {
        @Override
        public String fun(PsiElement element) {
          return "Multiple method overrides";
        }
      };
    }

    @Override
    public String getElementPresentation(PsiElement element) {
      final PsiElement parent = element.getParent();
      if (parent instanceof PsiFunctionalExpression) {
        return PsiExpressionTrimRenderer.render((PsiExpression)parent);
      }
      return super.getElementPresentation(element);
    }
  }
}
