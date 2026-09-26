// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight;

import com.intellij.openapi.extensions.ExtensionPointName;

/**
 * Extends {@link TargetElementUtil} class with extra 'search-for' flags
 */
public interface TargetElementUtilExtender {
  ExtensionPointName<TargetElementUtilExtender> EP_NAME = ExtensionPointName.create("com.intellij.targetElementUtilExtender");

  /**
   * @return all extended flags regardless of what is being searched (implementations or usages)
   */
  int getAllAdditionalFlags();

  /**
   * @return extended flags used only when searching for implementations
   */
  int getAdditionalDefinitionSearchFlags();

  /**
   * @return extended flags used only when searching for usages
   */
  int getAdditionalReferenceSearchFlags();
}
