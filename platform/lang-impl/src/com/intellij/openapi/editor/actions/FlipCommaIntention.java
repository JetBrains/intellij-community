// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.actions;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.lang.*;
import com.intellij.lang.parser.GeneratedParserUtilBase;
import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.Presentation;
import com.intellij.modcommand.PsiUpdateModCommandAction;
import com.intellij.openapi.editor.ex.DocumentEx;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.SyntaxTraverser;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.containers.JBIterable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.openapi.editor.actions.lists.DefaultListSplitJoinContextKt.isComma;


public final class FlipCommaIntention extends PsiUpdateModCommandAction<PsiElement> implements DumbAware {
  public FlipCommaIntention() {
    super(PsiElement.class);
  }
  
  @Override
  public @NotNull String getFamilyName() {
    return CodeInsightBundle.message("intention.family.name.flip");
  }

  @Override
  protected @Nullable Presentation getPresentation(@NotNull ActionContext context, @NotNull PsiElement element) {
    PsiElement comma = currentCommaElement(context);
    if (comma == null) {
      return null;
    }
    final PsiElement left = smartAdvance(comma, false);
    final PsiElement right = smartAdvance(comma, true);
    if (left == null || right == null || left.getText().equals(right.getText()) || !Flipper.isCanFlip(left, right)) return null;
    return Presentation.of(CodeInsightBundle.message("intention.name.flip"));
  }

  @Override
  protected void invoke(@NotNull ActionContext context, @NotNull PsiElement element, @NotNull ModPsiUpdater updater) {
    final PsiElement comma = updater.getWritable(currentCommaElement(context));
    if (comma != null) {
      swapAtComma(comma);
    }
  }

  private static void swapAtComma(@NotNull PsiElement comma) {
    PsiElement prev = smartAdvance(comma, false);
    PsiElement next = smartAdvance(comma, true);
    if (prev != null && next != null) {
      if (Flipper.tryFlip(prev, next)) {
        return;
      }
      swapViaDocument(comma, prev, next);
    }
  }

  // not via PSI because such language-unaware change can lead to PSI-text inconsistencies
  private static void swapViaDocument(@NotNull PsiElement comma, PsiElement prev, PsiElement next) {
    DocumentEx document = (DocumentEx)comma.getContainingFile().getFileDocument();

    String prevText = prev.getText();
    String nextText = next.getText();

    TextRange prevRange = prev.getTextRange();
    TextRange nextRange = next.getTextRange();

    document.replaceString(prevRange.getStartOffset(), prevRange.getEndOffset(), nextText);
    nextRange = nextRange.shiftRight(nextText.length() - prevText.length());
    document.replaceString(nextRange.getStartOffset(), nextRange.getEndOffset(), prevText);
  }

  public interface Flipper {
    LanguageExtension<Flipper> EXTENSION = new LanguageExtension<>("com.intellij.flipCommaIntention.flipper");

    /**
     * @return true, if the elements were flipped; false, if the default flip implementation should be used.
     */
    boolean flip(@NotNull PsiElement left, @NotNull PsiElement right);

    /**
     * @return false, if the elements should not be flipped; true, if the default flip implementation should be used.
     */
    default boolean canFlip(@NotNull PsiElement left, @NotNull PsiElement right) {
      return true;
    }

    static boolean tryFlip(PsiElement left, PsiElement right) {
      final Language language = left.getLanguage();
      for (Flipper handler : EXTENSION.allForLanguage(language)) {
        if (handler.flip(left, right)) {
          return true;
        }
      }
      return false;
    }

    static boolean isCanFlip(PsiElement left, PsiElement right) {
      final Language language = left.getLanguage();
      for (Flipper handler : EXTENSION.allForLanguage(language)) {
        if (!handler.canFlip(left, right)) {
          return false;
        }
      }
      return true;
    }
  }

  private static PsiElement currentCommaElement(@NotNull ActionContext context) {
    PsiElement element;
    if (!isComma(element = context.findLeafOnTheLeft()) && !isComma(element = context.findLeaf())) {
      return null;
    }
    return element;
  }

  private static @NotNull JBIterable<PsiElement> getSiblings(PsiElement element, boolean fwd) {
    SyntaxTraverser.ApiEx<PsiElement> api = fwd ? SyntaxTraverser.psiApi() : SyntaxTraverser.psiApiReversed();
    api.next(element);
    JBIterable<PsiElement> flatSiblings = JBIterable.generate(element, api::next).skip(1);
    return SyntaxTraverser.syntaxTraverser(api)
      .withRoots(flatSiblings)
      .expandAndSkip(e -> api.typeOf(e) == GeneratedParserUtilBase.DUMMY_BLOCK)
      .traverse();
  }

  private static boolean isFlippable(PsiElement e) {
    if (e instanceof PsiWhiteSpace || e instanceof PsiComment || e.textMatches("\n")) return false;
    return !StringUtil.collapseWhiteSpace(e.getText()).isEmpty();
  }

  private static @Nullable PsiElement smartAdvance(PsiElement element, boolean fwd) {
    final PsiElement candidate = getSiblings(element, fwd).filter(e -> isFlippable(e)).first();
    if (candidate != null && isBrace(candidate)) return null;
    return candidate;
  }

  private static boolean isBrace(@NotNull PsiElement candidate) {
    final ASTNode node = candidate.getNode();
    if (node != null && node.getFirstChildNode() == null) {
      final PairedBraceMatcher braceMatcher = LanguageBraceMatching.INSTANCE.forLanguage(candidate.getLanguage());
      if (braceMatcher != null) {
        final IElementType elementType = node.getElementType();
        for (BracePair pair : braceMatcher.getPairs()) {
          if (elementType == pair.getLeftBraceType() || elementType == pair.getRightBraceType()) {
            return true;
          }
        }
      }
    }
    return false;
  }
}
