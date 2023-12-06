// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeInsight.daemon.GutterIconNavigationHandler;
import com.intellij.codeInsight.daemon.LineMarkerInfo;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightingFeature;
import com.intellij.codeInsight.daemon.impl.analysis.JavaModuleGraphUtil;
import com.intellij.icons.AllIcons;
import com.intellij.java.analysis.JavaAnalysisBundle;
import com.intellij.navigation.NavigationItem;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.text.HtmlBuilder;
import com.intellij.openapi.util.text.HtmlChunk;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.resolve.reference.impl.JavaReflectionReferenceUtil;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.ig.callMatcher.CallMatcher;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.event.MouseEvent;
import java.util.*;

import static com.intellij.psi.CommonClassNames.JAVA_UTIL_SERVICE_LOADER;

/**
 * Utility for working with Java services (jigsaw)
 */
final public class JavaServiceUtil {
  public static final String PROVIDER = "provider";
  public static final Set<String> JAVA_UTIL_SERVICE_LOADER_METHODS = Set.of("load", "loadInstalled");

  static final CallMatcher SERVICE_LOADER_LOAD = CallMatcher.staticCall(JAVA_UTIL_SERVICE_LOADER,
                                                                        ArrayUtil.toStringArray(JAVA_UTIL_SERVICE_LOADER_METHODS));

  /**
   * Checks if the given method is a service provider method.
   *
   * @param method for checking
   * @return true if the method is a service provider method, false otherwise
   */
  public static boolean isServiceProviderMethod(@NotNull PsiMethod method) {
    return PROVIDER.equals(method.getName()) &&
           method.getParameterList().isEmpty() &&
           method.hasModifierProperty(PsiModifier.PUBLIC) &&
           method.hasModifierProperty(PsiModifier.STATIC);
  }

  /**
   * Finds a service provider method within a given PsiClass.
   *
   * @param psiClass to search for the service provider method
   * @return service provider method, or null if not found
   */
  @Nullable
  public static PsiMethod findServiceProviderMethod(@NotNull PsiClass psiClass) {
    return ContainerUtil.find(psiClass.findMethodsByName("provider", false), JavaServiceUtil::isServiceProviderMethod);
  }

  @NotNull
  static List<LineMarkerInfo<PsiElement>> collectServiceProviderMethod(@NotNull PsiMethod method) {
    PsiClass containingClass = method.getContainingClass();
    PsiClass resultClass = PsiUtil.resolveClassInType(method.getReturnType());
    return createJavaServiceLineMarkerInfo(method.getNameIdentifier(), containingClass, resultClass);
  }

  @NotNull
  static List<LineMarkerInfo<PsiElement>> collectServiceImplementationClass(@NotNull PsiClass psiClass) {
    if (findServiceProviderMethod(psiClass) != null) return Collections.emptyList();
    for (PsiMethod constructor : psiClass.getConstructors()) {
      if (!constructor.hasParameters()) return createJavaServiceLineMarkerInfo(constructor.getNameIdentifier(), psiClass, psiClass);
    }
    return createJavaServiceLineMarkerInfo(psiClass.getNameIdentifier(), psiClass, psiClass);
  }

  @NotNull
  private static List<LineMarkerInfo<PsiElement>> createJavaServiceLineMarkerInfo(@Nullable PsiIdentifier identifier,
                                                                                  @Nullable PsiClass implementerClass,
                                                                                  @Nullable PsiClass resultClass) {
    if (identifier == null || implementerClass == null || resultClass == null) return Collections.emptyList();
    if (!HighlightingFeature.MODULES.isAvailable(identifier)) return Collections.emptyList();

    String implementerClassName = implementerClass.getQualifiedName();
    if (implementerClassName == null) return Collections.emptyList();

    PsiJavaModule javaModule = JavaModuleGraphUtil.findDescriptorByElement(identifier);
    if (javaModule == null) return Collections.emptyList();

    for (PsiProvidesStatement providesStatement : javaModule.getProvides()) {
      PsiClassType interfaceType = providesStatement.getInterfaceType();
      if (interfaceType == null) continue;
      PsiReferenceList implementationList = providesStatement.getImplementationList();
      if (implementationList == null) continue;

      PsiClassType[] implementationTypes = implementationList.getReferencedTypes();
      for (PsiClassType implementationType : implementationTypes) {
        if (!implementerClass.equals(implementationType.resolve())) continue;
        PsiClass interfaceClass = interfaceType.resolve();
        if (!InheritanceUtil.isInheritorOrSelf(resultClass, interfaceClass, true)) continue;
        String interfaceClassName = interfaceClass.getQualifiedName();
        if (interfaceClassName == null) continue;

        return Collections.singletonList(new LineMarkerInfo<>(identifier, identifier.getTextRange(), AllIcons.Gutter.Java9Service,
                                                              e -> calculateTooltip("service.provides", interfaceClassName),
                                                              new ServiceProvidesNavigationHandler(interfaceClassName,
                                                                                                   implementerClassName),
                                                              GutterIconRenderer.Alignment.LEFT,
                                                              () -> JavaAnalysisBundle.message("service.provides")));
      }
    }
    return Collections.emptyList();
  }

  @NotNull
  private static String calculateTooltip(@NotNull String key, @NlsSafe String interfaceClassName) {
    return new HtmlBuilder().append(JavaAnalysisBundle.message(key)).append(" ")
      .appendLink("#javaClass/" + interfaceClassName, interfaceClassName)
      .br().append(HtmlChunk.text(JavaAnalysisBundle.message("service.click.to.navigate"))
                     .wrapWith(HtmlChunk.font(2))
                     .wrapWith(HtmlChunk.div("margin-top: 5px"))).toString();
  }

  static List<LineMarkerInfo<PsiElement>> collectServiceLoaderLoadCall(@NotNull PsiIdentifier identifier,
                                                                       @NotNull PsiMethodCallExpression methodCall) {
    if (!HighlightingFeature.MODULES.isAvailable(methodCall)) return Collections.emptyList();
    PsiExpression[] arguments = methodCall.getArgumentList().getExpressions();

    JavaReflectionReferenceUtil.ReflectiveType serviceType = findServiceTypeInArguments(arguments);
    if (serviceType == null || !serviceType.isExact()) return Collections.emptyList();

    PsiClass psiClass = serviceType.getPsiClass();
    if (psiClass == null) return Collections.emptyList();

    String qualifiedName = psiClass.getQualifiedName();
    if (qualifiedName == null) return Collections.emptyList();

    PsiJavaModule javaModule = JavaModuleGraphUtil.findDescriptorByElement(methodCall);
    if (javaModule == null) return Collections.emptyList();

    for (PsiUsesStatement statement : javaModule.getUses()) {
      PsiClassType usedClass = statement.getClassType();
      if (usedClass == null || !Objects.equals(psiClass, usedClass.resolve())) continue;
      return Collections.singletonList(new LineMarkerInfo<>(identifier, identifier.getTextRange(), AllIcons.Gutter.Java9Service,
                                                            e -> calculateTooltip("service.uses", qualifiedName),
                                                            new ServiceUsesNavigationHandler(qualifiedName),
                                                            GutterIconRenderer.Alignment.LEFT,
                                                            () -> JavaAnalysisBundle.message("service.uses")));
    }
    return Collections.emptyList();
  }

  @Nullable
  private static JavaReflectionReferenceUtil.ReflectiveType findServiceTypeInArguments(PsiExpression[] arguments) {
    return StreamEx.of(arguments).map(JavaReflectionReferenceUtil::getReflectiveType)
      .filter(Objects::nonNull).findAny()
      .orElse(null);
  }

  abstract static class ServiceNavigationHandler implements GutterIconNavigationHandler<PsiElement> {
    final String myInterfaceClassName;

    ServiceNavigationHandler(@NotNull String interfaceClassName) { myInterfaceClassName = interfaceClassName; }

    @Override
    public void navigate(MouseEvent e, @Nullable PsiElement element) {
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
      return reference != null && Objects.equals(getTargetFQN(), reference.getQualifiedName());
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
      PsiProvidesStatement statement = ContainerUtil.find(module.getProvides(), this::isTargetStatement);
      if (statement == null) return null;

      PsiReferenceList list = statement.getImplementationList();
      if (list == null) return null;

      return ContainerUtil.find(list.getReferenceElements(), this::isTargetReference);
    }

    @NotNull
    @Override
    protected String getTargetFQN() {
      return myImplementerClassName;
    }

    private boolean isTargetStatement(@NotNull PsiProvidesStatement statement) {
      PsiJavaCodeReferenceElement reference = statement.getInterfaceReference();
      return reference != null && myInterfaceClassName.equals(reference.getQualifiedName());
    }
  }
}