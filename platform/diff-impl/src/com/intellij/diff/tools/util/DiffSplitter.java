/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.diff.tools.util;

import com.intellij.openapi.ui.Divider;
import com.intellij.openapi.ui.Splitter;
import org.jetbrains.annotations.CalledInAwt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;


public class DiffSplitter extends Splitter {
  @Nullable private Painter myPainter;

  public DiffSplitter() {
    setDividerWidth(30);
  }

  @Override
  protected Divider createDivider() {
    return new DividerImpl() {
      public void paint(Graphics g) {
        super.paint(g);
        if (myPainter != null) myPainter.paint(g, this);
      }
    };
  }

  @CalledInAwt
  public void setPainter(@Nullable Painter painter) {
    myPainter = painter;
  }

  public void repaintDivider() {
    getDivider().repaint();
  }

  public interface Painter {
    void paint(@NotNull Graphics g, @NotNull Component divider);
  }
}
