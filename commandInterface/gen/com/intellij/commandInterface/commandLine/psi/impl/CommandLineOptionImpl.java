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
import com.intellij.commandInterface.command.Option;

public class CommandLineOptionImpl extends CommandLineElement implements CommandLineOption {

  public CommandLineOptionImpl(@NotNull ASTNode node) {
    super(node);
  }

  public void accept(@NotNull CommandLineVisitor visitor) {
    visitor.visitOption(this);
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof CommandLineVisitor) accept((CommandLineVisitor)visitor);
    else super.accept(visitor);
  }

  @Override
  @Nullable
  public PsiElement getLongOptionNameToken() {
    return findChildByType(LONG_OPTION_NAME_TOKEN);
  }

  @Override
  @Nullable
  public PsiElement getShortOptionNameToken() {
    return findChildByType(SHORT_OPTION_NAME_TOKEN);
  }

  @Override
  public @Nullable @NonNls String getOptionName() {
    return CommandLinePsiImplUtils.getOptionName(this);
  }

  @Override
  public boolean isLong() {
    return CommandLinePsiImplUtils.isLong(this);
  }

  @Override
  public @Nullable Option findRealOption() {
    return CommandLinePsiImplUtils.findRealOption(this);
  }

  @Override
  public @Nullable CommandLineArgument findArgument() {
    return CommandLinePsiImplUtils.findArgument(this);
  }

}
