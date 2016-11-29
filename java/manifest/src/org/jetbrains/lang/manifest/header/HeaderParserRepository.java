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
package org.jetbrains.lang.manifest.header;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.util.NotNullLazyValue;
import com.intellij.psi.PsiReference;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.text.CaseInsensitiveStringHashingStrategy;
import gnu.trove.THashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.lang.manifest.psi.Header;
import org.jetbrains.lang.manifest.psi.HeaderValuePart;

import java.util.Map;
import java.util.Set;

/**
 * @author Robert F. Beeger (robert@beeger.net)
 */
public class HeaderParserRepository {
  public static HeaderParserRepository getInstance() {
    return ServiceManager.getService(HeaderParserRepository.class);
  }

  private final NotNullLazyValue<Map<String, HeaderParser>> myParsers = new NotNullLazyValue<Map<String, HeaderParser>>() {
    @NotNull
    @Override
    protected Map<String, HeaderParser> compute() {
      Map<String, HeaderParser> map = new THashMap<>(CaseInsensitiveStringHashingStrategy.INSTANCE);
      for (HeaderParserProvider provider : Extensions.getExtensions(HeaderParserProvider.EP_NAME)) {
        map.putAll(provider.getHeaderParsers());
      }
      return map;
    }
  };

  @Nullable
  public HeaderParser getHeaderParser(@Nullable String headerName) {
    return myParsers.getValue().get(headerName);
  }

  @NotNull
  public Set<String> getAllHeaderNames() {
    return myParsers.getValue().keySet();
  }

  @Nullable
  public Object getConvertedValue(@NotNull Header header) {
    HeaderParser parser = getHeaderParser(header.getName());
    return parser != null ? parser.getConvertedValue(header) : null;
  }

  @NotNull
  public PsiReference[] getReferences(@NotNull HeaderValuePart headerValuePart) {
    Header header = PsiTreeUtil.getParentOfType(headerValuePart, Header.class);
    if (header != null) {
      HeaderParser parser = getHeaderParser(header.getName());
      if (parser != null) {
        return parser.getReferences(headerValuePart);
      }
    }

    return PsiReference.EMPTY_ARRAY;
  }
}
