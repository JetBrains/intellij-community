// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.formatting.templateLanguages;

import com.intellij.formatting.Block;
import com.intellij.openapi.util.TextRange;
import org.jetbrains.annotations.NotNull;

public interface ITemplateBlock<TDataBlock extends IDataBlock> extends Block, BlockWithParent {
  void addForeignChild(@NotNull TDataBlock foreignChild);

  boolean isRequiredRange(@NotNull TextRange range);
}
