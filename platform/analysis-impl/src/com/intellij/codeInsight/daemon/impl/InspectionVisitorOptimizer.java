// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl;

import com.intellij.extapi.psi.ASTDelegatePsiElement;
import com.intellij.extapi.psi.StubBasedPsiElementBase;
import com.intellij.lang.ASTNode;
import com.intellij.navigation.NavigationItem;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.ui.Queryable;
import com.intellij.openapi.util.Iconable;
import com.intellij.openapi.util.UserDataHolder;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.openapi.util.UserDataHolderEx;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.pom.Navigatable;
import com.intellij.pom.PomTarget;
import com.intellij.psi.*;
import com.intellij.psi.impl.ElementBase;
import com.intellij.psi.impl.PsiElementBase;
import com.intellij.psi.impl.ReparseableASTNode;
import com.intellij.psi.impl.source.tree.CompositePsiElement;
import com.intellij.psi.impl.source.tree.LeafElement;
import com.intellij.psi.impl.source.tree.TreeElement;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.CollectionFactory;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

import java.io.Serializable;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Infers classes of elements for inspection visitors to skip some of the PSI elements during inspection pass.
 * <p>
 * Declare `inspection.basicVisitor` in plugin.xml for your language to get speed up of inspection runs.
 */
@ApiStatus.Internal
public final class InspectionVisitorOptimizer {
  private static final Logger LOG = Logger.getInstance(InspectionVisitorOptimizer.class);
  private final @NotNull Map<Class<?>, Collection<Class<?>>> myTargetPsiClasses;
  private static final List<Class<?>> ALL_ELEMENTS_VISIT_LIST = List.of(PsiElement.class);

  private static final boolean useOptimizedVisitors = Registry.is("ide.optimize.inspection.visitors");
  private static final boolean inTests = ApplicationManager.getApplication().isUnitTestMode();

  public InspectionVisitorOptimizer(@NotNull List<? extends PsiElement> elements) {
    myTargetPsiClasses = getTargetPsiClasses(elements);
  }

  @NotNull @Unmodifiable
  static List<? extends Class<?>> getAcceptingPsiTypes(@NotNull PsiElementVisitor visitor) {
    if (!useOptimizedVisitors) return ALL_ELEMENTS_VISIT_LIST;

    List<? extends Class<?>> acceptingPsiTypes;
    if (visitor instanceof HintedPsiElementVisitor hinted) {
      acceptingPsiTypes = hinted.getHintPsiElements();

      if (inTests) {
        VisitorTypes handlesTypes = VISITOR_TYPES.get(visitor.getClass());
        if (!handlesTypes.overridesVisitPsiElement() && !handlesTypes.handlesElementTypes().equals(acceptingPsiTypes)) {
          LOG.error("HintedPsiElementVisitor implementations must override PsiElementVisitor.visitElement", visitor.getClass().getName());
        }
      }

      if (acceptingPsiTypes.contains(PsiElement.class) || acceptingPsiTypes.isEmpty()) {
        acceptingPsiTypes = ALL_ELEMENTS_VISIT_LIST;
      }
    }
    else {
      acceptingPsiTypes = VISITOR_TYPES.get(visitor.getClass()).handlesElementTypes();
    }

    return acceptingPsiTypes;
  }

  private static final Function<Class<?>, Collection<Class<?>>> TARGET_PSI_CLASSES_INIT = aSuper -> {
    List<Class<?>> c = new ArrayList<>(10);
    if (!aSuper.isInterface() && !Modifier.isAbstract(aSuper.getModifiers())) { // PSI elements in the tree cannot be abstract
      c.add(aSuper);
    }
    return c;
  };

  private static @NotNull Map<Class<?>, Collection<Class<?>>> getTargetPsiClasses(@NotNull List<? extends PsiElement> elements) {
    if (!useOptimizedVisitors) return Collections.emptyMap();

    Map<Class<?>, Collection<Class<?>>> targetPsiClasses = new IdentityHashMap<>(100);
    Set<Class<?>> uniqueElementClasses = CollectionFactory.createSmallMemoryFootprintSet(100);
    for (int i = 0; i < elements.size(); i++) {
      PsiElement element = elements.get(i);
      Class<? extends PsiElement> elementClass = element.getClass();

      // this check guarantees that items are unique in value collections, so we can use simple lists inside
      if (uniqueElementClasses.add(elementClass)) {
        for (Class<?> aSuper : SELF_AND_SUPERS.get(elementClass)) {
          Collection<Class<?>> classes = targetPsiClasses.computeIfAbsent(aSuper, TARGET_PSI_CLASSES_INIT);
          classes.add(elementClass);
        }
      }
    }

    return targetPsiClasses;
  }

  private Set<Class<?>> getVisitorAcceptClasses(@NotNull List<? extends Class<?>> acceptingPsiTypes) {
    Map<Class<?>, Collection<Class<?>>> targetPsiClasses = myTargetPsiClasses;
    if (acceptingPsiTypes.size() == 1) {
      return Set.copyOf(targetPsiClasses.getOrDefault(acceptingPsiTypes.get(0), Collections.emptyList()));
    }

    Set<Class<?>> accepts = null;
    for (Class<?> psiType : acceptingPsiTypes) {
      Collection<Class<?>> classes = targetPsiClasses.getOrDefault(psiType, Collections.emptyList());
      if (!classes.isEmpty()) {
        if (accepts == null) {
          accepts = new HashSet<>(classes);
        }
        else {
          accepts.addAll(classes);
        }
      }
    }

    return accepts;
  }

  private static final ClassValue<Class<?>[]> SELF_AND_SUPERS = new ClassValue<>() {
    @Override
    protected Class<?> @NotNull [] computeValue(@NotNull Class<?> type) {
      return getAllSupers(type);
    }

    private static Class<?> @NotNull [] getAllSupers(@NotNull Class<?> clazz) {
      Collection<Class<?>> supers = new HashSet<>();
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

      supers.removeIf(aSuper -> aSuper == UserDataHolder.class
                                || aSuper == UserDataHolderBase.class
                                || aSuper == UserDataHolderEx.class
                                || aSuper == CompositePsiElement.class
                                || aSuper == StubBasedPsiElementBase.class
                                || aSuper == ASTNode.class
                                || aSuper == ReparseableASTNode.class
                                || aSuper == ElementBase.class
                                || aSuper == Cloneable.class
                                || aSuper == Iconable.class
                                || aSuper == Serializable.class
                                || aSuper == PomTarget.class
                                || aSuper == Queryable.class
                                || aSuper == Navigatable.class
                                || aSuper == AtomicReference.class
                                || aSuper == NavigationItem.class
                                || aSuper == NavigatablePsiElement.class
                                || aSuper == PsiElementBase.class
                                || aSuper == TreeElement.class
                                || aSuper == LeafElement.class
                                || aSuper == ASTDelegatePsiElement.class);
      return supers.toArray(ArrayUtil.EMPTY_CLASS_ARRAY);
    }

    private static void addInterfaces(@NotNull Class<?> clazz, @NotNull Collection<? super Class<?>> supers) {
      Class<?>[] interfaces = clazz.getInterfaces();
      Collections.addAll(supers, interfaces);
      for (Class<?> anInterface : interfaces) {
        Collections.addAll(supers, getAllSupers(anInterface));
      }
    }
  };

  public void acceptElements(@NotNull List<? extends PsiElement> elements, @NotNull PsiElementVisitor elementVisitor) {
    List<? extends Class<?>> acceptingPsiTypes = getAcceptingPsiTypes(elementVisitor);
    acceptElements(elements, acceptingPsiTypes, element -> element.accept(elementVisitor));
  }

  void acceptElements(@NotNull List<? extends PsiElement> elements,
                      @NotNull List<? extends Class<?>> acceptingPsiTypes,
                      @NotNull Consumer<? super PsiElement> consumer) {
    if (acceptingPsiTypes == ALL_ELEMENTS_VISIT_LIST) {
      for (int i = 0; i < elements.size(); i++) {
        PsiElement element = elements.get(i);
        ProgressManager.checkCanceled();
        consumer.accept(element);
      }
    }
    else {
      Set<Class<?>> accepts = getVisitorAcceptClasses(acceptingPsiTypes);
      if (accepts != null && !accepts.isEmpty()) {
        for (int i = 0; i < elements.size(); i++) {
          PsiElement element = elements.get(i);
          if (accepts.contains(element.getClass())) {
            ProgressManager.checkCanceled();
            consumer.accept(element);
          }
        }
      }
    }
  }

  private record VisitorTypes(@NotNull @Unmodifiable List<? extends Class<?>> handlesElementTypes, boolean overridesVisitPsiElement) {
  }

  private static final ClassValue<VisitorTypes> VISITOR_TYPES = new ClassValue<>() {
    @Override
    protected VisitorTypes computeValue(@NotNull Class<?> type) {
      List<Class<?>> visitClasses = new ArrayList<>();

      Collection<String> visitorClasses = BasicInspectionVisitorBean.getVisitorClasses();

      Class<?> superClass = type;
      breakWhile:
      while (superClass != null) {
        if (superClass == PsiElementVisitor.class) {
          // no `inspection.basicVisitor` defined in hierarchy
          return new VisitorTypes(ALL_ELEMENTS_VISIT_LIST, visitClasses.contains(PsiElement.class));
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
            if (parameterType == PsiElement.class) {
              break breakWhile;
            }
          }
        }

        if (HintedPsiElementVisitor.class.isAssignableFrom(superClass)) {
          // do not inspect parent
          break;
        }

        superClass = superClass.getSuperclass();
      }

      if (visitClasses.contains(PsiElement.class)) {
        return new VisitorTypes(ALL_ELEMENTS_VISIT_LIST, true);
      }

      return new VisitorTypes(List.copyOf(visitClasses), false);
    }
  };
}
