package com.intellij.psi.formatter;

import com.intellij.formatting.Block;

public interface ReadOnlyBlockInformationProvider {
  boolean isReadOnly(Block block);
}
