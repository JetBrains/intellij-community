package com.intellij.lang.parameterInfo;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.text.CharArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

/**
 * @author Maxim.Mossienko
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
    if (offset < curOffset) return -1;
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

  @Nullable
  public static <E extends PsiElement> E findArgumentList(PsiFile file, int offset, int lbraceOffset,
                                                          @NotNull ParameterInfoHandlerWithTabActionSupport findArgumentListHelper){
    if (file == null) return null;

    CharSequence chars = file.getViewProvider().getContents();
    if (offset >= chars.length()) offset = chars.length() - 1;
    int offset1 = CharArrayUtil.shiftBackward(chars, offset, " \t\n\r");
    if (offset1 < 0) return null;
    boolean acceptRparenth = true;
    boolean acceptLparenth = false;
    if (offset1 != offset){
      offset = offset1;
      acceptRparenth = false;
      acceptLparenth = true;
    }

    PsiElement element = file.findElementAt(offset);
    if (element == null) return null;
    PsiElement parent = element.getParent();

    while(true){
      if (findArgumentListHelper.getArgumentListClass().isInstance(parent)) {
        TextRange range = parent.getTextRange();
        if (!acceptRparenth){
          if (offset == range.getEndOffset() - 1){
            PsiElement[] children = parent.getChildren();
            PsiElement last = children[children.length - 1];
            if (last.getNode().getElementType() == findArgumentListHelper.getActualParametersRBraceType()){
              parent = parent.getParent();
              continue;
            }
          }
        }
        if (!acceptLparenth){
          if (offset == range.getStartOffset()){
            parent = parent.getParent();
            continue;
          }
        }
        if (lbraceOffset >= 0 && range.getStartOffset() != lbraceOffset){
          parent = parent.getParent();
          continue;
        }
        break;
      }
      if (parent instanceof PsiFile) return null;
      parent = parent.getParent();
    }

    PsiElement listParent = parent.getParent();
    for(Class c: (Set<Class>)findArgumentListHelper.getArgumentListAllowedParentClasses()) {
      if (c.isInstance(listParent)) return (E)parent;
    }

    return null;
  }
}
