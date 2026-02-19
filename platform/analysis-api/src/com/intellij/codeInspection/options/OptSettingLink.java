// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.options;

import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Represents the link to a settings pane
 * 
 * @param displayName label to display on the link
 * @param configurableID ID of the settings configurable to open
 * @param controlLabel name of the control to focus on (optional)
 */
public record OptSettingLink(@NotNull LocMessage displayName,
                             @NotNull @NonNls String configurableID,
                             @Nullable @Nls String controlLabel) implements OptRegularComponent {
}
