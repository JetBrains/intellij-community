// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.injected.editor;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.util.Segment;
import com.intellij.openapi.util.TextRange;
import com.intellij.util.text.StringOperation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

/**
 * A document used for injected PSI, based on one or several ranges from a "real" document hosting that injected PSI.
 * @see com.intellij.lang.injection.InjectedLanguageManager
 */
public interface DocumentWindow extends Document {
  /**
   * @return the host document of the injected PSI corresponding to this document
   */
  @NotNull
  Document getDelegate();

  int injectedToHost(int injectedOffset);

  /**
   * @param minHostOffset if {@code true} minimum host offset corresponding to given injected offset is returned, otherwise maximum related
   *                      host offset is returned
   */
  int injectedToHost(int injectedOffset, boolean minHostOffset);

  @NotNull
  TextRange injectedToHost(@NotNull TextRange injectedOffset);

  int hostToInjected(int hostOffset);

  @Nullable
  TextRange getHostRange(int hostOffset);

  int injectedToHostLine(int line);

  Segment @NotNull [] getHostRanges();

  boolean areRangesEqual(@NotNull DocumentWindow documentWindow);

  /**
   * @return whether this injected document hasn't been invalidated (which could happen
   * e.g. if its document window ranges were removed or injected PSI was invalidated).
   */
  boolean isValid();

  boolean containsRange(int hostStart, int hostEnd);

  boolean isOneLine();

  /**
   * Returns set of operations which will be performed on the host document,
   * when replacement is requested on this document window.
   * <p>
   * This method doesn't modify the host document.
   */
  @NotNull Collection<@NotNull StringOperation> prepareReplaceString(int startOffset, int endOffset, @NotNull CharSequence s);
}
