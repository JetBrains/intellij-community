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
