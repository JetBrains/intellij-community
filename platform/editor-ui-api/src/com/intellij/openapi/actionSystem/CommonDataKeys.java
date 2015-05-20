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
package com.intellij.openapi.actionSystem;

import com.intellij.openapi.editor.Caret;
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
   * This key can be used to obtain reference to host editor instance, in case {@link #EDITOR} key is referring to an injected editor.
   */
  public static final DataKey<Editor> HOST_EDITOR = DataKey.create("host.editor");
  /**
   * A key to retrieve caret instance (in host or injected editor, depending on context).
   */
  public static final DataKey<Caret> CARET = DataKey.create("caret");
  /**
   * Returns com.intellij.openapi.editor.Editor even if focus currently is in find bar
   */
  public static final DataKey<Editor> EDITOR_EVEN_IF_INACTIVE = DataKey.create("editor.even.if.inactive");

  /**
   * This key can be used to obtain {@link Navigatable} instance.
   *<br>
   * If {@link DataContext} has a value for {@link #PSI_ELEMENT} key which implements {@link Navigatable} it will automatically
   * return it for this key if explicit value isn't provided.
   */
  public static final DataKey<Navigatable> NAVIGATABLE = DataKey.create("Navigatable");

  /**
   * This key can be used to obtain several navigatables e.g. if several elements are selected in a component.
   *<br>
   * Note that if {@link DataContext} has a value for {@link #NAVIGATABLE} key it will automatically return single-element array for this
   * key if explicit value isn't provided so there is no need to perform such wrapping by hand.
   */
  public static final DataKey<Navigatable[]> NAVIGATABLE_ARRAY = DataKey.create("NavigatableArray");

  /**
   * This key can be used to obtain {@link VirtualFile} instance.
   *<br>
   * Note that if {@link DataContext} has a value for {@link #PSI_FILE} key it will automatically return the corresponding {@link VirtualFile}
   * for this key if explicit value isn't provided. Also it'll return the containing file if a value for {@link #PSI_ELEMENT} key is provided.
   */
  public static final DataKey<VirtualFile> VIRTUAL_FILE = DataKey.create("virtualFile");

  /**
   * This key can be used to obtain several {@link VirtualFile} instances e.g. if several elements are selected in a component.
   *<br>
   * Note that if {@link DataContext} doesn't have an explicit value for this key it will automatically collect {@link VirtualFile} instances
   * corresponding to values provided for {@link #VIRTUAL_FILE}, {@link #PSI_FILE}, {@link #PSI_ELEMENT} and other keys and return the array
   * containing unique instances of the found files.
   */
  public static final DataKey<VirtualFile[]> VIRTUAL_FILE_ARRAY = DataKey.create("virtualFileArray");

  public static final DataKey<PsiElement> PSI_ELEMENT = DataKey.create("psi.Element");

  /**
   * This key can be used to obtain several {@link PsiFile} instance.
   *<br>
   * Note that if {@link DataContext} has a value for {@link #VIRTUAL_FILE} key it will automatically return the corresponding {@link PsiFile}
   * for this key if explicit value isn't provided. Also it'll return the containing file if a value for {@link #PSI_ELEMENT} key is provided.
   */
  public static final DataKey<PsiFile> PSI_FILE = DataKey.create("psi.File");
}
