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

import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.options.colors.AttributesDescriptor;
import com.intellij.openapi.options.colors.ColorSettingsPage;
import com.intellij.openapi.options.colors.ColorSettingsPages;
import com.intellij.openapi.util.Pair;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class ColorSettingsPagesImpl extends ColorSettingsPages {
  private final List<ColorSettingsPage> myPages = new ArrayList<>();
  private boolean myExtensionsLoaded = false;
  private final Map<TextAttributesKey, Pair<ColorSettingsPage, AttributesDescriptor>> myKeyToDescriptorMap =
    new HashMap<>();

  @Override
  public void registerPage(ColorSettingsPage page) {
    myPages.add(page);
  }

  @Override
  public ColorSettingsPage[] getRegisteredPages() {
    if (!myExtensionsLoaded) {
      myExtensionsLoaded = true;
      Collections.addAll(myPages, Extensions.getExtensions(ColorSettingsPage.EP_NAME));
    }
    return myPages.toArray(new ColorSettingsPage[myPages.size()]);
  }

  @Override
  @Nullable
  public Pair<ColorSettingsPage,AttributesDescriptor> getAttributeDescriptor(TextAttributesKey key) {
    if (myKeyToDescriptorMap.containsKey(key)) {
      return myKeyToDescriptorMap.get(key);
    }
    else {
      for (ColorSettingsPage page : getRegisteredPages()) {
        for (AttributesDescriptor descriptor : page.getAttributeDescriptors()) {
          if (descriptor.getKey() == key) {
            Pair<ColorSettingsPage,AttributesDescriptor> result = Pair.create(page, descriptor);
            myKeyToDescriptorMap.put(key, result);
            return result;
          }
        }
      }
      myKeyToDescriptorMap.put(key, null);
    }
    return null;
  }
}
