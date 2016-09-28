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
package com.intellij.json.psi.impl;

import com.intellij.json.JsonLanguage;
import com.intellij.json.psi.JsonFile;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFileSystemItem;
import com.intellij.psi.impl.PsiTreeChangeEventImpl;
import com.intellij.psi.impl.PsiTreeChangePreprocessorBase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class JsonTreeChangePreprocessor extends PsiTreeChangePreprocessorBase {
  public JsonTreeChangePreprocessor(@NotNull Project project) {
    super(project);
  }

  @Override
  public void treeChanged(@NotNull PsiTreeChangeEventImpl event) {
    if (event.getFile() instanceof JsonFile) {
      super.treeChanged(event);
    }
  }

  @Override
  protected boolean isInsideCodeBlock(@Nullable PsiElement element) {
    if (element instanceof PsiFileSystemItem) {
      return false;
    }
    if (element == null || element.getParent() == null) {
      return true;
    }
    return !(element.getLanguage() instanceof JsonLanguage);
  }
}