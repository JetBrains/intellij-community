// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diagnostic;

import org.jetbrains.annotations.ApiStatus;

import static java.util.Objects.requireNonNull;

/** @deprecated plugins shouldn't change memory settings */
@Deprecated(forRemoval = true)
@ApiStatus.Internal
public class EditXmxVMOptionDialog extends EditMemorySettingsDialog {
  public EditXmxVMOptionDialog() {
    super(requireNonNull(VMOptions.getUserOptionsFile()), VMOptions.MemoryKind.HEAP, false);
    PluginException.reportDeprecatedUsage("EditXmxVMOptionDialog", "Plugins shouldn't change memory settings");
  }
}
