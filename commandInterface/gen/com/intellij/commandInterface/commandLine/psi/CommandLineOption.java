// This is a generated file. Not intended for manual editing.
package com.intellij.commandInterface.commandLine.psi;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElement;
import com.intellij.commandInterface.commandLine.CommandLinePart;
import com.intellij.commandInterface.command.Option;

public interface CommandLineOption extends CommandLinePart {

  @Nullable
  PsiElement getLongOptionNameToken();

  @Nullable
  PsiElement getShortOptionNameToken();

  @Nullable @NonNls String getOptionName();

  boolean isLong();

  @Nullable Option findRealOption();

  @Nullable CommandLineArgument findArgument();

}
