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
package com.intellij.openapi.editor.colors;

import com.intellij.openapi.editor.markup.TextAttributes;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.Nullable;

/**
 * Gives the {@link TextAttributes} for a {@link TextAttributesKey}.
 * <p>
 * {@link EditorColorsScheme} is the main implementation.
 */
public interface TextAttributesScheme {
  /**
   * Returns the attributes for {@code key}.
   * <p>
   * An implementation can look at the fallback key of {@code key} and at the parent scheme.
   * The result can be {@code null} also for a non-null key, when no source has a value.
   * Use {@link TextAttributesKey#getDefaultAttributes()} as the fallback when a caller needs a value.
   * <p>
   * The scheme can share the returned object.
   * Do not change it. Change a {@link TextAttributes#clone() clone} instead.
   *
   * @return the attributes, or {@code null} when {@code key} is {@code null} or the scheme has no value for it
   */
  @Contract("null -> null")
  TextAttributes getAttributes(@Nullable TextAttributesKey key);
}
