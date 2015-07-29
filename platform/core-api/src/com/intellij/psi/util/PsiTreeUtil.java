/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
import com.intellij.psi.stubs.StubBase;
import com.intellij.psi.stubs.StubElement;
import com.intellij.psi.templateLanguages.OuterLanguageElement;
import com.intellij.util.ArrayUtil;
import com.intellij.util.PairProcessor;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class PsiTreeUtil {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.util.PsiTreeUtil");

  private static final Key<Integer> INDEX = Key.create("PsiTreeUtil.copyElements.INDEX");
  private static final Key<Object> MARKER = Key.create("PsiTreeUtil.copyElements.MARKER");

  /**
   * Checks whether one element in the psi tree is under another.
   *
   * @param ancestor parent candidate. <code>false</code> will be returned if ancestor is null.
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
   * Checks whether one element in the psi tree is under another in {@link com.intellij.psi.PsiElement#getContext()}  hierarchy.
   *
   * @param ancestor parent candidate. <code>false</code> will be returned if ancestor is null.
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

    ArrayList<PsiElement> parents1 = getParents(element1, topLevel);
    ArrayList<PsiElement> parents2 = getParents(element2, topLevel);
    int size = Math.min(parents1.size(), parents2.size());
    PsiElement parent = topLevel;
    for (int i = 1; i <= size; i++) {
      PsiElement parent1 = parents1.get(parents1.size() - i);
      PsiElement parent2 = parents2.get(parents2.size() - i);
      if (!parent1.equals(parent2)) break;
      parent = parent1;
    }
    return parent;
  }

  @NotNull
  private static ArrayList<PsiElement> getParents(@NotNull PsiElement element, @Nullable PsiElement topLevel) {
    ArrayList<PsiElement> parents = new ArrayList<PsiElement>();
    PsiElement parent = element;
    while (parent != topLevel && parent != null) {
      parents.add(parent);
      parent = parent.getParent();
    }
    return parents;
  }

  @Nullable
  public static PsiElement findCommonContext(@NotNull PsiElement... elements) {
    return findCommonContext(Arrays.asList(elements));
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

    ArrayList<PsiElement> parents1 = getContexts(element1, topLevel);
    ArrayList<PsiElement> parents2 = getContexts(element2, topLevel);
    int size = Math.min(parents1.size(), parents2.size());
    PsiElement parent = topLevel;
    for (int i = 1; i <= size; i++) {
      PsiElement parent1 = parents1.get(parents1.size() - i);
      PsiElement parent2 = parents2.get(parents2.size() - i);
      if (!parent1.equals(parent2)) break;
      parent = parent1;
    }
    return parent;
  }

  @NotNull
  private static ArrayList<PsiElement> getContexts(@NotNull PsiElement element, @Nullable PsiElement topLevel) {
    ArrayList<PsiElement> parents = new ArrayList<PsiElement>();
    PsiElement parent = element;
    while (parent != topLevel && parent != null) {
      parents.add(parent);
      parent = parent.getContext();
    }
    return parents;
  }

  @Nullable
  public static <T extends PsiElement> T findChildOfType(@Nullable final PsiElement element, @NotNull final Class<T> aClass) {
    //noinspection unchecked
    return findChildOfAnyType(element, true, aClass);
  }

  @Nullable
  public static <T extends PsiElement> T findChildOfType(@Nullable final PsiElement element,
                                                         @NotNull final Class<T> aClass,
                                                         final boolean strict) {
    //noinspection unchecked
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
  @Nullable
  @Contract("null, _, _ -> null")
  public static <T extends PsiElement> T findChildOfAnyType(@Nullable final PsiElement element,
                                                            final boolean strict,
                                                            @NotNull final Class<? extends T>... classes) {
    PsiElementProcessor.FindElement<PsiElement> processor = new PsiElementProcessor.FindElement<PsiElement>() {
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

  @NotNull
  public static <T extends PsiElement> Collection<T> findChildrenOfType(@Nullable PsiElement element, @NotNull Class<? extends T> aClass) {
    return findChildrenOfAnyType(element, aClass);
  }

  @NotNull
  public static <T extends PsiElement> Collection<T> findChildrenOfAnyType(@Nullable final PsiElement element,
                                                                           @NotNull final Class<? extends T>... classes) {
    if (element == null) {
      return ContainerUtil.emptyList();
    }

    PsiElementProcessor.CollectElements<T> processor = new PsiElementProcessor.CollectElements<T>() {
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
        //noinspection unchecked
        return (T)child;
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
        if (result == null) result = new SmartList<T>();
        //noinspection unchecked
        result.add((T)child);
      }
    }
    return result == null ? null : ArrayUtil.toObjectArray(result, aClass);
  }

  @NotNull
  public static <T extends PsiElement> List<T> getChildrenOfTypeAsList(@Nullable PsiElement element, @NotNull Class<T> aClass) {
    if (element == null) return Collections.emptyList();

    List<T> result = new SmartList<T>();
    for (PsiElement child = element.getFirstChild(); child != null; child = child.getNextSibling()) {
      if (aClass.isInstance(child)) {
        //noinspection unchecked
        result.add((T)child);
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
  @Nullable
  @Contract("null, _ -> null")
  public static <T extends PsiElement> T getChildOfAnyType(@Nullable PsiElement element, @NotNull Class<? extends T>... classes) {
    if (element == null) return null;
    for (PsiElement child = element.getFirstChild(); child != null; child = child.getNextSibling()) {
      for (Class<? extends T> aClass : classes) {
        if (aClass.isInstance(child)) {
          //noinspection unchecked
          return (T)child;
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
        //noinspection unchecked
        return (T)child;
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
        //noinspection unchecked
        return (T)child;
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
        //noinspection unchecked
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
        //noinspection unchecked
        return (E)stub.getParentStubOfType(parentClass);
      }
    }
    return getParentOfType(element, parentClass);
  }

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

    //noinspection unchecked
    return (T)element;
  }

  @Nullable
  @Contract("null, _, _ -> null")
  public static <T extends PsiElement> T getContextOfType(@Nullable PsiElement element,
                                                          @NotNull Class<? extends T> aClass,
                                                          boolean strict) {
    return getContextOfType(element, strict, aClass);
  }

  @Nullable
  public static <T extends PsiElement> T getContextOfType(@Nullable PsiElement element, @NotNull Class<? extends T>... classes) {
    return getContextOfType(element, true, classes);
  }

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

    //noinspection unchecked
    return (T)element;
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
        //noinspection unchecked
        return (T)element;
      }
      if (element instanceof PsiFile) {
        return null;
      }
      element = element.getParent();
    }

    return null;
  }

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

    //noinspection unchecked
    return (T)element;
  }

  @Nullable
  @Contract("null, _ -> null")
  public static PsiElement skipSiblingsForward(@Nullable PsiElement element, @NotNull Class... elementClasses) {
    if (element == null) return null;
    NextSibling:
    for (PsiElement e = element.getNextSibling(); e != null; e = e.getNextSibling()) {
      if (instanceOf(e, elementClasses)) continue NextSibling;
      return e;
    }
    return null;
  }

  @Nullable
  @Contract("null, _ -> null")
  public static PsiElement skipSiblingsBackward(@Nullable PsiElement element, @NotNull Class... elementClasses) {
    if (element == null) return null;
    NextSibling:
    for (PsiElement e = element.getPrevSibling(); e != null; e = e.getPrevSibling()) {
      if (instanceOf(e, elementClasses)) continue NextSibling;
      return e;
    }
    return null;
  }

  @Nullable
  @Contract("null, _ -> null")
  public static PsiElement skipParentsOfType(@Nullable PsiElement element, @NotNull Class... parentClasses) {
    if (element == null) return null;
    NextSibling:
    for (PsiElement e = element.getParent(); e != null; e = e.getParent()) {
      if (instanceOf(e, parentClasses)) continue NextSibling;
      return e;
    }
    return null;
  }

  @Nullable
  @Contract("null, _ -> null")
  public static <T extends PsiElement> T getParentOfType(@Nullable final PsiElement element,
                                                         @NotNull final Class<? extends T>... classes) {
    if (element == null || element instanceof PsiFile) return null;
    PsiElement parent = element.getParent();
    if (parent == null) return null;
    return getNonStrictParentOfType(parent, classes);
  }

  @Nullable
  @Contract("null, _ -> null")
  public static <T extends PsiElement> T getNonStrictParentOfType(@Nullable final PsiElement element,
                                                                  @NotNull final Class<? extends T>... classes) {
    PsiElement run = element;
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

  @NotNull
  public static PsiElement[] collectElements(@Nullable PsiElement element, @NotNull PsiElementFilter filter) {
    PsiElementProcessor.CollectFilteredElements<PsiElement> processor = new PsiElementProcessor.CollectFilteredElements<PsiElement>(filter);
    processElements(element, processor);
    return processor.toArray();
  }

  @NotNull
  public static <T extends PsiElement> Collection<T> collectElementsOfType(@Nullable final PsiElement element,
                                                                           @NotNull final Class<T>... classes) {
    PsiElementProcessor.CollectFilteredElements<T> processor = new PsiElementProcessor.CollectFilteredElements<T>(new PsiElementFilter() {

      @Override
      public boolean isAccepted(PsiElement element) {
        for (Class<T> clazz : classes) {
          if (clazz.isInstance(element)) {
            return true;
          }
        }

        return false;
      }
    });
    processElements(element, processor);
    return processor.getCollection();
  }

  @Contract("null, _ -> true")
  public static boolean processElements(@Nullable PsiElement element, @NotNull final PsiElementProcessor processor) {
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

  @NotNull
  public static PsiElement[] copyElements(@NotNull PsiElement[] elements) {
    ArrayList<PsiElement> roots = new ArrayList<PsiElement>();
    for (int i = 0; i < elements.length; i++) {
      PsiElement rootCandidate = elements[i];
      boolean failed = false;
      for (int j = 0; j < elements.length; j++) {
        PsiElement element = elements[j];
        if (i != j && isAncestor(element, rootCandidate, true)) {
          failed = true;
          break;
        }
      }
      if (!failed) {
        roots.add(rootCandidate);
      }
    }
    for (int i = 0; i < elements.length; i++) {
      PsiElement element = elements[i];
      element.putCopyableUserData(INDEX, Integer.valueOf(i));
    }
    PsiElement[] newRoots = new PsiElement[roots.size()];
    for (int i = 0; i < roots.size(); i++) {
      PsiElement root = roots.get(i);
      newRoots[i] = root.copy();
    }

    final PsiElement[] result = new PsiElement[elements.length];
    for (PsiElement newRoot : newRoots) {
      decodeIndices(newRoot, result);
    }
    return result;
  }

  private static void decodeIndices(@NotNull PsiElement element, @NotNull PsiElement[] result) {
    final Integer data = element.getCopyableUserData(INDEX);
    if (data != null) {
      element.putCopyableUserData(INDEX, null);
      int index = data.intValue();
      result[index] = element;
    }
    PsiElement child = element.getFirstChild();
    while (child != null) {
      decodeIndices(child, result);
      child = child.getNextSibling();
    }
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

  public static boolean hasErrorElements(@NotNull final PsiElement element) {
    if (element instanceof PsiErrorElement) return true;

    for (PsiElement child : element.getChildren()) {
      if (hasErrorElements(child)) return true;
    }

    return false;
  }

  @NotNull
  public static PsiElement[] filterAncestors(@NotNull PsiElement[] elements) {
    if (LOG.isDebugEnabled()) {
      for (PsiElement element : elements) {
        LOG.debug("element = " + element);
      }
    }

    ArrayList<PsiElement> filteredElements = new ArrayList<PsiElement>();
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

  @NotNull
  public static <T extends PsiElement> Iterator<T> childIterator(@NotNull final PsiElement element, @NotNull final Class<T> aClass) {
    return new Iterator<T>() {
      private T next = getChildOfType(element, aClass);

      @Override
      public boolean hasNext() {
        return next != null;
      }

      @Override
      public T next() {
        if (next == null) throw new NoSuchElementException();
        T current = this.next;
        next = getNextSiblingOfType(current, aClass);
        return current;
      }

      @Override
      public void remove() {
        throw new UnsupportedOperationException();
      }
    };
  }
}
