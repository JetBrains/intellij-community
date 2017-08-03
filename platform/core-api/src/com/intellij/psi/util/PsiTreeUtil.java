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
package com.intellij.psi.util;

import com.intellij.lang.Language;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.search.PsiElementProcessor;
import com.intellij.psi.search.PsiElementProcessor.CollectElements;
import com.intellij.psi.search.PsiElementProcessor.CollectFilteredElements;
import com.intellij.psi.search.PsiElementProcessor.FindElement;
import com.intellij.psi.stubs.StubBase;
import com.intellij.psi.stubs.StubElement;
import com.intellij.psi.templateLanguages.OuterLanguageElement;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Consumer;
import com.intellij.util.PairProcessor;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static com.intellij.psi.SyntaxTraverser.psiTraverser;

public class PsiTreeUtil {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.util.PsiTreeUtil");

  private static final Key<Object> MARKER = Key.create("PsiTreeUtil.copyElements.MARKER");
  private static final Class[] WS = {PsiWhiteSpace.class};
  private static final Class[] WS_COMMENTS = {PsiWhiteSpace.class, PsiComment.class};

  /**
   * Checks whether one element in the psi tree is under another.
   *
   * @param ancestor parent candidate. {@code false} will be returned if ancestor is null.
   * @param element  child candidate
   * @param strict   whether return true if ancestor and parent are the same.
   * @return true if element has ancestor as its parent somewhere in the hierarchy and false otherwise.
   */
  @Contract("null, _, _ -> false")
  public static boolean isAncestor(@Nullable PsiElement ancestor, @NotNull PsiElement element, boolean strict) {
    if (ancestor == null) return false;
    // fast path to avoid loading tree
    if (ancestor instanceof StubBasedPsiElement && ((StubBasedPsiElement)ancestor).getStub() != null ||
        element instanceof StubBasedPsiElement && ((StubBasedPsiElement)element).getStub() != null) {
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
   * Checks whether one element in the psi tree is under another in {@link PsiElement#getContext()}  hierarchy.
   *
   * @param ancestor parent candidate. {@code false} will be returned if ancestor is null.
   * @param element  child candidate
   * @param strict   whether return true if ancestor and parent are the same.
   * @return true if element has ancestor as its parent somewhere in the hierarchy and false otherwise.
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
        final PsiElement context = parent.getContext();
        if (context == null) return false;
      }
      parent = parent.getContext();
    }
  }

  @Nullable
  @SuppressWarnings("Duplicates")
  public static PsiElement findCommonParent(@NotNull List<? extends PsiElement> elements) {
    if (elements.isEmpty()) return null;

    PsiElement toReturn = null;
    for (PsiElement element : elements) {
      if (element == null) continue;
      toReturn = toReturn == null ? element : findCommonParent(toReturn, element);
      if (toReturn == null) return null;
    }
    return toReturn;
  }

  @Nullable
  @SuppressWarnings("Duplicates")
  public static PsiElement findCommonParent(@NotNull PsiElement... elements) {
    if (elements.length == 0) return null;

    PsiElement toReturn = null;
    for (PsiElement element : elements) {
      if (element == null) continue;
      toReturn = toReturn == null ? element : findCommonParent(toReturn, element);
      if (toReturn == null) return null;
    }
    return toReturn;
  }

  @Nullable
  public static PsiElement findCommonParent(@NotNull PsiElement element1, @NotNull PsiElement element2) {
    // optimization
    if (element1 == element2) return element1;
    final PsiFile containingFile = element1.getContainingFile();
    final PsiElement topLevel = containingFile == element2.getContainingFile() ? containingFile : null;

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
  private static int getDepth(@NotNull PsiElement element, @Nullable PsiElement topLevel) {
    int depth=0;
    PsiElement parent = element;
    while (parent != topLevel && parent != null) {
      depth++;
      parent = parent.getParent();
    }
    return depth;
  }

  @Nullable
  public static PsiElement findCommonContext(@NotNull Collection<? extends PsiElement> elements) {
    if (elements.isEmpty()) return null;
    PsiElement toReturn = null;
    for (PsiElement element : elements) {
      if (element == null) continue;
      toReturn = toReturn == null ? element : findCommonContext(toReturn, element);
      if (toReturn == null) return null;
    }
    return toReturn;
  }

  @Nullable
  public static PsiElement findCommonContext(@NotNull PsiElement element1, @NotNull PsiElement element2) {
    // optimization
    if (element1 == element2) return element1;
    final PsiFile containingFile = element1.getContainingFile();
    final PsiElement topLevel = containingFile == element2.getContainingFile() ? containingFile : null;

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

  @Nullable
  @Contract("null, _ -> null")
  public static <T extends PsiElement> T findChildOfType(@Nullable final PsiElement element, @NotNull final Class<T> aClass) {
    return findChildOfAnyType(element, true, aClass);
  }

  @Nullable
  @Contract("null, _, _ -> null")
  public static <T extends PsiElement> T findChildOfType(@Nullable final PsiElement element,
                                                         @NotNull final Class<T> aClass,
                                                         final boolean strict) {
    return findChildOfAnyType(element, strict, aClass);
  }

  /**
   * Recursive (depth first) strict({@code element} isn't included) search for first element of any of given {@code classes}.
   *
   * @param element a PSI element to start search from.
   * @param classes element types to search for.
   * @param <T>     type to cast found element to.
   * @return first found element, or null if nothing found.
   */
  @Nullable
  @Contract("null, _ -> null")
  @SafeVarargs
  public static <T extends PsiElement> T findChildOfAnyType(@Nullable final PsiElement element, @NotNull final Class<? extends T>... classes) {
    return findChildOfAnyType(element, true, classes);
  }

  /**
   * Recursive (depth first) search for first element of any of given {@code classes}.
   *
   * @param element a PSI element to start search from.
   * @param strict  if false the {@code element} is also included in the search.
   * @param classes element types to search for.
   * @param <T>     type to cast found element to.
   * @return first found element, or null if nothing found.
   */
  @SafeVarargs
  @Nullable
  @Contract("null, _, _ -> null")
  public static <T extends PsiElement> T findChildOfAnyType(@Nullable final PsiElement element,
                                                            final boolean strict,
                                                            @NotNull final Class<? extends T>... classes) {
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
    @SuppressWarnings("unchecked") T t = (T)processor.getFoundElement();
    return t;
  }

  @NotNull
  public static <T extends PsiElement> Collection<T> findChildrenOfType(@Nullable PsiElement element, @NotNull Class<? extends T> aClass) {
    return findChildrenOfAnyType(element, aClass);
  }

  @SafeVarargs
  @NotNull
  public static <T extends PsiElement> Collection<T> findChildrenOfAnyType(@Nullable final PsiElement element,
                                                                           @NotNull final Class<? extends T>... classes) {
    if (element == null) {
      return ContainerUtil.emptyList();
    }

    CollectElements<T> processor = new CollectElements<T>() {
      @Override
      public boolean execute(@NotNull T each) {
        if (each == element) return true;
        if (instanceOf(each, classes)) {
          return super.execute(each);
        }
        return true;
      }
    };
    processElements(element, processor);
    return processor.getCollection();
  }

  /**
   * Non-recursive search for element of type T amongst given {@code element} children.
   *
   * @param element a PSI element to start search from.
   * @param aClass  element type to search for.
   * @param <T>     element type to search for.
   * @return first found element, or null if nothing found.
   */
  @Nullable
  public static <T extends PsiElement> T getChildOfType(@Nullable PsiElement element, @NotNull Class<T> aClass) {
    if (element == null) return null;
    for (PsiElement child = element.getFirstChild(); child != null; child = child.getNextSibling()) {
      if (aClass.isInstance(child)) {
        @SuppressWarnings("unchecked") T t = (T)child;
        return t;
      }
    }
    return null;
  }

  @Nullable
  public static PsiElement findFirstParent(@Nullable PsiElement element, Condition<PsiElement> condition) {
    return findFirstParent(element, false, condition);
  }

  @Nullable
  public static PsiElement findFirstParent(@Nullable PsiElement element, boolean strict, Condition<PsiElement> condition) {
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

  @Nullable
  public static PsiElement findFirstContext(@Nullable PsiElement element, boolean strict, Condition<PsiElement> condition) {
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

  @NotNull
  public static <T extends PsiElement> T getRequiredChildOfType(@NotNull PsiElement element, @NotNull Class<T> aClass) {
    final T child = getChildOfType(element, aClass);
    assert child != null : "Missing required child of type " + aClass.getName();
    return child;
  }

  @Nullable
  public static <T extends PsiElement> T[] getChildrenOfType(@Nullable PsiElement element, @NotNull Class<T> aClass) {
    if (element == null) return null;

    List<T> result = null;
    for (PsiElement child = element.getFirstChild(); child != null; child = child.getNextSibling()) {
      if (aClass.isInstance(child)) {
        if (result == null) result = new SmartList<>();
        @SuppressWarnings("unchecked") T t = (T)child;
        result.add(t);
      }
    }
    return result == null ? null : ArrayUtil.toObjectArray(result, aClass);
  }

  @SafeVarargs
  @NotNull
  public static <T extends PsiElement> List<T> getChildrenOfAnyType(@Nullable PsiElement element, @NotNull Class<? extends T>... classes) {
    if (element == null) return ContainerUtil.emptyList();

    List<T> result = null;
    for (PsiElement child = element.getFirstChild(); child != null; child = child.getNextSibling()) {
      if (instanceOf(child, classes)) {
        if (result == null) result = ContainerUtil.newSmartList();
        @SuppressWarnings("unchecked") T t = (T)child;
        result.add(t);
      }
    }
    if (result == null) {
      return ContainerUtil.emptyList();
    }
    return result;
  }

  @NotNull
  public static <T extends PsiElement> List<T> getChildrenOfTypeAsList(@Nullable PsiElement element, @NotNull Class<T> aClass) {
    if (element == null) return Collections.emptyList();

    List<T> result = new SmartList<>();
    for (PsiElement child = element.getFirstChild(); child != null; child = child.getNextSibling()) {
      if (aClass.isInstance(child)) {
        @SuppressWarnings("unchecked") T t = (T)child;
        result.add(t);
      }
    }
    return result;
  }

  @Nullable
  public static <T extends PsiElement> T getStubChildOfType(@Nullable PsiElement element, @NotNull Class<T> aClass) {
    if (element == null) return null;
    StubElement<?> stub = element instanceof StubBasedPsiElement ? ((StubBasedPsiElement)element).getStub() : null;
    if (stub == null) {
      return getChildOfType(element, aClass);
    }
    for (StubElement childStub : stub.getChildrenStubs()) {
      PsiElement child = childStub.getPsi();
      if (aClass.isInstance(child)) {
        @SuppressWarnings("unchecked") T t = (T)child;
        return t;
      }
    }
    return null;
  }

  @NotNull
  public static <T extends PsiElement> List<T> getStubChildrenOfTypeAsList(@Nullable PsiElement element, @NotNull Class<T> aClass) {
    if (element == null) return Collections.emptyList();
    StubElement<?> stub = element instanceof StubBasedPsiElement ? ((StubBasedPsiElement)element).getStub() : null;
    if (stub == null) {
      return getChildrenOfTypeAsList(element, aClass);
    }

    List<T> result = new SmartList<>();
    for (StubElement childStub : stub.getChildrenStubs()) {
      PsiElement child = childStub.getPsi();
      if (aClass.isInstance(child)) {
        @SuppressWarnings("unchecked") T t = (T)child;
        result.add(t);
      }
    }
    return result;
  }

  public static boolean instanceOf(final Object object, final Class<?>... classes) {
    if (classes != null) {
      for (final Class<?> c : classes) {
        if (c.isInstance(object)) return true;
      }
    }
    return false;
  }

  /**
   * Returns a direct child of the specified element which has any of the specified classes.
   *
   * @param element the element to get the child for.
   * @param classes the array of classes.
   * @return the element, or null if none was found.
   * @since 5.1
   */
  @SafeVarargs
  @Nullable
  @Contract("null, _ -> null")
  public static <T extends PsiElement> T getChildOfAnyType(@Nullable PsiElement element, @NotNull Class<? extends T>... classes) {
    if (element == null) return null;
    for (PsiElement child = element.getFirstChild(); child != null; child = child.getNextSibling()) {
      for (Class<? extends T> aClass : classes) {
        if (aClass.isInstance(child)) {
          @SuppressWarnings("unchecked") T t = (T)child;
          return t;
        }
      }
    }
    return null;
  }

  @Nullable
  @Contract("null, _ -> null")
  public static <T extends PsiElement> T getNextSiblingOfType(@Nullable PsiElement sibling, @NotNull Class<T> aClass) {
    if (sibling == null) return null;
    for (PsiElement child = sibling.getNextSibling(); child != null; child = child.getNextSibling()) {
      if (aClass.isInstance(child)) {
        @SuppressWarnings("unchecked") T t = (T)child;
        return t;
      }
    }
    return null;
  }

  @Nullable
  @Contract("null, _ -> null")
  public static <T extends PsiElement> T getPrevSiblingOfType(@Nullable PsiElement sibling, @NotNull Class<T> aClass) {
    if (sibling == null) return null;
    for (PsiElement child = sibling.getPrevSibling(); child != null; child = child.getPrevSibling()) {
      if (aClass.isInstance(child)) {
        @SuppressWarnings("unchecked") T t = (T)child;
        return t;
      }
    }
    return null;
  }

  @Nullable
  @Contract("null, _ -> null")
  public static <T extends PsiElement> T getTopmostParentOfType(@Nullable PsiElement element, @NotNull Class<T> aClass) {
    T answer = getParentOfType(element, aClass);

    do {
      T next = getParentOfType(answer, aClass);
      if (next == null) break;
      answer = next;
    }
    while (true);

    return answer;
  }

  @Nullable
  @Contract("null, _ -> null")
  public static <T extends PsiElement> T getParentOfType(@Nullable PsiElement element, @NotNull Class<T> aClass) {
    return getParentOfType(element, aClass, true);
  }

  @Nullable
  @Contract("null -> null")
  public static PsiElement getStubOrPsiParent(@Nullable PsiElement element) {
    if (element instanceof StubBasedPsiElement) {
      StubBase stub = (StubBase)((StubBasedPsiElement)element).getStub();
      if (stub != null) {
        final StubElement parentStub = stub.getParentStub();
        return parentStub != null ? parentStub.getPsi() : null;
      }
    }
    return element != null ? element.getParent() : null;
  }

  @Nullable
  @Contract("null, _ -> null")
  public static <E extends PsiElement> E getStubOrPsiParentOfType(@Nullable PsiElement element, @NotNull Class<E> parentClass) {
    if (element instanceof StubBasedPsiElement) {
      StubBase stub = (StubBase)((StubBasedPsiElement)element).getStub();
      if (stub != null) {
        @SuppressWarnings("unchecked") E e = (E)stub.getParentStubOfType(parentClass);
        return e;
      }
    }
    return getParentOfType(element, parentClass);
  }

  @SafeVarargs
  @Nullable
  @Contract("null, _, _, _ -> null")
  public static <T extends PsiElement> T getContextOfType(@Nullable PsiElement element,
                                                          @NotNull Class<T> aClass,
                                                          boolean strict,
                                                          Class<? extends PsiElement>... stopAt) {
    if (element == null) return null;
    if (strict) {
      element = element.getContext();
    }

    while (element != null && !aClass.isInstance(element)) {
      if (instanceOf(element, stopAt)) return null;
      element = element.getContext();
    }

    @SuppressWarnings("unchecked") T t = (T)element;
    return t;
  }

  @Nullable
  @Contract("null, _, _ -> null")
  public static <T extends PsiElement> T getContextOfType(@Nullable PsiElement element,
                                                          @NotNull Class<? extends T> aClass,
                                                          boolean strict) {
    return getContextOfType(element, strict, aClass);
  }

  @SafeVarargs
  @Nullable
  public static <T extends PsiElement> T getContextOfType(@Nullable PsiElement element, @NotNull Class<? extends T>... classes) {
    return getContextOfType(element, true, classes);
  }

  @SafeVarargs
  @Nullable
  @Contract("null, _, _ -> null")
  public static <T extends PsiElement> T getContextOfType(@Nullable PsiElement element,
                                                          boolean strict,
                                                          @NotNull Class<? extends T>... classes) {
    if (element == null) return null;
    if (strict) {
      element = element.getContext();
    }

    while (element != null && !instanceOf(element, classes)) {
      element = element.getContext();
    }

    @SuppressWarnings("unchecked") T t = (T)element;
    return t;
  }

  @Nullable
  @Contract("null, _, _ -> null")
  public static <T extends PsiElement> T getParentOfType(@Nullable PsiElement element, @NotNull Class<T> aClass, boolean strict) {
    return getParentOfType(element, aClass, strict, -1);
  }

  @Contract("null, _, _, _ -> null")
  public static <T extends PsiElement> T getParentOfType(@Nullable PsiElement element, @NotNull Class<T> aClass, boolean strict, int minStartOffset) {
    if (element == null) {
      return null;
    }

    if (strict) {
      if (element instanceof PsiFile) {
        return null;
      }
      element = element.getParent();
    }

    while (element != null && (minStartOffset == -1 || element.getNode().getStartOffset() >= minStartOffset)) {
      if (aClass.isInstance(element)) {
        @SuppressWarnings("unchecked") T t = (T)element;
        return t;
      }
      if (element instanceof PsiFile) {
        return null;
      }
      element = element.getParent();
    }

    return null;
  }

  @SafeVarargs
  @Nullable
  @Contract("null, _, _, _ -> null")
  public static <T extends PsiElement> T getParentOfType(@Nullable PsiElement element,
                                                         @NotNull Class<T> aClass,
                                                         boolean strict,
                                                         @NotNull Class<? extends PsiElement>... stopAt) {
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

    @SuppressWarnings("unchecked") T t = (T)element;
    return t;
  }

  @Nullable
  public static PsiElement findSiblingForward(@NotNull final PsiElement element,
                                              @NotNull final IElementType elementType,
                                              @Nullable final Consumer<PsiElement> consumer) {
    return findSiblingForward(element, elementType, true, consumer);
  }

  @Nullable
  public static PsiElement findSiblingForward(@NotNull final PsiElement element,
                                              @NotNull final IElementType elementType,
                                              boolean strict,
                                              @Nullable final Consumer<PsiElement> consumer) {
    for (PsiElement e = strict ? element.getNextSibling() : element; e != null; e = e.getNextSibling()) {
      if (elementType.equals(e.getNode().getElementType())) {
        return e;
      }
      if (consumer != null) consumer.consume(e);
    }
    return null;
  }

  @Nullable
  public static PsiElement findSiblingBackward(@NotNull final PsiElement element,
                                               @NotNull final IElementType elementType,
                                               @Nullable final Consumer<PsiElement> consumer) {
    return findSiblingBackward(element, elementType, true, consumer);
  }

  @Nullable
  public static PsiElement findSiblingBackward(@NotNull final PsiElement element,
                                               @NotNull final IElementType elementType,
                                               boolean strict,
                                               @Nullable final Consumer<PsiElement> consumer) {
    for (PsiElement e = strict ? element.getPrevSibling() : element; e != null; e = e.getPrevSibling()) {
      if (elementType.equals(e.getNode().getElementType())) {
        return e;
      }
      if (consumer != null) consumer.consume(e);
    }
    return null;
  }

  @Nullable
  @Contract("null, _ -> null")
  public static PsiElement skipSiblingsForward(@Nullable PsiElement element, @NotNull Class... elementClasses) {
    if (element == null) return null;
    for (PsiElement e = element.getNextSibling(); e != null; e = e.getNextSibling()) {
      if (!instanceOf(e, elementClasses)) {
        return e;
      }
    }
    return null;
  }

  @Nullable
  @Contract("null -> null")
  public static PsiElement skipWhitespacesForward(@Nullable PsiElement element) {
    return skipSiblingsForward(element, WS);
  }

  @Nullable
  @Contract("null -> null")
  public static PsiElement skipWhitespacesAndCommentsForward(@Nullable PsiElement element) {
    return skipSiblingsForward(element, WS_COMMENTS);
  }

  @Nullable
  @Contract("null, _ -> null")
  public static PsiElement skipSiblingsBackward(@Nullable PsiElement element, @NotNull Class... elementClasses) {
    if (element == null) return null;
    for (PsiElement e = element.getPrevSibling(); e != null; e = e.getPrevSibling()) {
      if (!instanceOf(e, elementClasses)) {
        return e;
      }
    }
    return null;
  }

  @Nullable
  @Contract("null -> null")
  public static PsiElement skipWhitespacesBackward(@Nullable PsiElement element) {
    return skipSiblingsBackward(element, WS);
  }

  @Nullable
  @Contract("null -> null")
  public static PsiElement skipWhitespacesAndCommentsBackward(@Nullable PsiElement element) {
    return skipSiblingsBackward(element, WS_COMMENTS);
  }

  @Nullable
  @Contract("null, _ -> null")
  public static PsiElement skipParentsOfType(@Nullable PsiElement element, @NotNull Class... parentClasses) {
    if (element == null) return null;
    for (PsiElement e = element.getParent(); e != null; e = e.getParent()) {
      if (!instanceOf(e, parentClasses)) {
        return e;
      }
    }
    return null;
  }

  @SafeVarargs
  @Nullable
  @Contract("null, _ -> null")
  public static <T extends PsiElement> T getParentOfType(@Nullable final PsiElement element,
                                                         @NotNull final Class<? extends T>... classes) {
    if (element == null || element instanceof PsiFile) return null;
    PsiElement parent = element.getParent();
    if (parent == null) return null;
    return getNonStrictParentOfType(parent, classes);
  }

  @SafeVarargs
  @Nullable
  @Contract("null, _ -> null")
  public static <T extends PsiElement> T getNonStrictParentOfType(@Nullable final PsiElement element,
                                                                  @NotNull final Class<? extends T>... classes) {
    PsiElement run = element;
    while (run != null) {
      if (instanceOf(run, classes)) {
        @SuppressWarnings("unchecked") T t = (T)run;
        return t;
      }
      if (run instanceof PsiFile) break;
      run = run.getParent();
    }

    return null;
  }

  @NotNull
  public static PsiElement[] collectElements(@Nullable PsiElement element, @NotNull PsiElementFilter filter) {
    CollectFilteredElements<PsiElement> processor = new CollectFilteredElements<>(filter);
    processElements(element, processor);
    return processor.toArray();
  }

  @SafeVarargs
  @NotNull
  public static <T extends PsiElement> Collection<T> collectElementsOfType(@Nullable PsiElement element, @NotNull Class<T>... classes) {
    CollectFilteredElements<T> processor = new CollectFilteredElements<>(element1 -> {
      for (Class<T> clazz : classes) {
        if (clazz.isInstance(element1)) {
          return true;
        }
      }
      return false;
    });
    processElements(element, processor);
    return processor.getCollection();
  }

  @Contract("null, _ -> true")
  public static boolean processElements(@Nullable PsiElement element, @NotNull PsiElementProcessor processor) {
    if (element == null) return true;
    if (element instanceof PsiCompiledElement || !element.isPhysical()) { // DummyHolders cannot be visited by walking visitors because children/parent relationship is broken there
      //noinspection unchecked
      if (!processor.execute(element)) return false;
      for (PsiElement child : element.getChildren()) {
        if (!processElements(child, processor)) return false;
      }
      return true;
    }
    final boolean[] result = {true};
    element.accept(new PsiRecursiveElementWalkingVisitor() {
      @Override
      public void visitElement(PsiElement element) {
        //noinspection unchecked
        if (processor.execute(element)) {
          super.visitElement(element);
        }
        else {
          stopWalking();
          result[0] = false;
        }
      }
    });

    return result[0];
  }

  public static boolean processElements(@NotNull PsiElementProcessor processor, @Nullable PsiElement... elements) {
    if (elements == null || elements.length == 0) return true;
    for (PsiElement element : elements) {
      if (!processElements(element, processor)) return false;
    }
    return true;
  }

  public static void mark(@NotNull PsiElement element, @NotNull Object marker) {
    element.putCopyableUserData(MARKER, marker);
  }

  @Nullable
  public static PsiElement releaseMark(@NotNull PsiElement root, @NotNull Object marker) {
    if (marker.equals(root.getCopyableUserData(MARKER))) {
      root.putCopyableUserData(MARKER, null);
      return root;
    }
    else {
      PsiElement child = root.getFirstChild();
      while (child != null) {
        final PsiElement result = releaseMark(child, marker);
        if (result != null) return result;
        child = child.getNextSibling();
      }
      return null;
    }
  }

  @Nullable
  public static <T extends PsiElement> T findElementOfClassAtOffset(@NotNull PsiFile file,
                                                                    int offset,
                                                                    @NotNull Class<T> clazz,
                                                                    boolean strictStart) {
    final List<PsiFile> psiRoots = file.getViewProvider().getAllFiles();
    T result = null;
    for (PsiElement root : psiRoots) {
      final PsiElement elementAt = root.findElementAt(offset);
      if (elementAt != null) {
        final T parent = getParentOfType(elementAt, clazz, strictStart);
        if (parent != null) {
          final TextRange range = parent.getTextRange();
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
  @Nullable
  public static <T extends PsiElement> T findElementOfClassAtOffsetWithStopSet(@NotNull PsiFile file,
                                                                               int offset,
                                                                               @NotNull Class<T> clazz,
                                                                               boolean strictStart,
                                                                               @NotNull Class<? extends PsiElement>... stopAt) {
    final List<PsiFile> psiRoots = file.getViewProvider().getAllFiles();
    T result = null;
    for (PsiElement root : psiRoots) {
      final PsiElement elementAt = root.findElementAt(offset);
      if (elementAt != null) {
        final T parent = getParentOfType(elementAt, clazz, strictStart, stopAt);
        if (parent != null) {
          final TextRange range = parent.getTextRange();
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
   * @return maximal element of specified Class starting at startOffset exactly and ending not farther than endOffset
   */
  @Nullable
  public static <T extends PsiElement> T findElementOfClassAtRange(@NotNull PsiFile file,
                                                                   int startOffset,
                                                                   int endOffset,
                                                                   @NotNull Class<T> clazz) {
    final FileViewProvider viewProvider = file.getViewProvider();
    T result = null;
    for (Language lang : viewProvider.getLanguages()) {
      PsiElement elementAt = viewProvider.findElementAt(startOffset, lang);
      T run = getParentOfType(elementAt, clazz, false);
      T prev = run;
      while (run != null && run.getTextRange().getStartOffset() == startOffset &&
             run.getTextRange().getEndOffset() <= endOffset) {
        prev = run;
        run = getParentOfType(run, clazz);
      }

      if (prev == null) continue;
      final int elementStartOffset = prev.getTextRange().getStartOffset();
      final int elementEndOffset = prev.getTextRange().getEndOffset();
      if (elementStartOffset != startOffset || elementEndOffset > endOffset) continue;

      if (result == null || result.getTextRange().getEndOffset() < elementEndOffset) {
        result = prev;
      }
    }

    return result;
  }

  @NotNull
  public static PsiElement getDeepestFirst(@NotNull PsiElement elt) {
    @NotNull PsiElement res = elt;
    do {
      final PsiElement firstChild = res.getFirstChild();
      if (firstChild == null) return res;
      res = firstChild;
    }
    while (true);
  }

  @NotNull
  public static PsiElement getDeepestLast(@NotNull PsiElement elt) {
    @NotNull PsiElement res = elt;
    do {
      final PsiElement lastChild = res.getLastChild();
      if (lastChild == null) return res;
      res = lastChild;
    }
    while (true);
  }

  @Nullable
  public static PsiElement prevLeaf(@NotNull PsiElement current) {
    final PsiElement prevSibling = current.getPrevSibling();
    if (prevSibling != null) return lastChild(prevSibling);
    final PsiElement parent = current.getParent();
    if (parent == null || parent instanceof PsiFile) return null;
    return prevLeaf(parent);
  }

  @Nullable
  public static PsiElement nextLeaf(@NotNull PsiElement current) {
    final PsiElement nextSibling = current.getNextSibling();
    if (nextSibling != null) return firstChild(nextSibling);
    final PsiElement parent = current.getParent();
    if (parent == null || parent instanceof PsiFile) return null;
    return nextLeaf(parent);
  }

  public static PsiElement lastChild(@NotNull PsiElement element) {
    PsiElement lastChild = element.getLastChild();
    if (lastChild != null) return lastChild(lastChild);
    return element;
  }

  public static PsiElement firstChild(@NotNull final PsiElement element) {
    PsiElement child = element.getFirstChild();
    if (child != null) return firstChild(child);
    return element;
  }

  @Nullable
  public static PsiElement prevLeaf(@NotNull final PsiElement element, final boolean skipEmptyElements) {
    PsiElement prevLeaf = prevLeaf(element);
    while (skipEmptyElements && prevLeaf != null && prevLeaf.getTextLength() == 0) prevLeaf = prevLeaf(prevLeaf);
    return prevLeaf;
  }

  @Nullable
  public static PsiElement prevVisibleLeaf(@NotNull final PsiElement element) {
    PsiElement prevLeaf = prevLeaf(element, true);
    while (prevLeaf != null && StringUtil.isEmptyOrSpaces(prevLeaf.getText())) prevLeaf = prevLeaf(prevLeaf, true);
    return prevLeaf;
  }

  @Nullable
  public static PsiElement nextVisibleLeaf(@NotNull final PsiElement element) {
    PsiElement nextLeaf = nextLeaf(element, true);
    while (nextLeaf != null && StringUtil.isEmptyOrSpaces(nextLeaf.getText())) nextLeaf = nextLeaf(nextLeaf, true);
    return nextLeaf;
  }

  @Nullable
  public static PsiElement nextLeaf(final PsiElement element, final boolean skipEmptyElements) {
    PsiElement nextLeaf = nextLeaf(element);
    while (skipEmptyElements && nextLeaf != null && nextLeaf.getTextLength() == 0) nextLeaf = nextLeaf(nextLeaf);
    return nextLeaf;
  }

  public static boolean hasErrorElements(@NotNull PsiElement element) {
    return !psiTraverser(element).traverse().filter(PsiErrorElement.class).isEmpty();
  }

  @NotNull
  public static PsiElement[] filterAncestors(@NotNull PsiElement[] elements) {
    if (LOG.isDebugEnabled()) {
      for (PsiElement element : elements) {
        LOG.debug("element = " + element);
      }
    }

    ArrayList<PsiElement> filteredElements = new ArrayList<>();
    ContainerUtil.addAll(filteredElements, elements);

    int previousSize;
    do {
      previousSize = filteredElements.size();
      outer:
      for (PsiElement element : filteredElements) {
        for (PsiElement element2 : filteredElements) {
          if (element == element2) continue;
          if (isAncestor(element, element2, false)) {
            if (LOG.isDebugEnabled()) {
              LOG.debug("removing " + element2);
            }
            filteredElements.remove(element2);
            break outer;
          }
        }
      }
    }
    while (filteredElements.size() != previousSize);

    if (LOG.isDebugEnabled()) {
      for (PsiElement element : filteredElements) {
        LOG.debug("filtered element = " + element);
      }
    }

    return PsiUtilCore.toPsiElementArray(filteredElements);
  }

  public static boolean treeWalkUp(@NotNull final PsiScopeProcessor processor,
                                   @NotNull final PsiElement entrance,
                                   @Nullable final PsiElement maxScope,
                                   @NotNull final ResolveState state) {
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

  public static boolean treeWalkUp(@NotNull final PsiElement entrance,
                                   @Nullable final PsiElement maxScope,
                                   PairProcessor<PsiElement, PsiElement> eachScopeAndLastParent) {
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

  @NotNull
  public static PsiElement findPrevParent(@NotNull PsiElement ancestor, @NotNull PsiElement descendant) {
    PsiElement cur = descendant;
    while (cur != null) {
      final PsiElement parent = cur.getParent();
      if (parent == ancestor) {
        return cur;
      }
      cur = parent;
    }
    throw new AssertionError(descendant + " is not a descendant of " + ancestor);
  }

  public static List<PsiElement> getInjectedElements(@NotNull OuterLanguageElement outerLanguageElement) {
    PsiElement psi = outerLanguageElement.getContainingFile().getViewProvider().getPsi(outerLanguageElement.getLanguage());
    TextRange injectionRange = outerLanguageElement.getTextRange();
    List<PsiElement> res = ContainerUtil.newArrayList();

    assert psi != null : outerLanguageElement;
    for (PsiElement element = psi.findElementAt(injectionRange.getStartOffset());
         element != null && injectionRange.intersectsStrict(element.getTextRange());
         element = element.getNextSibling()) {
      res.add(element);
    }

    return res;
  }

  @Nullable
  public static PsiElement getDeepestVisibleFirst(@NotNull PsiElement psiElement) {
    PsiElement first = getDeepestFirst(psiElement);
    if (StringUtil.isEmptyOrSpaces(first.getText())) {
      first = nextVisibleLeaf(first);
    }
    return first;
  }

  @Nullable
  public static PsiElement getDeepestVisibleLast(@NotNull PsiElement psiElement) {
    PsiElement last = getDeepestLast(psiElement);
    if (StringUtil.isEmptyOrSpaces(last.getText())) {
      last = prevVisibleLeaf(last);
    }
    return last;
  }

  //<editor-fold desc="Deprecated stuff.">
  /** use {@link SyntaxTraverser#psiTraverser()} (to be removed in IDEA 2019) */
  @Deprecated
  public static <T extends PsiElement> Iterator<T> childIterator(@NotNull PsiElement element, @NotNull Class<T> aClass) {
    return psiTraverser().children(element).filter(aClass).iterator();
  }
  //</editor-fold>
}