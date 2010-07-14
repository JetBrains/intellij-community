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

/*
 * @author max
 */
package com.intellij.util;

import com.intellij.ide.IconProvider;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.DumbAware;
import com.intellij.psi.PsiElement;
import com.intellij.ui.DeferredIcon;
import com.intellij.ui.IconDeferrer;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class PsiIconUtil {

  @Nullable
  public static Icon getProvidersIcon(PsiElement element, int flags) {
    final boolean dumb = DumbService.getInstance(element.getProject()).isDumb();
    for (final IconProvider iconProvider : getIconProviders()) {
      if (dumb && !DumbService.isDumbAware(iconProvider)) {
        continue;
      }

      final Icon icon = iconProvider.getIcon(element, flags);
      if (icon != null) return icon;
    }
    return null;
  }

  private static class IconProviderHolder {
    private static final IconProvider[] ourIconProviders = Extensions.getExtensions(IconProvider.EXTENSION_POINT_NAME);
  }

  private static IconProvider[] getIconProviders() {
    return IconProviderHolder.ourIconProviders;
  }

}