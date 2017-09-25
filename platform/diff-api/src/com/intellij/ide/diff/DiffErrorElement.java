/*
 * Copyright 2000-2011 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.ide.diff;

import com.intellij.diff.chains.DiffRequestProducerException;
import com.intellij.diff.contents.DiffContent;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.util.PlatformIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.IOException;

/**
 * @author Konstantin Bulenkov
 */
public class DiffErrorElement extends DiffElement {
  private final String myMessage;

  public DiffErrorElement() {
    this("Can't load children", "");
  }

  public DiffErrorElement(@NotNull String message, @NotNull String description) {
    myMessage = message;
  }

  @Override
  public String getPath() {
    return "";
  }

  @NotNull
  @Override
  public String getName() {
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

  @Nullable
  @Override
  public byte[] getContent() {
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

  @NotNull
  public DiffContent createDiffContent(@Nullable Project project, @NotNull ProgressIndicator indicator)
    throws DiffRequestProducerException, ProcessCanceledException {
    throw new DiffRequestProducerException(myMessage);
  }
}
