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

package com.intellij.codeInsight.editorActions.wordSelection;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.util.Condition;
import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;

public class WordSelectioner extends AbstractWordSelectioner {
  private static final ExtensionPointName<Condition<PsiElement>> EP_NAME = ExtensionPointName.create("com.intellij.basicWordSelectionFilter");

  @Override
  public boolean canSelect(@NotNull PsiElement e) {
    if (e instanceof PsiComment || e instanceof PsiWhiteSpace) {
      return false;
    }
    for (Condition<PsiElement> filter : Extensions.getExtensions(EP_NAME)) {
      if (!filter.value(e)) {
        return false;
      }
    }
    return true;
  }
}
