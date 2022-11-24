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
package com.intellij.injected.editor;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.util.UserDataHolderEx;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

public interface EditorWindow extends UserDataHolderEx, Editor {
  boolean isValid();

  @NotNull
  PsiFile getInjectedFile();

  @NotNull
  LogicalPosition hostToInjected(@NotNull LogicalPosition hPos);

  @NotNull
  LogicalPosition injectedToHost(@NotNull LogicalPosition pos);

  @NotNull
  Editor getDelegate();

  @NotNull
  @Override
  DocumentWindow getDocument();
}
