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
package com.intellij.diff.util;

import com.intellij.icons.AllIcons;
import com.intellij.util.IconUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class DiffIcons {
  public final static Icon REPLACE_LEFT = IconUtil.flip(AllIcons.Diff.Arrow, true);
  public final static Icon REPLACE_RIGHT = AllIcons.Diff.Arrow;

  // TODO: different icon
  public final static Icon APPEND_LEFT = IconUtil.flip(AllIcons.Diff.Arrow, true);
  public final static Icon APPEND_RIGHT = AllIcons.Diff.Arrow;

  public final static Icon REVERT_LEFT = AllIcons.Diff.Remove;
  public final static Icon REVERT_RIGHT = AllIcons.Diff.Remove;

  @NotNull
  public static Icon getReplaceIcon(@Nullable Side side) {
    if (side == null) side = Side.LEFT;
    return side.selectN(REPLACE_LEFT, REPLACE_RIGHT);
  }

  @Nullable
  public static Icon getAppendIcon(@Nullable Side side) {
    if (side == null) side = Side.LEFT;
    return side.selectN(APPEND_LEFT, APPEND_RIGHT);
  }

  @NotNull
  public static Icon getRevertIcon(@Nullable Side side) {
    if (side == null) side = Side.LEFT;
    return side.selectN(REVERT_LEFT, REVERT_RIGHT);
  }
}
