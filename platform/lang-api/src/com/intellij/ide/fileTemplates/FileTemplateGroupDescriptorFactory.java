// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.fileTemplates;

import com.intellij.openapi.extensions.ExtensionPointName;

/**
 * Registers a set of bundled file templates.
 *
 * @see <a href="https://plugins.jetbrains.com/docs/intellij/providing-file-templates.html#other">Providing File and Code Templates (IntelliJ Platform Docs)</a>
 */
public interface FileTemplateGroupDescriptorFactory {
  ExtensionPointName<FileTemplateGroupDescriptorFactory> EXTENSION_POINT_NAME = ExtensionPointName.create("com.intellij.fileTemplateGroup");

  /**
   * Creates a templates group containing a set of belonging templates.
   */
  FileTemplateGroupDescriptor getFileTemplatesDescriptor();
}
