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
 */
public interface LineMarkerProvider {
  /**
   * Get line markers for this PsiElement.
   * <p/>
   * NOTE for implementers:
   * Please return line marker info for exact element you were asked for.
   * For example, do not return class marker info if getLineMarkerInfo() was called for a method.
   * Please return relevant line marker info for as small element as possible.
   * For example, do not return method marker for PsiMethod. Instead, return it for the PsiIdentifier which is a name of this method.
   * <p/>
   * More technical details:
   * Inspection (specifically, LineMarkersPass) for performance reasons queries all LineMarkerProviders in two passes:
   * <ul>
   * <li>first pass for all elements in visible area</li>
   * <li>second pass for all the rest elements</li>
   * </ul>
   * If providers return nothing for either area, its line markers are cleared.
   * <p/>
   * So if, for example a method, is half-visible (e.g. its name is visible but a part of its body isn't) and
   * some poorly written LineMarkerProvider returns info for the PsiMethod instead of PsiIdentifier then following happens:
   * <ul>
   * <li>the first pass removes line marker info because whole PsiMethod is not visible</li>
   * <li>the second pass tries to add line marker info back because LineMarkerProvider is called for the PsiMethod at last</li>
   * </ul>
   * As a result, line marker icon blinks annoyingly.
   */
  @Nullable
  LineMarkerInfo getLineMarkerInfo(@NotNull PsiElement element);

  void collectSlowLineMarkers(@NotNull List<PsiElement> elements, @NotNull Collection<LineMarkerInfo> result);
}
