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
package com.intellij.diff.tools.util.side;

import com.intellij.diff.tools.holders.EditorHolder;
import com.intellij.diff.tools.util.DiffSplitter;
import org.jetbrains.annotations.CalledInAwt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.List;

public class TwosideContentPanel extends JPanel {
  @Nullable private final DiffSplitter mySplitter;

  public TwosideContentPanel(@NotNull List<? extends EditorHolder> holders, @NotNull List<JComponent> titleComponents) {
    super(new BorderLayout());
    assert holders.size() == 2;
    assert titleComponents.size() == 2;

    EditorHolder holder1 = holders.get(0);
    EditorHolder holder2 = holders.get(1);

    if (holder1 != null && holder2 != null) {
      mySplitter = new DiffSplitter();
      mySplitter.setFirstComponent(new HolderPanel(holder1, titleComponents.get(0)));
      mySplitter.setSecondComponent(new HolderPanel(holder2, titleComponents.get(1)));
      mySplitter.setHonorComponentsMinimumSize(false);
      add(mySplitter, BorderLayout.CENTER);
    }
    else {
      mySplitter = null;
      if (holder1 != null) {
        add(new HolderPanel(holder1, titleComponents.get(0)), BorderLayout.CENTER);
      }
      else if (holder2 != null) {
        add(new HolderPanel(holder2, titleComponents.get(1)), BorderLayout.CENTER);
      }
    }
  }

  @CalledInAwt
  public void setPainter(@Nullable DiffSplitter.Painter painter) {
    if (mySplitter != null) mySplitter.setPainter(painter);
  }

  public void repaintDivider() {
    if (mySplitter != null) mySplitter.repaintDivider();
  }

  @Nullable
  public DiffSplitter getSplitter() {
    return mySplitter;
  }
}
