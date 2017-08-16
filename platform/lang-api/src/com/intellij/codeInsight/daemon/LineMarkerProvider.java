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

package com.intellij.codeInsight.daemon;

import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;

/**
 * @author yole
 * @see LineMarkerProviders#EP_NAME
 * @see LineMarkerProviderDescriptor
 */
public interface LineMarkerProvider {
  /**
   * Get line markers for this PsiElement.
   * <p/>
   * NOTE for implementers:
   * Please create line marker info for leaf elements only - i.e. the smallest possible elements.
   * For example, instead of returning method marker for {@code PsiMethod},
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
   * {@code class MyBadLineMarkerProvider implements LineMarkerProvider {
   *     public LineMarkerInfo getLineMarkerInfo(PsiElement element) {
   *       if (element instanceof PsiMethod) return // ACTUALLY DONT!
   *            new LineMarkerInfo(element, element.getTextRange(), icon, null,null, alignment);
   *       else return null;
   *     }
   *     ...
   *   }
   * }
   * </pre>
   * Note that it create LIneMarkerInfo for the whole method body.
   * Following will happen when this method is half-visible (e.g. its name is visible but a part of its body isn't):
   * <ul>
   * <li>the first pass would remove line marker info because the whole PsiMethod isn't visible</li>
   * <li>the second pass would try to add line marker info back because LineMarkerProvider was called for the PsiMethod at last</li>
   * </ul>
   * As a result, line marker icon will blink annoyingly.
   * Instead, write this:
   * <pre>
   * {@code class MyGoodLineMarkerProvider implements LineMarkerProvider {
   *     public LineMarkerInfo getLineMarkerInfo(PsiElement element) {
   *       if (element instanceof PsiIdentifier &&
   *           (parent = element.getParent()) instanceof PsiMethod &&
   *           ((PsiMethod)parent).getMethodIdentifier() == element)) // aha, we are at method name
   *            return new LineMarkerInfo(element, element.getTextRange(), icon, null,null, alignment);
   *       else return null;
   *     }
   *     ...
   *   }
   * }
   * </pre>
   */
  @Nullable
  LineMarkerInfo getLineMarkerInfo(@NotNull PsiElement element);

  void collectSlowLineMarkers(@NotNull List<PsiElement> elements, @NotNull Collection<LineMarkerInfo> result);
}
