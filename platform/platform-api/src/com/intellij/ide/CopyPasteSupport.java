package com.intellij.ide;

import com.intellij.ide.CutProvider;
import com.intellij.ide.CopyProvider;
import com.intellij.ide.PasteProvider;

public interface CopyPasteSupport {
  CutProvider getCutProvider();
  CopyProvider getCopyProvider();
  PasteProvider getPasteProvider();
}
