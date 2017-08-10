/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.psi.impl.source.codeStyle.javadoc;

import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.lang.ASTNode;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.LineTokenizer;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.psi.codeStyle.JavaCodeStyleSettings;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.text.CharArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.psi.impl.source.codeStyle.javadoc.JDParser.CommentInfo;

/**
 * @author max
 */
public class CommentFormatter {
  private static final Logger LOG = Logger.getInstance(
    "#com.intellij.psi.impl.source.codeStyle.javadoc.CommentFormatter");

  private final CodeStyleSettings mySettings;
  private final JDParser myParser;
  private final Project myProject;

  public CommentFormatter(@NotNull Project project) {
    mySettings = CodeStyleSettingsManager.getSettings(project);
    myParser = new JDParser(mySettings);
    myProject = project;
  }

  public JavaCodeStyleSettings getSettings() {
    return mySettings.getCustomSettings(JavaCodeStyleSettings.class);
  }

  public JDParser getParser() {
    return myParser;
  }

  public void processComment(@Nullable ASTNode element) {
    if (!getSettings().ENABLE_JAVADOC_FORMATTING) return;

    PsiElement psiElement = SourceTreeToPsiMap.treeElementToPsi(element);
    if (psiElement != null) {
      getParser().formatCommentText(psiElement, this);
    }
  }

  public void replaceCommentText(@Nullable String newCommentText, @Nullable PsiDocComment oldComment) {
    if (newCommentText != null) newCommentText = stripSpaces(newCommentText);
    if (newCommentText == null || oldComment == null || newCommentText.equals(oldComment.getText())) {
      return;
    }
    try {
      PsiComment newComment = JavaPsiFacade.getInstance(myProject).getElementFactory().createCommentFromText(
        newCommentText, null);
      final ASTNode oldNode = oldComment.getNode();
      final ASTNode newNode = newComment.getNode();
      assert oldNode != null && newNode != null;
      final ASTNode parent = oldNode.getTreeParent();
      parent.replaceChild(oldNode, newNode); //important to replace with tree operation to avoid resolve and repository update
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
    }
  }

  private static String stripSpaces(String text) {
    String[] lines = LineTokenizer.tokenize(text.toCharArray(), false);
    StringBuilder buf = new StringBuilder(text.length());
    for (int i = 0; i < lines.length; i++) {
      if (i > 0) buf.append('\n');
      buf.append(rTrim(lines[i]));
    }
    return buf.toString();
  }

  private static String rTrim(String text) {
    int idx = text.length();
    while (idx > 0) {
      if (!Character.isWhitespace(text.charAt(idx-1))) break;
      idx--;
    }
    return text.substring(0, idx);
  }

  @Nullable
  public static CommentInfo getOrigCommentInfo(PsiDocCommentOwner element) {
    PsiElement e = element.getFirstChild();
    if (!(e instanceof PsiComment)) {
      //no comments for this element
      return null;
    }
    else {
      return getCommentInfo((PsiComment)e);
    }
  }

  @Nullable
  public static CommentInfo getCommentInfo(PsiComment element) {
    String commentHeader = null;
    String commentFooter = null;

    StringBuilder sb = new StringBuilder();
    PsiElement e = element;
    boolean first = true;
    while (true) {
      if (e instanceof PsiDocComment) {
        PsiComment cm = (PsiComment)e;
        String text = cm.getText();
        if (text.startsWith("//")) {
          if (!first) sb.append('\n');
          sb.append(text.substring(2).trim());
        }
        else if (text.startsWith("/*")) {
          int commentHeaderEndOffset = CharArrayUtil.shiftForward(text, 1, "*");
          int commentFooterStartOffset = CharArrayUtil.shiftBackward(text, text.length() - 2, "*");

          if (commentHeaderEndOffset <= commentFooterStartOffset) {
            commentHeader = text.substring(0, commentHeaderEndOffset);
            commentFooter = text.substring(commentFooterStartOffset + 1);
            text = text.substring(commentHeaderEndOffset, commentFooterStartOffset + 1);
          }
          else {
            commentHeader = text.substring(0, commentHeaderEndOffset);
            text = "";
            commentFooter = "";
          }
          sb.append(text);
        }
      }
      else if (!(e instanceof PsiWhiteSpace) && !(e instanceof PsiComment)) {
        break;
      }
      first = false;
      e = e.getNextSibling();
    }

    return new CommentInfo(commentHeader, sb.toString(), commentFooter);
  }

  /**
   * Computes indentation of PsiClass, PsiMethod and PsiField elements after formatting
   * @param element PsiClass or PsiMethod or PsiField
   * @return indentation size
   */
  private int getIndentSpecial(@NotNull PsiElement element) {
    if (element instanceof PsiDocComment) {
      return 0;
    }
    LOG.assertTrue(element instanceof PsiClass ||
                   element instanceof PsiField ||
                   element instanceof PsiMethod);

    int indentSize = mySettings.getIndentSize(JavaFileType.INSTANCE);
    boolean doNotIndentTopLevelClassMembers = mySettings.getCommonSettings(JavaLanguage.INSTANCE).DO_NOT_INDENT_TOP_LEVEL_CLASS_MEMBERS;

    int indent = 0;
    PsiClass top = PsiUtil.getTopLevelClass(element);
    while (top != null && !element.isEquivalentTo(top)) {
      if (doNotIndentTopLevelClassMembers && element.getParent().isEquivalentTo(top)) {
        break;
      }
      element = element.getParent();
      indent += indentSize;
    }

    return indent;
  }

  /**
   * Used while formatting javadocs. We need precise element indentation after formatting to wrap comments correctly.
   * Used only for PsiClass, PsiMethod and PsiFields.
   * @return indent which would be used for the given element when it's formatted according to the current code style settings
   */
  @NotNull
  public String getIndent(@NotNull PsiElement element) {
    return StringUtil.repeatSymbol(' ', getIndentSpecial(element));
  }
}
