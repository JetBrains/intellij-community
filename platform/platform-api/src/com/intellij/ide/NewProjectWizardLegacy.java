// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide;

/**
 * @deprecated Fallback to old implementation of the new project wizard isn't needed. Please remove it.
 */
@Deprecated(forRemoval = true)
public final class NewProjectWizardLegacy {
  public static boolean isAvailable() {
    return false;
  }
}