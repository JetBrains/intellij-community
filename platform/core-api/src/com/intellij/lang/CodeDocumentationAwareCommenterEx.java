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
package com.intellij.lang;

import com.intellij.psi.PsiElement;


/**
 * Support for extended code documentation handling.
 */
public interface CodeDocumentationAwareCommenterEx extends CodeDocumentationAwareCommenter {
  /**
   * Documentation comments may consist of various nested elements: e.g. javadoc tags, start/end markers,
   * and comment text elements. This method verifies is given element represents the latter.
   *
   * @param element element to check
   * @return true if the element is a documentation comment part with text
   */
  boolean isDocumentationCommentText(PsiElement element);
}
