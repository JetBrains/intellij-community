// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.keymap;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

public interface KeymapExtension {
  @NonNls ExtensionPointName<KeymapExtension> EXTENSION_POINT_NAME = new ExtensionPointName<>("com.intellij.keymapExtension");

  @Nullable KeymapGroup createGroup(Condition<? super AnAction> filtered, @Nullable Project project);
}