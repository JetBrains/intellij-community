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
package com.intellij.psi.impl.source.tree.injected;

import com.intellij.openapi.util.ProperTextRange;
import com.intellij.openapi.util.Segment;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

class ShredImpl implements PsiLanguageInjectionHost.Shred {
  private final SmartPsiFileRange relevantRangeInHost;
  private final SmartPsiElementPointer<PsiLanguageInjectionHost> hostElementPointer;
  private final TextRange range; // range in (decoded) PSI
  private final String prefix;
  private final String suffix;
  private final boolean usePsiRange;

  ShredImpl(@NotNull SmartPsiFileRange relevantRangeInHost,
            @NotNull SmartPsiElementPointer<PsiLanguageInjectionHost> hostElementPointer,
            @NotNull String prefix,
            @NotNull String suffix,
            @NotNull TextRange range,
            boolean usePsiRange) {
    this.hostElementPointer = hostElementPointer;
    this.relevantRangeInHost = relevantRangeInHost;
    this.prefix = prefix;
    this.suffix = suffix;
    this.range = range;
    this.usePsiRange = usePsiRange;

    assertValid();
  }

  private void assertValid() {
    Segment hostRange = getHostRangeMarker();
    assert hostRange != null : "invalid host range: " + relevantRangeInHost;

    PsiLanguageInjectionHost host = hostElementPointer.getElement();
    assert host != null && host.isValid() : "no host: " + hostElementPointer;
  }

  ShredImpl withPsiRange() {
    return new ShredImpl(relevantRangeInHost, hostElementPointer, prefix, suffix, range, true);
  }

  @NotNull
  public SmartPsiElementPointer<PsiLanguageInjectionHost> getSmartPointer() {
    return hostElementPointer;
  }

  @Override
  @Nullable("returns null when the host document marker is invalid")
  public Segment getHostRangeMarker() {
    return usePsiRange ? relevantRangeInHost.getPsiRange() : relevantRangeInHost.getRange();
  }

  @Override
  @NotNull
  public TextRange getRangeInsideHost() {
    PsiLanguageInjectionHost host = getHost();
    Segment psiRange = relevantRangeInHost.getPsiRange();
    TextRange textRange = psiRange == null ? null : TextRange.create(psiRange);
    if (host == null) {
      if (textRange != null) return textRange;
      Segment fromSP = hostElementPointer.getPsiRange();
      if (fromSP != null) return TextRange.create(fromSP);
      return new TextRange(0,0);
    }
    TextRange hostTextRange = host.getTextRange();
    textRange = textRange == null ? null : textRange.intersection(hostTextRange);
    if (textRange == null) return new ProperTextRange(0, hostTextRange.getLength());
    return textRange.shiftRight(-hostTextRange.getStartOffset());
  }

  @Override
  @SuppressWarnings({"HardCodedStringLiteral"})
  public String toString() {
    PsiLanguageInjectionHost host = getHost();
    Segment hostRange = getHostRangeMarker();
    return "Shred " + (host == null ? null : host.getTextRange()) + ": " + host +
           " In host range: " + (hostRange != null ? "(" + hostRange.getStartOffset() + "," + hostRange.getEndOffset() + ");" : "invalid;") +
           " PSI range: " + this.range;
  }

  @Override
  public boolean isValid() {
    PsiLanguageInjectionHost host = getHost();
    return getHostRangeMarker() != null && host != null && host.isValid();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    PsiLanguageInjectionHost.Shred shred = (PsiLanguageInjectionHost.Shred)o;

    PsiLanguageInjectionHost host = getHost();
    Segment hostRangeMarker = getHostRangeMarker();
    Segment hostRangeMarker2 = shred.getHostRangeMarker();
    return host != null &&
           host.equals(shred.getHost()) &&
           prefix.equals(shred.getPrefix()) &&
           suffix.equals(shred.getSuffix()) &&
           range.equals(shred.getRange()) &&
           hostRangeMarker != null &&
           hostRangeMarker2 != null &&
           TextRange.create(hostRangeMarker).equals(TextRange.create(hostRangeMarker2));
  }

  @Override
  public int hashCode() {
    return range.hashCode();
  }

  @Override
  public void dispose() {
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
