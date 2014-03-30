/*
 * Copyright (c) 2007-2009, Osmorc Development Team
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification,
 * are permitted provided that the following conditions are met:
 *     * Redistributions of source code must retain the above copyright notice, this list
 *       of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above copyright notice, this
 *       list of conditions and the following disclaimer in the documentation and/or other
 *       materials provided with the distribution.
 *     * Neither the name of 'Osmorc Development Team' nor the names of its contributors may be
 *       used to endorse or promote products derived from this software without specific
 *       prior written permission.
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY
 * EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL
 * THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT
 * OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION)
 * HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR
 * TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE,
 * EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package org.jetbrains.lang.manifest.header.impl;

import com.intellij.lang.PsiBuilder;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.psi.PsiReference;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.lang.manifest.header.HeaderParser;
import org.jetbrains.lang.manifest.parser.ManifestParser;
import org.jetbrains.lang.manifest.psi.*;

/**
 * @author Robert F. Beeger (robert@beeger.net)
 */
public class StandardHeaderParser implements HeaderParser {
  public static final HeaderParser INSTANCE = new StandardHeaderParser();

  @Override
  public void parse(@NotNull PsiBuilder builder) {
    PsiBuilder.Marker marker = builder.mark();
    while (!builder.eof() && !ManifestParser.HEADER_END_TOKENS.contains(builder.getTokenType())) {
      IElementType lastToken = builder.getTokenType();
      builder.advanceLexer();
      if (lastToken == ManifestTokenType.NEWLINE && builder.getTokenType() != ManifestTokenType.SIGNIFICANT_SPACE) {
        break;
      }
    }
    marker.done(ManifestElementType.HEADER_VALUE_PART);
  }

  @Override
  public boolean annotate(@NotNull Header header, @NotNull AnnotationHolder holder) {
    return false;
  }

  @Nullable
  @Override
  public Object getConvertedValue(@NotNull Header header) {
    HeaderValue value = header.getHeaderValue();
    return value != null ? value.getUnwrappedText() : null;
  }

  @NotNull
  @Override
  public PsiReference[] getReferences(@NotNull HeaderValuePart headerValuePart) {
    return PsiReference.EMPTY_ARRAY;
  }
}
