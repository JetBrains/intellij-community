// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.externalSystem.service.task.ui;

import com.intellij.ide.projectView.PresentationData;
import com.intellij.ide.util.treeView.PresentableNodeDescriptor;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

@ApiStatus.Internal
public class ExternalSystemNodeDescriptor<T> extends PresentableNodeDescriptor<T> {

  private final @NotNull T myElement;
  private final @NotNull @Nls String myDescription;

  public ExternalSystemNodeDescriptor(@NotNull T element, @NotNull String name, @NotNull @Nls String description, @Nullable Icon icon) {
    super(null, null);
    myElement = element;
    myName = name;
    setIcon(icon);
    myDescription = description;
    getPresentation().setTooltip(description);
  }

  public void setName(@NotNull String name) {
    myName = name;
  }
  
  @Override
  protected void update(@NotNull PresentationData presentation) {
    presentation.setPresentableText(myName);
    presentation.setIcon(getIcon());
    presentation.setTooltip(myDescription);
  }
  
  @Override
  public @NotNull T getElement() {
    return myElement;
  }
}
