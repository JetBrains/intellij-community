/*
 * Copyright 2000-2005 JetBrains s.r.o.
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

import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.search.PsiElementProcessor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;

public class PsiTreeUtil {
  private static final Key<Integer> INDEX = Key.create("PsiTreeUtil.copyElements.INDEX");
  private static final Key<Object> MARKER = Key.create("PsiTreeUtil.copyElements.INDEX");

  public static boolean isAncestor(@NotNull PsiElement ancestor, @NotNull PsiElement element, boolean strict) {
    PsiElement parent = strict ? element.getParent() : element;
    while (true) {
      if (parent == null) return false;
      if (parent.equals(ancestor)) return true;
      parent = parent.getParent();
    }
  }

  public static PsiElement findCommonParent (PsiElement... elements) {
    if (elements.length == 0)  return null;
    if (elements.length == 1)  return elements[0];
    PsiElement toReturn = elements[0];
    for (int i = 1; i < elements.length; i++) {
      toReturn = findCommonParent(toReturn, elements[i]);
      if (toReturn == null) return null;
    }

    return toReturn;
  }

  public static @Nullable PsiElement findCommonParent(@NotNull PsiElement element1, @NotNull PsiElement element2) {
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

  private static @NotNull ArrayList<PsiElement> getParents(@NotNull PsiElement element, @Nullable PsiElement topLevel) {
    ArrayList<PsiElement> parents = new ArrayList<PsiElement>();
    PsiElement parent = element;
    while (parent != topLevel && parent != null) {
      parents.add(parent);
      parent = parent.getParent();
    }
    return parents;
  }

  @Nullable public static <ChildType extends PsiElement> ChildType getChildOfType(@NotNull PsiElement element, @NotNull Class<ChildType> aClass) {
    for(PsiElement child = element.getFirstChild(); child != null; child = child.getNextSibling()){
      if (aClass.isInstance(child)) return (ChildType)child;
    }
    return null;
  }

  @Nullable public static <ChildType extends PsiElement> ChildType getNextSiblingOfType(@NotNull PsiElement sibling, @NotNull Class<ChildType> aClass) {
    for(PsiElement child = sibling.getNextSibling(); child != null; child = child.getNextSibling()){
      if (aClass.isInstance(child)) return (ChildType)child;
    }
    return null;
  }

  @Nullable public static <T extends PsiElement> T getPrevSiblingOfType(@NotNull PsiElement sibling, @NotNull Class<T> aClass) {
    for(PsiElement child = sibling.getPrevSibling(); child != null; child = child.getPrevSibling()){
      if (aClass.isInstance(child)) return (T)child;
    }
    return null;
  }

  @Nullable public static <ParentType extends PsiElement> ParentType getParentOfType(@Nullable PsiElement element, @NotNull Class<ParentType> aClass) {
    return getParentOfType(element, aClass, true);
  }

  @Nullable public static <ContextType extends PsiElement> ContextType getContextOfType(@Nullable PsiElement element, @NotNull Class<ContextType> aClass, boolean strict) {
    if (element == null) return null;
    if (strict) {
      element = element.getContext();
    }

    while (element != null && !aClass.isInstance(element)) {
      element = element.getContext();
    }

    return (ContextType)element;
  }

  @Nullable
  public static <ParentType extends PsiElement> ParentType getParentOfType(@Nullable PsiElement element, @NotNull Class<ParentType> aClass, boolean strict) {
    if (element == null) return null;
    if (strict) {
      element = element.getParent();
    }

    while (element != null && !aClass.isInstance(element)) {
      element = element.getParent();
    }

    return (ParentType)element;
  }

  @Nullable public static PsiElement skipSiblingsForward (@Nullable PsiElement element, @NotNull Class[] elementClasses) {
    if (element == null) return null;
    NextSibling:
    for (PsiElement e = element.getNextSibling(); e != null; e = e.getNextSibling()) {
      for (Class aClass : elementClasses) {
        if (aClass.isInstance(e)) continue NextSibling;
      }
      return e;
    }
    return null;
  }

  @Nullable
  public static PsiElement skipSiblingsBackward (@Nullable PsiElement element, @NotNull Class[] elementClasses) {
    if (element == null) return null;
    NextSibling:
    for (PsiElement e = element.getPrevSibling(); e != null; e = e.getPrevSibling()) {
      for (Class aClass : elementClasses) {
        if (aClass.isInstance(e)) continue NextSibling;
      }
      return e;
    }
    return null;
  }

  public static @Nullable <T extends PsiElement> T getParentOfType(PsiElement element, Class<? extends T>... classes) {
    return getParentOfType(element, classes, true);
  }

  @Nullable
  public static <T extends PsiElement> T getParentOfType(@NotNull PsiElement element, @NotNull Class<? extends T>[] classes, boolean strict) {
    if (strict) {
      element = element.getParent();
    }

    while (element != null) {
      for (Class<? extends T> aClass : classes) {
        if (aClass.isInstance(element)) return (T)element;
      }
      element = element.getParent();
    }

    return null;
  }

  public static @NotNull PsiElement[] collectElements(@Nullable PsiElement element, @NotNull PsiElementFilter filter) {
    PsiElementProcessor.CollectFilteredElements processor = new PsiElementProcessor.CollectFilteredElements(filter);
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
      element.putCopyableUserData(INDEX, new Integer(i));
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

  public static @Nullable PsiElement releaseMark(@NotNull PsiElement root, @NotNull Object marker) {
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
        final T parent = getParentOfType(elementAt, clazz);
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
    final PsiElement[] psiRoots = file.getPsiRoots();
    T result = null;
    for (PsiElement root : psiRoots) {
      PsiElement elementAt = root.findElementAt(startOffset);
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
}
