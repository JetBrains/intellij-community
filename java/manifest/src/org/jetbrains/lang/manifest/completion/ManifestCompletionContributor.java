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
package org.jetbrains.lang.manifest.completion;

import com.intellij.codeInsight.completion.*;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.openapi.editor.EditorModificationUtil;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.lang.manifest.ManifestLanguage;
import org.jetbrains.lang.manifest.header.HeaderParserRepository;
import org.jetbrains.lang.manifest.psi.ManifestTokenType;

/**
 * Completion contributor which adds the name of all known headers to the autocomplete list.
 *
 * @author <a href="mailto:janthomae@janthomae.de">Jan Thom&auml;</a>
 * @author Robert F. Beeger (robert@beeger.net)
 */
public class ManifestCompletionContributor extends CompletionContributor {
  public ManifestCompletionContributor(@NotNull final HeaderParserRepository repository) {
    extend(CompletionType.BASIC,
           PlatformPatterns.psiElement(ManifestTokenType.HEADER_NAME).withLanguage(ManifestLanguage.INSTANCE),
           new CompletionProvider<CompletionParameters>() {
             @Override
             public void addCompletions(@NotNull CompletionParameters parameters,
                                        ProcessingContext context,
                                        @NotNull CompletionResultSet resultSet) {
               for (String header : repository.getAllHeaderNames()) {
                 resultSet.addElement(LookupElementBuilder.create(header).withInsertHandler(HEADER_INSERT_HANDLER));
               }
             }
           }
    );
  }

  private static final InsertHandler<LookupElement> HEADER_INSERT_HANDLER = new InsertHandler<LookupElement>() {
    @Override
    public void handleInsert(InsertionContext context, LookupElement item) {
      context.setAddCompletionChar(false);
      EditorModificationUtil.insertStringAtCaret(context.getEditor(), ": ");
      context.commitDocument();
    }
  };
}
