// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.jvm.source;

import com.intellij.lang.jvm.JvmElement;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNameIdentifierOwner;
import com.intellij.util.containers.FlatteningIterator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

public final class JvmDeclarationSearch {
  private JvmDeclarationSearch() {}

  /**
   * <b>Example 1</b>
   * <pre>
   * public class JavaClass {
   *     public static void &lt;caret&gt;main(String[] args) {}
   * }
   * </pre>
   * In this case there will be a single result:
   * {@link com.intellij.lang.jvm.JvmMethod JvmMethod} instance corresponding to the {@code main} method.
   *
   *
   * <b>Example 2</b>
   * <pre>
   * class GroovyClass {
   *     static void &lt;caret&gt;main(String[] args, whatever = 2) {}
   * }
   * </pre>
   * In this case the result will consist of two {@link com.intellij.lang.jvm.JvmMethod JvmMethods} corresponding to respective method overload
   * (because in class file there will be two methods).
   */
  public static @NotNull Iterable<JvmElement> getElementsByIdentifier(@NotNull PsiElement identifierElement) {
    PsiElement declaringElement = findDeclaringElement(identifierElement);
    if (declaringElement == null) {
      return Collections.emptyList();
    }
    else {
      return () -> iterateDeclarations(declaringElement);
    }
  }

  private static @Nullable PsiElement findDeclaringElement(@NotNull PsiElement potentiallyIdentifyingElement) {
    PsiElement parent = potentiallyIdentifyingElement.getParent();
    if (parent instanceof PsiNameIdentifierOwner &&
        ((PsiNameIdentifierOwner)parent).getIdentifyingElement() == potentiallyIdentifyingElement) {
      return parent;
    }
    for (JvmDeclarationSearcher searcher : JvmDeclarationSearcher.EP.allForLanguage(potentiallyIdentifyingElement.getLanguage())) {
      PsiElement declaringElement = searcher.adjustIdentifierElement(potentiallyIdentifyingElement);
      if (declaringElement != null) {
        return declaringElement;
      }
    }
    return null;
  }

  private static @NotNull Iterator<JvmElement> iterateDeclarations(@NotNull PsiElement declaringElement) {
    List<JvmDeclarationSearcher> searchers = JvmDeclarationSearcher.EP.allForLanguage(declaringElement.getLanguage());
    return searchers.isEmpty() ? Collections.emptyIterator() : iterateDeclarations(declaringElement, searchers);
  }

  private static @NotNull Iterator<JvmElement> iterateDeclarations(@NotNull PsiElement declaringElement,
                                                                   @NotNull Collection<? extends JvmDeclarationSearcher> searchers) {
    return new FlatteningIterator<JvmDeclarationSearcher, JvmElement>(searchers.iterator()) {
      @Override
      public boolean hasNext() {
        ProgressManager.checkCanceled();
        return super.hasNext();
      }

      @Override
      protected Iterator<JvmElement> createValueIterator(JvmDeclarationSearcher searcher) {
        return searcher.findDeclarations(declaringElement).iterator();
      }
    };
  }

  /**
   * <b>Example</b>
   * <pre>
   * class GroovyClass {
   *     static void main(String[] args, whatever = 2) {&lt;caret&gt;} // note caret is within method body
   * }
   * </pre>
   * In this case the result will consist of two {@link com.intellij.lang.jvm.JvmMethod JvmMethods} corresponding to respective method overload
   * (because in class file there will be two methods).
   */
  public static @NotNull Iterable<JvmElement> getImmediatelyContainingElements(@NotNull PsiElement place) {
    List<JvmDeclarationSearcher> extensions = JvmDeclarationSearcher.EP.allForLanguage(place.getLanguage());
    if (extensions.isEmpty()) {
      return Collections.emptyList();
    }

    PsiElement current = place;
    while (current != null) {
      Iterator<JvmElement> iterator = iterateDeclarations(current, extensions);
      if (iterator.hasNext()) {
        return () -> iterator;
      }
      current = current.getParent();
    }

    return Collections.emptyList();
  }
}
