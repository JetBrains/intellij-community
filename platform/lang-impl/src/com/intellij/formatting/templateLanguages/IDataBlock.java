// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.formatting.templateLanguages;

import com.intellij.formatting.Block;
import com.intellij.formatting.BlockEx;
import com.intellij.formatting.Spacing;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface IDataBlock extends BlockEx, Block, BlockWithParent {
  void addTlChild(@NotNull ITemplateBlock tlBlock);

  @NotNull
  Block getOriginal();

  void setRightHandSpacing(@NotNull IDataBlock rightHandWrapper, @Nullable Spacing spacing);
}
