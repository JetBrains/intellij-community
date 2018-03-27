// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.jvm.source;

import com.intellij.lang.jvm.JvmElement;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.psi.PsiElement;
import com.intellij.util.containers.SmartHashSet;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static com.intellij.lang.jvm.source.JvmDeclarationSearcher.EP;

public class JvmDeclarationSearch {

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
  @NotNull
  public static Collection<JvmElement> getElementsByIdentifier(@NotNull PsiElement identifierElement) {
    Collection<JvmElement> result = new SmartHashSet<>();
    for (JvmDeclarationSearcher search : EP.allForLanguage(identifierElement.getLanguage())) {
      ProgressManager.checkCanceled();
      search.findDeclarationsByIdentifier(identifierElement, result::add);
    }
    ProgressManager.checkCanceled();
    return result;
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
  @NotNull
  public static Collection<JvmElement> getImmediatelyContainingElements(@NotNull PsiElement place) {
    List<JvmDeclarationSearcher> extensions = EP.allForLanguage(place.getLanguage());
    if (extensions.isEmpty()) return Collections.emptyList();

    Set<JvmElement> result = new SmartHashSet<>();
    PsiElement current = place;
    while (current != null) {
      for (JvmDeclarationSearcher searcher : extensions) {
        searcher.findDeclarations(current, result::add);
        if (!result.isEmpty()) {
          return result;
        }
      }
      current = current.getParent();
    }
    return Collections.emptyList();
  }
}
