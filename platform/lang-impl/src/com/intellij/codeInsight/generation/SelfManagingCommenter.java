/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.codeInsight.generation;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

/**
 * @author Maxim.Mossienko
 */
public interface SelfManagingCommenter<T extends SelfManagingCommenter.CommenterDataHolder> {
  ExtensionPointName<SelfManagingCommenter> EP_NAME = ExtensionPointName.create("com.intellij.selfManagingCommenter");

  T createLineCommentingState(int startLine, int endLine, Document document, PsiFile file);
  
  void commentLine(int line, int offset, Document document, PsiFile file, T data);

  void uncommentLine(int line, int startOffset, Document document, PsiFile file, T data);

  boolean isCommented(int line, int lineStart, Document document, PsiFile file, T data);

  @NotNull
  String getCommentPrefix(int line, Document document, PsiFile file, T data);

  interface CommenterDataHolder {
    CommenterDataHolder EMPTY_STATE = new CommenterDataHolder() {}; 
  }
}
