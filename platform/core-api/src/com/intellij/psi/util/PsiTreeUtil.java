// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.util;

import com.intellij.lang.ASTNode;
import com.intellij.lang.Language;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.search.PsiElementProcessor;
import com.intellij.psi.search.PsiElementProcessor.CollectElements;
import com.intellij.psi.search.PsiElementProcessor.FindElement;
import com.intellij.psi.stubs.StubElement;
import com.intellij.psi.templateLanguages.OuterLanguageElement;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.*;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.IntArrayList;
import org.jetbrains.annotations.*;

import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Provides utility methods for navigating, locating and collecting PSI elements.
 * <p>
 * The naming scheme of methods in this class <b>does not</b> consistently follow the established convention of:
 * <ul>
 * <li> <i>children</i> being only the direct descendants of the current node
 * <li> <i>parent</i> being only the direct ancestor of the current node
 * </ul>
 * @see SyntaxTraverser
 */
@ApiStatus.NonExtendable
public class PsiTreeUtil {
  private static final Key<Object> MARKER = Key.create("PsiTreeUtil.copyElements.MARKER");
  @SuppressWarnings("unchecked") private static final Class<? extends PsiElement>[] WS = new Class[]{PsiWhiteSpace.class};
  @SuppressWarnings("unchecked") private static final Class<? extends PsiElement>[] WS_COMMENTS = new Class[]{PsiWhiteSpace.class, PsiComment.class};

  /**
   * Checks whether one element in the PSI tree is under another.
   *
   * @param ancestor parent candidate. {@code false} will be returned if ancestor is {@code null}.
   * @param element  child candidate
   * @param strict   whether to start search from the element ({@code true}) or from the element's parent ({@code false}).
   * @return {@code true} if {@code element} has {@code ancestor} as its ancestor somewhere in the hierarchy, {@code false} otherwise.
   */
  @Contract(value = "null, _, _ -> false", pure = true)
  public static boolean isAncestor(@Nullable PsiElement ancestor, @NotNull PsiElement element, boolean strict) {
    if (ancestor == null) return false;
    // fast path to avoid loading the tree
    if (ancestor instanceof StubBasedPsiElement && ((StubBasedPsiElement<?>)ancestor).getStub() != null ||
        element instanceof StubBasedPsiElement && ((StubBasedPsiElement<?>)element).getStub() != null) {
      if (ancestor.getContainingFile() != element.getContainingFile()) return false;
    }

    boolean stopAtFileLevel = !(ancestor instanceof PsiFile || ancestor instanceof PsiDirectory);

    PsiElement parent = strict ? element.getParent() : element;
    while (true) {
      if (parent == null) return false;
      if (parent.equals(ancestor)) return true;
      if (stopAtFileLevel && parent instanceof PsiFile) return false;
      parent = parent.getParent();
    }
  }

  /**
   * Checks whether one element in the PSI tree is under another in a {@link PsiElement#getContext()} hierarchy.
   *
   * @param ancestor parent candidate. {@code false} will be returned if ancestor is {@code null}.
   * @param element  child candidate
   * @param strict   whether to start search from the element ({@code true}) or from the element's parent ({@code false}).
   * @return {@code true} if the element has ancestor as its parent somewhere in the hierarchy, {@code false} otherwise.
   */
  @Contract("null, _, _ -> false")
  public static boolean isContextAncestor(@Nullable PsiElement ancestor, @NotNull PsiElement element, boolean strict) {
    if (ancestor == null) return false;
    boolean stopAtFileLevel = !(ancestor instanceof PsiFile || ancestor instanceof PsiDirectory);
    PsiElement parent = strict ? element.getContext() : element;
    while (true) {
      if (parent == null) return false;
      if (parent.equals(ancestor)) return true;
      if (stopAtFileLevel && parent instanceof PsiFile) {
        PsiElement context = parent.getContext();
        if (context == null) return false;
      }
      parent = parent.getContext();
    }
  }

  /**
   * @return the common ancestor of all provided {@code elements}, or {@code null} if there is no common ancestor.
   */
  public static @Nullable PsiElement findCommonParent(@NotNull List<? extends @Nullable PsiElement> elements) {
    if (elements.isEmpty()) return null;

    PsiElement toReturn = null;
    for (PsiElement element : elements) {
      if (element == null) continue;
      toReturn = toReturn == null ? element : findCommonParent(toReturn, element);
      if (toReturn == null) return null;
    }
    return toReturn;
  }

  public static @Nullable PsiElement findCommonParent(@Nullable PsiElement @NotNull ... elements) {
    return elements.length == 0 ? null : findCommonParent(Arrays.asList(elements));
  }

  /**
   * @return common ancestor of {@code element1} and {@code element2}, or {@code null} if there is no common ancestor.
   */
  public static @Nullable PsiElement findCommonParent(@NotNull PsiElement element1, @NotNull PsiElement element2) {
    // optimization
    if (element1 == element2) return element1;
    PsiFile file1 = element1.getContainingFile();
    PsiFile file2 = element2.getContainingFile();

    PsiElement topLevel = file1 == file2 ? file1 : null;

    int depth1 = getDepth(element1, topLevel);
    int depth2 = getDepth(element2, topLevel);

    PsiElement parent1 = element1;
    PsiElement parent2 = element2;
    while (depth1 > depth2) {
      parent1 = parent1.getParent();
      depth1--;
    }
    while (depth2 > depth1) {
      parent2 = parent2.getParent();
      depth2--;
    }
    while (parent1 != null && parent2 != null && !parent1.equals(parent2)) {
      parent1 = parent1.getParent();
      parent2 = parent2.getParent();
    }
    return parent1;
  }

  @Contract(pure = true)
  public static int getDepth(@NotNull PsiElement element, @Nullable PsiElement topLevel) {
    int depth = 0;
    PsiElement parent = element;
    while (parent != topLevel && parent != null) {
      depth++;
      parent = parent.getParent();
    }
    return depth;
  }

  public static @Nullable PsiElement findCommonContext(@NotNull Collection<? extends @Nullable PsiElement> elements) {
    if (elements.isEmpty()) return null;
    PsiElement toReturn = null;
    for (PsiElement element : elements) {
      if (element == null) continue;
      toReturn = toReturn == null ? element : findCommonContext(toReturn, element);
      if (toReturn == null) return null;
    }
    return toReturn;
  }

  public static @Nullable PsiElement findCommonContext(@NotNull PsiElement element1, @NotNull PsiElement element2) {
    if (element1 == element2) return element1;  // optimization

    PsiFile containingFile = element1.getContainingFile();
    PsiElement topLevel = containingFile == element2.getContainingFile() ? containingFile : null;

    int depth1 = getContextDepth(element1, topLevel);
    int depth2 = getContextDepth(element2, topLevel);

    PsiElement parent1 = element1;
    PsiElement parent2 = element2;
    while(depth1 > depth2 && parent1 != null) {
      parent1 = parent1.getContext();
      depth1--;
    }
    while(depth2 > depth1 && parent2 != null) {
      parent2 = parent2.getContext();
      depth2--;
    }
    while(parent1 != null && parent2 != null && !parent1.equals(parent2)) {
      parent1 = parent1.getContext();
      parent2 = parent2.getContext();
    }
    return parent1;
  }

  private static int getContextDepth(@NotNull PsiElement element, @Nullable PsiElement topLevel) {
    int depth=0;
    PsiElement parent = element;
    while (parent != topLevel && parent != null) {
      depth++;
      parent = parent.getContext();
    }
    return depth;
  }

  /**
   * Recursive (depth-first) search for the first element of class {@code aClass} among {@code element}'s descendants.
   * <p>
   * The {@code element} itself is not included in the search.
   * @see #findChildOfType(PsiElement, Class, boolean, Class)
   */
  @Contract("null, _ -> null")
  public static @Nullable <T extends PsiElement> T findChildOfType(@Nullable PsiElement element, @NotNull Class<T> aClass) {
    return findChildOfType(element, aClass, true, null);
  }

  /**
   * Recursive (depth-first) search for the first element of class {@code aClass} among {@code element}'s descendants.
   *
   * @param strict if {@code true} then the {@code element} itself is not included in the search
   * @see #findChildOfType(PsiElement, Class, boolean, Class)
   */
  @Contract("null, _, _ -> null")
  public static @Nullable <T extends PsiElement> T findChildOfType(@Nullable PsiElement element, @NotNull Class<T> aClass, boolean strict) {
    return findChildOfType(element, aClass, strict, null);
  }

  /**
   * Recursive (depth-first) search for the first element of a given class {@code aClass} among {@code element}'s descendants.
   *
   * @param element a PSI element to start search from
   * @param strict  if {@code true} then the {@code element} itself is not included in the search
   * @param aClass  element type to search for
   * @param stopAt  element type to abort the search at
   * @param <T>     type to cast the found element to
   * @return first found element, or {@code null} if nothing found
   */
  @Contract("null, _, _, _ -> null")
  public static @Nullable <T extends PsiElement> T findChildOfType(@Nullable PsiElement element,
                                                                   @NotNull Class<T> aClass,
                                                                   boolean strict,
                                                                   @Nullable Class<? extends PsiElement> stopAt) {
    if (element == null) return null;

    FindElement<PsiElement> processor = new PsiElementProcessor.FindElement<PsiElement>() {
      @Override
      public boolean execute(@NotNull PsiElement each) {
        if (strict && each == element) return true;
        if (aClass.isInstance(each)) return setFound(each);
        return stopAt == null || !stopAt.isInstance(each);
      }
    };
    processElements(element, processor);
    return aClass.cast(processor.getFoundElement());
  }

  /**
   * Recursive (depth-first) search for the first element that is an instance of any of {@code classes}.
   *
   * @param element a PSI element to start search from. It is not included in the search.
   * @param classes element types to search for.
   * @see #findChildOfAnyType(PsiElement, boolean, Class[])
   */
  @SafeVarargs
  @Contract("null, _ -> null")
  public static @Nullable <T extends PsiElement> T findChildOfAnyType(@Nullable PsiElement element,
                                                                      @NotNull Class<? extends T> @NotNull ... classes) {
    return findChildOfAnyType(element, true, classes);
  }

  /**
   * Recursive (depth-first) search for the first element that is an instance of any of {@code classes}.
   *
   * @param element a PSI element to start search from.
   * @param strict  if {@code true} then the {@code element} itself is not included in the search.
   * @param classes element types to search for.
   * @param <T>     type to cast the found element to.
   * @return first found element, or {@code null} if nothing found.
   */
  @SafeVarargs
  @Contract("null, _, _ -> null")
  public static @Nullable <T extends PsiElement> T findChildOfAnyType(@Nullable PsiElement element,
                                                                      boolean strict,
                                                                      @NotNull Class<? extends T> @NotNull ... classes) {
    if (element == null) return null;

    FindElement<PsiElement> processor = new FindElement<PsiElement>() {
      @Override
      public boolean execute(@NotNull PsiElement each) {
        if (strict && each == element) return true;
        if (instanceOf(each, classes)) {
          return setFound(each);
        }
        return true;
      }
    };

    processElements(element, processor);
    //noinspection unchecked
    return (T)processor.getFoundElement();
  }

  /**
   * Recursive (depth-first) search for all descendant elements that are an instance of {@code aClass}.
   *
   * @param element a PSI element to start search from. It is not included in the search.
   * @param <T>     type to cast found elements to.
   * @return {@code Collection<T>} of all found elements, or empty {@code List<T>} if nothing found.
   * @see #findChildrenOfAnyType(PsiElement, Class[])
  */
  public static @Unmodifiable @NotNull <T extends PsiElement> Collection<T> findChildrenOfType(@Nullable PsiElement element, @NotNull Class<? extends T> aClass) {
    return findChildrenOfAnyType(element, true, aClass);
  }

  /**
   * Recursive (depth-first) search for all descendant elements that are an instance of any of {@code classes}.
   *
   * @param element a PSI element to start search from. It is not included in the search.
   * @param classes element types to search for.
   * @param <T>     type to cast found elements to.
   * @return {@code Collection<T>} of all found elements, or empty {@code List<T>} if nothing found.
   * @see #findChildrenOfAnyType(PsiElement, boolean, Class[])
  */
  @SafeVarargs
  public static @Unmodifiable @NotNull <T extends PsiElement> Collection<T> findChildrenOfAnyType(@Nullable PsiElement element,
                                                                                    @NotNull Class<? extends T> @NotNull ... classes) {
    return findChildrenOfAnyType(element, true, classes);
  }

  /**
   * Recursive (depth-first) search for all descendant elements that are an instance of any of {@code classes}.
   *
   * @param element a PSI element to start search from.
   * @param strict  if {@code true} then the {@code element} itself is not included in the search.
   * @param classes element types to search for.
   * @param <T>     type to cast found elements to.
   * @return {@code Collection<T>} of all found elements, or empty {@code List<T>} if nothing found.
   */
  @SafeVarargs
  public static @Unmodifiable @NotNull <T extends PsiElement> Collection<T> findChildrenOfAnyType(@Nullable PsiElement element,
                                                                                    boolean strict,
                                                                                    @NotNull Class<? extends T> @NotNull ... classes) {
    if (element == null) return Collections.emptyList();

    CollectElements<PsiElement> processor = new CollectElements<PsiElement>() {
      @Override
      public boolean execute(@NotNull PsiElement each) {
        if (strict && each == element) return true;
        if (instanceOf(each, classes)) {
          return super.execute(each);
        }
        return true;
      }
    };
    processElements(element, processor);
    //noinspection unchecked
    return (Collection<T>)processor.getCollection();
  }

  /**
   * Non-recursive search for the first element of type {@link T} among {@code element}'s children.
   * <p>
   * If stubs are available, consider using {@link #getStubChildOfType(PsiElement, Class)} instead for improved performance.
   * </p>
   *
   * @param element a PSI element to start search from.
   * @param aClass  element type to search for.
   * @param <T>     element type to search for.
   * @return first found element, or {@code null} if nothing found.
   */
  @Contract("null, _ -> null")
  public static @Nullable <T extends PsiElement> T getChildOfType(@Nullable PsiElement element, @NotNull Class<T> aClass) {
    if (element != null) {
      for (PsiElement child = element.getFirstChild(); child != null; child = child.getNextSibling()) {
        if (aClass.isInstance(child)) {
          return aClass.cast(child);
        }
      }
    }

    return null;
  }

  /**
   * @param element the starting element from which to begin the search upwards. It is included in the search.
   * @param condition determines whether an ancestor element satisfies the search criteria.
   * @return the first ancestor of {@code element} that satisfies {@code condition}, or null if no such ancestor exists.
   * @see #findFirstParent(PsiElement, boolean, Condition)
   */
  @Contract("null, _ -> null")
  public static @Nullable PsiElement findFirstParent(@Nullable PsiElement element, @NotNull Condition<? super PsiElement> condition) {
    return findFirstParent(element, false, condition);
  }

  /**
   * @param element the starting element to search from.
   * @param strict  if true, then the {@code element} itself is excluded from the search and search starts from its parent instead.
   * @param condition determines whether an ancestor element satisfies the search criteria.
   * @return the first ancestor of {@code element} that satisfies {@code condition}, or null if no such ancestor exists.
   */
  @Contract("null, _, _ -> null")
  public static @Nullable PsiElement findFirstParent(@Nullable PsiElement element, boolean strict, @NotNull Condition<? super PsiElement> condition) {
    if (strict && element != null) {
      element = element.getParent();
    }
    while (element != null) {
      if (condition.value(element)) {
        return element;
      }
      element = element.getParent();
    }
    return null;
  }

  public static @Nullable PsiElement findFirstContext(@Nullable PsiElement element, boolean strict, @NotNull Condition<? super PsiElement> condition) {
    if (strict && element != null) {
      element = element.getContext();
    }
    while (element != null) {
      if (condition.value(element)) {
        return element;
      }
      element = element.getContext();
    }
    return null;
  }

  public static @NotNull <T extends PsiElement> T getRequiredChildOfType(@NotNull PsiElement element, @NotNull Class<T> aClass) {
    T child = getChildOfType(element, aClass);
    assert child != null : "Missing required child of type " + aClass.getName();
    return child;
  }

  public static int countChildrenOfType(@NotNull PsiElement element, @NotNull Class<? extends PsiElement> clazz) {
    int result = 0;
    for (PsiElement cur = element.getFirstChild(); cur != null; cur = cur.getNextSibling()) {
      if (clazz.isInstance(cur)) {
        result++;
      }
    }
    return result;
  }

  public static <T extends PsiElement> T @Nullable [] getChildrenOfType(@Nullable PsiElement element, @NotNull Class<T> aClass) {
    if (element == null) return null;
    List<T> result = getChildrenOfTypeAsList(element, aClass);
    return result.isEmpty() ? null : ArrayUtil.toObjectArray(result, aClass);
  }

  @SafeVarargs
  public static @Unmodifiable @NotNull <T extends PsiElement> List<T> getChildrenOfAnyType(@Nullable PsiElement element, @NotNull Class<? extends T> @NotNull ... classes) {
    List<T> result = null;
    if (element != null) {
      for (PsiElement child = element.getFirstChild(); child != null; child = child.getNextSibling()) {
        if (instanceOf(child, classes)) {
          if (result == null) result = new SmartList<>();
          //noinspection unchecked
          result.add((T)child);
        }
      }
    }
    return result != null ? result : ContainerUtil.emptyList();
  }

  public static @Unmodifiable @NotNull <T extends PsiElement> List<T> getChildrenOfTypeAsList(@Nullable PsiElement element, @NotNull Class<? extends T> aClass) {
    List<T> result = null;
    if (element != null) {
      for (PsiElement child = element.getFirstChild(); child != null; child = child.getNextSibling()) {
        if (aClass.isInstance(child)) {
          if (result == null) result = new SmartList<>();
          result.add(aClass.cast(child));
        }
      }
    }
    return result != null ? result : Collections.emptyList();
  }

  public static @Unmodifiable @NotNull List<PsiElement> getElementsOfRange(@NotNull PsiElement start, @NotNull PsiElement end) {
    List<PsiElement> result = new ArrayList<>();
    for (PsiElement e = start; e != end; e = e.getNextSibling()) {
      if (e == null) throw new IllegalArgumentException("Invalid range: " + start + ".." + end);
      result.add(e);
    }
    result.add(end);
    return result;
  }

  /**
   * @see #getChildOfType(PsiElement, Class)
   */
  @Contract("null, _ -> null")
  public static @Nullable <T extends PsiElement> T getStubChildOfType(@Nullable PsiElement element, @NotNull Class<T> aClass) {
    if (element == null) return null;

    StubElement<?> stub = element instanceof StubBasedPsiElement ? ((StubBasedPsiElement<?>)element).getStub() : null;
    if (stub == null) return getChildOfType(element, aClass);

    for (StubElement<?> childStub : stub.getChildrenStubs()) {
      PsiElement child = childStub.getPsi();
      if (aClass.isInstance(child)) {
        return aClass.cast(child);
      }
    }
    return null;
  }

  public static @Unmodifiable @NotNull <T extends PsiElement> List<T> getStubChildrenOfTypeAsList(@Nullable PsiElement element, @NotNull Class<? extends T> aClass) {
    if (element == null) return Collections.emptyList();

    StubElement<?> stub = element instanceof StubBasedPsiElement ? ((StubBasedPsiElement<?>)element).getStub() : null;
    if (stub == null) return getChildrenOfTypeAsList(element, aClass);

    List<T> result = new SmartList<>();
    for (StubElement<?> childStub : stub.getChildrenStubs()) {
      PsiElement child = childStub.getPsi();
      if (aClass.isInstance(child)) {
        result.add(aClass.cast(child));
      }
    }
    return result;
  }

  /**
   * Returns true if the given {@code object} is an instance of any of the specified {@code classes}.
   */
  public static boolean instanceOf(Object object, @NotNull Class<?> @NotNull ... classes) {
    if (object != null) {
      for (Class<?> c : classes) {
        if (c.isInstance(object)) return true;
      }
    }
    return false;
  }

  /**
   * Returns a direct child of the specified element having any of the specified classes.
   *
   * @param element the element to get the child for.
   * @param classes the array of classes.
   * @return the element, or {@code null} if none was found.
   */
  @SafeVarargs
  @Contract(value = "null, _ -> null", pure = true)
  public static @Nullable <T extends PsiElement> T getChildOfAnyType(@Nullable PsiElement element, Class<? extends T> @NotNull ... classes) {
    if (element != null) {
      for (PsiElement child = element.getFirstChild(); child != null; child = child.getNextSibling()) {
        for (Class<? extends T> aClass : classes) {
          if (aClass.isInstance(child)) {
            return aClass.cast(child);
          }
        }
      }
    }
    return null;
  }

  @Contract(value = "null, _ -> null", pure = true)
  public static @Nullable <T extends PsiElement> T getNextSiblingOfType(@Nullable PsiElement sibling, @NotNull Class<T> aClass) {
    if (sibling != null) {
      for (PsiElement nextSibling = sibling.getNextSibling(); nextSibling != null; nextSibling = nextSibling.getNextSibling()) {
        if (aClass.isInstance(nextSibling)) {
          return aClass.cast(nextSibling);
        }
      }
    }
    return null;
  }

  @Contract(value = "null, _ -> null", pure = true)
  public static @Nullable <T extends PsiElement> T getPrevSiblingOfType(@Nullable PsiElement sibling, @NotNull Class<T> aClass) {
    if (sibling != null) {
      for (PsiElement prevSibling = sibling.getPrevSibling(); prevSibling != null; prevSibling = prevSibling.getPrevSibling()) {
        if (aClass.isInstance(prevSibling)) {
          return aClass.cast(prevSibling);
        }
      }
    }
    return null;
  }

  @Contract(value = "null, _ -> null", pure = true)
  public static @Nullable <T extends PsiElement> T getTopmostParentOfType(@Nullable PsiElement element, @NotNull Class<T> aClass) {
    T answer = getParentOfType(element, aClass);
    do {
      T next = getParentOfType(answer, aClass);
      if (next == null) break;
      answer = next;
    }
    while (true);
    return answer;
  }

  @Contract(value = "null, _ -> null", pure = true)
  public static @Nullable <T extends PsiElement> T getParentOfType(@Nullable PsiElement element, @NotNull Class<T> aClass) {
    return getParentOfType(element, aClass, true);
  }

  @Contract("null -> null")
  public static @Nullable PsiElement getStubOrPsiParent(@Nullable PsiElement element) {
    if (element instanceof StubBasedPsiElement) {
      StubElement<?> stub = ((StubBasedPsiElement<?>)element).getStub();
      if (stub != null) {
        StubElement<?> parentStub = stub.getParentStub();
        return parentStub != null ? parentStub.getPsi() : null;
      }
    }
    return element != null ? element.getParent() : null;
  }

  @Contract("null, _ -> null")
  public static @Nullable <E extends PsiElement> E getStubOrPsiParentOfType(@Nullable PsiElement element, @NotNull Class<E> parentClass) {
    if (element instanceof StubBasedPsiElement) {
      StubElement<?> stub = ((StubBasedPsiElement<?>)element).getStub();
      if (stub != null) {
        return stub.getParentStubOfType(parentClass);
      }
    }
    return getParentOfType(element, parentClass);
  }

  @SafeVarargs
  @Contract("null, _, _, _ -> null")
  public static @Nullable <T extends PsiElement> T getContextOfType(@Nullable PsiElement element,
                                                                    @NotNull Class<T> aClass,
                                                                    boolean strict,
                                                                    @NotNull Class<? extends PsiElement> @NotNull ... stopAt) {
    if (element == null) return null;
    if (strict) {
      element = element.getContext();
    }
    while (element != null && !aClass.isInstance(element)) {
      if (instanceOf(element, stopAt)) return null;
      element = element.getContext();
    }
    return aClass.cast(element);
  }

  @Contract("null, _, _ -> null")
  public static @Nullable <T extends PsiElement> T getContextOfType(@Nullable PsiElement element,
                                                                    @NotNull Class<? extends T> aClass,
                                                                    boolean strict) {
    return getContextOfType(element, strict, aClass);
  }

  @SafeVarargs
  public static @Nullable <T extends PsiElement> T getContextOfType(@Nullable PsiElement element, @NotNull Class<? extends T> @NotNull ... classes) {
    return getContextOfType(element, true, classes);
  }

  @SafeVarargs
  @Contract("null, _, _ -> null")
  public static @Nullable <T extends PsiElement> T getContextOfType(@Nullable PsiElement element,
                                                                    boolean strict,
                                                                    @NotNull Class<? extends T> @NotNull ... classes) {
    if (element == null) return null;
    if (strict) {
      element = element.getContext();
    }
    while (element != null && !instanceOf(element, classes)) {
      element = element.getContext();
    }
    //noinspection unchecked
    return (T)element;
  }

  @Contract("null, _, _ -> null")
  public static @Nullable <T extends PsiElement> T getParentOfType(@Nullable PsiElement element, @NotNull Class<T> aClass, boolean strict) {
    return getParentOfType(element, aClass, strict, -1);
  }

  @Contract("null, _, _, _ -> null")
  public static <T extends PsiElement> T getParentOfType(@Nullable PsiElement element, @NotNull Class<T> aClass, boolean strict, int minStartOffset) {
    if (element == null) return null;

    if (strict) {
      if (element instanceof PsiFile) {
        return null;
      }
      element = element.getParent();
    }

    while (element != null && (minStartOffset == -1 || element.getNode().getStartOffset() >= minStartOffset)) {
      if (aClass.isInstance(element)) {
        return aClass.cast(element);
      }
      if (element instanceof PsiFile) {
        return null;
      }
      element = element.getParent();
    }

    return null;
  }

  @SafeVarargs
  @Contract("null, _, _, _ -> null")
  public static @Nullable <T extends PsiElement> T getParentOfType(@Nullable PsiElement element,
                                                                   @NotNull Class<T> aClass,
                                                                   boolean strict,
                                                                   @NotNull Class<? extends PsiElement> @NotNull ... stopAt) {
    if (element == null) return null;
    if (strict) {
      if (element instanceof PsiFile) return null;
      element = element.getParent();
    }

    while (element != null && !aClass.isInstance(element)) {
      if (instanceOf(element, stopAt)) return null;
      if (element instanceof PsiFile) return null;
      element = element.getParent();
    }

    return aClass.cast(element);
  }

  public static @Unmodifiable @NotNull <T extends PsiElement> List<T> collectParents(@NotNull PsiElement element,
                                                                                     @NotNull Class<? extends T> parent,
                                                                                     boolean includeMyself,
                                                                                     @NotNull Predicate<? super PsiElement> stopCondition) {
    if (!includeMyself) {
      element = element.getParent();
    }
    List<T> parents = new SmartList<>();
    while (element != null) {
      if (stopCondition.test(element)) break;
      if (parent.isInstance(element)) {
        parents.add(parent.cast(element));
      }
      element = element.getParent();
    }
    return parents;
  }

  public static @Nullable PsiElement findSiblingForward(@NotNull PsiElement element,
                                                        @NotNull IElementType elementType,
                                                        @Nullable Consumer<? super PsiElement> consumer) {
    return findSiblingForward(element, elementType, true, consumer);
  }

  public static @Nullable PsiElement findSiblingForward(@NotNull PsiElement element,
                                                        @NotNull IElementType elementType,
                                                        boolean strict,
                                                        @Nullable Consumer<? super PsiElement> consumer) {
    for (PsiElement e = strict ? element.getNextSibling() : element; e != null; e = e.getNextSibling()) {
      if (elementType.equals(e.getNode().getElementType())) {
        return e;
      }
      if (consumer != null) consumer.consume(e);
    }
    return null;
  }

  public static @Nullable PsiElement findSiblingBackward(@NotNull PsiElement element,
                                                         @NotNull IElementType elementType,
                                                         @Nullable Consumer<? super PsiElement> consumer) {
    return findSiblingBackward(element, elementType, true, consumer);
  }

  public static @Nullable PsiElement findSiblingBackward(@NotNull PsiElement element,
                                                         @NotNull IElementType elementType,
                                                         boolean strict,
                                                         @Nullable Consumer<? super PsiElement> consumer) {
    for (PsiElement e = strict ? element.getPrevSibling() : element; e != null; e = e.getPrevSibling()) {
      if (elementType.equals(e.getNode().getElementType())) {
        return e;
      }
      if (consumer != null) consumer.consume(e);
    }
    return null;
  }

  /**
   * Finds the closest next sibling, skipping elements of supplied types.
   * @param element element to start search from
   * @param elementClasses element types to skip
   * @return the found next sibling; null if not found.
   */
  @SafeVarargs
  @Contract("null, _ -> null")
  public static @Nullable PsiElement skipSiblingsForward(@Nullable PsiElement element, @NotNull Class<? extends PsiElement> @NotNull ... elementClasses) {
    if (element != null) {
      for (PsiElement e = element.getNextSibling(); e != null; e = e.getNextSibling()) {
        if (!instanceOf(e, elementClasses)) {
          return e;
        }
      }
    }
    return null;
  }

  /**
   * Finds the closest next sibling, ignoring {@linkplain PsiWhiteSpace white spaces}.
   * @param element element to start search from
   * @return the found next sibling; null if not found.
   */
  @Contract("null -> null")
  public static @Nullable PsiElement skipWhitespacesForward(@Nullable PsiElement element) {
    return skipSiblingsForward(element, WS);
  }

  /**
   * Finds the closest next sibling, ignoring {@linkplain PsiWhiteSpace white spaces} and {@linkplain PsiComment comments}.
   * @param element element to start search from
   * @return the found next sibling; null if not found.
   */
  @Contract("null -> null")
  public static @Nullable PsiElement skipWhitespacesAndCommentsForward(@Nullable PsiElement element) {
    return skipSiblingsForward(element, WS_COMMENTS);
  }

  /**
   * Finds the closest previous sibling, skipping elements of supplied types.
   * @param element element to start search from
   * @param elementClasses element types to skip
   * @return found previous sibling; null if not found.
   */
  @SafeVarargs
  @Contract("null, _ -> null")
  public static @Nullable PsiElement skipSiblingsBackward(@Nullable PsiElement element, @NotNull Class<? extends PsiElement> @NotNull ... elementClasses) {
    if (element != null) {
      for (PsiElement e = element.getPrevSibling(); e != null; e = e.getPrevSibling()) {
        if (!instanceOf(e, elementClasses)) {
          return e;
        }
      }
    }
    return null;
  }

  /**
   * Finds the closest previous sibling, ignoring {@linkplain PsiWhiteSpace white spaces}.
   * @param element element to start search from
   * @return found previous sibling; null if not found.
   */
  @Contract("null -> null")
  public static @Nullable PsiElement skipWhitespacesBackward(@Nullable PsiElement element) {
    return skipSiblingsBackward(element, WS);
  }

  /**
   * Finds the closest previous sibling, ignoring {@linkplain PsiWhiteSpace white spaces} and {@linkplain PsiComment comments}.
   * @param element element to start search from
   * @return found previous sibling; null if not found.
   */
  @Contract("null -> null")
  public static @Nullable PsiElement skipWhitespacesAndCommentsBackward(@Nullable PsiElement element) {
    return skipSiblingsBackward(element, WS_COMMENTS);
  }

  /**
   * Finds the closest parent that is not an instance of one of the supplied classes.
   *
   * @param element element to start traversal from
   * @param parentClasses element types to skip
   * @return the found parent; null if not found.
   */
  @SafeVarargs
  @Contract("null, _ -> null")
  public static @Nullable PsiElement skipParentsOfType(@Nullable PsiElement element, @NotNull Class<? extends PsiElement> @NotNull ... parentClasses) {
    if (element != null) {
      for (PsiElement e = element.getParent(); e != null; e = e.getParent()) {
        if (!instanceOf(e, parentClasses)) {
          return e;
        }
      }
    }
    return null;
  }

  /**
   * Finds the closest element, skipping elements matching the given predicate.
   *
   * @param element   element to start search from
   * @param next      a function to get the next element
   * @param condition a predicate to test whether the element should be skipped
   */
  @Contract("null, _, _ -> null")
  public static @Nullable PsiElement skipMatching(@Nullable PsiElement element,
                                                  @NotNull Function<? super @NotNull PsiElement, ? extends @Nullable PsiElement> next,
                                                  @NotNull Predicate<? super @NotNull PsiElement> condition) {
    if (element != null) {
      for (PsiElement e = next.apply(element); e != null; e = next.apply(e)) {
        if (!condition.test(e)) {
          return e;
        }
      }
    }
    return null;
  }

  /**
   * Finds the closest ancestor that is an instance of one of the supplied {@code classes}. Traversal stops at {@link PsiFile} level.
   *
   * @param element element to start traversal from
   * @param classes wanted element types
   * @param <T> common supertype for all wanted types
   * @return the found parent; null if not found.
   */
  @SafeVarargs
  @Contract("null, _ -> null")
  public static @Nullable <T extends PsiElement> T getParentOfType(@Nullable PsiElement element,
                                                                   @NotNull Class<? extends T> @NotNull ... classes) {
    return getParentOfType(element, true, classes);
  }

  @SafeVarargs
  @Contract("null, _ -> null")
  public static @Nullable <T extends PsiElement> T getNonStrictParentOfType(@Nullable PsiElement element,
                                                                            @NotNull Class<? extends T> @NotNull ... classes) {
    return getParentOfType(element, false, classes);
  }

  @SafeVarargs
  @Contract("null, _, _ -> null")
  public static @Nullable <T extends PsiElement> T getParentOfType(@Nullable PsiElement element, boolean strict,
                                                                   @NotNull Class<? extends T> @NotNull ... classes) {
    PsiElement run;
    if (strict) {
      if (element == null || element instanceof PsiFile) return null;
      run = element.getParent();
    } else {
      run = element;
    }

    while (run != null) {
      if (instanceOf(run, classes)) {
        //noinspection unchecked
        return (T)run;
      }
      if (run instanceof PsiFile) break;
      run = run.getParent();
    }

    return null;
  }

  @Contract(pure=true)
  public static PsiElement @NotNull [] collectElements(@Nullable PsiElement element, @NotNull PsiElementFilter filter) {
    List<PsiElement> result = new ArrayList<>();
    processElements(element, e -> {
      if (filter.isAccepted(e)) result.add(e);
      return true;
    });
    return result.toArray(PsiElement.EMPTY_ARRAY);
  }

  @SafeVarargs
  @Contract(pure = true)
  public static @Unmodifiable @NotNull <T extends PsiElement> Collection<T> collectElementsOfType(@Nullable PsiElement element,
                                                                                                  @NotNull Class<T> @NotNull ... classes) {
    return findChildrenOfAnyType(element, false, classes);
  }

  /**
   * Recursively process children elements that are instances of the given class. The root element is processed as well.
   *
   * @param element root element to process.
   * @param elementClass the class of elements to process. All other elements are skipped.
   * @param processor processor to consume elements
   * @param <T> type of elements to process
   * @return {@code true} if processing was not canceled ({@code Processor.execute()} method returned {@code true} for all elements).
   */
  @Contract("null, _, _ -> true")
  public static <T extends PsiElement> boolean processElements(@Nullable PsiElement element,
                                                               @NotNull Class<T> elementClass,
                                                               @NotNull PsiElementProcessor<? super T> processor) {
    return element == null || processElements(element, e -> {
      T t = ObjectUtils.tryCast(e, elementClass);
      return t == null || processor.execute(t);
    });
  }

  /**
   * Recursively process children elements, including the {@code root}.
   *
   * @param root root element to process
   * @param processor processor to consume elements
   * @return {@code true} if processing was not canceled ({@code Processor.execute()} method returned {@code true} for all elements).
   * @see PsiElementProcessor#execute
   */
  @Contract("null, _ -> true")
  public static boolean processElements(@Nullable PsiElement root, @NotNull PsiElementProcessor<? super PsiElement> processor) {
    if (root == null) {
      return true;
    }
    // walking visitors are not used here because the children/parent relationship is broken in DummyHolders
    Deque<PsiElement> stack = new ArrayDeque<>();
    stack.push(root);
    boolean ret = true;
    while (!stack.isEmpty()) {
      PsiElement element = stack.pop();
      if (!processor.execute(element)) {
        ret = false;
        break;
      }
      PsiElement child = element.getLastChild();
      while (child != null) {
        stack.push(child);
        child = child.getPrevSibling();
      }
    }
    return ret;
  }

  public static boolean processElements(@NotNull PsiElementProcessor<? super PsiElement> processor, PsiElement @Nullable ... elements) {
    if (elements != null) {
      for (PsiElement element : elements) {
        if (!processElements(element, processor)) {
          return false;
        }
      }
    }
    return true;
  }

  public static void mark(@NotNull PsiElement element, @NotNull Object marker) {
    element.getNode().putCopyableUserData(MARKER, marker);
  }

  public static Object mark(@NotNull PsiElement element) {
    Object mark = new Object();
    mark(element, mark);
    return mark;
  }

  public static @Nullable PsiElement releaseMark(@NotNull PsiElement root, @NotNull Object marker) {
    ASTNode node = root.getNode();
    if (marker.equals(node.getCopyableUserData(MARKER))) {
      node.putCopyableUserData(MARKER, null);
      return root;
    }
    else {
      PsiElement child = root.getFirstChild();
      while (child != null) {
        PsiElement result = releaseMark(child, marker);
        if (result != null) return result;
        child = child.getNextSibling();
      }
      return null;
    }
  }

  @Contract(pure = true)
  public static @Nullable <T extends PsiElement> T findElementOfClassAtOffset(@NotNull PsiFile file,
                                                                              int offset,
                                                                              @NotNull Class<T> clazz,
                                                                              boolean strictStart) {
    T result = null;
    for (PsiElement root : file.getViewProvider().getAllFiles()) {
      PsiElement elementAt = root.findElementAt(offset);
      if (elementAt != null) {
        T parent = getParentOfType(elementAt, clazz, strictStart);
        if (parent != null) {
          TextRange range = parent.getTextRange();
          if (!strictStart || range.getStartOffset() == offset) {
            if (result == null || result.getTextRange().getEndOffset() > range.getEndOffset()) {
              result = parent;
            }
          }
        }
      }
    }
    return result;
  }

  @SafeVarargs
  @Contract(pure = true)
  public static @Nullable <T extends PsiElement> T findElementOfClassAtOffsetWithStopSet(@NotNull PsiFile file,
                                                                                         int offset,
                                                                                         @NotNull Class<T> clazz,
                                                                                         boolean strictStart,
                                                                                         @NotNull Class<? extends PsiElement> @NotNull ... stopAt) {
    T result = null;
    for (PsiElement root : file.getViewProvider().getAllFiles()) {
      PsiElement elementAt = root.findElementAt(offset);
      if (elementAt != null) {
        T parent = getParentOfType(elementAt, clazz, strictStart, stopAt);
        if (parent != null) {
          TextRange range = parent.getTextRange();
          if (!strictStart || range.getStartOffset() == offset) {
            if (result == null || result.getTextRange().getEndOffset() > range.getEndOffset()) {
              result = parent;
            }
          }
        }
      }
    }

    return result;
  }

  /**
   * @return maximal element of specified Class starting at {@code startOffset} exactly and ending not farther than {@code endOffset}.
   */
  @Contract(pure = true)
  public static @Nullable <T extends PsiElement> T findElementOfClassAtRange(@NotNull PsiFile file,
                                                                             int startOffset,
                                                                             int endOffset,
                                                                             @NotNull Class<T> clazz) {
    T result = null;

    FileViewProvider viewProvider = file.getViewProvider();
    for (Language lang : viewProvider.getLanguages()) {
      PsiElement elementAt = viewProvider.findElementAt(startOffset, lang);
      T run = getParentOfType(elementAt, clazz, false);
      T prev = run;
      while (run != null && run.getTextRange().getStartOffset() == startOffset && run.getTextRange().getEndOffset() <= endOffset) {
        prev = run;
        run = getParentOfType(run, clazz);
      }

      if (prev == null) continue;
      int elementStartOffset = prev.getTextRange().getStartOffset();
      int elementEndOffset = prev.getTextRange().getEndOffset();
      if (elementStartOffset != startOffset || elementEndOffset > endOffset) continue;

      if (result == null || result.getTextRange().getEndOffset() < elementEndOffset) {
        result = prev;
      }
    }

    return result;
  }

  /**
   * @param element element to start the search from
   * @return the first leaf descendant of the element (first child of first child and so on until the leaf element is found).
   * May return the input element if it has no children.
   */
  public static @NotNull PsiElement getDeepestFirst(@NotNull PsiElement element) {
    PsiElement result = element;
    do {
      PsiElement firstChild = result.getFirstChild();
      if (firstChild == null) return result;
      result = firstChild;
    }
    while (true);
  }

  /**
   * @param element element to start the search from
   * @return the last leaf descendant of the element (last child of last child and so on until the leaf element is found).
   * May return the input element if it has no children.
   */
  public static @NotNull PsiElement getDeepestLast(@NotNull PsiElement element) {
    PsiElement result = element;
    do {
      PsiElement lastChild = result.getLastChild();
      if (lastChild == null) return result;
      result = lastChild;
    }
    while (true);
  }

  /**
   * @param current element to start the search from
   * @return the previous leaf element within the current file; null if there's no such an element
   */
  public static @Nullable PsiElement prevLeaf(@NotNull PsiElement current) {
    while (true) {
      if (current instanceof PsiFileSystemItem) return null;
      PsiElement prevSibling = current.getPrevSibling();
      if (prevSibling != null) return lastChild(prevSibling);
      PsiElement parent = current.getParent();
      if (parent == null || parent instanceof PsiFile) return null;
      current = parent;
    }
  }

  /**
   * @param current element to start the search from
   * @return the next leaf element within the current file; null if there's no such an element
   */
  public static @Nullable PsiElement nextLeaf(@NotNull PsiElement current) {
    while (true) {
      if (current instanceof PsiFileSystemItem) return null;
      PsiElement nextSibling = current.getNextSibling();
      if (nextSibling != null) return firstChild(nextSibling);
      PsiElement parent = current.getParent();
      if (parent == null || parent instanceof PsiFile) return null;
      current = parent;
    }
  }

  /**
   * The same as {@link #getDeepestLast(PsiElement)}
   */
  public static @NotNull PsiElement lastChild(@NotNull PsiElement element) {
    return getDeepestLast(element);
  }

  /**
   * The same as {@link #getDeepestFirst(PsiElement)}
   */
  public static @NotNull PsiElement firstChild(@NotNull PsiElement element) {
    return getDeepestFirst(element);
  }

  /**
   * @param element element to start the search from
   * @param skipEmptyElements if true, elements whose text is empty are skipped.
   * @return the previous leaf element within the current file (skipping empty elements if skipEmptyElements is true);
   * null if there's no such an element
   * @see #prevLeaf(PsiElement)
   */
  public static @Nullable PsiElement prevLeaf(@NotNull PsiElement element, boolean skipEmptyElements) {
    PsiElement prevLeaf = prevLeaf(element);
    while (skipEmptyElements && prevLeaf != null && prevLeaf.getTextLength() == 0) prevLeaf = prevLeaf(prevLeaf);
    return prevLeaf;
  }

  /**
   * @param element element to start the search from
   * @return the previous leaf element within the current file that is not empty and not white-space only;
   * null if there's no such an element
   */
  public static @Nullable PsiElement prevVisibleLeaf(@NotNull PsiElement element) {
    PsiElement prevLeaf = prevLeaf(element, true);
    while (prevLeaf != null && StringUtil.isEmptyOrSpaces(prevLeaf.getText())) prevLeaf = prevLeaf(prevLeaf, true);
    return prevLeaf;
  }

  /**
   * @param element element to start the search from
   * @return the next leaf element within the current file that is not empty and not white-space only;
   * null if there's no such an element
   */
  public static @Nullable PsiElement nextVisibleLeaf(@NotNull PsiElement element) {
    PsiElement nextLeaf = nextLeaf(element, true);
    while (nextLeaf != null && StringUtil.isEmptyOrSpaces(nextLeaf.getText())) nextLeaf = nextLeaf(nextLeaf, true);
    return nextLeaf;
  }

  /**
   * @return closest leaf (not necessarily a sibling) before the given element
   * which has a non-empty range and is neither a whitespace nor a comment
   */
  public static @Nullable PsiElement prevCodeLeaf(@NotNull PsiElement element) {
    PsiElement prevLeaf = prevLeaf(element, true);
    while (prevLeaf != null && isNonCodeLeaf(prevLeaf)) prevLeaf = prevLeaf(prevLeaf, true);
    return prevLeaf;
  }

  /**
   * @return closest leaf (not necessarily a sibling) after the given element
   * which has a non-empty range and is neither a whitespace nor a comment
   */
  public static @Nullable PsiElement nextCodeLeaf(@NotNull PsiElement element) {
    PsiElement nextLeaf = nextLeaf(element, true);
    while (nextLeaf != null && isNonCodeLeaf(nextLeaf)) nextLeaf = nextLeaf(nextLeaf, true);
    return nextLeaf;
  }

  private static boolean isNonCodeLeaf(PsiElement leaf) {
    return StringUtil.isEmptyOrSpaces(leaf.getText()) || getNonStrictParentOfType(leaf, PsiComment.class) != null;
  }

  /**
   * @param element element to start the search from
   * @param skipEmptyElements if true, elements whose text is empty are skipped.
   * @return the next leaf element within the current file (skipping empty elements if skipEmptyElements is true);
   * null if there's no such an element
   * @see #nextLeaf(PsiElement)
   */
  public static @Nullable PsiElement nextLeaf(@NotNull PsiElement element, boolean skipEmptyElements) {
    PsiElement nextLeaf = nextLeaf(element);
    while (skipEmptyElements && nextLeaf != null && nextLeaf.getTextLength() == 0) nextLeaf = nextLeaf(nextLeaf);
    return nextLeaf;
  }

  /**
   * @param element element to search in
   * @return true if it contains at least one {@link PsiErrorElement} descendant or is {@link PsiErrorElement} itself.
   */
  public static boolean hasErrorElements(@NotNull PsiElement element) {
    Ref<Boolean> result = new Ref<>(false);
    PsiWalkingState.processAll(element, el -> {
      if (el instanceof PsiErrorElement) {
        result.set(true);
        return false;
      } else return true;
    });
    return result.get();
  }

  public static @NotNull PsiElement @NotNull [] filterAncestors(@NotNull PsiElement @NotNull [] elements) {
    List<PsiElement> filteredElements = new ArrayList<>();
    ContainerUtil.addAll(filteredElements, elements);

    int previousSize;
    do {
      previousSize = filteredElements.size();
      outer:
      for (int i = 0; i < filteredElements.size(); i++) {
        PsiElement element = filteredElements.get(i);
        for (int j = 0; j < filteredElements.size(); j++) {
          PsiElement element2 = filteredElements.get(j);
          if (i == j) continue;
          if (isAncestor(element, element2, false)) {
            filteredElements.remove(element2);
            break outer;
          }
        }
      }
    }
    while (filteredElements.size() != previousSize);
    return PsiUtilCore.toPsiElementArray(filteredElements);
  }

  public static boolean treeWalkUp(@NotNull PsiScopeProcessor processor,
                                   @NotNull PsiElement entrance,
                                   @Nullable PsiElement maxScope,
                                   @NotNull ResolveState state) {
    PsiElement prevParent = entrance;
    PsiElement scope = entrance;

    while (scope != null) {
      if (!scope.processDeclarations(processor, state, prevParent, entrance)) return false;

      if (scope == maxScope) break;
      prevParent = scope;
      scope = prevParent.getContext();
    }

    return true;
  }

  public static boolean treeWalkUp(@NotNull PsiElement entrance,
                                   @Nullable PsiElement maxScope,
                                   @NotNull PairProcessor<? super PsiElement, ? super PsiElement> eachScopeAndLastParent) {
    PsiElement prevParent = null;
    PsiElement scope = entrance;

    while (scope != null) {
      if (!eachScopeAndLastParent.process(scope, prevParent)) return false;

      if (scope == maxScope) break;
      prevParent = scope;
      scope = prevParent.getContext();
    }

    return true;
  }

  public static @NotNull PsiElement findPrevParent(@NotNull PsiElement ancestor, @NotNull PsiElement descendant) {
    PsiElement cur = descendant;
    while (cur != null) {
      PsiElement parent = cur.getParent();
      if (parent == ancestor) {
        return cur;
      }
      cur = parent;
    }
    throw new AssertionError(descendant + " is not a descendant of " + ancestor);
  }

  public static @Unmodifiable @NotNull List<PsiElement> getInjectedElements(@NotNull OuterLanguageElement outerLanguageElement) {
    PsiElement psi = outerLanguageElement.getContainingFile().getViewProvider().getPsi(outerLanguageElement.getLanguage());
    TextRange injectionRange = outerLanguageElement.getTextRange();
    List<PsiElement> res = new ArrayList<>();

    assert psi != null : outerLanguageElement;
    for (PsiElement element = psi.findElementAt(injectionRange.getStartOffset());
         element != null && injectionRange.intersectsStrict(element.getTextRange());
         element = element.getNextSibling()) {
      res.add(element);
    }

    return res;
  }

  /**
   * @param psiElement element to start the search from
   * @return the first leaf descendant that is not empty or white-space only.
   * Returns psiElement itself if it's visible and has no children.
   * Returns the next visible leaf if the whole input psiElement is white-space only.
   * Returns null if the whole input psiElement is white-space only and there's no next visible leaf.
   */
  public static @Nullable PsiElement getDeepestVisibleFirst(@NotNull PsiElement psiElement) {
    PsiElement first = getDeepestFirst(psiElement);
    if (StringUtil.isEmptyOrSpaces(first.getText())) {
      first = nextVisibleLeaf(first);
    }
    return first;
  }

  /**
   * @param psiElement element to start the search from
   * @return the last leaf descendant that is not empty or white-space only.
   * Returns psiElement itself if it's visible and has no children.
   * Returns the previous visible leaf if the whole input psiElement is white-space only.
   * Returns null if the whole input psiElement is white-space only and there's no previous visible leaf.
   */
  public static @Nullable PsiElement getDeepestVisibleLast(@NotNull PsiElement psiElement) {
    PsiElement last = getDeepestLast(psiElement);
    if (StringUtil.isEmptyOrSpaces(last.getText())) {
      last = prevVisibleLeaf(last);
    }
    return last;
  }

  /**
   * Returns the same element in the file copy.
   *
   * @param element an element to find
   * @param copy file that must be a copy of {@code element.getContainingFile()}
   * @return found element; null if input element is null
   * @throws IllegalStateException if it's detected that the supplied file is not an exact copy of the original file.
   * The exception is thrown on a best-effort basis, so you cannot rely on it.
   */
  @Contract("null, _ -> null; !null, _ -> !null")
  public static <T extends PsiElement> T findSameElementInCopy(@Nullable T element, @NotNull PsiFile copy) throws IllegalStateException {
    if (element == null) return null;
    if (element instanceof SyntheticElement) {
      //noinspection unchecked
      return (T)((SyntheticElement)element).findSameElementInCopy(copy);
    }
    IntArrayList offsets = new IntArrayList();
    PsiElement cur = element;
    while (!cur.getClass().equals(copy.getClass())) {
      int pos = 0;
      for (PsiElement sibling = cur.getPrevSibling(); sibling != null; sibling = sibling.getPrevSibling()) {
        pos++;
      }
      offsets.add(pos);
      cur = cur.getParent();
      if (cur == null) {
        throw new IllegalStateException("Cannot find parent file; element class: " + element.getClass());
      }
    }

    cur = copy;
    for (int level = offsets.size() - 1; level >= 0; level--) {
      int pos = offsets.get(level);
      cur = cur.getFirstChild();
      if (cur == null) {
        throw new IllegalStateException("File structure differs: no child");
      }
      for (int i = 0; i < pos; i++) {
        cur = cur.getNextSibling();
        if (cur == null) {
          throw new IllegalStateException("File structure differs: number of siblings is less than " + pos);
        }
      }
    }
    if (!cur.getClass().equals(element.getClass())) {
      throw new IllegalStateException("File structure differs: " + cur.getClass() + " != " + element.getClass());
    }
    //noinspection unchecked
    return (T)cur;
  }
}
