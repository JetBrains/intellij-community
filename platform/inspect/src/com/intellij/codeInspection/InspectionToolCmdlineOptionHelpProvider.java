// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection;

import org.jetbrains.annotations.ApiStatus;

/**
 * @author Roman.Chernyatchik
 */
@ApiStatus.OverrideOnly
public interface InspectionToolCmdlineOptionHelpProvider {
  void printHelpAndExit();
}
