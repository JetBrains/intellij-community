// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeInsight.daemon.DaemonBundle;
import com.intellij.codeInsight.daemon.GutterIconNavigationHandler;
import com.intellij.codeInsight.daemon.LineMarkerInfo;
import com.intellij.codeInsight.daemon.impl.analysis.JavaModuleGraphUtil;
import com.intellij.icons.AllIcons;
import com.intellij.navigation.NavigationItem;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.resolve.reference.impl.JavaReflectionReferenceUtil;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.ig.callMatcher.CallMatcher;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.event.MouseEvent;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

class JavaServiceUtil {
  static final CallMatcher SERVICE_LOADER_LOAD = CallMatcher.staticCall("java.util.ServiceLoader", "load", "loadInstalled");

  static boolean isServiceProviderMethod(@NotNull PsiMethod method) {
    return "provider".equals(method.getName()) &&
           method.getParameterList().isEmpty() &&
           method.hasModifierProperty(PsiModifier.PUBLIC) &&
           method.hasModifierProperty(PsiModifier.STATIC);
  }

  @NotNull
  static List<LineMarkerInfo> collectServiceProviderMethod(@NotNull PsiMethod method) {
    PsiClass containingClass = method.getContainingClass();
    PsiClass resultClass = PsiUtil.resolveClassInType(method.getReturnType());
    return createJavaServiceLineMarkerInfo(method.getNameIdentifier(), containingClass, resultClass);
  }

  @NotNull
  static List<LineMarkerInfo> collectServiceImplementationClass(@NotNull PsiClass psiClass) {
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
                        new LineMarkerInfo<>(identifier, identifier.getTextRange(), AllIcons.Gutter.Java9Service,
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

  static List<LineMarkerInfo> collectServiceLoaderLoadCall(@NotNull PsiIdentifier identifier,
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
                    new LineMarkerInfo<>(identifier, identifier.getTextRange(), AllIcons.Gutter.Java9Service,
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

  abstract static class ServiceNavigationHandler implements GutterIconNavigationHandler<PsiElement> {
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
      PsiProvidesStatement statement = ContainerUtil.find(module.getProvides(), this::isTargetStatement);
      if (statement != null) {
        PsiReferenceList list = statement.getImplementationList();
        if (list != null) {
          return ContainerUtil.find(list.getReferenceElements(), this::isTargetReference);
        }
      }

      return null;
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
