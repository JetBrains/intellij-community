// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.engine;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.sun.jdi.TypeComponent;

/**
 * Allows to mark as synthetic more than what's marked in the bytecode, for example lambda classes
 */
public interface SyntheticTypeComponentProvider {
  ExtensionPointName<SyntheticTypeComponentProvider> EP_NAME = ExtensionPointName.create("com.intellij.debugger.syntheticProvider");

  boolean isSynthetic(TypeComponent typeComponent);

  //override this method to prevent other providers treating type component as synthetic
  default boolean isNotSynthetic(TypeComponent typeComponent) {
    return false;
  }
}
