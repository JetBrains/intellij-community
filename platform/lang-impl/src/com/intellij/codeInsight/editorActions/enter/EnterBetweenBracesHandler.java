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

package com.intellij.codeInsight.editorActions.enter;

import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiFile;
import com.intellij.util.text.CharArrayUtil;
import org.jetbrains.annotations.NotNull;

/**
 * @Deprecated Please, use the {@code EnterBetweenBracesDelegate} language-specific implementation instead.
 */
public class EnterBetweenBracesHandler extends EnterBetweenBracesFinalHandler {
  protected boolean isApplicable(@NotNull PsiFile file,
                                 @NotNull Editor editor,
                                 CharSequence documentText,
                                 int caretOffset,
                                 EnterBetweenBracesDelegate helper) {
    int prevCharOffset = CharArrayUtil.shiftBackward(documentText, caretOffset - 1, " \t");
    int nextCharOffset = CharArrayUtil.shiftForward(documentText, caretOffset, " \t");
    return isValidOffset(prevCharOffset, documentText) &&
           isValidOffset(nextCharOffset, documentText) &&
           isBracePair(documentText.charAt(prevCharOffset), documentText.charAt(nextCharOffset)) &&
           !ourDefaultBetweenDelegate.bracesAreInTheSameElement(file, editor, prevCharOffset, nextCharOffset);
  }

  protected boolean isBracePair(char lBrace, char rBrace) {
    return ourDefaultBetweenDelegate.isBracePair(lBrace, rBrace);
  }
}
