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
import com.intellij.diff.util.Side;
import com.intellij.openapi.actionSystem.AnAction;
import org.jetbrains.annotations.CalledInAwt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.List;

public class TwosideContentPanel extends JPanel {
  @NotNull private final DiffSplitter mySplitter;

  public TwosideContentPanel(@NotNull List<? extends EditorHolder> holders, @NotNull List<JComponent> titleComponents) {
    super(new BorderLayout());
    assert holders.size() == 2;
    assert titleComponents.size() == 2;

    mySplitter = new DiffSplitter();
    mySplitter.setFirstComponent(new HolderPanel(Side.LEFT.select(holders), Side.LEFT.select(titleComponents)));
    mySplitter.setSecondComponent(new HolderPanel(Side.RIGHT.select(holders), Side.RIGHT.select(titleComponents)));
    mySplitter.setHonorComponentsMinimumSize(false);
    add(mySplitter, BorderLayout.CENTER);
  }

  public void setBottomAction(@Nullable AnAction value) {
    mySplitter.setBottomAction(value);
  }

  public void setTopAction(@Nullable AnAction value) {
    mySplitter.setTopAction(value);
  }

  @CalledInAwt
  public void setPainter(@Nullable DiffSplitter.Painter painter) {
    mySplitter.setPainter(painter);
  }

  public void repaintDivider() {
    mySplitter.repaintDivider();
  }

  @NotNull
  public DiffSplitter getSplitter() {
    return mySplitter;
  }
}
