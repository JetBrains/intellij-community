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
package com.intellij.psi.presentation.java;

import com.intellij.navigation.ColoredItemPresentation;
import com.intellij.navigation.ItemPresentation;
import com.intellij.navigation.ItemPresentationProvider;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.psi.PsiPackage;
import com.intellij.util.PlatformIcons;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class PackagePresentationProvider implements ItemPresentationProvider<PsiPackage> {
  @Override
  public ItemPresentation getPresentation(@NotNull final PsiPackage aPackage) {
    return new ColoredItemPresentation() {
      @Override
      public TextAttributesKey getTextAttributesKey() {
        return null;
      }

      @Override
      public String getPresentableText() {
        return aPackage.getName();
      }

      @Override
      public String getLocationString() {
        return aPackage.getQualifiedName();
      }

      @Override
      public Icon getIcon(boolean open) {
        return PlatformIcons.PACKAGE_ICON;
      }
    };
  }
}
