/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.lang;

import com.intellij.ide.structureView.StructureViewBuilder;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.Nullable;

/**
 * Should be registered as language extension
 * @see LanguageStructureViewBuilder
 */
public interface PsiStructureViewFactory {

  String EP_NAME = "com.intellij.lang.psiStructureViewFactory";

  @Nullable
  StructureViewBuilder getStructureViewBuilder(PsiFile psiFile);
}