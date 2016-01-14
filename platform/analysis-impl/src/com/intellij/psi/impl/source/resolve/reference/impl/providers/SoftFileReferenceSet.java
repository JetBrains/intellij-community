/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.psi.impl.source.resolve.reference.impl.providers;

import com.intellij.openapi.fileTypes.FileType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReferenceProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Dennis.Ushakov
 */
public class SoftFileReferenceSet extends FileReferenceSet {
  public SoftFileReferenceSet(String str,
                              @NotNull PsiElement element,
                              int startInElement,
                              PsiReferenceProvider provider,
                              boolean caseSensitive,
                              boolean endingSlashNotAllowed,
                              @Nullable FileType[] suitableFileTypes) {
    super(str, element, startInElement, provider, caseSensitive, endingSlashNotAllowed, suitableFileTypes);
  }

  public SoftFileReferenceSet(String str,
                              @NotNull PsiElement element,
                              int startInElement,
                              PsiReferenceProvider provider,
                              boolean caseSensitive,
                              boolean endingSlashNotAllowed, @Nullable FileType[] suitableFileTypes, boolean init) {
    super(str, element, startInElement, provider, caseSensitive, endingSlashNotAllowed, suitableFileTypes, init);
  }

  public SoftFileReferenceSet(String str,
                              @NotNull PsiElement element,
                              int startInElement,
                              @Nullable PsiReferenceProvider provider, boolean isCaseSensitive) {
    super(str, element, startInElement, provider, isCaseSensitive);
  }

  public SoftFileReferenceSet(@NotNull String str,
                              @NotNull PsiElement element,
                              int startInElement,
                              PsiReferenceProvider provider, boolean isCaseSensitive, boolean endingSlashNotAllowed) {
    super(str, element, startInElement, provider, isCaseSensitive, endingSlashNotAllowed);
  }

  public SoftFileReferenceSet(@NotNull PsiElement element) {
    super(element);
  }

  @Override
  protected boolean isSoft() {
    return true;
  }
}
