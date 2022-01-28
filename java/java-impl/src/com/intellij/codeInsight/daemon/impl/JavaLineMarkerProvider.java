// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.daemon.impl;

import com.intellij.CommonBundle;
import com.intellij.codeInsight.daemon.*;
import com.intellij.concurrency.JobLauncher;
import com.intellij.icons.AllIcons;
import com.intellij.java.JavaBundle;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.ex.EditorSettingsExternalizable;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressIndicatorProvider;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.impl.FindSuperElementsHelper;
import com.intellij.psi.search.searches.AllOverridingMethodsSearch;
import com.intellij.psi.search.searches.DirectClassInheritorsSearch;
import com.intellij.psi.search.searches.FunctionalExpressionSearch;
import com.intellij.psi.search.searches.SuperMethodsSearch;
import com.intellij.psi.util.MethodSignatureBackedByPsiMethod;
import com.intellij.psi.util.PsiExpressionTrimRenderer;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.*;

public class JavaLineMarkerProvider extends LineMarkerProviderDescriptor {
  public static final Option LAMBDA_OPTION = new Option("java.lambda", CommonBundle.message("title.lambda"), AllIcons.Gutter.ImplementingFunctionalInterface) {
    @Override
    public boolean isEnabledByDefault() {
      return false;
    }
  };

  private final Option myOverriddenOption = new Option("java.overridden", JavaBundle.message("gutter.overridden.method"), AllIcons.Gutter.OverridenMethod);
  private final Option myImplementedOption = new Option("java.implemented", JavaBundle.message("gutter.implemented.method"), AllIcons.Gutter.ImplementedMethod);
  private final Option myOverridingOption = new Option("java.overriding", JavaBundle.message("gutter.overriding.method"), AllIcons.Gutter.OverridingMethod);
  private final Option myImplementingOption = new Option("java.implementing", JavaBundle.message("gutter.implementing.method"), AllIcons.Gutter.ImplementingMethod);
  private final Option mySiblingsOption = new Option("java.sibling.inherited", JavaBundle.message("gutter.sibling.inherited.method"), AllIcons.Gutter.SiblingInheritedMethod);
  private final Option myServiceOption = new Option("java.service", JavaBundle.message("gutter.service"), AllIcons.Gutter.Java9Service);

  public JavaLineMarkerProvider() { }

  /**
   * @deprecated use {@link #JavaLineMarkerProvider()}
   */
  @Deprecated
  @ApiStatus.ScheduledForRemoval(inVersion = "2021.3")
  public JavaLineMarkerProvider(DaemonCodeAnalyzerSettings daemonSettings, EditorColorsManager colorsManager) { }

  @Override
  public LineMarkerInfo<?> getLineMarkerInfo(final @NotNull PsiElement element) {
    PsiElement parent = element.getParent();
    if (element instanceof PsiIdentifier && parent instanceof PsiMethod) {
      if (!myOverridingOption.isEnabled() && !myImplementingOption.isEnabled()) return null;
      PsiMethod method = (PsiMethod)parent;
      MethodSignatureBackedByPsiMethod superSignature = SuperMethodsSearch.search(method, null, true, false).findFirst();
      if (superSignature != null) {
        boolean overrides =
          method.hasModifierProperty(PsiModifier.ABSTRACT) == superSignature.getMethod().hasModifierProperty(PsiModifier.ABSTRACT);

        final Icon icon;
        if (overrides) {
          if (!myOverridingOption.isEnabled()) return null;
          icon = AllIcons.Gutter.OverridingMethod;
        }
        else {
          if (!myImplementingOption.isEnabled()) return null;
          icon = AllIcons.Gutter.ImplementingMethod;
        }
        return createSuperMethodLineMarkerInfo(element, icon);
      }
    }
    // in case of ()->{}, anchor to "->"
    // in case of (xxx)->{}, anchor to "->"
    // in case of Type::method, anchor to "method"
    if (LAMBDA_OPTION.isEnabled() &&
        parent instanceof PsiFunctionalExpression &&
        (element instanceof PsiJavaToken && ((PsiJavaToken)element).getTokenType() == JavaTokenType.ARROW && parent instanceof PsiLambdaExpression ||
         element instanceof PsiIdentifier && parent instanceof PsiMethodReferenceExpression && ((PsiMethodReferenceExpression)parent).getReferenceNameElement() == element)
      ) {
      final PsiMethod interfaceMethod = LambdaUtil.getFunctionalInterfaceMethod(parent);
      if (interfaceMethod != null) {
        return createSuperMethodLineMarkerInfo(element, AllIcons.Gutter.ImplementingFunctionalInterface);
      }
    }

    if (DaemonCodeAnalyzerSettings.getInstance().SHOW_METHOD_SEPARATORS && element.getFirstChild() == null) {
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
          return LineMarkersPass.createMethodSeparatorLineMarker(element, EditorColorsManager.getInstance());
        }
      }
    }

    return null;
  }

  @NotNull
  private static LineMarkerInfo<PsiElement> createSuperMethodLineMarkerInfo(@NotNull PsiElement name, @NotNull Icon icon) {
    ArrowUpLineMarkerInfo info = new ArrowUpLineMarkerInfo(name, icon, MarkerType.OVERRIDING_METHOD);
    return NavigateAction.setNavigateAction(info, JavaBundle.message("action.go.to.super.method.text"), IdeActions.ACTION_GOTO_SUPER);
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
  public void collectSlowLineMarkers(final @NotNull List<? extends PsiElement> elements, final @NotNull Collection<? super LineMarkerInfo<?>> result) {
    ApplicationManager.getApplication().assertReadAccessAllowed();

    List<Computable<List<LineMarkerInfo<PsiElement>>>> tasks = new ArrayList<>();

    MultiMap<PsiClass, PsiMethod> canBeOverridden = MultiMap.createSet();
    MultiMap<PsiClass, PsiMethod> canHaveSiblings = MultiMap.create();
    //noinspection ForLoopReplaceableByForEach
    for (int i = 0; i < elements.size(); i++) {
      PsiElement element = elements.get(i);
      ProgressManager.checkCanceled();
      if (!(element instanceof PsiIdentifier)) continue;
      PsiElement parent = element.getParent();
      if (parent instanceof PsiMethod) {
        final PsiMethod method = (PsiMethod)parent;
        PsiClass containingClass = method.getContainingClass();
        if (containingClass != null && PsiUtil.canBeOverridden(method)) {
          canBeOverridden.putValue(containingClass, method);
        }
        if (mySiblingsOption.isEnabled() && FindSuperElementsHelper.canHaveSiblingSuper(method, containingClass)) {
          canHaveSiblings.putValue(containingClass, method);
        }
        if (JavaServiceUtil.isServiceProviderMethod(method)) {
          tasks.add(() -> JavaServiceUtil.collectServiceProviderMethod(method));
        }
      }
      else if (parent instanceof PsiClass && !(parent instanceof PsiTypeParameter)) {
        tasks.add(() -> collectInheritingClasses((PsiClass)parent));
        tasks.add(() -> JavaServiceUtil.collectServiceImplementationClass((PsiClass)parent));
      }
      else if (parent instanceof PsiReferenceExpression && parent.getParent() instanceof PsiMethodCallExpression) {
        PsiMethodCallExpression grandParent = (PsiMethodCallExpression)parent.getParent();
        if (JavaServiceUtil.SERVICE_LOADER_LOAD.test(grandParent)) {
          tasks.add(() -> JavaServiceUtil.collectServiceLoaderLoadCall((PsiIdentifier)element, grandParent));
        }
      }
    }

    for (Map.Entry<PsiClass, Collection<PsiMethod>> entry : canBeOverridden.entrySet()) {
      PsiClass psiClass = entry.getKey();
      Set<PsiMethod> methods = (Set<PsiMethod>)entry.getValue();
      tasks.add(() -> collectOverridingMethods(methods, psiClass));
    }
    for (PsiClass psiClass : canHaveSiblings.keySet()) {
      Collection<PsiMethod> methods = canHaveSiblings.get(psiClass);
      tasks.add(() -> collectSiblingInheritedMethods(methods));
    }

    Object lock = new Object();
    ProgressIndicator indicator = ProgressIndicatorProvider.getGlobalProgressIndicator();
    List<LineMarkerInfo<PsiElement>> found = new ArrayList<>();
    JobLauncher.getInstance().invokeConcurrentlyUnderProgress(tasks, indicator, computable -> {
      List<LineMarkerInfo<PsiElement>> infos = computable.compute();
      synchronized (lock) {
        found.addAll(infos);
      }
      return true;
    });
    synchronized (lock) {
      result.addAll(found);
    }
  }

  @NotNull
  private static List<LineMarkerInfo<PsiElement>> collectSiblingInheritedMethods(@NotNull final Collection<? extends PsiMethod> methods) {
    Map<PsiMethod, FindSuperElementsHelper.SiblingInfo> map = FindSuperElementsHelper.getSiblingInheritanceInfos(methods);
    return ContainerUtil.map(map.keySet(), method -> {
      PsiElement range = getMethodRange(method);
      ArrowUpLineMarkerInfo upInfo =
        new ArrowUpLineMarkerInfo(range, AllIcons.Gutter.SiblingInheritedMethod, MarkerType.SIBLING_OVERRIDING_METHOD);
      return NavigateAction.setNavigateAction(upInfo, JavaBundle.message("action.go.to.super.method.text"), IdeActions.ACTION_GOTO_SUPER);
    });
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

  @NotNull
  protected List<LineMarkerInfo<PsiElement>> collectInheritingClasses(@NotNull PsiClass aClass) {
    if (!shouldSearchImplementedMethods() && !shouldSearchOverriddenMethods()) {
      return Collections.emptyList();
    }
    if (aClass.hasModifierProperty(PsiModifier.FINAL)) {
      return Collections.emptyList();
    }
    if (CommonClassNames.JAVA_LANG_OBJECT.equals(aClass.getQualifiedName())) {
      return Collections.emptyList(); // It's useless to have overridden markers for object.
    }

    PsiClass subClass = DirectClassInheritorsSearch.search(aClass).findFirst();
    if (subClass != null || FunctionalExpressionSearch.search(aClass).findFirst() != null) {
      final Icon icon;
      if (aClass.isInterface()) {
        if (!shouldSearchImplementedMethods()) return Collections.emptyList();
        icon = AllIcons.Gutter.ImplementedMethod;
      }
      else {
        if (!shouldSearchOverriddenMethods()) return Collections.emptyList();
        icon = AllIcons.Gutter.OverridenMethod;
      }
      PsiElement range = aClass.getNameIdentifier();
      if (range == null) {
        range = aClass;
      }
      MarkerType type = MarkerType.SUBCLASSED_CLASS;
      LineMarkerInfo<PsiElement> info = new LineMarkerInfo<>(range, range.getTextRange(),
                                                 icon, type.getTooltip(),
                                                 type.getNavigationHandler(),
                                                 GutterIconRenderer.Alignment.RIGHT);
      NavigateAction.setNavigateAction(info, aClass.isInterface() ? JavaBundle.message("action.go.to.implementation.text")
                                                                  : JavaBundle.message("action.go.to.subclass.text"), IdeActions.ACTION_GOTO_IMPLEMENTATION);
      return Collections.singletonList(info);
    }
    return Collections.emptyList();
  }

  private boolean shouldSearchOverriddenMethods() {
    return EditorSettingsExternalizable.getInstance().areGutterIconsShown() && myOverriddenOption.isEnabled();
  }

  private boolean shouldSearchImplementedMethods() {
    return EditorSettingsExternalizable.getInstance().areGutterIconsShown() && myImplementedOption.isEnabled();
  }

  @NotNull
  private List<LineMarkerInfo<PsiElement>> collectOverridingMethods(@NotNull final Set<PsiMethod> methodSet, @NotNull PsiClass containingClass) {
    if (!shouldSearchOverriddenMethods() && !shouldSearchImplementedMethods()) return Collections.emptyList();
    final Set<PsiMethod> overridden = new HashSet<>();

    AllOverridingMethodsSearch.search(containingClass).forEach(pair -> {
      ProgressManager.checkCanceled();

      final PsiMethod superMethod = pair.getFirst();
      if (methodSet.remove(superMethod)) {
        overridden.add(superMethod);
      }
      return !methodSet.isEmpty();
    });

    if (!methodSet.isEmpty()) {
      final PsiMethod interfaceMethod = LambdaUtil.getFunctionalInterfaceMethod(containingClass);
      if (interfaceMethod != null &&
          methodSet.contains(interfaceMethod) &&
          FunctionalExpressionSearch.search(containingClass).findFirst() != null) {
        overridden.add(interfaceMethod);
      }
    }

    List<LineMarkerInfo<PsiElement>> result = new ArrayList<>(overridden.size());
    for (PsiMethod method : overridden) {
      ProgressManager.checkCanceled();
      boolean overrides = !method.hasModifierProperty(PsiModifier.ABSTRACT);
      if (overrides && !shouldSearchOverriddenMethods()) continue;
      if (!overrides && !shouldSearchImplementedMethods()) continue;
      PsiElement range = getMethodRange(method);
      final MarkerType type = MarkerType.OVERRIDDEN_METHOD;
      final Icon icon = overrides ? AllIcons.Gutter.OverridenMethod : AllIcons.Gutter.ImplementedMethod;
      LineMarkerInfo<PsiElement> info = new LineMarkerInfo<>(range, range.getTextRange(),
                                                             icon, type.getTooltip(),
                                                             type.getNavigationHandler(),
                                                             GutterIconRenderer.Alignment.RIGHT);
      NavigateAction.setNavigateAction(info, overrides ? JavaBundle.message("action.go.to.overriding.methods.text")
                                                       : JavaBundle.message("action.go.to.implementation.text"), IdeActions.ACTION_GOTO_IMPLEMENTATION);
      result.add(info);
    }
    return result;
  }

  @Override
  public String getName() {
    return JavaBundle.message("java.line.markers");
  }

  @Override
  public Option @NotNull [] getOptions() {
    return new Option[]{LAMBDA_OPTION, myOverriddenOption, myImplementedOption, myOverridingOption, myImplementingOption, mySiblingsOption, myServiceOption};
  }

  private static final class ArrowUpLineMarkerInfo extends MergeableLineMarkerInfo<PsiElement> {
    private ArrowUpLineMarkerInfo(@NotNull PsiElement element, @NotNull Icon icon, @NotNull MarkerType markerType) {
      super(element, element.getTextRange(), icon, markerType.getTooltip(),
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
    public Icon getCommonIcon(@NotNull List<? extends MergeableLineMarkerInfo<?>> infos) {
      return myIcon;
    }

    @NotNull
    @Override
    public Function<? super PsiElement, String> getCommonTooltip(@NotNull List<? extends MergeableLineMarkerInfo<?>> infos) {
      return (Function<PsiElement, String>)element -> "Multiple method overrides";
    }

    @NotNull
    @Override
    public String getElementPresentation(@NotNull PsiElement element) {
      final PsiElement parent = element.getParent();
      return parent instanceof PsiFunctionalExpression
             ? PsiExpressionTrimRenderer.render((PsiExpression)parent)
             : super.getElementPresentation(element);
    }
  }
}