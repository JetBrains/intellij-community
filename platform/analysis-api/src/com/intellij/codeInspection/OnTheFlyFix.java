// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection;

/**
 * A marker interface to mark fixes not applicable in the batch mode.
 * Fixes that implement this interface will be filtered out of the fixes list 
 * if the inspection is executed in the batch mode (onTheFly = false)
 */
public interface OnTheFlyFix extends LocalQuickFix {
}
