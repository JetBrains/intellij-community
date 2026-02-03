// This is a generated file. Not intended for manual editing.
package com.intellij.commandInterface.commandLine.psi.impl;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.util.PsiTreeUtil;
import static com.intellij.commandInterface.commandLine.CommandLineElementTypes.*;
import com.intellij.commandInterface.commandLine.CommandLineElement;
import com.intellij.commandInterface.commandLine.psi.*;
import com.intellij.commandInterface.command.Argument;
import com.intellij.commandInterface.command.Help;
import com.intellij.commandInterface.command.Option;

public class CommandLineArgumentImpl extends CommandLineElement implements CommandLineArgument {

  public CommandLineArgumentImpl(@NotNull ASTNode node) {
    super(node);
  }

  public void accept(@NotNull CommandLineVisitor visitor) {
    visitor.visitArgument(this);
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof CommandLineVisitor) accept((CommandLineVisitor)visitor);
    else super.accept(visitor);
  }

  @Override
  @Nullable
  public PsiElement getLiteralStartsFromDigit() {
    return findChildByType(LITERAL_STARTS_FROM_DIGIT);
  }

  @Override
  @Nullable
  public PsiElement getLiteralStartsFromLetter() {
    return findChildByType(LITERAL_STARTS_FROM_LETTER);
  }

  @Override
  @Nullable
  public PsiElement getLiteralStartsFromSymbol() {
    return findChildByType(LITERAL_STARTS_FROM_SYMBOL);
  }

  @Override
  @Nullable
  public PsiElement getSingleQSpacedLiteralStartsFromDigit() {
    return findChildByType(SINGLE_Q_SPACED_LITERAL_STARTS_FROM_DIGIT);
  }

  @Override
  @Nullable
  public PsiElement getSingleQSpacedLiteralStartsFromLetter() {
    return findChildByType(SINGLE_Q_SPACED_LITERAL_STARTS_FROM_LETTER);
  }

  @Override
  @Nullable
  public PsiElement getSingleQSpacedLiteralStartsFromSymbol() {
    return findChildByType(SINGLE_Q_SPACED_LITERAL_STARTS_FROM_SYMBOL);
  }

  @Override
  @Nullable
  public PsiElement getSpacedLiteralStartsFromDigit() {
    return findChildByType(SPACED_LITERAL_STARTS_FROM_DIGIT);
  }

  @Override
  @Nullable
  public PsiElement getSpacedLiteralStartsFromLetter() {
    return findChildByType(SPACED_LITERAL_STARTS_FROM_LETTER);
  }

  @Override
  @Nullable
  public PsiElement getSpacedLiteralStartsFromSymbol() {
    return findChildByType(SPACED_LITERAL_STARTS_FROM_SYMBOL);
  }

  @Override
  public @Nullable Option findOptionForOptionArgument() {
    return CommandLinePsiImplUtils.findOptionForOptionArgument(this);
  }

  @Override
  public @Nullable Argument findRealArgument() {
    return CommandLinePsiImplUtils.findRealArgument(this);
  }

  @Override
  public @Nullable Help findBestHelp() {
    return CommandLinePsiImplUtils.findBestHelp(this);
  }

  @Override
  public @NotNull String getValueNoQuotes() {
    return CommandLinePsiImplUtils.getValueNoQuotes(this);
  }

}
