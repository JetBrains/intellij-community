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

public class CommandLineCommandImpl extends CommandLineElement implements CommandLineCommand {

  public CommandLineCommandImpl(@NotNull ASTNode node) {
    super(node);
  }

  public void accept(@NotNull CommandLineVisitor visitor) {
    visitor.visitCommand(this);
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof CommandLineVisitor) accept((CommandLineVisitor)visitor);
    else super.accept(visitor);
  }

  @Override
  @NotNull
  public PsiElement getLiteralStartsFromLetter() {
    return findNotNullChildByType(LITERAL_STARTS_FROM_LETTER);
  }

}
