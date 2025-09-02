// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl;

import com.intellij.CommonBundle;
import com.intellij.codeInsight.daemon.*;
import com.intellij.concurrency.JobLauncher;
import com.intellij.icons.AllIcons;
import com.intellij.java.JavaBundle;
import com.intellij.java.codeserver.core.JavaServiceProviderUtil;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.ex.EditorSettingsExternalizable;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressIndicatorProvider;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.impl.FindSuperElementsHelper;
import com.intellij.psi.search.searches.AllOverridingMethodsSearch;
import com.intellij.psi.search.searches.DirectClassInheritorsSearch;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.search.searches.SuperMethodsSearch;
import com.intellij.psi.util.MethodSignatureBackedByPsiMethod;
import com.intellij.psi.util.PsiExpressionTrimRenderer;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import javax.swing.*;
import java.util.*;

public class JavaLineMarkerProvider extends LineMarkerProviderDescriptor implements DumbAware {
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

  @Override
  public LineMarkerInfo<?> getLineMarkerInfo(@NotNull PsiElement element) {
    if (DumbService.isDumb(element.getProject())) {
      return getLineMarkerInfoDumb(element);
    }

    PsiElement parent = element.getParent();
    if (element instanceof PsiIdentifier && parent instanceof PsiMethod method) {
      if (!myOverridingOption.isEnabled() && !myImplementingOption.isEnabled()) return null;
      MethodSignatureBackedByPsiMethod superSignature = SuperMethodsSearch.search(method, null, true, false).findFirst();
      if (superSignature != null) {
        boolean overrides =
          method.hasModifierProperty(PsiModifier.ABSTRACT) == superSignature.getMethod().hasModifierProperty(PsiModifier.ABSTRACT);

        Icon icon;
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
      PsiMethod interfaceMethod = LambdaUtil.getFunctionalInterfaceMethod(parent);
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
        PsiFile psiFile = element1.getContainingFile();
        Document document = psiFile == null ? null : PsiDocumentManager.getInstance(psiFile.getProject()).getLastCommittedDocument(psiFile);
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

  private @Nullable LineMarkerInfo<?> getLineMarkerInfoDumb(@NotNull PsiElement element) {
    PsiElement parent = element.getParent();
    if (element instanceof PsiIdentifier && parent instanceof PsiMethod method) {
      if (!myOverridingOption.isEnabled() || !myImplementingOption.isEnabled()) return null;

      for (PsiAnnotation annotation : method.getAnnotations()) {
        PsiJavaCodeReferenceElement nameElement = annotation.getNameReferenceElement();
        if (nameElement != null && "Override".equals(nameElement.getText())) {
          return createSuperMethodLineMarkerInfo(element, getDumbOverridingIcon(method));
        }
      }
    }

    return null;
  }

  private static @NotNull Icon getDumbOverridingIcon(@NotNull PsiMethod method) {
    PsiClass aClass = method.getContainingClass();
    if (aClass == null) return AllIcons.Gutter.OverridingMethod;

    PsiReferenceList implementsList = aClass.getImplementsList();
    PsiReferenceList extendsList = aClass.getExtendsList();
    if (implementsList != null && implementsList.getReferenceElements().length != 0 &&
        (extendsList == null || extendsList.getReferenceElements().length == 0)) {
      // has some interfaces to implement
      String methodName = method.getName();
      if (!methodName.equals("equals")
          && !methodName.equals("hashCode")
          && !methodName.equals("clone")
          && !methodName.equals("toString")) {
        return AllIcons.Gutter.ImplementingMethod;
      }
    }

    return AllIcons.Gutter.OverridingMethod;
  }

  private static @NotNull LineMarkerInfo<PsiElement> createSuperMethodLineMarkerInfo(@NotNull PsiElement name, @NotNull Icon icon) {
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
  public void collectSlowLineMarkers(@NotNull List<? extends PsiElement> elements, @NotNull Collection<? super LineMarkerInfo<?>> result) {
    PsiElement first = ContainerUtil.getFirstItem(elements);
    if (first != null && DumbService.isDumb(first.getProject())) return;

    List<Computable<List<LineMarkerInfo<PsiElement>>>> tasks = new ArrayList<>();

    MultiMap<PsiClass, PsiMethod> canBeOverridden = MultiMap.createSet();
    MultiMap<PsiClass, PsiMethod> canHaveSiblings = MultiMap.create();
    //noinspection ForLoopReplaceableByForEach
    for (int i = 0; i < elements.size(); i++) {
      PsiElement element = elements.get(i);
      ProgressManager.checkCanceled();
      if (!(element instanceof PsiIdentifier)) continue;
      PsiElement parent = element.getParent();
      if (parent instanceof PsiMethod method) {
        PsiClass containingClass = method.getContainingClass();
        if (containingClass != null && PsiUtil.canBeOverridden(method)) {
          canBeOverridden.putValue(containingClass, method);
        }
        if (mySiblingsOption.isEnabled() && FindSuperElementsHelper.canHaveSiblingSuper(method, containingClass)) {
          canHaveSiblings.putValue(containingClass, method);
        }
        if (JavaServiceProviderUtil.isServiceProviderMethod(method)) {
          tasks.add(() -> JavaServiceLineMarkerUtil.collectServiceProviderMethod(method));
        }
      }
      else if (parent instanceof PsiClass && !(parent instanceof PsiTypeParameter)) {
        tasks.add(() -> collectInheritingClasses((PsiClass)parent));
        tasks.add(() -> JavaServiceLineMarkerUtil.collectServiceImplementationClass((PsiClass)parent));
      }
      else if (parent instanceof PsiReferenceExpression &&
               parent.getParent() instanceof PsiMethodCallExpression parentCall &&
               JavaServiceLineMarkerUtil.SERVICE_LOADER_LOAD.test(parentCall)) {
        tasks.add(() -> JavaServiceLineMarkerUtil.collectServiceLoaderLoadCall((PsiIdentifier)element, parentCall));
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

  private @Unmodifiable @NotNull List<LineMarkerInfo<PsiElement>> collectSiblingInheritedMethods(@NotNull Collection<? extends PsiMethod> methods) {
    if (!shouldSearchImplementedMethods() && !shouldSearchOverriddenMethods()) {
      return Collections.emptyList();
    }
    Map<PsiMethod, FindSuperElementsHelper.SiblingInfo> map = FindSuperElementsHelper.getSiblingInheritanceInfos(methods);
    return ContainerUtil.map(map.keySet(), method -> {
      PsiElement range = getMethodRange(method);
      ArrowUpLineMarkerInfo upInfo =
        new ArrowUpLineMarkerInfo(range, AllIcons.Gutter.SiblingInheritedMethod, MarkerType.SIBLING_OVERRIDING_METHOD);
      return NavigateAction.setNavigateAction(upInfo, JavaBundle.message("action.go.to.super.method.text"), IdeActions.ACTION_GOTO_SUPER);
    });
  }

  private static @NotNull PsiElement getMethodRange(@NotNull PsiMethod method) {
    PsiElement range;
    if (method.isPhysical()) {
      range = method.getNameIdentifier();
    }
    else {
      PsiElement navigationElement = method.getNavigationElement();
      range = navigationElement instanceof PsiNameIdentifierOwner
              ? ((PsiNameIdentifierOwner)navigationElement).getNameIdentifier()
              : navigationElement;
    }
    if (range == null) {
      range = method;
    }
    return range;
  }

  protected @NotNull List<LineMarkerInfo<PsiElement>> collectInheritingClasses(@NotNull PsiClass aClass) {
    if (!shouldSearchImplementedMethods() && !shouldSearchOverriddenMethods()) {
      return Collections.emptyList();
    }
    if (aClass.hasModifierProperty(PsiModifier.FINAL)) {
      return Collections.emptyList();
    }
    if (CommonClassNames.JAVA_LANG_OBJECT.equals(aClass.getQualifiedName())) {
      return Collections.emptyList(); // It's useless to have overridden markers for object.
    }

    boolean overridden = !aClass.isInterface();
    boolean shouldSearch = overridden ? shouldSearchOverriddenMethods() : shouldSearchImplementedMethods();
    if (!shouldSearch) return Collections.emptyList();
    PsiClass subClass = DirectClassInheritorsSearch.search(aClass).findFirst();
    if (subClass != null || (LambdaUtil.isFunctionalClass(aClass) && ReferencesSearch.search(aClass).findFirst() != null)) {
      PsiElement range = aClass.getNameIdentifier();
      if (range == null) {
        range = aClass;
      }
      LineMarkerInfo<PsiElement> info = createMarker(overridden, range, MarkerType.SUBCLASSED_CLASS);
      NavigateAction.setNavigateAction(info, overridden ? JavaBundle.message("action.go.to.subclass.text") :
                                             subClass == null ? JavaBundle.message("action.go.to.functional.implementation.text")
                                                              : JavaBundle.message("action.go.to.implementation.text"),
                                       IdeActions.ACTION_GOTO_IMPLEMENTATION);
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

  private @NotNull List<LineMarkerInfo<PsiElement>> collectOverridingMethods(@NotNull Set<PsiMethod> methodSet, @NotNull PsiClass containingClass) {
    if (!shouldSearchOverriddenMethods() && !shouldSearchImplementedMethods()) return Collections.emptyList();
    // Value = true -> functional interface which may have only functional implementations
    Map<PsiMethod, Boolean> overridden = new HashMap<>();

    AllOverridingMethodsSearch.search(containingClass).forEach(pair -> {
      ProgressManager.checkCanceled();

      PsiMethod superMethod = pair.getFirst();
      if (methodSet.remove(superMethod)) {
        overridden.put(superMethod, false);
      }
      return !methodSet.isEmpty();
    });

    if (!methodSet.isEmpty()) {
      PsiMethod interfaceMethod = LambdaUtil.getFunctionalInterfaceMethod(containingClass);
      if (interfaceMethod != null && methodSet.contains(interfaceMethod)) {
        if (ReferencesSearch.search(containingClass).findFirst() != null) {
          overridden.put(interfaceMethod, true);
        }
      }
    }

    List<LineMarkerInfo<PsiElement>> result = new ArrayList<>(overridden.size());
    for (Map.Entry<PsiMethod, Boolean> entry : overridden.entrySet()) {
      ProgressManager.checkCanceled();
      PsiMethod method = entry.getKey();
      boolean overrides = !method.hasModifierProperty(PsiModifier.ABSTRACT);
      if (overrides && !shouldSearchOverriddenMethods()) continue;
      if (!overrides && !shouldSearchImplementedMethods()) continue;
      PsiElement range = getMethodRange(method);
      LineMarkerInfo<PsiElement> info = createMarker(overrides, range, MarkerType.OVERRIDDEN_METHOD);
      NavigateAction.setNavigateAction(info,
                                       overrides ? JavaBundle.message("action.go.to.overriding.methods.text") :
                                       entry.getValue() ? JavaBundle.message("action.go.to.functional.implementation.text")
                                                        : JavaBundle.message("action.go.to.implementation.text"),
                                       IdeActions.ACTION_GOTO_IMPLEMENTATION);
      result.add(info);
    }
    return result;
  }

  private static @NotNull LineMarkerInfo<PsiElement> createMarker(boolean overridden, @NotNull PsiElement range, @NotNull MarkerType type) {
    Icon icon = overridden ? AllIcons.Gutter.OverridenMethod : AllIcons.Gutter.ImplementedMethod;
    return new LineMarkerInfo<>(range, range.getTextRange(),
                                icon, type.getTooltip(),
                                type.getNavigationHandler(),
                                GutterIconRenderer.Alignment.RIGHT);
  }

  @Override
  public String getName() {
    return JavaBundle.message("java.line.markers");
  }

  @Override
  public Option @NotNull [] getOptions() {
    return new Option[]{LAMBDA_OPTION, myOverriddenOption, myImplementedOption, myOverridingOption, myImplementingOption, mySiblingsOption,
      myServiceOption};
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

    @Override
    public @NotNull Function<? super PsiElement, String> getCommonTooltip(@NotNull List<? extends MergeableLineMarkerInfo<?>> infos) {
      return __ -> "Multiple method overrides";
    }

    @Override
    public @NotNull String getElementPresentation(@NotNull PsiElement element) {
      PsiElement parent = element.getParent();
      return parent instanceof PsiFunctionalExpression
             ? PsiExpressionTrimRenderer.render((PsiExpression)parent)
             : super.getElementPresentation(element);
    }
  }
}