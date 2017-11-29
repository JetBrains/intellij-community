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
package com.intellij.openapi.options.colors;

import com.intellij.openapi.editor.colors.TextAttributesKey;
import org.jetbrains.annotations.NotNull;

/**
 * Describes a text attribute key the attributes for which can be configured in a custom
 * colors and fonts page.
 *
 * @see ColorSettingsPage#getAttributeDescriptors()
 */
public final class AttributesDescriptor extends AbstractKeyDescriptor<TextAttributesKey> {

  /**
   * Creates an attribute descriptor with the specified name and text attributes key.
   *
   * @param displayName the name of the attribute shown in the colors list.
   * @param key         the attributes key for which the colors are specified.
   */
  public AttributesDescriptor(@NotNull String displayName, @NotNull TextAttributesKey key) {
    super(displayName, key);
  }

  @NotNull
  @Override
  public String getDisplayName() {
    return super.getDisplayName();
  }

  @NotNull
  @Override
  public TextAttributesKey getKey() {
    return super.getKey();
  }
}