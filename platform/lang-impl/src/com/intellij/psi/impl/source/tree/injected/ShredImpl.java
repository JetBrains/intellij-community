/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.psi.impl.source.tree.injected;

import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.util.ProperTextRange;
import com.intellij.openapi.util.Segment;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

class ShredImpl implements PsiLanguageInjectionHost.Shred {
  private final RangeMarker relevantRangeInHost;
  private final SmartPsiElementPointer<PsiLanguageInjectionHost> hostElementPointer;
  private final TextRange range; // range in (decoded) PSI
  private final String prefix;
  private final String suffix;

  ShredImpl(@NotNull PsiLanguageInjectionHost host,
            @NotNull PsiFile hostPsiFile,
            @NotNull final RangeMarker relevantRangeInHost,
            @NotNull String prefix,
            @NotNull String suffix,
            @NotNull TextRange range) {
    hostElementPointer = createHostSmartPointer(host, hostPsiFile);
    this.relevantRangeInHost = relevantRangeInHost;
    this.prefix = prefix;
    this.suffix = suffix;
    this.range = range;
    assert isValid();
    assert relevantRangeInHost.isValid();
  }

  public SmartPsiElementPointer<PsiLanguageInjectionHost> getSmartPointer() {
    return hostElementPointer;
  }

  @NotNull
  private static <T extends PsiLanguageInjectionHost> SmartPsiElementPointer<T> createHostSmartPointer(@NotNull T host,
                                                                                                       @NotNull PsiFile hostPsiFile) {
    return hostPsiFile.isPhysical()
           ? SmartPointerManager.getInstance(hostPsiFile.getProject()).createSmartPsiElementPointer(host, hostPsiFile)
           : new IdentitySmartPointer<T>(host, hostPsiFile);
  }

  @Override
  @Nullable("returns null when the host document marker is invalid")
  public Segment getHostRangeMarker() {
    RangeMarker marker = relevantRangeInHost;
    return marker.isValid() ? marker : null;
  }

  @Override
  @NotNull
  public TextRange getRangeInsideHost() {
    PsiLanguageInjectionHost host = getHost();
    ProperTextRange textRange =
      relevantRangeInHost.isValid() ? new ProperTextRange(relevantRangeInHost.getStartOffset(), relevantRangeInHost.getEndOffset()) : null;
    if (host == null) {
      if (textRange != null) return textRange;
      Segment fromSP = hostElementPointer.getRange();
      if (fromSP != null) return TextRange.create(fromSP);
      return new TextRange(0,0);
    }
    TextRange hostTextRange = host.getTextRange();
    textRange = textRange == null ? null : textRange.intersection(hostTextRange);
    if (textRange == null) return new ProperTextRange(0, hostTextRange.getLength());
    return textRange.shiftRight(-hostTextRange.getStartOffset());
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  public String toString() {
    PsiLanguageInjectionHost host = getHost();
    return "Shred " + (host == null ? null : host.getTextRange()) + ": " + host +
           " Inhost range: " + (relevantRangeInHost.isValid() ? "" : "!") +
           "(" + relevantRangeInHost.getStartOffset() + "," + relevantRangeInHost.getEndOffset() + ");" +
           " PSI range: " + range;
  }

  @Override
  public boolean isValid() {
    PsiLanguageInjectionHost host = getHost();
    return relevantRangeInHost.isValid() && host != null && host.isValid();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    PsiLanguageInjectionHost.Shred shred = (PsiLanguageInjectionHost.Shred)o;

    PsiLanguageInjectionHost host = getHost();
    Segment hostRangeMarker = shred.getHostRangeMarker();
    return host != null &&
           host.equals(shred.getHost()) &&
           prefix.equals(shred.getPrefix()) &&
           suffix.equals(shred.getSuffix()) &&
           range.equals(shred.getRange()) &&
           hostRangeMarker != null &&
           TextRange.create(relevantRangeInHost).equals(TextRange.create(hostRangeMarker));
  }

  @Override
  public int hashCode() {
    return range.hashCode();
  }

  @Override
  public void dispose() {
    relevantRangeInHost.dispose();
  }

  @Override
  @Nullable
  public PsiLanguageInjectionHost getHost() {
    return hostElementPointer.getElement();
  }

  @NotNull
  @Override
  public TextRange getRange() {
    return range;
  }

  @NotNull
  @Override
  public String getPrefix() {
    return prefix;
  }

  @NotNull
  @Override
  public String getSuffix() {
    return suffix;
  }
}
