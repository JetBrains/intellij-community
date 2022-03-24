// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.openapi.roots;

import com.intellij.openapi.module.Module;
import org.jetbrains.annotations.ApiStatus;

/**
 * This is an internal class, use {@link LanguageLevelModuleExtension} instead.
 */
@ApiStatus.Internal
public abstract class LanguageLevelModuleExtensionImpl extends ModuleExtension implements LanguageLevelModuleExtension {
  /**
   * @deprecated this method returns an implementation specific class, use {@link com.intellij.openapi.module.LanguageLevelUtil#getCustomLanguageLevel(Module)} instead.
   */
  @Deprecated(forRemoval = true)
  public static LanguageLevelModuleExtensionImpl getInstance(final Module module) {
    return ModuleRootManager.getInstance(module).getModuleExtension(LanguageLevelModuleExtensionImpl.class);
  }
}
