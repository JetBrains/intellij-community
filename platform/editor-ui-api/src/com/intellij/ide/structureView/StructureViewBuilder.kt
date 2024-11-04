// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.structureView

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.extensions.ExtensionPointName.Companion.create
import com.intellij.openapi.extensions.KeyedFactoryEPBean
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileTypes.FileTypeExtensionFactory
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.ApiStatus

/**
 * Defines the implementation of Structure View and the file structure popup for
 * a file type. This class allows to replace the entire Structure View component
 * implementation. If it is acceptable to have the standard component implementation
 * and to customize only how the Structure View is populated with the file data,
 * the standard implementation of this interface - [TreeBasedStructureViewBuilder] -
 * should be used.
 *
 * @see com.intellij.lang.LanguageStructureViewBuilder.getStructureViewBuilder
 * @see com.intellij.lang.PsiStructureViewFactory.getStructureViewBuilder
 */
interface StructureViewBuilder {
  /**
   * Returns the structure view implementation for the specified file
   *
   * @param fileEditor the editor for which the structure view is requested. Can be null if file is not open (e.g. structure is requested
   * from the project view)
   * @param project    the project containing the file for which the structure view is requested.
   * @return the structure view implementation.
   * @see TreeBasedStructureViewBuilder
   */
  fun createStructureView(fileEditor: FileEditor?, project: Project): StructureView

  @ApiStatus.Internal
  companion object {
    @ApiStatus.Internal
    @JvmField
    val EP_NAME: ExtensionPointName<KeyedFactoryEPBean> = create<KeyedFactoryEPBean>("com.intellij.structureViewBuilder")

    @JvmStatic
    fun getProvider(): StructureViewBuilderProvider = PROVIDER

    @Deprecated(replaceWith = ReplaceWith("getProvider()"), message = "Use getProvider()")
    @JvmField
    val PROVIDER: StructureViewBuilderProvider = FileTypeExtensionFactory<StructureViewBuilderProvider>(
      StructureViewBuilderProvider::class.java, EP_NAME).get()
  }
}
