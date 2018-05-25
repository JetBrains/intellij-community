/*
 * Copyright 2000-2011 JetBrains s.r.o.
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

package com.intellij.codeInsight.folding.impl;

import com.intellij.lang.folding.FoldingBuilder;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class FoldingPolicy {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.folding.impl.FoldingPolicy");
  
  private static final GenericElementSignatureProvider GENERIC_PROVIDER = new GenericElementSignatureProvider();
  
  private FoldingPolicy() {}

  static boolean isCollapsedByDefault(@NotNull PsiElement element, @NotNull FoldingBuilder foldingBuilder) {
    try {
      return foldingBuilder.isCollapsedByDefault(element.getNode());
    }
    catch (IndexNotReadyException e) {
      LOG.error(e);
      return false;
    }
  }

  @Nullable
  public static String getSignature(@NotNull PsiElement element) {
    for(ElementSignatureProvider provider: Extensions.getExtensions(ElementSignatureProvider.EP_NAME)) {
      String signature = provider.getSignature(element);
      if (signature != null) return signature;
    }
    return GENERIC_PROVIDER.getSignature(element);
  }
  
  @Nullable
  public static PsiElement restoreBySignature(@NotNull PsiFile file, @NotNull String signature) {
    return restoreBySignature(file, signature, null);
  }

  /**
   * Tries to restore target PSI element from the given file by the given signature.
   * 
   * @param file                   target PSI file
   * @param signature              target element's signature
   * @param processingInfoStorage  buffer used for tracing 'restore element' processing (if necessary)
   * @return                       PSI element from the given PSI file that corresponds to the given signature (if found)
   *                               {@code null} otherwise
   */
  @Nullable
  public static PsiElement restoreBySignature(@NotNull PsiFile file,
                                              @NotNull String signature,
                                              @Nullable StringBuilder processingInfoStorage)
  {
    for(ElementSignatureProvider provider: Extensions.getExtensions(ElementSignatureProvider.EP_NAME)) {
      PsiElement result = provider.restoreBySignature(file, signature, processingInfoStorage);
      if (result != null) return result;
    }
    return GENERIC_PROVIDER.restoreBySignature(file, signature, processingInfoStorage);
  }
}
