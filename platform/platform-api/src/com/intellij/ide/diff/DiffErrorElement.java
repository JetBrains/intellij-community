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

import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import com.intellij.util.ArrayUtil;
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
  private final JTextArea myDescription;

  public DiffErrorElement() {
    this("Can't load children", "");
  }

  public DiffErrorElement(@NotNull String message, @NotNull String description) {
    myMessage = message;
    myDescription = new JTextArea(description);
    //myDescription.setBackground(new Color(0,0,0,0));
    myDescription.setEditable(false);
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
  public DiffElement[] getChildren() throws IOException {
    return EMPTY_ARRAY;
  }

  @Override
  public byte[] getContent() throws IOException {
    return ArrayUtil.EMPTY_BYTE_ARRAY;
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
  public JComponent getViewComponent(Project project, @Nullable DiffElement target, @NotNull Disposable parentDisposable) {
    return myDescription;
  }
}
