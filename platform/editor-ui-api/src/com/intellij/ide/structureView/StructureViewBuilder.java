// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.structureView;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.extensions.KeyedFactoryEPBean;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileTypes.FileTypeExtensionFactory;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Defines the implementation of Structure View and the file structure popup for
 * a file type. This class allows to replace the entire Structure View component
 * implementation. If it is acceptable to have the standard component implementation
 * and to customize only how the Structure View is populated with the file data,
 * the standard implementation of this interface - {@link TreeBasedStructureViewBuilder} -
 * should be used.
 *
 * @see com.intellij.lang.LanguageStructureViewBuilder#getStructureViewBuilder(com.intellij.psi.PsiFile)
 * @see com.intellij.lang.PsiStructureViewFactory#getStructureViewBuilder(com.intellij.psi.PsiFile)}
 */

public interface StructureViewBuilder {
  ExtensionPointName<KeyedFactoryEPBean> EP_NAME = ExtensionPointName.create("com.intellij.structureViewBuilder");

  StructureViewBuilderProvider PROVIDER =
    new FileTypeExtensionFactory<>(StructureViewBuilderProvider.class, EP_NAME).get();

  /**
   * Returns the structure view implementation for the specified file
   *
   * @param fileEditor the editor for which the structure view is requested. Can be null if file is not open (e.g. structure is requested
   *                   from the project view)
   * @param project    the project containing the file for which the structure view is requested.
   * @return the structure view implementation.
   * @see TreeBasedStructureViewBuilder
   */
  @NotNull
  StructureView createStructureView(@Nullable FileEditor fileEditor, @NotNull Project project);
}
