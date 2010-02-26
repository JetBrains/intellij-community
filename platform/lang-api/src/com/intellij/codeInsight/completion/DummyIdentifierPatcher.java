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

import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import com.intellij.openapi.editor.Document;
import com.intellij.psi.PsiFile;

/**
 * @author peter
 */
public class DummyIdentifierPatcher extends FileCopyPatcher {
  private final String myDummyIdentifier;

  public DummyIdentifierPatcher(final String dummyIdentifier) {
    myDummyIdentifier = dummyIdentifier;
  }

  public void patchFileCopy(@NotNull final PsiFile fileCopy, @NotNull final Document document, @NotNull final OffsetMap map) {
    if (StringUtil.isEmpty(myDummyIdentifier)) return;
    document.replaceString(map.getOffset(CompletionInitializationContext.START_OFFSET), map.getOffset(CompletionInitializationContext.SELECTION_END_OFFSET),
                           myDummyIdentifier);
  }

  @Override
  public String toString() {
    return "Insert \"" + myDummyIdentifier + "\"";
  }
}
