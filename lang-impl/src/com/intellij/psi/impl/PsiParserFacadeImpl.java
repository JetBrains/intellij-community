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

  @NotNull
  public PsiElement createWhiteSpaceFromText(@NotNull @NonNls String text) throws IncorrectOperationException {
    final FileElement holderElement = DummyHolderFactory.createHolder(myManager, null).getTreeElement();
    final LeafElement newElement = ASTFactory.leaf(TokenType.WHITE_SPACE, holderElement.getCharTable().intern(text));
    holderElement.rawAddChildren(newElement);
    return newElement.getPsi();
  }

  @NotNull
  public PsiComment createLineCommentFromText(@NotNull final LanguageFileType fileType,
                                              @NotNull final String text) throws IncorrectOperationException {
    Commenter commenter = LanguageCommenters.INSTANCE.forLanguage(fileType.getLanguage());
    assert commenter != null;
    String prefix = commenter.getLineCommentPrefix();
    assert prefix != null;

    PsiFile aFile = createDummyFile(prefix + text, fileType);
    PsiElement[] children = aFile.getChildren();
    for (PsiElement aChildren : children) {
      if (aChildren instanceof PsiComment) {
        PsiComment comment = (PsiComment)aChildren;
        DummyHolderFactory.createHolder(myManager, (TreeElement)SourceTreeToPsiMap.psiElementToTree(comment), null);
        return comment;
      }
    }
    throw new IncorrectOperationException("Incorrect comment \"" + text + "\".");
  }

  @NotNull
  public PsiComment createLineOrBlockCommentFromText(@NotNull Language lang, @NotNull String text)
    throws IncorrectOperationException {
    Commenter commenter = LanguageCommenters.INSTANCE.forLanguage(lang);
    assert commenter != null;
    String prefix = commenter.getLineCommentPrefix();
    final String blockCommentPrefix = commenter.getBlockCommentPrefix();
    final String blockCommentSuffix = commenter.getBlockCommentSuffix();
    assert prefix != null || (blockCommentPrefix != null && blockCommentSuffix != null);

    PsiFile aFile = PsiFileFactory.getInstance(myManager.getProject()).createFileFromText("_Dummy_", lang, prefix != null ? (prefix + text) : (blockCommentPrefix + text + blockCommentSuffix));
    PsiElement[] children = aFile.getChildren();
    for (PsiElement aChildren : children) {
      if (aChildren instanceof PsiComment) {
        PsiComment comment = (PsiComment)aChildren;
        DummyHolderFactory.createHolder(myManager, (TreeElement)SourceTreeToPsiMap.psiElementToTree(comment), null);
        return comment;
      }
    }
    throw new IncorrectOperationException("Incorrect comment \"" + text + "\".");
  }

  protected PsiFile createDummyFile(String text, final LanguageFileType fileType) {
    String ext = fileType.getDefaultExtension();
    @NonNls String fileName = "_Dummy_." + ext;

    return PsiFileFactory.getInstance(myManager.getProject()).createFileFromText(fileType, fileName, text, 0, text.length());
  }
}
