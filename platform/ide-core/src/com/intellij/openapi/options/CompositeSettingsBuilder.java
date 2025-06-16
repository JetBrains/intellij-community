// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.options;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

import javax.swing.*;
import java.util.Collection;

public interface CompositeSettingsBuilder<Settings> {
  @NotNull @Unmodifiable
  Collection<SettingsEditor<Settings>> getEditors();
  @NotNull
  JComponent createCompoundEditor();
}
