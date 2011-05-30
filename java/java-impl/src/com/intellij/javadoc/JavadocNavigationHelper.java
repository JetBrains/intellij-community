/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.javadoc;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.psi.JavaDocTokenType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.javadoc.PsiDocTag;
import com.intellij.psi.javadoc.PsiDocTagValue;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * This class is not singleton but provides {@link #getInstance() single-point-of-usage field}.
 * 
 * @author Denis Zhdanov
 * @since 5/27/11 2:35 PM
 */
public class JavadocNavigationHelper {
  
  private static final JavadocNavigationHelper INSTANCE = new JavadocNavigationHelper();
  
  @NotNull
  public static JavadocNavigationHelper getInstance() {
    return INSTANCE;
  }

  /**
   * Returns information about all lines that contain javadoc parameters and are adjacent to the one that holds given offset.  
   *
   * @param psiFile       PSI holder for the document exposed the given editor
   * @param editor        target editor
   * @param offset        target offset that identifies anchor line to check
   * @return              list of javadoc parameter infos for the target lines if any; empty list otherwise
   */
  @SuppressWarnings("MethodMayBeStatic")
  @NotNull
  public List<JavadocParameterInfo> parse(@NotNull PsiFile psiFile, @NotNull Editor editor, int offset) {
    List<JavadocParameterInfo> result = new ArrayList<JavadocParameterInfo>();
    final PsiElement elementAtCaret = psiFile.findElementAt(offset);
    if (elementAtCaret == null) {
      return result;
    }

    PsiDocTag tag = PsiTreeUtil.getParentOfType(elementAtCaret, PsiDocTag.class);
    if (tag == null) {
      // Due to javadoc PSI specifics.
      if (elementAtCaret instanceof PsiWhiteSpace) {
        final PsiElement prevSibling = elementAtCaret.getPrevSibling();
        if (prevSibling != null) {
          tag = PsiTreeUtil.getParentOfType(prevSibling, PsiDocTag.class, false);
        } 
      }
    }
    if (tag == null) {
      return result;
    } 
    
    JavadocParameterInfo anchorInfo = parse(tag, editor);
    if (anchorInfo == null) {
      return result;
    }
    
    // Parse previous parameters.
    for (PsiElement e = tag.getPrevSibling(); e != null; e = e.getPrevSibling()) {
      JavadocParameterInfo info = parse(e, editor);
      if (info == null) {
        break;
      }
      result.add(0, info);
    }
    
    result.add(anchorInfo);

    // Parse subsequent parameters.
    for (PsiElement e = tag.getNextSibling(); e != null; e = e.getNextSibling()) {
      JavadocParameterInfo info = parse(e, editor);
      if (info == null) {
        break;
      }
      result.add(info);
    }

    return result;
  }

  @Nullable
  private static JavadocParameterInfo parse(@NotNull PsiElement element, @NotNull Editor editor) {
    final PsiDocTag tag = PsiTreeUtil.getParentOfType(element, PsiDocTag.class, false);
    if (tag == null) {
      return null;
    }

    final PsiDocTagValue paramRef = PsiTreeUtil.getChildOfType(tag, PsiDocTagValue.class);
    if (paramRef == null) {
      return null;
    }

    for (PsiElement e = paramRef.getNextSibling(); e != null; e = e.getNextSibling()) {
      final ASTNode node = e.getNode();
      if (node == null) {
        break;
      }
      final IElementType elementType = node.getElementType();
      if (elementType == JavaDocTokenType.DOC_COMMENT_DATA) {
        return new JavadocParameterInfo(
          editor.offsetToLogicalPosition(paramRef.getTextRange().getEndOffset()),
          editor.offsetToLogicalPosition(e.getTextRange().getStartOffset())
        );
      }
      else if (elementType == JavaDocTokenType.DOC_COMMENT_LEADING_ASTERISKS) {
        break;
      }
    }
    return new JavadocParameterInfo(editor.offsetToLogicalPosition(paramRef.getTextRange().getEndOffset()), null);
  }
  
  /**
   * Encapsulates information about source code line that holds javadoc parameter.
   */
  public static class JavadocParameterInfo {

    /**
     * Logical position that points to location just after javadoc parameter name.
     * <p/>
     * Example:
     * <pre>
     *   /**
     *    * @param i[X]  description
     *    *&#47;
     * </pre>
     */
    @NotNull public final  LogicalPosition parameterNameEndPosition;
    @Nullable public final LogicalPosition parameterDescriptionStartPosition;

    public JavadocParameterInfo(@NotNull LogicalPosition parameterNameEndPosition, LogicalPosition parameterDescriptionStartPosition) {
      this.parameterNameEndPosition = parameterNameEndPosition;
      this.parameterDescriptionStartPosition = parameterDescriptionStartPosition;
    }

    @Override
    public String toString() {
      return "name end: " + parameterNameEndPosition + ", description start: " + parameterDescriptionStartPosition;
    }
  }
}
