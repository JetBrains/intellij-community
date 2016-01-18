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
package com.intellij.codeInsight.daemon;

import com.intellij.codeInsight.daemon.impl.HighlightInfoType;
import com.intellij.codeInsight.daemon.impl.JavaHighlightInfoTypes;
import com.intellij.openapi.editor.Document;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.ExpectedHighlightingData;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;

public class JavaExpectedHighlightingData extends ExpectedHighlightingData {
  public JavaExpectedHighlightingData(@NotNull Document document,
                                      boolean checkWarnings,
                                      boolean checkWeakWarnings,
                                      boolean checkInfos, @Nullable PsiFile file) {
    super(document, checkWarnings, checkWeakWarnings, checkInfos, file);
  }

  @Override
  protected HighlightInfoType getTypeByName(String typeString) throws Exception {
    try {
      Field field = JavaHighlightInfoTypes.class.getField(typeString);
      return (HighlightInfoType)field.get(null);
    }
    catch (Exception e) {
      return super.getTypeByName(typeString);
    }
  }
}
