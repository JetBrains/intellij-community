// Copyright 2000-2017 JetBrains s.r.o.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.intellij.lang.parameterInfo;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.Conditions;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.SyntaxTraverser;
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

  @Nullable
  public static <T extends PsiElement> T findParentOfType (PsiFile file, int offset, Class<T> parentClass) {
    return findParentOfTypeWithStopElements(file, offset, parentClass);
  }

  @SafeVarargs
  @Nullable
  public static <T extends PsiElement> T findParentOfTypeWithStopElements (PsiFile file, int offset, Class<T> parentClass, @NotNull Class<? extends PsiElement>... stopAt) {
    PsiElement element = file.findElementAt(offset);
    if (element == null) return null;

    T parentOfType = PsiTreeUtil.getParentOfType(element, parentClass, true, stopAt);
    if (element instanceof PsiWhiteSpace) {
      parentOfType = PsiTreeUtil.getParentOfType(PsiTreeUtil.prevLeaf(element), parentClass, true, stopAt);
    }
    return parentOfType;
  }

  public static int getCurrentParameterIndex(ASTNode argList, int offset, IElementType delimiterType) {
    SyntaxTraverser<ASTNode> s = SyntaxTraverser.astTraverser(argList).expandAndSkip(Conditions.is(argList));
    return getCurrentParameterIndex(s, offset, delimiterType);
  }

  public static <V> int getCurrentParameterIndex(SyntaxTraverser<V> s, int offset, IElementType delimiterType) {
    V root = s.getRoot();
    int curOffset = s.api.rangeOf(root).getStartOffset();
    if (offset < curOffset) return -1;
    int index = 0;

    for (V child : s) {
      curOffset += s.api.rangeOf(child).getLength();
      if (offset < curOffset) break;

      IElementType type = s.api.typeOf(child);
      if (type == delimiterType) index++;
    }

    return index;
  }

  @Nullable
  public static <E extends PsiElement> E findArgumentList(PsiFile file, int offset, int lbraceOffset,
                                                          @NotNull ParameterInfoHandlerWithTabActionSupport findArgumentListHelper) {
    return findArgumentList(file, offset, lbraceOffset, findArgumentListHelper, true);
  }

  /**
   * @param allowOuter whether it's OK to return enclosing argument list (starting at {@code lbraceOffset}) when there exists an inner
   *                   argument list at a given {@code offset}
   */
  @Nullable
  public static <E extends PsiElement> E findArgumentList(PsiFile file, int offset, int lbraceOffset,
                                                          @NotNull ParameterInfoHandlerWithTabActionSupport findArgumentListHelper,
                                                          boolean allowOuter){
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
        if (range != null) {
          if (!acceptRparenth){
            if (offset == range.getEndOffset() - 1){
              PsiElement[] children = parent.getChildren();
              if (children.length == 0) return null;
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
            if (!allowOuter) return null;
            parent = parent.getParent();
            continue;
          }
          break;
        }
      }
      if (parent instanceof PsiFile || parent == null) return null;

      final Set<? extends Class> set = findArgumentListHelper.getArgListStopSearchClasses();
      for (Class aClass : set) {
        if (aClass.isInstance(parent)) return null;
      }

      parent = parent.getParent();
    }

    PsiElement listParent = parent.getParent();
    for(Class c: (Set<Class>)findArgumentListHelper.getArgumentListAllowedParentClasses()) {
      if (c.isInstance(listParent)) return (E)parent;
    }

    return null;
  }
}
