/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

package com.intellij.psi;

import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.util.ProperTextRange;
import com.intellij.openapi.util.TextRange;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Marks psi element as (potentially) containing text in other language.
 * Injected language PSI does not embed into the PSI tree of the hosting element,
 * but is used by IDEA for highlighting, completion and other code insight actions.
 * In order to do the injection, you have to
 * <ul>
 * <li>Implement {@link com.intellij.psi.LanguageInjector} to describe exact place where injection should occur.</li>  
 * <li>Register injection in {@link com.intellij.psi.LanguageInjector#EXTENSION_POINT_NAME} extension point.</li>
 * </ul>
 * Currently, language can be injected into string literals, XML tag contents and XML attributes.
 * You don't have to implement PsiLanguageInjectionHost by yourself, unless you want to inject something into your own custom PSI.
 * For all returned injected PSI elements, {@link PsiElement#getContext()} method returns PsiLanguageInjectionHost they were injected into.
 */
public interface PsiLanguageInjectionHost extends PsiElement {
  boolean isValidHost();

  PsiLanguageInjectionHost updateText(@NotNull String text);
  
  @NotNull
  LiteralTextEscaper<? extends PsiLanguageInjectionHost> createLiteralTextEscaper();


  interface InjectedPsiVisitor {
    void visit(@NotNull PsiFile injectedPsi, @NotNull List<Shred> places);
  }

  class Shred {
    public PsiLanguageInjectionHost host;
    private final RangeMarker relevantRangeInHost;
    public final TextRange range; // range in (decoded) PSI
    public final String prefix;
    public final String suffix;

    public Shred(@NotNull PsiLanguageInjectionHost host, @NotNull final RangeMarker relevantRangeInHost, @NotNull String prefix, @NotNull String suffix, @NotNull TextRange range) {
      this.host = host;
      this.relevantRangeInHost = relevantRangeInHost;
      this.prefix = prefix;
      this.suffix = suffix;
      this.range = range;
      assert isValid();
      assert relevantRangeInHost.isValid();
    }

    public RangeMarker getHostRangeMarker() {
      return relevantRangeInHost;
    }

    public TextRange getRangeInsideHost() {
      TextRange hostTextRange = host.getTextRange();
      ProperTextRange textRange = relevantRangeInHost.isValid() ? new ProperTextRange(relevantRangeInHost.getStartOffset(), relevantRangeInHost.getEndOffset()) : null;
      textRange = textRange == null ? null : textRange.intersection(hostTextRange);
      if (textRange == null) return new ProperTextRange(0, hostTextRange.getLength());
      return textRange.shiftRight(-hostTextRange.getStartOffset());
    }

    @SuppressWarnings({"HardCodedStringLiteral"})
    public String toString() {
      return "Shred "+ host.getTextRange() + ": "+ host+
             " Inhost range: "+(relevantRangeInHost.isValid() ? "" : "!") + "(" + relevantRangeInHost.getStartOffset()+","+relevantRangeInHost.getEndOffset()+");" +
             " PSI range: "+range;
    }

    public boolean isValid() {
      return relevantRangeInHost.isValid() && host.isValid();
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      Shred shred = (Shred)o;

      return host.equals(shred.host) &&
             prefix.equals(shred.prefix) &&
             suffix.equals(shred.suffix) &&
             range.equals(shred.range) &&
             TextRange.create(relevantRangeInHost).equals(TextRange.create(shred.relevantRangeInHost));
    }

    @Override
    public int hashCode() {
      return range.hashCode();
    }
  }
}
