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
package com.intellij.psi.presentation.java;

import com.intellij.icons.AllIcons;
import com.intellij.navigation.ItemPresentation;
import com.intellij.navigation.ItemPresentationProvider;
import com.intellij.psi.PsiJavaModule;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class JavaModulePresentationProvider implements ItemPresentationProvider<PsiJavaModule> {
  @Override
  public ItemPresentation getPresentation(@NotNull final PsiJavaModule item) {
    return new ItemPresentation() {
      @Nullable
      @Override
      public String getPresentableText() {
        return item.getModuleName();
      }

      @Nullable
      @Override
      public String getLocationString() {
        return null;
      }

      @Nullable
      @Override
      public Icon getIcon(boolean unused) {
        return AllIcons.Nodes.JavaModule;
      }
    };
  }
}