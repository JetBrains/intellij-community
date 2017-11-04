/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;


/**
 * @author Maxim.Mossienko
 */
public interface CustomizableReferenceProvider {

  void setOptions(@Nullable Map<CustomizationKey, Object> options);

  @Nullable
  Map<CustomizationKey, Object> getOptions();

  @NotNull
  PsiReference[] getReferencesByElement(@NotNull PsiElement element, @NotNull ProcessingContext context);


  final class CustomizationKey<T> {
    
    private final String myOptionDescription;

    public CustomizationKey(@NonNls String optionDescription) {
      myOptionDescription = optionDescription;
    }

    public String toString() { return myOptionDescription; }

    /** @noinspection unchecked*/
    public T getValue(@Nullable Map<CustomizationKey, Object> options) {
      return options == null ? null : (T)options.get(this);
    }

    public boolean getBooleanValue(@Nullable Map<CustomizationKey, Object> options) {
      Boolean o = options == null ? null : (Boolean)options.get(this);
      return o != null && o.booleanValue();
    }

    public void putValue(Map<CustomizationKey, Object> options, T value) {
      options.put(this, value);
    }
  }
}
