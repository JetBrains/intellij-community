// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.psi.formatter;

import com.intellij.formatting.Block;
import com.intellij.formatting.FormattingDocumentModel;
import com.intellij.formatting.FormattingModelEx;
import com.intellij.injected.editor.DocumentWindow;
import com.intellij.lang.ASTNode;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtilBase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class PsiBasedFormattingModel implements FormattingModelEx {

  private static final Logger LOG = Logger.getInstance(PsiBasedFormattingModel.class);

  private final Project myProject;
  private final ASTNode myASTNode;
  private final FormattingDocumentModelImpl myDocumentModel;
  private final @NotNull Block myRootBlock;
  protected boolean myCanModifyAllWhiteSpaces = false;

  public PsiBasedFormattingModel(final PsiFile file,
                                 final @NotNull Block rootBlock,
                                 final FormattingDocumentModelImpl documentModel) {
    myASTNode = SourceTreeToPsiMap.psiElementToTree(file);
    myDocumentModel = documentModel;
    myRootBlock = rootBlock;
    myProject = file.getProject();
  }



  @Override
  public TextRange replaceWhiteSpace(TextRange textRange, String whiteSpace) {
    return replaceWhiteSpace(textRange, null, whiteSpace);
  }

  @Override
  public TextRange replaceWhiteSpace(TextRange textRange, ASTNode nodeAfter, String whiteSpace) {
    String whiteSpaceToUse
      = myDocumentModel.adjustWhiteSpaceIfNecessary(whiteSpace, textRange.getStartOffset(), textRange.getEndOffset(), nodeAfter, true).toString();
    final String wsReplaced = replaceWithPSI(textRange, whiteSpaceToUse);

    if (wsReplaced != null){
      return new TextRange(textRange.getStartOffset(), textRange.getStartOffset() + wsReplaced.length());
    } else {
      return textRange;
    }
  }

  @Override
  public TextRange shiftIndentInsideRange(ASTNode node, TextRange textRange, int shift) {
    return textRange; // TODO: Remove this method from here...
  }

  @Override
  public void commitChanges() {
  }


  private @Nullable String replaceWithPSI(final TextRange textRange, final String whiteSpace) {
    final int offset = textRange.getEndOffset();
    ASTNode leafElement = findElementAt(offset);

    if (leafElement != null) {
      PsiFile hostFile = myASTNode.getPsi().getContainingFile();
      TextRange effectiveRange = textRange;
      List<DocumentWindow> injections =
        InjectedLanguageManager.getInstance(hostFile.getProject()).getCachedInjectedDocumentsInRange(hostFile, TextRange.from(offset, 0));
      if (!injections.isEmpty()) {
        PsiElement injectedElement = PsiDocumentManager.getInstance(myProject).getPsiFile(injections.get(0));
        PsiLanguageInjectionHost host = InjectedLanguageUtilBase.findInjectionHost(injectedElement);
        TextRange corrected = host == null ? null : correctRangeByInjection(textRange, host);
        if (corrected != null) {
          effectiveRange = corrected;
        }
      }

      if (leafElement.getPsi() instanceof PsiFile) {
        return null;
      } else {
        LOG.assertTrue(leafElement.getPsi().isValid());
        return replaceWithPsiInLeaf(effectiveRange, whiteSpace, leafElement);
      }
    }
    else if (textRange.getEndOffset() == myASTNode.getTextLength()){
      CodeStyleManager.getInstance(myProject).performActionWithFormatterDisabled(
        (Runnable)() -> FormatterUtil.replaceLastWhiteSpace(myASTNode, whiteSpace, textRange)
      );
      return whiteSpace;
    }
    else {
      return null;
    }
  }

  private static TextRange correctRangeByInjection(TextRange textRange, PsiLanguageInjectionHost host) {
    ElementManipulator<PsiLanguageInjectionHost> manipulator = ElementManipulators.getManipulator(host);
    if (manipulator == null) return null;

    final TextRange injectionRangeInHost = manipulator.getRangeInElement(host);
    final int hostStartOffset = host.getTextRange().getStartOffset();

    final @NotNull TextRange injectedDocument = injectionRangeInHost.shiftRight(hostStartOffset);

    TextRange intersection = textRange.intersection(injectedDocument);
    return intersection == null ? null : intersection.shiftLeft(injectedDocument.getStartOffset());
  }

  protected @Nullable String replaceWithPsiInLeaf(final TextRange textRange, final String whiteSpace, final ASTNode leafElement) {
    if (!myCanModifyAllWhiteSpaces) {
      if (leafElement.getElementType() == TokenType.WHITE_SPACE) return null;
    }

    CodeStyleManager.getInstance(myProject).performActionWithFormatterDisabled(
      (Runnable)() -> FormatterUtil.replaceWhiteSpace(whiteSpace, leafElement, TokenType.WHITE_SPACE, textRange));

    return whiteSpace;
  }

  protected @Nullable ASTNode findElementAt(final int offset) {
    PsiFile containingFile = myASTNode.getPsi().getContainingFile();
    Project project = containingFile.getProject();

    assert !PsiDocumentManager.getInstance(project).isUncommited(myDocumentModel.getDocument());
    // TODO:default project can not be used for injections, because latter might wants (unavailable) indices

    PsiElement psiElement = project.isDefault() ? null : InjectedLanguageManager.getInstance(containingFile.getProject())
      .findInjectedElementAt(containingFile, offset);
    if (psiElement != null) {
      return psiElement.getNode();
    }

    psiElement = containingFile.findElementAt(offset);
    return psiElement != null ? psiElement.getNode() : null;
  }

  @Override
  public @NotNull FormattingDocumentModel getDocumentModel() {
    return myDocumentModel;
  }

  @Override
  public @NotNull Block getRootBlock() {
    return myRootBlock;
  }
  
  public void canModifyAllWhiteSpaces() {
    myCanModifyAllWhiteSpaces = true;
  }
}
