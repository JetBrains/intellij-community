// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework;

import com.intellij.lang.Language;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.OutputStream;

/**
 * @author gregsh
 */
public class ReadOnlyLightVirtualFile extends LightVirtualFile {
  public ReadOnlyLightVirtualFile(@NotNull String name,
                                  @NotNull Language language,
                                  @NotNull CharSequence text) {
    super(name, language, text);
    super.setWritable(false);
  }

  @Override
  public final void setContent(Object requestor, @NotNull CharSequence content, boolean fireEvent) {
    throw new UnsupportedOperationException();
  }

  @Override
  public final void setWritable(boolean writable) {
    if (writable) throw new UnsupportedOperationException();
  }

  @Override
  public @NotNull OutputStream getOutputStream(Object requestor, long newModificationStamp, long newTimeStamp) throws IOException {
    throw new UnsupportedOperationException();
  }
}
