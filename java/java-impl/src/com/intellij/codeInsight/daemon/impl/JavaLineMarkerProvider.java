/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
import com.intellij.codeInsight.daemon.impl.analysis.JavaModuleGraphUtil;
import com.intellij.concurrency.JobLauncher;
import com.intellij.icons.AllIcons;
import com.intellij.navigation.NavigationItem;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.colors.CodeInsightColors;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.openapi.editor.markup.SeparatorPlacement;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressIndicatorProvider;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.impl.FindSuperElementsHelper;
import com.intellij.psi.impl.source.resolve.reference.impl.JavaReflectionReferenceUtil;
import com.intellij.psi.search.searches.AllOverridingMethodsSearch;
import com.intellij.psi.search.searches.DirectClassInheritorsSearch;
import com.intellij.psi.search.searches.FunctionalExpressionSearch;
import com.intellij.psi.search.searches.SuperMethodsSearch;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.MethodSignatureBackedByPsiMethod;
import com.intellij.psi.util.PsiExpressionTrimRenderer;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.Function;
import com.intellij.util.FunctionUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.HashSet;
import com.intellij.util.containers.MultiMap;
import com.siyeh.ig.callMatcher.CallMatcher;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.MouseEvent;
import java.util.*;

public class JavaLineMarkerProvider extends LineMarkerProviderDescriptor {
  protected final DaemonCodeAnalyzerSettings myDaemonSettings;
  protected final EditorColorsManager myColorsManager;
  private final Option myLambdaOption = new Option("java.lambda", "Lambda", AllIcons.Gutter.ImplementingFunctionalInterface);
  private final Option myOverriddenOption = new Option("java.overridden", "Overridden method", AllIcons.Gutter.OverridenMethod);
  private final Option myImplementedOption = new Option("java.implemented", "Implemented method", AllIcons.Gutter.ImplementedMethod);
  private final Option myOverridingOption = new Option("java.overriding", "Overriding method", AllIcons.Gutter.OverridingMethod);
  private final Option myImplementingOption = new Option("java.implementing", "Implementing method", AllIcons.Gutter.ImplementingMethod);
  private final Option myServiceOption = new Option("java.service", "Service", AllIcons.Gutter.Java9Service);

  private static final CallMatcher SERVICE_LOADER_LOAD = CallMatcher.staticCall("java.util.ServiceLoader", "load", "loadInstalled");

  public JavaLineMarkerProvider(DaemonCodeAnalyzerSettings daemonSettings, EditorColorsManager colorsManager) {
    myDaemonSettings = daemonSettings;
    myColorsManager = colorsManager;
  }

  @Override
  @Nullable
  public LineMarkerInfo getLineMarkerInfo(@NotNull final PsiElement element) {
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
        return createSuperMethodLineMarkerInfo(element, icon, Pass.LINE_MARKERS);
      }
    }
    // in case of ()->{}, anchor to "->"
    // in case of (xxx)->{}, anchor to "->"
    // in case of Type::method, anchor to "method"
    if (myLambdaOption.isEnabled() &&
        parent instanceof PsiFunctionalExpression &&
        (element instanceof PsiJavaToken && ((PsiJavaToken)element).getTokenType() == JavaTokenType.ARROW && parent instanceof PsiLambdaExpression ||
         element instanceof PsiIdentifier && parent instanceof PsiMethodReferenceExpression && ((PsiMethodReferenceExpression)parent).getReferenceNameElement() == element)
      ) {
      final PsiMethod interfaceMethod = LambdaUtil.getFunctionalInterfaceMethod(parent);
      if (interfaceMethod != null) {
        return createSuperMethodLineMarkerInfo(element, AllIcons.Gutter.ImplementingFunctionalInterface, Pass.LINE_MARKERS);
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
          LineMarkerInfo info = new LineMarkerInfo<>(element, element.getTextRange(), null, Pass.LINE_MARKERS,
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

    List<Computable<List<LineMarkerInfo>>> tasks = new ArrayList<>();

    MultiMap<PsiClass, PsiMethod> canbeOverridden = MultiMap.create();
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
          canbeOverridden.putValue(containingClass, method);
        }
        if (FindSuperElementsHelper.canHaveSiblingSuper(method, containingClass)) {
          canHaveSiblings.putValue(containingClass, method);
        }
        if (isServiceProviderMethod(method)) {
          tasks.add(() -> collectServiceProviderMethod(method));
        }
      }
      else if (parent instanceof PsiClass && !(parent instanceof PsiTypeParameter)) {
        tasks.add(() -> collectInheritingClasses((PsiClass)parent));
        tasks.add(() -> collectServiceImplementationClass((PsiClass)parent));
      }
      else if (parent instanceof PsiReferenceExpression && parent.getParent() instanceof PsiMethodCallExpression) {
        PsiMethodCallExpression grandParent = (PsiMethodCallExpression)parent.getParent();
        if (SERVICE_LOADER_LOAD.test(grandParent)) {
          tasks.add(() -> collectServiceLoaderLoadCall((PsiIdentifier)element, grandParent));
        }
      }
    }
    for (PsiClass psiClass : canbeOverridden.keySet()) {
      Collection<PsiMethod> methods = canbeOverridden.get(psiClass);
      tasks.add(() -> collectOverridingMethods(methods, psiClass));
    }
    for (PsiClass psiClass : canHaveSiblings.keySet()) {
      Collection<PsiMethod> methods = canHaveSiblings.get(psiClass);
      tasks.add(() -> collectSiblingInheritedMethods(methods));
    }

    Object lock = new Object();
    ProgressIndicator indicator = ProgressIndicatorProvider.getGlobalProgressIndicator();
    JobLauncher.getInstance().invokeConcurrentlyUnderProgress(tasks, indicator, true, computable -> {
      List<LineMarkerInfo> infos = computable.compute();
      synchronized (lock) {
        result.addAll(infos);
      }
      return true;
    });
  }

  @NotNull
  private static List<LineMarkerInfo> collectSiblingInheritedMethods(@NotNull final Collection<PsiMethod> methods) {
    Map<PsiMethod, FindSuperElementsHelper.SiblingInfo> map = FindSuperElementsHelper.getSiblingInheritanceInfos(methods);
    return ContainerUtil.map(map.keySet(), method -> {
      PsiElement range = getMethodRange(method);
      ArrowUpLineMarkerInfo upInfo = new ArrowUpLineMarkerInfo(range, AllIcons.Gutter.SiblingInheritedMethod, MarkerType.SIBLING_OVERRIDING_METHOD,
                                                               Pass.LINE_MARKERS);
      return NavigateAction.setNavigateAction(upInfo, "Go to super method", IdeActions.ACTION_GOTO_SUPER);
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
  protected List<LineMarkerInfo> collectInheritingClasses(@NotNull PsiClass aClass) {
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
        if (!myImplementedOption.isEnabled()) return Collections.emptyList();
        icon = AllIcons.Gutter.ImplementedMethod;
      }
      else {
        if (!myOverriddenOption.isEnabled()) return Collections.emptyList();
        icon = AllIcons.Gutter.OverridenMethod;
      }
      PsiElement range = aClass.getNameIdentifier();
      if (range == null) {
        range = aClass;
      }
      MarkerType type = MarkerType.SUBCLASSED_CLASS;
      LineMarkerInfo<PsiElement> info = new LineMarkerInfo<>(range, range.getTextRange(),
                                                 icon, Pass.LINE_MARKERS, type.getTooltip(),
                                                 type.getNavigationHandler(),
                                                 GutterIconRenderer.Alignment.RIGHT);
      NavigateAction.setNavigateAction(info, aClass.isInterface() ? "Go to implementation(s)" : "Go to subclass(es)", IdeActions.ACTION_GOTO_IMPLEMENTATION);
      return Collections.singletonList(info);
    }
    return Collections.emptyList();
  }

  @NotNull
  private List<LineMarkerInfo> collectOverridingMethods(@NotNull final Iterable<PsiMethod> _methods, @NotNull PsiClass containingClass) {
    if (!myOverriddenOption.isEnabled() && !myImplementedOption.isEnabled()) return Collections.emptyList();
    final Set<PsiMethod> overridden = new HashSet<>();

    Set<PsiMethod> methodSet = ContainerUtil.newHashSet(_methods);

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
      if (interfaceMethod != null && FunctionalExpressionSearch.search(containingClass).findFirst() != null) {
        overridden.add(interfaceMethod);
      }
    }

    List<LineMarkerInfo> result = new ArrayList<>();
    for (PsiMethod method : overridden) {
      ProgressManager.checkCanceled();
      boolean overrides = !method.hasModifierProperty(PsiModifier.ABSTRACT);
      if (overrides) {
        if (!myOverriddenOption.isEnabled()) return Collections.emptyList();
      }
      else {
        if (!myImplementedOption.isEnabled()) return Collections.emptyList();
      }
      PsiElement range = getMethodRange(method);
      final MarkerType type = MarkerType.OVERRIDDEN_METHOD;
      final Icon icon = overrides ? AllIcons.Gutter.OverridenMethod : AllIcons.Gutter.ImplementedMethod;
      LineMarkerInfo<PsiElement> info = new LineMarkerInfo<>(range, range.getTextRange(),
                                                             icon, Pass.LINE_MARKERS, type.getTooltip(),
                                                             type.getNavigationHandler(),
                                                             GutterIconRenderer.Alignment.RIGHT);
      NavigateAction.setNavigateAction(info, overrides ? "Go to overriding methods" : "Go to implementation(s)", IdeActions.ACTION_GOTO_IMPLEMENTATION);
      result.add(info);
    }
    return result;
  }

  @Override
  public String getName() {
    return "Java line markers";
  }

  @NotNull
  @Override
  public Option[] getOptions() {
    return new Option[]{myLambdaOption, myOverriddenOption, myImplementedOption, myOverridingOption, myImplementingOption, myServiceOption};
  }

  private static boolean isServiceProviderMethod(@NotNull PsiMethod method) {
    return "provider".equals(method.getName()) &&
           method.getParameterList().getParametersCount() == 0 &&
           method.hasModifierProperty(PsiModifier.PUBLIC) &&
           method.hasModifierProperty(PsiModifier.STATIC);
  }

  @NotNull
  private static List<LineMarkerInfo> collectServiceProviderMethod(@NotNull PsiMethod method) {
    PsiClass containingClass = method.getContainingClass();
    PsiClass resultClass = PsiUtil.resolveClassInType(method.getReturnType());
    return createJavaServiceLineMarkerInfo(method.getNameIdentifier(), containingClass, resultClass);
  }

  @NotNull
  private static List<LineMarkerInfo> collectServiceImplementationClass(@NotNull PsiClass psiClass) {
    return createJavaServiceLineMarkerInfo(psiClass.getNameIdentifier(), psiClass, psiClass);
  }

  @NotNull
  private static List<LineMarkerInfo> createJavaServiceLineMarkerInfo(@Nullable PsiIdentifier identifier,
                                                                      @Nullable PsiClass implementerClass,
                                                                      @Nullable PsiClass resultClass) {
    if (identifier != null && implementerClass != null && resultClass != null) {
      String implementerClassName = implementerClass.getQualifiedName();
      if (implementerClassName != null && PsiUtil.isLanguageLevel9OrHigher(identifier)) {
        PsiJavaModule javaModule = JavaModuleGraphUtil.findDescriptorByElement(identifier);
        if (javaModule != null) {
          Iterable<PsiProvidesStatement> provides = javaModule.getProvides();
          for (PsiProvidesStatement providesStatement : provides) {
            PsiJavaCodeReferenceElement interfaceReference = providesStatement.getInterfaceReference();
            PsiReferenceList implementationList = providesStatement.getImplementationList();
            if (interfaceReference != null && implementationList != null) {
              PsiReference[] implementationReferences = implementationList.getReferenceElements();
              for (PsiReference implementationReference : implementationReferences) {
                if (implementationReference.isReferenceTo(implementerClass)) {
                  PsiClass interfaceClass = ObjectUtils.tryCast(interfaceReference.resolve(), PsiClass.class);
                  if (InheritanceUtil.isInheritorOrSelf(resultClass, interfaceClass, true)) {
                    String interfaceClassName = interfaceClass.getQualifiedName();
                    if (interfaceClassName != null) {
                      LineMarkerInfo<PsiElement> info =
                        new LineMarkerInfo<>(identifier, identifier.getTextRange(), AllIcons.Gutter.Java9Service, Pass.LINE_MARKERS,
                                             e -> DaemonBundle.message("service.provides", interfaceClassName),
                                             new ServiceProvidesNavigationHandler(interfaceClassName, implementerClassName),
                                             GutterIconRenderer.Alignment.LEFT);
                      return Collections.singletonList(info);
                    }
                  }
                }
              }
            }
          }
        }
      }
    }
    return Collections.emptyList();
  }

  private static List<LineMarkerInfo> collectServiceLoaderLoadCall(@NotNull PsiIdentifier identifier,
                                                                   @NotNull PsiMethodCallExpression methodCall) {
    if (PsiUtil.isLanguageLevel9OrHigher(methodCall)) {
      PsiExpression[] arguments = methodCall.getArgumentList().getExpressions();

      JavaReflectionReferenceUtil.ReflectiveType serviceType = null;
      for (int i = 0; i < arguments.length && serviceType == null; i++) {
        serviceType = JavaReflectionReferenceUtil.getReflectiveType(arguments[i]);
      }

      if (serviceType != null && serviceType.isExact()) {
        PsiClass psiClass = serviceType.getPsiClass();
        if (psiClass != null) {
          String qualifiedName = psiClass.getQualifiedName();
          if (qualifiedName != null) {
            PsiJavaModule javaModule = JavaModuleGraphUtil.findDescriptorByElement(methodCall);
            if (javaModule != null) {
              for (PsiUsesStatement statement : javaModule.getUses()) {
                PsiJavaCodeReferenceElement reference = statement.getClassReference();
                if (reference != null && reference.isReferenceTo(psiClass)) {
                  LineMarkerInfo<PsiElement> info =
                    new LineMarkerInfo<>(identifier, identifier.getTextRange(), AllIcons.Gutter.Java9Service, Pass.LINE_MARKERS,
                                         e -> DaemonBundle.message("service.uses", qualifiedName),
                                         new ServiceUsesNavigationHandler(qualifiedName),
                                         GutterIconRenderer.Alignment.LEFT);
                  return Collections.singletonList(info);
                }
              }
            }
          }
        }
      }
    }
    return Collections.emptyList();
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
      return (Function<PsiElement, String>)element -> "Multiple method overrides";
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


  public abstract static class ServiceNavigationHandler implements GutterIconNavigationHandler<PsiElement> {
    final String myInterfaceClassName;

    ServiceNavigationHandler(@NotNull String interfaceClassName) {myInterfaceClassName = interfaceClassName;}

    @Override
    public void navigate(MouseEvent e, PsiElement element) {
      Optional.ofNullable(JavaModuleGraphUtil.findDescriptorByElement(element))
        .map(this::findTargetReference)
        .filter(NavigationItem.class::isInstance)
        .map(NavigationItem.class::cast)
        .ifPresent(item -> item.navigate(true));
    }

    public abstract PsiJavaCodeReferenceElement findTargetReference(@NotNull PsiJavaModule module);

    @NotNull
    protected String getTargetFQN() {
      return myInterfaceClassName;
    }

    boolean isTargetReference(PsiJavaCodeReferenceElement reference) {
      return reference != null && getTargetFQN().equals(reference.getQualifiedName());
    }
  }

  private static class ServiceUsesNavigationHandler extends ServiceNavigationHandler {
    ServiceUsesNavigationHandler(String interfaceClassName) {
      super(interfaceClassName);
    }

    @Override
    public PsiJavaCodeReferenceElement findTargetReference(@NotNull PsiJavaModule module) {
      return StreamEx.of(module.getUses().iterator())
        .map(PsiUsesStatement::getClassReference)
        .findAny(this::isTargetReference)
        .orElse(null);
    }
  }

  private static class ServiceProvidesNavigationHandler extends ServiceNavigationHandler {
    private final String myImplementerClassName;

    ServiceProvidesNavigationHandler(@NotNull String interfaceClassName, @NotNull String implementerClassName) {
      super(interfaceClassName);
      myImplementerClassName = implementerClassName;
    }

    @Override
    public PsiJavaCodeReferenceElement findTargetReference(@NotNull PsiJavaModule module) {
      PsiJavaCodeReferenceElement[] references =
        StreamEx.of(module.getProvides().iterator())
          .findAny(this::isTargetStatement)
          .map(PsiProvidesStatement::getImplementationList)
          .map(PsiReferenceList::getReferenceElements)
          .orElse(PsiJavaCodeReferenceElement.EMPTY_ARRAY);

      return ContainerUtil.find(references, this::isTargetReference);
    }

    @Override
    @NotNull
    protected String getTargetFQN() {
      return myImplementerClassName;
    }

    private boolean isTargetStatement(@NotNull PsiProvidesStatement statement) {
      PsiJavaCodeReferenceElement reference = statement.getInterfaceReference();
      return reference != null && myInterfaceClassName.equals(reference.getQualifiedName());
    }
  }
}
