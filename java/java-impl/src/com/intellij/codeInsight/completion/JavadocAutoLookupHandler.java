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
package com.intellij.codeInsight.completion;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.editor.Editor;

/**
 *
 */
public class JavadocAutoLookupHandler extends CodeCompletionHandlerBase {
  public JavadocAutoLookupHandler() {
    super(CompletionType.BASIC, false, false);
  }

  protected void doComplete(final int offset1, final int offset2, final CompletionContext context, final FileCopyPatcher dummyIdentifier,
                            final Editor editor, final int invocationCount) {
    PsiFile file = context.file;
    int offset = context.getStartOffset();

    PsiElement lastElement = file.findElementAt(offset - 1);
    if (lastElement == null || !StringUtil.endsWithChar(lastElement.getText(), '@')) return;

    super.doComplete(offset1, offset2, context, dummyIdentifier, editor, invocationCount);
  }
}
