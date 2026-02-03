// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.util;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.module.Module;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * An extension point that allows getting the mapping from an additional multi-release module to a main multi-release module.
 * Such a mapping is extra-linguistic (not mandated by Java specification) and could be mandated by the build system used.
 */
public interface JavaMultiReleaseModuleSupport {
  ExtensionPointName<JavaMultiReleaseModuleSupport> EP_NAME = new ExtensionPointName<>("com.intellij.lang.jvm.multiReleaseSupport");

  /**
   * @param additionalModule additional module (where release-specific code resides)
   * @return main module (where common code for different releases resides); null if the supplied module is not recognized as
   * an additional module, or the module uses a different build system.
   */
  @Nullable Module getMainMultiReleaseModule(@NotNull Module additionalModule);
}
