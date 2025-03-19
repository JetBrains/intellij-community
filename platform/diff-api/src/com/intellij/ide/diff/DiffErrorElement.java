// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.diff;

import com.intellij.diff.chains.DiffRequestProducerException;
import com.intellij.diff.contents.DiffContent;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.util.PlatformIcons;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * @author Konstantin Bulenkov
 */
public class DiffErrorElement extends DiffElement {
  private final @Nls String myMessage;

  public DiffErrorElement(@NotNull @Nls String message, @NotNull @Nls String description) {
    myMessage = message;
  }

  @Override
  public String getPath() {
    return "";
  }

  @Override
  public @NotNull String getName() {
    return myMessage;
  }

  @Override
  public long getSize() {
    return -1;
  }

  @Override
  public long getTimeStamp() {
    return -1;
  }

  @Override
  public boolean isContainer() {
    return false;
  }

  @Override
  public DiffElement[] getChildren() {
    return EMPTY_ARRAY;
  }

  @Override
  public byte @Nullable [] getContent() {
    return null;
  }

  @Override
  public Object getValue() {
    return null;
  }

  @Override
  public Icon getIcon() {
    return PlatformIcons.ERROR_INTRODUCTION_ICON;
  }

  @Override
  public @NotNull DiffContent createDiffContent(@Nullable Project project, @NotNull ProgressIndicator indicator)
    throws DiffRequestProducerException, ProcessCanceledException {
    throw new DiffRequestProducerException(myMessage);
  }
}
