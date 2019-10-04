// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.fileTemplates;

import com.intellij.openapi.extensions.ExtensionPointName;

/**
 * Registers a set of bundled file templates.
 */
public interface FileTemplateGroupDescriptorFactory {
  ExtensionPointName<FileTemplateGroupDescriptorFactory> EXTENSION_POINT_NAME = ExtensionPointName.create("com.intellij.fileTemplateGroup");

  FileTemplateGroupDescriptor getFileTemplatesDescriptor();
}