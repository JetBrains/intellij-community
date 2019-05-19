// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.jvm.actions;

import com.intellij.lang.jvm.JvmModifier;
import org.jetbrains.annotations.NotNull;

public interface ChangeModifierRequest extends ActionRequest {

  @NotNull
  JvmModifier getModifier();

  boolean shouldBePresent();
}
