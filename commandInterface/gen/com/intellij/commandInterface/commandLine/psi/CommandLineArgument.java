// This is a generated file. Not intended for manual editing.
package com.intellij.commandInterface.commandLine.psi;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElement;
import com.intellij.commandInterface.commandLine.CommandLinePart;
import com.intellij.commandInterface.command.Argument;
import com.intellij.commandInterface.command.Help;
import com.intellij.commandInterface.command.Option;

public interface CommandLineArgument extends CommandLinePart {

  @Nullable
  PsiElement getLiteralStartsFromDigit();

  @Nullable
  PsiElement getLiteralStartsFromLetter();

  @Nullable
  PsiElement getLiteralStartsFromSymbol();

  @Nullable
  PsiElement getSingleQSpacedLiteralStartsFromDigit();

  @Nullable
  PsiElement getSingleQSpacedLiteralStartsFromLetter();

  @Nullable
  PsiElement getSingleQSpacedLiteralStartsFromSymbol();

  @Nullable
  PsiElement getSpacedLiteralStartsFromDigit();

  @Nullable
  PsiElement getSpacedLiteralStartsFromLetter();

  @Nullable
  PsiElement getSpacedLiteralStartsFromSymbol();

  @Nullable Option findOptionForOptionArgument();

  @Nullable Argument findRealArgument();

  @Nullable Help findBestHelp();

  @NotNull String getValueNoQuotes();

}
