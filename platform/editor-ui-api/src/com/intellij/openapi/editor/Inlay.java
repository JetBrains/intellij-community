/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.openapi.editor;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.UserDataHolderEx;
import org.jetbrains.annotations.NotNull;

import java.awt.*;

public interface Inlay extends Disposable, UserDataHolderEx {
  boolean isValid();

  int getOffset();

  @NotNull
  Type getType();

  @NotNull
  Renderer getRenderer();

  int getWidthInPixels();

  int getHeightInPixels();

  void update();

  enum Type { INLINE, BLOCK }

  abstract class Renderer {
    public void paint(@NotNull Graphics g, @NotNull Rectangle r, @NotNull Editor editor) {
    }

    public int calcWidthInPixels(@NotNull Editor editor) {
      return editor.getLineHeight();
    }

    public int calcHeightInPixels(@NotNull Editor editor) {
      return editor.getLineHeight();
    }
  }
}
