/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.openapi.util.diff.tools.util;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.ui.Divider;
import com.intellij.openapi.ui.Splitter;
import com.intellij.openapi.util.diff.util.CalledInAwt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;


public class DiffSplitter extends Splitter {
  private boolean myQueued = false;
  @Nullable private Painter myPainter;

  public DiffSplitter() {
    setDividerWidth(30);
  }

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
    if (myQueued) return;

    final JPanel divider = getDivider();
    divider.repaint();
    myQueued = true;
    ApplicationManager.getApplication().invokeLater(new Runnable() {
      @Override
      public void run() {
        // TODO: is it OK to use paintImmediately? Maybe we can do better with simplified painting ?
        divider.paintImmediately(0, 0, divider.getWidth(), divider.getHeight());
        myQueued = false;
      }
    });
  }

  public interface Painter {
    void paint(@NotNull Graphics g, @NotNull Component divider);
  }
}
