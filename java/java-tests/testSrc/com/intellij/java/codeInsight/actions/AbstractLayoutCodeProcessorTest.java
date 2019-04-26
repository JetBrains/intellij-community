/*
 * Copyright 2000-2018 JetBrains s.r.o.
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
package com.intellij.java.codeInsight.actions;

import com.intellij.codeInsight.actions.ReformatFilesOptions;
import com.intellij.codeInsight.actions.TextRangeType;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.module.Module;
import com.intellij.psi.PsiElement;
import com.intellij.psi.search.SearchScope;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;


class AdditionalEventInfo {
  @Nullable private Editor myEditor;
  @Nullable private Module myModule;
  @Nullable private PsiElement myElement;

  @Nullable
  Module getModule() {
    return myModule;
  }

  AdditionalEventInfo setModule(@Nullable Module module) {
    myModule = module;
    return this;
  }

  @Nullable
  Editor getEditor() {
    return myEditor;
  }

  @Nullable
  PsiElement getElement() {
    return myElement;
  }

  AdditionalEventInfo setPsiElement(@Nullable PsiElement element) {
    myElement = element;
    return this;
  }

  AdditionalEventInfo setEditor(@Nullable Editor editor) {
    myEditor = editor;
    return this;
  }
}

class MockReformatFileSettings implements ReformatFilesOptions {
  private boolean myOptimizeImports;

  @Nullable
  @Override
  public SearchScope getSearchScope() {
    return null;
  }

  @Nullable
  @Override
  public String getFileTypeMask() {
    return null;
  }

  @Override
  public TextRangeType getTextRangeType() {
    return TextRangeType.WHOLE_FILE;
  }

  @Override
  public boolean isRearrangeCode() {
    return false;
  }

  @Override
  public boolean isOptimizeImports() {
    return myOptimizeImports;
  }

  @NotNull
  MockReformatFileSettings setOptimizeImports(boolean optimizeImports) {
    myOptimizeImports = optimizeImports;
    return this;
  }
}


