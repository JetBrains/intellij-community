// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight;

import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.UserDataHolder;
import com.intellij.psi.PsiModifierListOwner;
import com.intellij.psi.util.CachedValueProvider;
import org.jetbrains.annotations.NotNull;

/**
 * Converts element to normalized one before working with its cache.<br>
 * <br>
 * Without this step there might be a conflict between two cache providers:
 * <ul>
 *   <li>first provider adds some data to elements cache</li>
 *   <li>second provider wants to extract the same data</li>
 *   <li>second provider tries to update data and finds out that it is added by another provider</li>
 *   <li>exception is thrown since this might lead to unstable state of cache</li>
 * </ul>
 * <br>
 * After adding this step this conflict is resolved:
 * <ul>
 *   <li>first provider gets normalized version of the element</li>
 *   <li>first provider adds some data using normalized version of element</li>
 *   <li>
 *     second provider wants to extract some data, it normalizes element beforehand.
 *     if element is not the same then no data found, so there's no conflict.
 *     otherwise we continue to the next step.
 *   </li>
 *   <li> 
 *     second provider tries to update data and finds out that it is added by the same provider
 *     (since there equality is checked by fields of providers and types)
 *     </li>
 *   <li> everything is ok, exception is not thrown</li>
 * </ul>
 * 
 * @see com.intellij.psi.util.CachedValuesManager#getCachedValue(UserDataHolder, Key, CachedValueProvider, boolean) 
 */
public abstract class AnnotationCacheOwnerNormalizer {

  /**
   * @param listOwner element to normalize
   * @return if there's no normalizer same element is returned. otherwise, normalized version is returned
   */
  public static @NotNull PsiModifierListOwner normalize(@NotNull PsiModifierListOwner listOwner) {
    AnnotationCacheOwnerNormalizer normalizer = listOwner.getProject().getService(AnnotationCacheOwnerNormalizer.class);
    return normalizer == null ? listOwner : normalizer.doNormalize(listOwner);
  }
  
  protected abstract @NotNull PsiModifierListOwner doNormalize(@NotNull PsiModifierListOwner listOwner); 
}
