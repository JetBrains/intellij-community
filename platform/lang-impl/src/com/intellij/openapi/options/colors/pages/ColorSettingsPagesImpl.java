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
package com.intellij.openapi.options.colors.pages;

import com.intellij.application.options.colors.ColorSettingsUtil;
import com.intellij.openapi.editor.colors.ColorKey;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.options.colors.*;
import com.intellij.openapi.util.Pair;
import com.intellij.util.containers.FactoryMap;
import com.intellij.util.containers.JBIterable;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.Map;

public class ColorSettingsPagesImpl extends ColorSettingsPages {
  private final Map<Object, Pair<ColorAndFontDescriptorsProvider, ? extends AbstractKeyDescriptor>> myCache =
    FactoryMap.createMap(this::getDescriptorImpl);

  @Override
  public void registerPage(ColorSettingsPage page) {
    Extensions.getRootArea().getExtensionPoint(ColorSettingsPage.EP_NAME).registerExtension(page);
  }

  @Override
  public ColorSettingsPage[] getRegisteredPages() {
    return ColorSettingsPage.EP_NAME.getExtensions();
  }

  @Nullable
  @Override
  public Pair<ColorAndFontDescriptorsProvider, AttributesDescriptor> getAttributeDescriptor(TextAttributesKey key) {
    //noinspection unchecked
    return (Pair<ColorAndFontDescriptorsProvider, AttributesDescriptor>)myCache.get(key);
  }

  @Nullable
  @Override
  public Pair<ColorAndFontDescriptorsProvider, ColorDescriptor> getColorDescriptor(ColorKey key) {
    //noinspection unchecked
    return (Pair<ColorAndFontDescriptorsProvider, ColorDescriptor>)myCache.get(key);
  }

  @Nullable
  private Pair<ColorAndFontDescriptorsProvider, ? extends AbstractKeyDescriptor> getDescriptorImpl(Object key) {
    ColorAndFontDescriptorsProvider[] extensions = Extensions.getExtensions(ColorAndFontDescriptorsProvider.EP_NAME);
    JBIterable<ColorAndFontDescriptorsProvider> providers = JBIterable.empty();
    for (ColorAndFontDescriptorsProvider page : providers.append(getRegisteredPages()).append(extensions)) {
      Iterable<? extends AbstractKeyDescriptor> descriptors =
        key instanceof TextAttributesKey ? ColorSettingsUtil.getAllAttributeDescriptors(page) :
        key instanceof ColorKey ? JBIterable.of(page.getColorDescriptors()) :
        Collections.emptyList();
      for (AbstractKeyDescriptor descriptor : descriptors) {
        if (descriptor.getKey() == key) {
          return Pair.create(page, descriptor);
        }
      }
    }
    return null;
  }
}
