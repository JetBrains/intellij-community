/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.search.PsiElementProcessor;
import com.intellij.psi.stubs.StubBase;
import com.intellij.util.ArrayUtil;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class PsiTreeUtil {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.util.PsiTreeUtil");

  private static final Key<Integer> INDEX = Key.create("PsiTreeUtil.copyElements.INDEX");
  private static final Key<Object> MARKER = Key.create("PsiTreeUtil.copyElements.INDEX");

  private PsiTreeUtil() {
  }

  /**
   * Checks whether one element in the psi tree is under another.
   * @param ancestor parent candidate. <code>false</code> will be returned if ancestor is null.
   * @param element child candidate
   * @param strict whether return true if ancestor and parent are the same.
   * @return true if element has ancestor as its parent somewhere in the hierarchy and false otherwise.
   */
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
   * @param ancestor parent candidate. <code>false</code> will be returned if ancestor is null.
   * @param element child candidate
   * @param strict whether return true if ancestor and parent are the same.
   * @return true if element has ancestor as its parent somewhere in the hierarchy and false otherwise.
   */
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
  public static PsiElement findCommonParent(@NotNull PsiElement... elements) {
    if (elements.length == 0)  return null;
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
    if(element1 == element2) return element1;
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
    if (elements.length == 0)  return null;
    PsiElement toReturn = elements[0];
    for (int i = 1; i < elements.length; i++) {
      toReturn = findCommonContext(toReturn, elements[i]);
      if (toReturn == null) return null;
    }

    return toReturn;
  }

  @Nullable
  public static PsiElement findCommonContext(@NotNull PsiElement element1, @NotNull PsiElement element2) {
    // optimization
    if(element1 == element2) return element1;
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

  @Nullable public static <T extends PsiElement> T getChildOfType(@NotNull PsiElement element, @NotNull Class<T> aClass) {
    for(PsiElement child = element.getFirstChild(); child != null; child = child.getNextSibling()){
      if (instanceOf(aClass, child)) return (T)child;
    }
    return null;
  }

  @NotNull public static <T extends PsiElement> T getRequiredChildOfType(@NotNull PsiElement element, @NotNull Class<T> aClass) {
    final T child = getChildOfType(element, aClass);
    assert child != null: "Missing required child of type " + aClass.getName();
    return child;
  }

  @Nullable public static <T extends PsiElement> T[] getChildrenOfType(@NotNull PsiElement element, @NotNull Class<T> aClass) {
    List<T> result = null;
    for(PsiElement child = element.getFirstChild(); child != null; child = child.getNextSibling()){
      if (instanceOf(aClass, child)) {
        if (result == null) result = new SmartList<T>();
        result.add((T)child);
      }
    }
    return result == null ? null : ArrayUtil.toObjectArray(result, aClass);
  }


  private static boolean instanceOf(final Class aClass, final PsiElement child) {
    /*
    if (aClass == PsiClass.class) return child instanceof PsiClass;
    else if (aClass == PsiMethod.class) return child instanceof PsiMethod;
    else if (aClass == PsiField.class) return child instanceof PsiField;
    else if (aClass == PsiMember.class) return child instanceof PsiMember;
    else if (aClass == PsiDocCommentOwner.class) return child instanceof PsiDocCommentOwner;
    else if (aClass == PsiStatement.class) return child instanceof PsiStatement;
    else if (aClass == PsiCodeBlock.class) return child instanceof PsiCodeBlock;
    else if (aClass == PsiClassInitializer.class) return child instanceof PsiClassInitializer;
    else if (aClass == XmlTag.class) return child instanceof XmlTag;
    else if (aClass == XmlDocument.class) return child instanceof XmlDocument;
    */

    return aClass.isInstance(child);
  }

  /**
   * Returns a direct child of the specified element which has any of the specified classes.
   *
   * @param element the element to get the child for.
   * @param classes the array of classes.
   * @return the element, or null if none was found.
   * @since 5.1
   */
  @Nullable public static <T extends PsiElement> T getChildOfAnyType(@NotNull PsiElement element, @NotNull Class<? extends T>... classes) {
    for(PsiElement child = element.getFirstChild(); child != null; child = child.getNextSibling()){
      for(Class<? extends T> aClass : classes) {
        if (instanceOf(aClass, child)) return (T)child;
      }
    }
    return null;
  }

  @Nullable public static <T extends PsiElement> T getNextSiblingOfType(@NotNull PsiElement sibling, @NotNull Class<T> aClass) {
    for(PsiElement child = sibling.getNextSibling(); child != null; child = child.getNextSibling()){
      if (instanceOf(aClass, child)) return (T)child;
    }
    return null;
  }

  @Nullable public static <T extends PsiElement> T getPrevSiblingOfType(@NotNull PsiElement sibling, @NotNull Class<T> aClass) {
    for(PsiElement child = sibling.getPrevSibling(); child != null; child = child.getPrevSibling()){
      if (instanceOf(aClass,  child)) return (T)child;
    }
    return null;
  }

  @Nullable public static <T extends PsiElement> T getParentOfType(@Nullable PsiElement element, @NotNull Class<T> aClass) {
    return getParentOfType(element, aClass, true);
  }

  @Nullable
  public static <E extends PsiElement> E getStubOrPsiParentOfType(@Nullable PsiElement element, final Class<E> parentClass) {
    if (element instanceof StubBasedPsiElement) {
      StubBase stub = (StubBase)((StubBasedPsiElement) element).getStub();
      if (stub != null) {
        //noinspection unchecked
        return (E)stub.getParentStubOfType(parentClass);
      }

    }
    return getParentOfType(element, parentClass);
  }

  @Nullable public static <T extends PsiElement> T getContextOfType(@Nullable PsiElement element, @NotNull Class<T> aClass, boolean strict, Class<? extends PsiElement>... stopAt) {
    if (element == null) return null;
    if (strict) {
      element = element.getContext();
    }

    while (element != null && !instanceOf(aClass, element)) {
      for (Class stopClass : stopAt) {
        if (instanceOf(stopClass, element)) {
          return null;
        }
      }

      element = element.getContext();
    }

    return (T)element;

  }

  @Nullable public static <T extends PsiElement> T getContextOfType(@Nullable PsiElement element, @NotNull Class<T> aClass, boolean strict) {
    if (element == null) return null;
    if (strict) {
      element = element.getContext();
    }

    while (element != null && !instanceOf(aClass, element)) {
      element = element.getContext();
    }

    return (T)element;
  }

  @Nullable
  public static <T extends PsiElement> T getParentOfType(@Nullable PsiElement element, @NotNull Class<T> aClass, boolean strict) {
    if (element == null) return null;
    if (strict) {
      element = element.getParent();
    }

    while (element != null && !instanceOf(aClass, element)) {
      if (element instanceof PsiFile) return null;
      element = element.getParent();
    }

    return (T)element;
  }

  @Nullable
  public static <T extends PsiElement> T getParentOfType(@Nullable PsiElement element, @NotNull Class<T> aClass, boolean strict, @NotNull Class<? extends PsiElement>... stopAt) {
    if (element == null) return null;
    if (strict) {
      element = element.getParent();
    }

    while (element != null && !instanceOf(aClass, element)) {
      for (Class<? extends PsiElement> stopClass : stopAt) {
        if (instanceOf(stopClass, element)) return null;
      }
      if (element instanceof PsiFile) return null;
      element = element.getParent();
    }

    return (T)element;
  }

  @Nullable public static PsiElement skipSiblingsForward (@Nullable PsiElement element, @NotNull Class... elementClasses) {
    if (element == null) return null;
    NextSibling:
    for (PsiElement e = element.getNextSibling(); e != null; e = e.getNextSibling()) {
      for (Class aClass : elementClasses) {
        if (instanceOf(aClass, e)) continue NextSibling;
      }
      return e;
    }
    return null;
  }

  @Nullable
  public static PsiElement skipSiblingsBackward (@Nullable PsiElement element, @NotNull Class... elementClasses) {
    if (element == null) return null;
    NextSibling:
    for (PsiElement e = element.getPrevSibling(); e != null; e = e.getPrevSibling()) {
      for (Class aClass : elementClasses) {
        if (instanceOf(aClass, e)) continue NextSibling;
      }
      return e;
    }
    return null;
  }

  @Nullable
  public static PsiElement skipParentsOfType(@Nullable PsiElement element, @NotNull Class... parentClasses) {
    if (element == null) return null;
    NextSibling:
    for (PsiElement e = element.getParent(); e != null; e = e.getParent()) {
      for (Class aClass : parentClasses) {
        if (instanceOf(aClass, e)) continue NextSibling;
      }
      return e;
    }
    return null;
  }

  @Nullable
  public static <T extends PsiElement> T getParentOfType(PsiElement element, @NotNull Class<? extends T>... classes) {
    if (element == null) return null;
    PsiElement parent = element.getParent();
    if (parent == null) return null;
    return getNonStrictParentOfType(parent, classes);
  }

  @Nullable
  public static <T extends PsiElement> T getNonStrictParentOfType(@NotNull PsiElement element, @NotNull Class<? extends T>... classes) {
    PsiElement run = element;
    while (run != null) {
      for (Class<? extends T> aClass : classes) {
        if (instanceOf(aClass, run)) return (T)run;
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

  public static boolean processElements(@Nullable PsiElement element, @NotNull PsiElementProcessor processor) {
    if (element == null) return true;
    if (!processor.execute(element)) return false;
    for (PsiElement child = element.getFirstChild(); child != null; child = child.getNextSibling()) {
      if (!processElements(child, processor)) return false;
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
        if (i != j && isAncestor(element,  rootCandidate, true)) {
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
    } else {
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
  public static <T extends PsiElement> T findElementOfClassAtOffset (@NotNull PsiFile file, int offset, @NotNull Class<T> clazz, boolean strictStart) {
    final PsiElement[] psiRoots = file.getPsiRoots();
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

  /**
   * @return maximal element of specified Class starting at startOffset exactly and ending not farther than endOffset
   */
  @Nullable
  public static <T extends PsiElement> T findElementOfClassAtRange (@NotNull PsiFile file, int startOffset, int endOffset, @NotNull Class<T> clazz) {
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

  public static PsiElement prevLeaf(PsiElement current){
    final PsiElement prevSibling = current.getPrevSibling();
    if(prevSibling != null) return lastChild(prevSibling);
    final PsiElement parent = current.getParent();
    if(parent == null || parent instanceof PsiFile) return null;
    return prevLeaf(parent);
  }

  public static PsiElement nextLeaf(PsiElement current){
    final PsiElement nextSibling = current.getNextSibling();
    if(nextSibling != null) return firstChild(nextSibling);
    final PsiElement parent = current.getParent();
    if(parent == null || parent instanceof PsiFile) return null;
    return nextLeaf(parent);
  }

  public static PsiElement lastChild(@NotNull PsiElement element) {
    PsiElement lastChild = element.getLastChild();
    if(lastChild != null) return lastChild(lastChild);
    return element;
  }

  public static PsiElement firstChild(final PsiElement element) {
    if(element.getFirstChild() != null) return firstChild(element.getFirstChild());
    return element;
  }

  public static PsiElement prevLeaf(final PsiErrorElement element, final boolean skipEmptyElements) {
    PsiElement prevLeaf = prevLeaf(element);
    while (skipEmptyElements && prevLeaf != null && prevLeaf.getTextLength() == 0) prevLeaf = prevLeaf(prevLeaf);
    return prevLeaf;
  }

  public static PsiElement nextLeaf(final PsiErrorElement element, final boolean skipEmptyElements) {
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

    return filteredElements.toArray(new PsiElement[filteredElements.size()]);
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
  
}
