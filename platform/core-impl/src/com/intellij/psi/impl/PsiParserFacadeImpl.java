// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.psi.impl;

import com.intellij.lang.ASTFactory;
import com.intellij.lang.Commenter;
import com.intellij.lang.Language;
import com.intellij.lang.LanguageCommenters;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.DummyHolderFactory;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.psi.impl.source.tree.FileElement;
import com.intellij.psi.impl.source.tree.LeafElement;
import com.intellij.psi.impl.source.tree.TreeElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;


public final class PsiParserFacadeImpl implements PsiParserFacade {
  private final PsiManagerEx myManager;

  public PsiParserFacadeImpl(@NotNull Project project) {
    myManager = PsiManagerEx.getInstanceEx(project);
  }

  @Override
  @NotNull
  public PsiElement createWhiteSpaceFromText(@NotNull @NonNls String text) throws IncorrectOperationException {
    FileElement holderElement = DummyHolderFactory.createHolder(myManager, null).getTreeElement();
    LeafElement newElement = ASTFactory.leaf(TokenType.WHITE_SPACE, holderElement.getCharTable().intern(text));
    holderElement.rawAddChildren(newElement);
    GeneratedMarkerVisitor.markGenerated(newElement.getPsi());
    return newElement.getPsi();
  }

  @Override
  @NotNull
  public PsiComment createLineCommentFromText(@NotNull LanguageFileType fileType,
                                              @NotNull String text) throws IncorrectOperationException {
    return createLineCommentFromText(fileType.getLanguage(), text);
  }

  @Override
  @NotNull
  public PsiComment createLineCommentFromText(@NotNull Language language,
                                              @NotNull String text) throws IncorrectOperationException {
    Commenter commenter = LanguageCommenters.INSTANCE.forLanguage(language);
    assert commenter != null;
    String prefix = commenter.getLineCommentPrefix();
    if (prefix == null) {
      throw new IncorrectOperationException("No line comment prefix defined for language " + language.getID());
    }

    PsiFile aFile = createDummyFile(language, prefix + text);
    return findPsiCommentChild(aFile);
  }

  @NotNull
  @Override
  public PsiComment createBlockCommentFromText(@NotNull Language language,
                                               @NotNull String text) throws IncorrectOperationException {
    Commenter commenter = LanguageCommenters.INSTANCE.forLanguage(language);
    assert commenter != null : language;
    String blockCommentPrefix = commenter.getBlockCommentPrefix();
    String blockCommentSuffix = commenter.getBlockCommentSuffix();
    assert blockCommentPrefix != null && blockCommentSuffix != null;

    PsiFile aFile = createDummyFile(language, blockCommentPrefix + text + blockCommentSuffix);
    return findPsiCommentChild(aFile);
  }

  @Override
  @NotNull
  public PsiComment createLineOrBlockCommentFromText(@NotNull Language language,
                                                     @NotNull String text) throws IncorrectOperationException {
    Commenter commenter = LanguageCommenters.INSTANCE.forLanguage(language);
    assert commenter != null : language;
    String prefix = commenter.getLineCommentPrefix();
    String blockCommentPrefix = commenter.getBlockCommentPrefix();
    String blockCommentSuffix = commenter.getBlockCommentSuffix();
    assert prefix != null || (blockCommentPrefix != null && blockCommentSuffix != null);

    PsiFile aFile = createDummyFile(language, prefix != null ? (prefix + text) : (blockCommentPrefix + text + blockCommentSuffix));
    return findPsiCommentChild(aFile);
  }

  private PsiComment findPsiCommentChild(PsiFile aFile) {
    PsiComment comment = PsiTreeUtil.findChildOfType(aFile, PsiComment.class);
    if (comment == null) {
      throw new IncorrectOperationException("Incorrect comment \"" + aFile.getText() + "\".");
    }

    DummyHolderFactory.createHolder(myManager, (TreeElement)SourceTreeToPsiMap.psiElementToTree(comment), null);
    return comment;
  }

  private PsiFile createDummyFile(Language language, String text) {
    return PsiFileFactory.getInstance(myManager.getProject()).createFileFromText("_Dummy_", language, text);
  }
}
