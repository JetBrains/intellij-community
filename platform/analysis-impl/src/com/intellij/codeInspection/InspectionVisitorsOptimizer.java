// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.ui.Queryable;
import com.intellij.openapi.util.Iconable;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.openapi.util.UserDataHolderEx;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.pom.Navigatable;
import com.intellij.pom.PomTarget;
import com.intellij.psi.BasicInspectionVisitorBean;
import com.intellij.psi.HintedPsiElementVisitor;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.impl.ElementBase;
import com.intellij.psi.impl.source.tree.CompositePsiElement;
import com.intellij.util.containers.CollectionFactory;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.Serializable;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

import static java.util.Collections.*;

/**
 * Infers classes of elements for inspection visitors in order to skip some of PSI elements during inspection pass.
 * <p>
 * Declare `inspection.basicVisitor` in plugin.xml for your language to get speed up of inspection runs.
 */
@ApiStatus.Internal
public final class InspectionVisitorsOptimizer {
  private InspectionVisitorsOptimizer() {
  }

  private static final Logger LOG = Logger.getInstance(InspectionVisitorsOptimizer.class);

  public static final List<Class<?>> ALL_ELEMENTS_VISIT_LIST = singletonList(PsiElement.class);

  private static final boolean useOptimizedVisitors = Registry.is("ide.optimize.inspection.visitors");
  private static final boolean inTests = ApplicationManager.getApplication().isUnitTestMode();

  public static @NotNull List<Class<?>> getAcceptingPsiTypes(@NotNull PsiElementVisitor visitor) {
    if (!useOptimizedVisitors) return ALL_ELEMENTS_VISIT_LIST;

    List<Class<?>> acceptingPsiTypes;
    if (visitor instanceof HintedPsiElementVisitor) {
      acceptingPsiTypes = ((HintedPsiElementVisitor)visitor).getHintPsiElements();

      if (inTests) {
        VisitorTypes handlesTypes = VISITOR_TYPES.get(visitor.getClass());
        if (!handlesTypes.overridesVisitPsiElement
            && !handlesTypes.handlesElementTypes.equals(acceptingPsiTypes)) {
          LOG.error("HintedPsiElementVisitor implementations must override PsiElementVisitor.visitElement",
                    visitor.getClass().getName());
        }
      }

      if (acceptingPsiTypes.contains(PsiElement.class) || acceptingPsiTypes.isEmpty()) {
        acceptingPsiTypes = ALL_ELEMENTS_VISIT_LIST;
      }
    }
    else {
      acceptingPsiTypes = VISITOR_TYPES.get(visitor.getClass()).handlesElementTypes;
    }

    return acceptingPsiTypes;
  }

  public static @NotNull Map<Class<?>, Collection<Class<?>>> getTargetPsiClasses(@NotNull List<? extends PsiElement> elements) {
    if (!useOptimizedVisitors) return emptyMap();

    Set<Class<?>> uniqueElementClasses = CollectionFactory.createSmallMemoryFootprintSet();
    for (int i = 0; i < elements.size(); i++) {
      PsiElement element = elements.get(i);
      uniqueElementClasses.add(element.getClass());
    }

    Map<Class<?>, Collection<Class<?>>> targetPsiClasses = new IdentityHashMap<>();
    for (Class<?> elementClass : uniqueElementClasses) {
      for (Class<?> aSuper : ELEMENT_TYPE_SUPERS.get(elementClass)) {
        Collection<Class<?>> classes = targetPsiClasses.get(aSuper);
        if (classes == null) {
          classes = CollectionFactory.createSmallMemoryFootprintSet();
          targetPsiClasses.put(aSuper, classes);
          if (!aSuper.isInterface() && !Modifier.isAbstract(aSuper.getModifiers())) { // PSI elements in tree cannot be abstract
            classes.add(aSuper);
          }
        }

        classes.add(elementClass);
      }
    }
    return targetPsiClasses;
  }

  public static @Nullable Set<Class<?>> getVisitorAcceptClasses(
    @NotNull Map<Class<?>, Collection<Class<?>>> targetPsiClasses,
    @NotNull List<Class<?>> acceptingPsiTypes
  ) {
    if (acceptingPsiTypes.size() == 1) {
      return Set.copyOf(targetPsiClasses.getOrDefault(acceptingPsiTypes.get(0), emptyList()));
    }

    Set<Class<?>> accepts = null;
    for (Class<?> psiType : acceptingPsiTypes) {
      Collection<Class<?>> classes = targetPsiClasses.getOrDefault(psiType, emptyList());
      if (!classes.isEmpty()) {
        if (accepts == null) {
          accepts = new HashSet<>(classes);
        }
        accepts.addAll(classes);
      }
    }

    return accepts;
  }

  private static final ClassValue<Collection<Class<?>>> ELEMENT_TYPE_SUPERS = new ClassValue<>() {
    @Override
    protected Collection<Class<?>> computeValue(Class<?> type) {
      return getAllSupers(type);
    }

    private static @NotNull Collection<Class<?>> getAllSupers(@NotNull Class<?> clazz) {
      List<Class<?>> supers = new ArrayList<>();
      supers.add(clazz);
      addInterfaces(clazz, supers);

      Class<?> superClass = clazz.getSuperclass();
      while (superClass != null) {
        if (superClass != Object.class) {
          supers.add(superClass);
          addInterfaces(superClass, supers);
        }
        superClass = superClass.getSuperclass();
      }

      supers.removeIf(aSuper -> {
        return (aSuper == UserDataHolderBase.class
                || aSuper == UserDataHolderEx.class
                || aSuper == CompositePsiElement.class
                || aSuper == ElementBase.class
                || aSuper == Cloneable.class
                || aSuper == Iconable.class
                || aSuper == Serializable.class
                || aSuper == PomTarget.class
                || aSuper == Queryable.class
                || aSuper == Navigatable.class
                || aSuper == AtomicReference.class);
      });

      return supers;
    }

    private static void addInterfaces(Class<?> clazz, List<Class<?>> supers) {
      Class<?>[] interfaces = clazz.getInterfaces();
      addAll(supers, interfaces);
      for (Class<?> anInterface : interfaces) {
        supers.addAll(getAllSupers(anInterface));
      }
    }
  };

  private record VisitorTypes(boolean hasBasicVisitor,
                              List<Class<?>> handlesElementTypes,
                              boolean overridesVisitPsiElement) {
  }

  private static final ClassValue<VisitorTypes> VISITOR_TYPES = new ClassValue<>() {
    @Override
    protected VisitorTypes computeValue(Class<?> type) {
      List<Class<?>> visitClasses = new ArrayList<>();

      Collection<String> visitorClasses = BasicInspectionVisitorBean.getVisitorClasses();

      Class<?> superClass = type;
      while (superClass != null) {
        if (superClass == PsiElementVisitor.class) {
          // no `inspection.basicVisitor` defined in hierarchy
          return new VisitorTypes(false, ALL_ELEMENTS_VISIT_LIST, visitClasses.contains(PsiElement.class));
        }

        if (visitorClasses.contains(superClass.getName())) {
          break;
        }

        for (Method declaredMethod : superClass.getDeclaredMethods()) {
          if (declaredMethod.getParameterCount() == 1
              && declaredMethod.getName().startsWith("visit")
              && Modifier.isPublic(declaredMethod.getModifiers())
              && !Modifier.isAbstract(declaredMethod.getModifiers())
              && !Modifier.isStatic(declaredMethod.getModifiers())) {
            Class<?> parameterType = declaredMethod.getParameterTypes()[0];
            visitClasses.add(parameterType);
          }
        }

        if (HintedPsiElementVisitor.class.isAssignableFrom(superClass)) {
          // do not inspect parent
          break;
        }

        superClass = superClass.getSuperclass();
      }

      if (visitClasses.contains(PsiElement.class)) {
        return new VisitorTypes(true, ALL_ELEMENTS_VISIT_LIST, true);
      }

      return new VisitorTypes(true, List.copyOf(visitClasses), false);
    }
  };
}
