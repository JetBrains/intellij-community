// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.source.codeStyle.javadoc;

import com.intellij.application.options.CodeStyle;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.lang.ASTNode;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.LineTokenizer;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.JavaCodeStyleSettings;
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
  private static final Logger LOG = Logger.getInstance(CommentFormatter.class);

  private final CodeStyleSettings mySettings;
  private final JDParser myParser;
  private final Project myProject;

  /**
   * @deprecated Use {@link ##CommentFormatter(PsiFile)} instead.
   */
  @Deprecated
  public CommentFormatter(@NotNull Project project) {
    mySettings = CodeStyle.getSettings(project);
    myParser = new JDParser(mySettings);
    myProject = project;
  }

  public CommentFormatter(@NotNull PsiFile file) {
    mySettings = CodeStyle.getSettings(file);
    myParser = new JDParser(mySettings);
    myProject = file.getProject();
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

  private int getIndentSpecial(@NotNull PsiElement element) {
    if (!(element instanceof PsiMember)) {
      return 0;
    }

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
   * Used while formatting Javadoc. We need precise element indentation after formatting to wrap comments correctly.
   */
  @NotNull
  public String getIndent(@NotNull PsiElement element) {
    return StringUtil.repeatSymbol(' ', getIndentSpecial(element));
  }
}