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
import com.intellij.openapi.editor.colors.CodeInsightColors;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.openapi.util.Iconable;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassOwner;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * @author yole
 */
public class ClassPresentationProvider implements ItemPresentationProvider<PsiClass> {
  @Override
  public ItemPresentation getPresentation(@NotNull final PsiClass psiClass) {
    return new ColoredItemPresentation() {
      @Override
      public String getPresentableText() {
        return ClassPresentationUtil.getNameForClass(psiClass, false);
      }

      @Override
      public String getLocationString() {
        PsiFile file = psiClass.getContainingFile();
        if (file instanceof PsiClassOwner) {
          PsiClassOwner classOwner = (PsiClassOwner)file;
          String packageName = classOwner.getPackageName();
          if (packageName.isEmpty()) return null;
          return "(" + packageName + ")";
        }
        return null;
      }

      @Override
      public TextAttributesKey getTextAttributesKey() {
        try {
          if (psiClass.isDeprecated()) {
            return CodeInsightColors.DEPRECATED_ATTRIBUTES;
          }
        }
        catch (IndexNotReadyException ignore) {
        }
        return null;
      }

      @Override
      public Icon getIcon(boolean open) {
        return psiClass.getIcon(Iconable.ICON_FLAG_VISIBILITY | Iconable.ICON_FLAG_READ_STATUS);
      }
    };
  }
}
