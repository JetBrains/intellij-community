/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.openapi.vcs.impl;

import com.intellij.openapi.editor.colors.ColorKey;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.vcs.FileStatus;

import java.awt.*;

/**
 * author: lesya
 */
public class FileStatusImpl implements FileStatus {
  private final String myStatus;
  private final ColorKey myColorKey;
  private final String myText;

  public FileStatusImpl(String status, ColorKey key, String text) {
    myStatus = status;
    myColorKey = key;
    myText = text;
  }

  public String toString() {
    return myStatus;
  }

  public String getText() {
    return myText;
  }

  public Color getColor() {
    return EditorColorsManager.getInstance().getGlobalScheme().getColor(getColorKey());
  }

  public ColorKey getColorKey() {
    return myColorKey;
  }

  public String getId() {
    return myStatus;
  }

  public static class OnlyColorFileStatus extends FileStatusImpl {
    public OnlyColorFileStatus(String status, ColorKey key, String text) {
      super(status, key, text);
    }

    @Override
    public String getId() {
      throw new UnsupportedOperationException();
    }

    @Override
    public String getText() {
      throw new UnsupportedOperationException();
    }
  }
}
