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
package com.intellij.ide.structureView;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.extensions.KeyedFactoryEPBean;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileTypes.FileTypeExtensionFactory;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

/**
 * Defines the implementation of Structure View and the file structure popup for
 * a file type. This class allows to replace the entire Structure View component
 * implementation. If it is acceptable to have the standard component implementation
 * and to customize only how the Structure View is populated with the file data,
 * the standard implementation of this interface - {@link TreeBasedStructureViewBuilder} -
 * should be used.
 *
 * @see com.intellij.lang.LanguageStructureViewBuilder#getStructureViewBuilder(com.intellij.psi.PsiFile)
 */

public interface StructureViewBuilder {
  ExtensionPointName<KeyedFactoryEPBean> EP_NAME = ExtensionPointName.create("com.intellij.structureViewBuilder");

  StructureViewBuilderProvider PROVIDER =
    new FileTypeExtensionFactory<>(StructureViewBuilderProvider.class, EP_NAME).get();

  /**
   * Returns the structure view implementation for the file displayed in the specified
   * editor.
   *
   * @param fileEditor the editor for which the structure view is requested.
   * @param project    the project containing the file for which the structure view is requested.
   * @return the structure view implementation.
   * @see TreeBasedStructureViewBuilder
   */
  @NotNull
  StructureView createStructureView(FileEditor fileEditor, @NotNull Project project);
}
