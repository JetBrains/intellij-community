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
package com.intellij.codeInsight.editorActions;

import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.tree.JavaDocElementType;
import com.intellij.psi.javadoc.PsiDocTag;
import com.intellij.psi.javadoc.PsiDocTagValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Advises typing in javadoc if necessary.
 * 
 * @author Denis Zhdanov
 * @since 2/2/11 11:17 AM
 */
public class JavadocTypedHandler extends TypedHandlerDelegate {

  private static final char START_TAG_SYMBOL = '<';
  private static final char CLOSE_TAG_SYMBOL = '>';
  private static final char SLASH = '/';
  
  @Override
  public Result charTyped(char c, Project project, Editor editor, PsiFile file) {
    if (project == null || editor == null || file == null) {
      return Result.CONTINUE;
    }
    insertClosingTagIfNecessary(c, project, editor, file);
    return Result.CONTINUE;
  }

  /**
   * Checks if it's necessary to insert closing tag on typed character.
   * 
   * @param c         typed symbol
   * @param project   current project
   * @param editor    current editor
   * @param file      current file
   * @return          <code>true</code> if closing tag is inserted; <code>false</code> otherwise
   */
  private static boolean insertClosingTagIfNecessary(char c, @NotNull Project project, @NotNull Editor editor, @NotNull PsiFile file) {
    if (c != CLOSE_TAG_SYMBOL || !CodeInsightSettings.getInstance().JAVADOC_GENERATE_CLOSING_TAG) {
      return false;
    }

    PsiDocumentManager.getInstance(project).commitAllDocuments();

    if (!isAppropriatePlace(editor, file)) {
      return false;
    }

    // Inspect symbols to the left of the current caret position, insert closing tag only if valid tag is just typed
    // (e.g. don't insert anything on single '>' symbol typing).
    int offset = editor.getCaretModel().getOffset();
    Document document = editor.getDocument();
    CharSequence tagName = getTagName(document.getText(), offset);
    if (tagName == null) {
      return false;
    }

    document.insertString(offset, String.valueOf(START_TAG_SYMBOL) + SLASH + tagName + CLOSE_TAG_SYMBOL);
    return true;
  }

  /**
   * Tries to derive start tag name assuming that given offset points to position just after <code>'>'</code> symbol.
   * <p/>
   * Is expected to return <code>null</code> when offset is not located just after start tag, e.g. the following situations:
   * <pre>
   * <ul>
   *   <li>standalone {@code '>'} symbol (surrounded by white spaces);</li>
   *   <li>after end tag {@code <mytag><mytag>[caret]};</li>
   *   <li>after empty element tag {@code <p/>[caret]};</li>
   * </ul>
   * </pre>
   * 
   * @param text            target text
   * @param afterTagOffset  offset that points after 
   * @return                tag name if the one is parsed; <code>null</code> otherwise
   */
  @Nullable
  static CharSequence getTagName(@NotNull CharSequence text, int afterTagOffset) {
    if (afterTagOffset > text.length()) {
      return null;
    }
    int endOffset = afterTagOffset - 1;
    
    // Check empty element like <p/>
    if (endOffset > 0 && text.charAt(endOffset - 1) == SLASH) {
      return null;
    }
    
    for (int i = endOffset - 1; i >= 0; i--) {
      char c = text.charAt(i);
      switch (c) {
        case '\n': return null;
        case CLOSE_TAG_SYMBOL: return null;
        case START_TAG_SYMBOL:
          if (text.charAt(i + 1) == SLASH) {
            // Handle situation like <tag></tag>[offset].
            return null;
          }
          return text.subSequence(i + 1, endOffset).toString();
        
        // There is a possible case that opening tag has attributes, e.g. <a href='bla-bla-bla'>[offset]. We want to extract
        // only tag name then.
        case ' ':
        case '\t': endOffset = i;
      }
    }
    return null;
  }
  
  private static boolean isAppropriatePlace(Editor editor, PsiFile file) {
    FileViewProvider provider = file.getViewProvider();
    int offset = editor.getCaretModel().getOffset();

    final PsiElement elementAtCaret;
    if (offset < editor.getDocument().getTextLength()) {
      elementAtCaret = provider.findElementAt(offset);
    }
    else {
      elementAtCaret = provider.findElementAt(editor.getDocument().getTextLength() - 1);
    }

    PsiElement element = elementAtCaret;
    while(element instanceof PsiWhiteSpace) {
      element = element.getPrevSibling();
    }

    if (element == null) {
      return false;
    }

    if (element instanceof PsiDocTag) {
      // We don't want to provide closing tag for the type parameters, i.e. at situations like the one below:
      // /**
      //  * @param <T>[caret]
      //  */
      PsiDocTag tag = (PsiDocTag)element;
      if ("param".equals(tag.getName())) {
        final PsiDocTagValue value = tag.getValueElement();
        if (value == null || value.getTextRange().getEndOffset() == offset) {
          return false;
        } 
      } 
    }
    
    ASTNode node = element.getNode();
    return node != null 
           && (JavaDocTokenType.ALL_JAVADOC_TOKENS.contains(node.getElementType())
               || JavaDocElementType.ALL_JAVADOC_ELEMENTS.contains(node.getElementType()));
  }
}
