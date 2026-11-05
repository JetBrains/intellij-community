// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
import com.intellij.psi.BasicInspectionVisitorBean;
import com.intellij.psi.HintedPsiElementVisitor;
import com.intellij.psi.NavigatablePsiElement;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.impl.ElementBase;
import com.intellij.psi.impl.PsiElementBase;
import com.intellij.psi.impl.ReparseableASTNode;
import com.intellij.psi.impl.source.tree.CompositePsiElement;
import com.intellij.psi.impl.source.tree.LeafElement;
import com.intellij.psi.impl.source.tree.TreeElement;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ClearableClassValue;
import com.intellij.util.containers.FreezableArrayList;
import com.intellij.util.containers.HashSetQueue;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.io.Serializable;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * Infers classes of elements for inspection visitors to skip some of the PSI elements during inspection pass.
 * <p>
 * Declare `inspection.basicVisitor` in plugin.xml for your language to get speed up of inspection runs.
 */
@ApiStatus.Internal
public final class InspectionVisitorOptimizer {
  private static final Logger LOG = Logger.getInstance(InspectionVisitorOptimizer.class);
  private static final boolean useOptimizedVisitors = Registry.is("ide.optimize.inspection.visitors");

  /**
   * Maps each indexed class or interface to the concrete PSI classes accepted for that type.
   * The constructor reads each element's runtime class and adds it to the entries for that class and its supertypes.
   * It skips the excluded types and processes each distinct runtime class once.
   * Each value contains only the distinct runtime classes found in the input list.
   * Visitors use this map to resolve accepted types before selecting element positions.
   * The map and its value lists remain unchanged after construction.
   * <p>
   * For example, the input {@code [methodA, classB, methodC]} contains two methods and one class.
   * Assume the methods are {@code PsiMethodImpl} instances and the class is a {@code PsiClassImpl} instance.
   * The maps contain these entries:
   * <pre>{@code
   * myTargetPsiClasses:
   *   PsiMethod.class       -> [PsiMethodImpl.class]
   *   PsiNamedElement.class -> [PsiMethodImpl.class, PsiClassImpl.class]
   *
   * myElementIndices:
   *   PsiMethodImpl.class -> [0, 2]
   *   PsiClassImpl.class  -> [1]
   * }</pre>
   */
  private final @NotNull Map<Class<? extends PsiElement>, List<Class<? extends PsiElement>>> myTargetPsiClasses =
    useOptimizedVisitors ? new IdentityHashMap<>(100) : Map.of();

  private final @NotNull List<? extends PsiElement> myElements;

  /**
   * Groups ascending, zero-based positions in {@link #myElements} by concrete PSI class.
   * The map and position lists remain unchanged after construction.
   *
   * @see #myTargetPsiClasses
   */
  private final @NotNull Map<Class<? extends PsiElement>, IntArrayList> myElementIndices;
  private static final List<Class<? extends PsiElement>> ALL_ELEMENTS_VISIT_LIST = List.of(PsiElement.class);
  private static final Set<Class<? extends PsiElement>> ALL_ELEMENTS_VISIT_SET = Set.of(PsiElement.class);
  private static final Comparator<ElementCursor> ELEMENT_CURSOR_COMPARATOR = Comparator.comparingInt(c -> c.elementIndex());

  private static final boolean inTests = ApplicationManager.getApplication().isUnitTestMode();

  /**
   * Indexes the input list. Its contents and order must remain unchanged.
   */
  public InspectionVisitorOptimizer(@NotNull List<? extends PsiElement> elements) {
    myElements = elements;
    myElementIndices = useOptimizedVisitors ? indexElements() : Map.of();
  }

  @ApiStatus.Internal
  public static @NotNull @Unmodifiable List<? extends Class<? extends PsiElement>> getAcceptingPsiTypes(@NotNull PsiElementVisitor visitor) {
    if (PsiElementVisitor.EMPTY_VISITOR == visitor) {
      return List.of();
    }
    if (!useOptimizedVisitors) {
      return ALL_ELEMENTS_VISIT_LIST;
    }
    List<? extends Class<? extends PsiElement>> acceptingPsiTypes;
    if (visitor instanceof HintedPsiElementVisitor hinted) {
      //noinspection unchecked,RedundantCast,rawtypes
      acceptingPsiTypes = (List<? extends Class<? extends PsiElement>>)(List)hinted.getHintPsiElements();

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

  private @NotNull Map<Class<? extends PsiElement>, IntArrayList> indexElements() {
    List<? extends PsiElement> elements = myElements;
    Map<Class<? extends PsiElement>, IntArrayList> elementIndices = new IdentityHashMap<>();
    for (int i = 0; i < elements.size(); i++) {
      ProgressManager.checkCanceled();
      PsiElement element = elements.get(i);
      Class<? extends PsiElement> elementClass = getElementClass(element);
      IntArrayList indices = elementIndices.computeIfAbsent(elementClass, _ -> new IntArrayList());

      if (indices.isEmpty()) {
        for (Class<? extends PsiElement> aSuper : SELF_AND_SUPERS.get(elementClass)) {
          List<Class<? extends PsiElement>> classes = myTargetPsiClasses.computeIfAbsent(aSuper, _ -> new ArrayList<>());
          classes.add(elementClass);
        }
      }
      indices.add(i);
    }

    return elementIndices;
  }

  /**
   * Keep the parameter typed as {@code Object} so javac emits {@code invokevirtual Object.getClass}.
   * Calling through {@code PsiElement} emits {@code invokeinterface} and can contend on JVM locks during method resolution.
   */
  private static @NotNull Class<? extends PsiElement> getElementClass(@NotNull Object element) {
    //noinspection unchecked
    return (Class<? extends PsiElement>)element.getClass();
  }

  /**
   * Resolves the accepted PSI types to concrete classes from both optimizers in one pass over {@code acceptingPsiTypes}.
   * The result can filter either optimizer's input list.
   * A result containing {@code PsiElement.class} accepts every element.
   */
  public static @NotNull Set<Class<? extends PsiElement>> getVisitorAcceptClasses(
    @NotNull List<? extends Class<? extends PsiElement>> acceptingPsiTypes,
    @NotNull InspectionVisitorOptimizer firstOptimizer,
    @NotNull InspectionVisitorOptimizer secondOptimizer) {
    return firstOptimizer.getVisitorAcceptClasses(acceptingPsiTypes, secondOptimizer);
  }

  private @NotNull Set<Class<? extends PsiElement>> getVisitorAcceptClasses(@NotNull List<? extends Class<? extends PsiElement>> acceptingPsiTypes,
                                                                            @Nullable InspectionVisitorOptimizer otherOptimizer) {
    if (acceptingPsiTypes == ALL_ELEMENTS_VISIT_LIST) {
      return ALL_ELEMENTS_VISIT_SET;
    }
    Map<Class<? extends PsiElement>, List<Class<? extends PsiElement>>> targetPsiClasses = myTargetPsiClasses;
    if (acceptingPsiTypes.size() == 1 && otherOptimizer == null) {
      return Set.copyOf(targetPsiClasses.getOrDefault(acceptingPsiTypes.getFirst(), Collections.emptyList()));
    }

    Set<Class<? extends PsiElement>> accepts = null;
    for (Class<? extends PsiElement> psiType : acceptingPsiTypes) {
      List<Class<? extends PsiElement>> classes = targetPsiClasses.getOrDefault(psiType, Collections.emptyList());
      List<Class<? extends PsiElement>> otherClasses = otherOptimizer == null
                                                     ? Collections.emptyList()
                                                     : otherOptimizer.myTargetPsiClasses.getOrDefault(psiType, Collections.emptyList());
      if (!classes.isEmpty() || !otherClasses.isEmpty()) {
        if (accepts == null) {
          accepts = new HashSet<>();
        }
        accepts.addAll(classes);
        accepts.addAll(otherClasses);
      }
    }

    return accepts == null ? Set.of() : accepts;
  }

  @ApiStatus.Internal
  public static void clearCache() {
    SELF_AND_SUPERS.clear();
  }

  private static final ClearableClassValue<Class<? extends PsiElement>[]> SELF_AND_SUPERS = new ClearableClassValue<>() {
    @Override
    public Class<? extends PsiElement> @NotNull [] computeValueImpl(@NotNull Class<?> clazz) {
      if (isExcluded(clazz)) {
        //noinspection unchecked
        return ArrayUtil.EMPTY_CLASS_ARRAY;
      }
      HashSetQueue<Class<?>> supers = new HashSetQueue<>();
      supers.add(clazz);
      for (Class<?> aClass : supers) {
        for (Class<?> anInterface : aClass.getInterfaces()) {
          if (!isExcluded(anInterface)) {
            supers.add(anInterface);
          }
        }
        Class<?> superClass = aClass.getSuperclass();
        if (superClass != null && superClass != Object.class && !isExcluded(superClass)) {
          supers.add(superClass);
        }
      }
      //noinspection unchecked
      return supers.toArray(ArrayUtil.EMPTY_CLASS_ARRAY);
    }
  };

  private static boolean isExcluded(Class<?> clazz) {
    return excludedClasses.contains(clazz);
  }

  private static final Set<Class<?>> excludedClasses = Set.of(
      ASTDelegatePsiElement.class
    , ASTNode.class
    , AtomicReference.class
    , Cloneable.class
    , CompositePsiElement.class
    , ElementBase.class
    , Iconable.class
    , LeafElement.class
    , Navigatable.class
    , NavigationItem.class
    , NavigatablePsiElement.class
    , PomTarget.class
    , PsiElementBase.class
    , Queryable.class
    , ReparseableASTNode.class
    , Serializable.class
    , StubBasedPsiElementBase.class
    , TreeElement.class
    , UserDataHolder.class
    , UserDataHolderBase.class
    , UserDataHolderEx.class
  );

  public void acceptElements(@NotNull PsiElementVisitor elementVisitor) {
    List<? extends Class<? extends PsiElement>> acceptingPsiTypes = getAcceptingPsiTypes(elementVisitor);
    acceptElements(filterElements(acceptingPsiTypes), element -> element.accept(elementVisitor));
  }

  /**
   * Resolves {@code acceptingPsiTypes} to concrete PSI classes, then selects the elements whose runtime classes belong to that set.
   * Class resolution uses the map built from the constructor input list.
   * The result preserves the input order.
   * <p>
   * Selects the stored positions of matching classes in the constructor input list.
   * Reads only matching elements and makes no further {@code getClass()} calls.
   * The input list must retain its original contents and order.
   * The result may reuse the input list when no filtering is needed.
   *
   * @param acceptingPsiTypes the PSI types accepted by the visitor
   * @return a read-only list of matching elements, or an empty list if no elements match
   */
  public @NotNull @Unmodifiable List<? extends PsiElement> filterElements(@NotNull @Unmodifiable List<? extends Class<? extends PsiElement>> acceptingPsiTypes) {
    return filterElements(getVisitorAcceptClasses(acceptingPsiTypes, null));
  }

  /**
   * Selects elements by their concrete classes and preserves the input order.
   * The set can include classes found only in other optimizers.
   * A set containing {@code PsiElement.class} accepts every element.
   * Uses the stored positions without further {@code getClass()} calls.
   * Copies one matching bucket directly and merges multiple buckets with a priority queue.
   *
   * @see #getVisitorAcceptClasses(List, InspectionVisitorOptimizer, InspectionVisitorOptimizer)
   */
  public @NotNull @Unmodifiable List<? extends PsiElement> filterElements(@NotNull Set<? extends Class<? extends PsiElement>> acceptingPsiClasses) {
    List<? extends PsiElement> elements = myElements;
    if (acceptingPsiClasses.contains(PsiElement.class)) {
      return elements;
    }
    if (acceptingPsiClasses.isEmpty() || elements.isEmpty()) {
      return List.of();
    }
    IntArrayList firstIndices = null;
    PriorityQueue<ElementCursor> cursors = null;
    int size = 0;
    for (Class<? extends PsiElement> acceptingClass : acceptingPsiClasses) {
      ProgressManager.checkCanceled();
      IntArrayList indices = myElementIndices.get(acceptingClass);
      if (indices != null) {
        if (firstIndices == null) {
          firstIndices = indices;
        }
        else {
          if (cursors == null) {
            cursors = new PriorityQueue<>(ELEMENT_CURSOR_COMPARATOR);
            cursors.add(new ElementCursor(firstIndices));
          }
          cursors.add(new ElementCursor(indices));
        }
        size += indices.size();
      }
    }
    if (firstIndices == null) {
      return List.of();
    }
    if (size == elements.size()) {
      return elements;
    }
    FreezableArrayList<PsiElement> result = new FreezableArrayList<>(size);
    if (cursors == null) {
      for (int i = 0; i < firstIndices.size(); i++) {
        ProgressManager.checkCanceled();
        result.add(elements.get(firstIndices.getInt(i)));
      }
    }
    else {
      while (!cursors.isEmpty()) {
        ProgressManager.checkCanceled();
        ElementCursor cursor = cursors.remove();
        result.add(elements.get(cursor.elementIndex()));
        if (++cursor.myOffset < cursor.myIndices.size()) {
          cursors.add(cursor);
        }
      }
    }
    return result.emptyOrFrozen();
  }

  /**
   * Visits every supplied element in list order.
   */
  public static void acceptElements(@NotNull @Unmodifiable List<? extends PsiElement> elements,
                                    @NotNull Consumer<? super PsiElement> consumer) {
    //noinspection ForLoopReplaceableByForEach
    for (int i = 0; i < elements.size(); i++) {
      ProgressManager.checkCanceled();
      consumer.accept(elements.get(i));
    }
  }

  private static final class ElementCursor {
    private final IntArrayList myIndices;
    private int myOffset;

    private ElementCursor(IntArrayList indices) {
      myIndices = indices;
    }

    private int elementIndex() {
      return myIndices.getInt(myOffset);
    }
  }

  private record VisitorTypes(@NotNull @Unmodifiable List<? extends Class<? extends PsiElement>> handlesElementTypes, boolean overridesVisitPsiElement) {
  }

  private static final ClassValue<VisitorTypes> VISITOR_TYPES = new ClassValue<>() {
    @Override
    protected VisitorTypes computeValue(@NotNull Class<?> type) {
      List<Class<PsiElement>> visitClasses = new ArrayList<>();

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
            //noinspection unchecked
            Class<PsiElement> parameterType = (Class<PsiElement>)declaredMethod.getParameterTypes()[0];
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
