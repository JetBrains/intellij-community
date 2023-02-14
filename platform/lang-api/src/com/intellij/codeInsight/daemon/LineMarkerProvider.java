// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.codeInsight.daemon;

import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;

/**
 * This extension point specifies line markers (icons in the vertical gutter at the left of the screen) for the particular {@link PsiElement}.
 * For example, {@link com.intellij.codeInsight.daemon.impl.JavaLineMarkerProvider} draws "arrow down" icon left to the Java method to navigate to all its overriding methods.
 *
 * @see LineMarkerProviders#EP_NAME
 * @see LineMarkerProviderDescriptor
 * @see RelatedItemLineMarkerProvider
 */
public interface LineMarkerProvider {
  /**
   * Get line markers for this PsiElement.
   * <p/>
   * NOTE for implementers:
   * Please create line marker info for leaf elements only - i.e., the smallest possible elements.
   * For example, instead of returning the line marker for {@code PsiMethod},
   * create the marker for the {@code PsiIdentifier} which is a name of this method.
   * <p/>
   * More technical details:<p>
   * Highlighting (specifically, LineMarkersPass) queries all LineMarkerProviders in two passes (for performance reasons):
   * <ul>
   * <li>first pass for all elements in visible area</li>
   * <li>second pass for all the rest elements</li>
   * </ul>
   * If provider returned nothing for both areas, its line markers are cleared.
   * <p/>
   * So imagine a {@code LineMarkerProvider} which (incorrectly) written like this:
   * <pre>
   * {@code
   *   class MyBadLineMarkerProvider implements LineMarkerProvider {
   *     public LineMarkerInfo getLineMarkerInfo(PsiElement element) {
   *       if (element instanceof PsiMethod)  // ACTUALLY DON'T!
   *          return new LineMarkerInfo(element, element.getTextRange(), icon, null,null, alignment);
   *       else
   *         return null;
   *     }
   *   }
   * }
   * </pre>
   * Note that it creates LineMarkerInfo for the whole method body.
   * Following will happen when this method is half-visible (e.g., its name is visible but a part of its body isn't):
   * <ul>
   * <li>the first pass would remove line marker info because the whole PsiMethod isn't visible</li>
   * <li>the second pass would try to add line marker info back when LineMarkerProvider is called for this PsiMethod at last</li>
   * </ul>
   * As a result, line marker icon will blink annoyingly.
   * Instead, write this:
   * <pre>
   * {@code
   *   class MyGoodLineMarkerProvider implements LineMarkerProvider {
   *     public LineMarkerInfo getLineMarkerInfo(PsiElement element) {
   *       if (element instanceof PsiIdentifier &&
   *           (parent = element.getParent()) instanceof PsiMethod &&
   *           ((PsiMethod)parent).getMethodIdentifier() == element))  // aha, we are looking at method name
   *         return new LineMarkerInfo(element, element.getTextRange(), icon, null,null, alignment);
   *       else
   *         return null;
   *     }
   *   }
   * }
   * </pre>
   */
  LineMarkerInfo<?> getLineMarkerInfo(@NotNull PsiElement element);

  /**
   * Collects line markers for several PsiElements in batch, after all (relatively faster) {@link #getLineMarkerInfo(PsiElement)} calls are finished.
   */
  default void collectSlowLineMarkers(@NotNull List<? extends PsiElement> elements, @NotNull Collection<? super LineMarkerInfo<?>> result) {
  }
}
