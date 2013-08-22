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
import com.intellij.openapi.roots.LanguageLevelProjectExtension;
import com.intellij.openapi.util.text.LineTokenizer;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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
    myParser = new JDParser(mySettings, LanguageLevelProjectExtension.getInstance(project).getLanguageLevel());
    myProject = project;
  }

  public CodeStyleSettings getSettings() {
    return mySettings;
  }

  public JDParser getParser() {
    return myParser;
  }

  public void processComment(@Nullable ASTNode element) {
    if (!getSettings().ENABLE_JAVADOC_FORMATTING) return;

    PsiElement psiElement = SourceTreeToPsiMap.treeElementToPsi(element);
    processElementComment(psiElement);
  }

  private void processElementComment(@Nullable PsiElement psiElement) {
    if (psiElement instanceof PsiClass) {
      String newCommentText = formatClassComment((PsiClass)psiElement);
      replaceDocComment(newCommentText, (PsiDocCommentOwner)psiElement);
    }
    else if (psiElement instanceof PsiMethod) {
      String newCommentText = formatMethodComment((PsiMethod)psiElement);
      replaceDocComment(newCommentText, (PsiDocCommentOwner)psiElement);
    }
    else if (psiElement instanceof PsiField) {
      String newCommentText = formatFieldComment((PsiField)psiElement);
      replaceDocComment(newCommentText, (PsiDocCommentOwner)psiElement);
    }
    else if (psiElement instanceof PsiDocComment) {
      processElementComment(psiElement.getParent());
    }
  }

  private void replaceDocComment(@Nullable String newCommentText, @NotNull final PsiDocCommentOwner psiDocCommentOwner) {
    final PsiDocComment oldComment = psiDocCommentOwner.getDocComment();
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
  private String formatClassComment(@NotNull PsiClass psiClass) {
    final String info = getOrigCommentInfo(psiClass);
    if (info == null) return null;

    JDComment comment = getParser().parse(info, new JDClassComment(this));
    return comment.generate(getIndent(psiClass));
  }

  @Nullable
  private String formatMethodComment(@NotNull PsiMethod psiMethod) {
    final String info = getOrigCommentInfo(psiMethod);
    if (info == null) return null;

    JDComment comment = getParser().parse(info, new JDMethodComment(this));
    return comment.generate(getIndent(psiMethod));
  }

  @Nullable
  private String formatFieldComment(@NotNull PsiField psiField) {
    final String info = getOrigCommentInfo(psiField);
    if (info == null) return null;

    JDComment comment = getParser().parse(info, new JDComment(this));
    return comment.generate(getIndent(psiField));
  }

  /**
   * Returns the original comment info of the specified element or null
   * 
   * @param element the specified element
   * @return text chunk
   */
  @Nullable
  private static String getOrigCommentInfo(PsiDocCommentOwner element) {
    StringBuilder sb = new StringBuilder();
    PsiElement e = element.getFirstChild();
    if (!(e instanceof PsiComment)) {
      // no comments for this element
      return null;
    }
    else {
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
            if (text.charAt(2) == '*') {
              text = text.substring(3, Math.max(3, text.length() - 2));
            }
            else {
              text = text.substring(2, Math.max(2, text.length() - 2));
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

      return sb.toString();
    }
  }

  /**
   * Computes indentation of PsiClass, PsiMethod and PsiField elements after formatting
   * @param element PsiClass or PsiMethod or PsiField
   * @return indentation size
   */
  private int getIndentSpecial(@NotNull PsiElement element) {
    assert(element instanceof PsiClass ||
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
  private String getIndent(@NotNull PsiElement element) {
    return StringUtil.repeatSymbol(' ', getIndentSpecial(element));
  }
}
