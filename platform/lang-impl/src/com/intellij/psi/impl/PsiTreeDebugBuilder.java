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

package com.intellij.psi.impl;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.PsiErrorElement;
import com.intellij.openapi.util.text.StringUtil;


public class PsiTreeDebugBuilder {
  private StringBuffer myBuffer;
  private boolean myShowWhiteSpaces = true;
  private boolean myShowErrorElements = true;

  public PsiTreeDebugBuilder setShowWhiteSpaces(boolean showWhiteSpaces) {
    myShowWhiteSpaces = showWhiteSpaces;
    return this;
  }

  public PsiTreeDebugBuilder setShowErrorElements(boolean showErrorElements) {
    myShowErrorElements = showErrorElements;
    return this;
  }

  public String psiToString(PsiElement root) {
    return psiToString(root, false, false); 
  }

  public String psiToString(PsiElement root, boolean showRanges, boolean showChildrenRanges) {
    myBuffer = new StringBuffer();
    psiToBuffer(root, 0, showRanges, showChildrenRanges);
    return myBuffer.toString();
  }

  private void psiToBuffer(PsiElement root, int indent, boolean showRanges, boolean showChildrenRanges) {
    if (!myShowWhiteSpaces && root instanceof PsiWhiteSpace) return;
    if (!myShowErrorElements && root instanceof PsiErrorElement) return;

    for (int i = 0; i < indent; i++) {
      myBuffer.append(' ');
    }
    final String rootStr = root.toString();
    myBuffer.append(rootStr);
    PsiElement child = root.getFirstChild();
    if (child == null) {
      String text = root.getText();
      assert text != null : "text is null for <" + root + ">";
      text = StringUtil.replace(text, "\n", "\\n");
      text = StringUtil.replace(text, "\r", "\\r");
      text = StringUtil.replace(text, "\t", "\\t");
      myBuffer.append("('");
      myBuffer.append(text);
      myBuffer.append("')");
    }

    if (showRanges) myBuffer.append(root.getTextRange());
    myBuffer.append("\n");
    while (child != null) {
      psiToBuffer(child, indent + 2, showChildrenRanges, showChildrenRanges);
      child = child.getNextSibling();
    }

  }

}
