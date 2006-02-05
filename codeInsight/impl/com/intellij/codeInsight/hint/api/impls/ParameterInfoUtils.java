package com.intellij.codeInsight.hint.api.impls;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.lang.ASTNode;

/**
 * Created by IntelliJ IDEA.
 * User: Maxim.Mossienko
 * Date: Feb 1, 2006
 * Time: 7:54:48 PM
 * To change this template use File | Settings | File Templates.
 */
public class ParameterInfoUtils {
  public static final String DEFAULT_PARAMETER_CLOSE_CHARS = ",){}";

  public static <T extends PsiElement> T findParentOfType (PsiFile file, int offset, Class<T> parentClass) {
    PsiElement element = file.findElementAt(offset);
    if (element == null) return null;
    return PsiTreeUtil.getParentOfType(element, parentClass);
  }

  public static int getCurrentParameterIndex(ASTNode argList, int offset, IElementType delimiterType) {
    int curOffset = argList.getTextRange().getStartOffset();
    ASTNode[] children = argList.getChildren(null);
    int index = 0;

    for (ASTNode child : children) {
      curOffset += child.getTextLength();
      if (offset < curOffset) break;
      IElementType type;

      type = child.getElementType();
      if (type == delimiterType) index++;
    }

    return index;
  }
}
