/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.slicer;

import com.intellij.lang.LanguageExtension;
import com.intellij.psi.PsiElement;
import com.intellij.util.Processor;
import org.jetbrains.annotations.NotNull;

/**
 * @author vlan
 */
public abstract class SliceProvider {
  public static final LanguageExtension<SliceProvider> EXTENSION = new LanguageExtension<SliceProvider>("com.intellij.lang.sliceProvider");

  public static SliceProvider forElement(@NotNull PsiElement element) {
    return EXTENSION.forLanguage(element.getLanguage());
  }

  public abstract boolean processUsagesFlownDownTo(@NotNull PsiElement element, @NotNull Processor<SliceUsage> processor,
                                                   @NotNull SliceUsage parent);
  public abstract boolean processUsagesFlownFromThe(@NotNull PsiElement element, @NotNull Processor<SliceUsage> processor,
                                                    @NotNull SliceUsage parent);
}
