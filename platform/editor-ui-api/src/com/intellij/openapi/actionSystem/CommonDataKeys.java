/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.openapi.actionSystem;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.Navigatable;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;

public class CommonDataKeys {
  public static final DataKey<Project> PROJECT = DataKey.create("project");
  public static final DataKey<Editor> EDITOR = DataKey.create("editor");

  /**
   * Returns com.intellij.openapi.editor.Editor even if focus currently is in find bar
   */
  public static final DataKey<Editor> EDITOR_EVEN_IF_INACTIVE = DataKey.create("editor.even.if.inactive");
  public static final DataKey<Navigatable> NAVIGATABLE = DataKey.create("Navigatable");
  public static final DataKey<Navigatable[]> NAVIGATABLE_ARRAY = DataKey.create("NavigatableArray");
  public static final DataKey<VirtualFile> VIRTUAL_FILE = DataKey.create("virtualFile");
  public static final DataKey<VirtualFile[]> VIRTUAL_FILE_ARRAY = DataKey.create("virtualFileArray");

  public static final DataKey<PsiElement> PSI_ELEMENT = DataKey.create("psi.Element");
  public static final DataKey<PsiFile> PSI_FILE = DataKey.create("psi.File");
}
