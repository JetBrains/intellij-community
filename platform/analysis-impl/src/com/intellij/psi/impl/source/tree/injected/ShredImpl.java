// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.source.tree.injected;

import com.intellij.openapi.util.ProperTextRange;
import com.intellij.openapi.util.Segment;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiLanguageInjectionHost;
import com.intellij.psi.SmartPointerManager;
import com.intellij.psi.SmartPsiElementPointer;
import com.intellij.psi.SmartPsiFileRange;
import com.intellij.psi.impl.smartPointers.SmartPointerManagerImpl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

class ShredImpl implements PsiLanguageInjectionHost.Shred {
  private final SmartPsiFileRange relevantRangeInHost;
  private final SmartPsiElementPointer<PsiLanguageInjectionHost> hostElementPointer;
  private final TextRange rangeInDecodedPSI;
  private final String prefix;
  private final String suffix;
  private final boolean usePsiRange;
  private final boolean isOneLine;

  ShredImpl(@NotNull SmartPsiFileRange relevantRangeInHost,
            @NotNull SmartPsiElementPointer<PsiLanguageInjectionHost> hostElementPointer,
            @NotNull String prefix,
            @NotNull String suffix,
            @NotNull TextRange rangeInDecodedPSI,
            boolean usePsiRange, boolean isOneLine) {
    this.hostElementPointer = hostElementPointer;
    this.relevantRangeInHost = relevantRangeInHost;
    this.prefix = prefix;
    this.suffix = suffix;
    this.rangeInDecodedPSI = rangeInDecodedPSI;
    this.usePsiRange = usePsiRange;
    this.isOneLine = isOneLine;

    assertValid();
  }

  private void assertValid() {
    Segment hostRange = relevantRangeInHost.getPsiRange();
    assert hostRange != null : "invalid host range: " + relevantRangeInHost;

    PsiLanguageInjectionHost host = hostElementPointer.getElement();
    assert host != null && host.isValid() : "no host: " + hostElementPointer;
  }

  @NotNull
  ShredImpl withPsiRange() {
    return new ShredImpl(relevantRangeInHost, hostElementPointer, prefix, suffix, rangeInDecodedPSI, true, isOneLine);
  }
  @NotNull
  ShredImpl withRange(@NotNull TextRange rangeInDecodedPSI,
                      @NotNull TextRange rangeInHostElementPSI,
                      @NotNull PsiLanguageInjectionHost newHost) {
    SmartPsiFileRange rangeMarker = relevantRangeInHost;
    Segment oldRangeInHostElementPSI = calcRangeInsideHostElement(false);
    SmartPointerManagerImpl pointerManager = (SmartPointerManagerImpl)SmartPointerManager.getInstance(rangeMarker.getProject());
    SmartPsiElementPointer<PsiLanguageInjectionHost> newHostPointer = pointerManager.createSmartPsiElementPointer(newHost, newHost.getContainingFile(), true);

    if (!rangeInHostElementPSI.equals(TextRange.create(oldRangeInHostElementPSI))) {
      Segment hostElementRange = newHostPointer.getRange();
      rangeMarker = pointerManager
        .createSmartPsiFileRangePointer(rangeMarker.getContainingFile(), rangeInHostElementPSI.shiftRight(hostElementRange.getStartOffset()), true);
    }
    return new ShredImpl(rangeMarker, newHostPointer, prefix, suffix, rangeInDecodedPSI, usePsiRange, isOneLine);
  }

  @NotNull
  SmartPsiElementPointer<PsiLanguageInjectionHost> getSmartPointer() {
    return hostElementPointer;
  }

  @Override
  public @Nullable("returns null when the host document marker is invalid") Segment getHostRangeMarker() {
    return usePsiRange ? relevantRangeInHost.getPsiRange() : relevantRangeInHost.getRange();
  }

  @Override
  public @NotNull TextRange getRangeInsideHost() {
    return calcRangeInsideHostElement(true);
  }

  private @NotNull TextRange calcRangeInsideHostElement(boolean usePsiRange) {
    PsiLanguageInjectionHost host = getHost();
    Segment psiRange = usePsiRange ? relevantRangeInHost.getPsiRange() : relevantRangeInHost.getRange();
    TextRange textRange = psiRange == null ? null : TextRange.create(psiRange);
    if (host == null) {
      if (textRange != null) return textRange;
      Segment fromSP = usePsiRange ? hostElementPointer.getPsiRange() : hostElementPointer.getRange();
      if (fromSP != null) return TextRange.create(fromSP);
      return new TextRange(0,0);
    }
    TextRange hostTextRange = host.getTextRange();
    textRange = textRange == null ? null : textRange.intersection(hostTextRange);
    if (textRange == null) return new ProperTextRange(0, hostTextRange.getLength());

    return textRange.shiftLeft(hostTextRange.getStartOffset());
  }

  @Override
  @SuppressWarnings("HardCodedStringLiteral")
  public String toString() {
    PsiLanguageInjectionHost host = getHost();
    Segment hostRange = getHostRangeMarker();
    return "Shred " + (host == null ? null : host.getTextRange()) + ": " + host +
           " In host range: " + (hostRange != null ? "(" + hostRange.getStartOffset() + "," + hostRange.getEndOffset() + ");" : "invalid;") +
           " PSI range: " + rangeInDecodedPSI;
  }

  @Override
  public boolean isValid() {
    return getHostRangeMarker() != null && getHost() != null;
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
           rangeInDecodedPSI.equals(shred.getRange()) &&
           hostRangeMarker != null &&
           hostRangeMarker2 != null &&
           TextRange.areSegmentsEqual(hostRangeMarker, hostRangeMarker2);
  }

  @Override
  public int hashCode() {
    return rangeInDecodedPSI.hashCode();
  }

  @Override
  public void dispose() {
  }

  @Override
  public @Nullable PsiLanguageInjectionHost getHost() {
    return hostElementPointer.getElement();
  }

  @Override
  public @NotNull TextRange getRange() {
    return rangeInDecodedPSI;
  }

  @Override
  public @NotNull String getPrefix() {
    return prefix;
  }

  @Override
  public @NotNull String getSuffix() {
    return suffix;
  }

  boolean isOneLine() {
    return isOneLine;
  }
}
