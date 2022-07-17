// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.services;

import com.intellij.ide.projectView.PresentationData;
import com.intellij.navigation.ItemPresentation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class SimpleServiceViewDescriptor implements ServiceViewDescriptor {
  private final ItemPresentation myPresentation;

  public SimpleServiceViewDescriptor(@NotNull String name, @Nullable Icon icon) {
    myPresentation = new PresentationData(name, null, icon, null);
  }

  @NotNull
  @Override
  public ItemPresentation getPresentation() {
    return myPresentation;
  }
}
