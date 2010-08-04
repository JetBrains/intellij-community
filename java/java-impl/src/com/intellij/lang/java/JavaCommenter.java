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
package com.intellij.lang.java;

import com.intellij.lang.CodeDocumentationAwareCommenter;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.PsiComment;
import com.intellij.psi.impl.source.tree.JavaDocElementType;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.Nullable;

/**
 * @author max
 */
public class JavaCommenter implements CodeDocumentationAwareCommenter {

  public String getLineCommentPrefix() {
    return "//";
  }

  public String getBlockCommentPrefix() {
    return "/*";
  }

  public String getBlockCommentSuffix() {
    return "*/";
  }

  public String getCommentedBlockCommentPrefix() {
    return null;
  }

  public String getCommentedBlockCommentSuffix() {
    return null;
  }

  @Nullable
  public IElementType getLineCommentTokenType() {
    return JavaTokenType.END_OF_LINE_COMMENT;
  }

  @Nullable
  public IElementType getBlockCommentTokenType() {
    return JavaTokenType.C_STYLE_COMMENT;
  }

  @Nullable
  public IElementType getDocumentationCommentTokenType() {
    return JavaDocElementType.DOC_COMMENT;
  }

  public String getDocumentationCommentPrefix() {
    return "/**";
  }

  public String getDocumentationCommentLinePrefix() {
    return "*";
  }

  public String getDocumentationCommentSuffix() {
    return "*/";
  }

  public boolean isDocumentationComment(final PsiComment element) {
    return element instanceof PsiDocComment;
  }
}
