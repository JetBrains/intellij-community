// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.services;

import com.intellij.ide.projectView.PresentationData;
import com.intellij.navigation.ItemPresentation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class SimpleServiceViewDescriptor implements ServiceViewDescriptor {
  private final ItemPresentation myPresentation;
  private final String myId;

  public SimpleServiceViewDescriptor(@NotNull String name, @Nullable Icon icon) {
    this(name, icon, name);
  }

  public SimpleServiceViewDescriptor(@NotNull String name, @Nullable Icon icon, @NotNull String id) {
    myPresentation = new PresentationData(name, null, icon, null);
    myId = id;
  }

  @Override
  public @NotNull ItemPresentation getPresentation() {
    return myPresentation;
  }

  @Override
  public @NotNull String getId() {
    return myId;
  }
}
