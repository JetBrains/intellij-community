/*
 * @author max
 */
package com.intellij.injected.editor;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.util.TextRange;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface DocumentWindow extends Document {
  @NotNull Document getDelegate();
  int injectedToHost(int injectedOffset);
  @NotNull TextRange injectedToHost(@NotNull TextRange injectedOffset);
  int hostToInjected(int hostOffset);

  /**
   * Use com.intellij.lang.injection.InjectedLanguageManager#intersectWithAllEditableFragments(com.intellij.psi.PsiFile, com.intellij.openapi.util.TextRange)
   * since editable fragments may well spread over several injection hosts
   */
  @Deprecated
  @Nullable
  TextRange intersectWithEditable(@NotNull TextRange range);

  @Nullable
  TextRange getHostRange(int offset);

  int injectedToHostLine(int line);

  @NotNull
  RangeMarker[] getHostRanges();

  boolean areRangesEqual(@NotNull DocumentWindow documentWindow);

  boolean isValid();
}
