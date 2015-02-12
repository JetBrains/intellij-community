/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

package com.intellij.psi.impl;

import com.intellij.lang.ASTFactory;
import com.intellij.lang.Commenter;
import com.intellij.lang.Language;
import com.intellij.lang.LanguageCommenters;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.DummyHolderFactory;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.psi.impl.source.tree.FileElement;
import com.intellij.psi.impl.source.tree.LeafElement;
import com.intellij.psi.impl.source.tree.TreeElement;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * @author yole
 */
public class PsiParserFacadeImpl implements PsiParserFacade {
  protected final PsiManagerEx myManager;

  public PsiParserFacadeImpl(PsiManagerEx manager) {
    myManager = manager;
  }

  @Override
  @NotNull
  public PsiElement createWhiteSpaceFromText(@NotNull @NonNls String text) throws IncorrectOperationException {
    final FileElement holderElement = DummyHolderFactory.createHolder(myManager, null).getTreeElement();
    final LeafElement newElement = ASTFactory.leaf(TokenType.WHITE_SPACE, holderElement.getCharTable().intern(text));
    holderElement.rawAddChildren(newElement);
    GeneratedMarkerVisitor.markGenerated(newElement.getPsi());
    return newElement.getPsi();
  }

  @Override
  @NotNull
  public PsiComment createLineCommentFromText(@NotNull final LanguageFileType fileType,
                                              @NotNull final String text) throws IncorrectOperationException {
    Commenter commenter = LanguageCommenters.INSTANCE.forLanguage(fileType.getLanguage());
    assert commenter != null;
    String prefix = commenter.getLineCommentPrefix();
    if (prefix == null) {
      throw new IncorrectOperationException("No line comment prefix defined for language " + fileType.getLanguage().getID());
    }

    PsiFile aFile = createDummyFile(prefix + text, fileType);
    return findPsiCommentChild(aFile);
  }

  @NotNull
  @Override
  public PsiComment createBlockCommentFromText(@NotNull Language language, @NotNull String text) throws IncorrectOperationException {
    Commenter commenter = LanguageCommenters.INSTANCE.forLanguage(language);
    assert commenter != null : language;
    final String blockCommentPrefix = commenter.getBlockCommentPrefix();
    final String blockCommentSuffix = commenter.getBlockCommentSuffix();

    PsiFile aFile = PsiFileFactory.getInstance(myManager.getProject()).createFileFromText("_Dummy_", language,
                                                                                          (blockCommentPrefix + text + blockCommentSuffix));
    return findPsiCommentChild(aFile);
  }

  @Override
  @NotNull
  public PsiComment createLineOrBlockCommentFromText(@NotNull Language lang, @NotNull String text)
    throws IncorrectOperationException {
    Commenter commenter = LanguageCommenters.INSTANCE.forLanguage(lang);
    assert commenter != null:lang;
    String prefix = commenter.getLineCommentPrefix();
    final String blockCommentPrefix = commenter.getBlockCommentPrefix();
    final String blockCommentSuffix = commenter.getBlockCommentSuffix();
    assert prefix != null || (blockCommentPrefix != null && blockCommentSuffix != null);

    PsiFile aFile = PsiFileFactory.getInstance(myManager.getProject()).createFileFromText("_Dummy_", lang, prefix != null ? (prefix + text) : (blockCommentPrefix + text + blockCommentSuffix));
    return findPsiCommentChild(aFile);
  }

  private PsiComment findPsiCommentChild(PsiFile aFile) {
    PsiElement[] children = aFile.getChildren();
    for (PsiElement aChildren : children) {
      if (aChildren instanceof PsiComment) {
        PsiComment comment = (PsiComment)aChildren;
        DummyHolderFactory.createHolder(myManager, (TreeElement)SourceTreeToPsiMap.psiElementToTree(comment), null);
        return comment;
      }
    }
    throw new IncorrectOperationException("Incorrect comment \"" + aFile.getText() + "\".");
  }

  protected PsiFile createDummyFile(String text, final LanguageFileType fileType) {
    String ext = fileType.getDefaultExtension();
    @NonNls String fileName = "_Dummy_." + ext;

    return PsiFileFactory.getInstance(myManager.getProject()).createFileFromText(fileType, fileName, text, 0, text.length());
  }
}
