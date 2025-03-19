// This is a generated file. Not intended for manual editing.
package com.intellij.commandInterface.commandLine;

import com.intellij.psi.tree.IElementType;
import com.intellij.psi.PsiElement;
import com.intellij.lang.ASTNode;
import com.intellij.commandInterface.commandLine.psi.impl.*;

public interface CommandLineElementTypes {

  IElementType ARGUMENT = new CommandLineElementType("ARGUMENT");
  IElementType COMMAND = new CommandLineElementType("COMMAND");
  IElementType OPTION = new CommandLineElementType("OPTION");

  IElementType EQ = new IElementType("=", null);
  IElementType LITERAL_STARTS_FROM_DIGIT = new IElementType("LITERAL_STARTS_FROM_DIGIT", null);
  IElementType LITERAL_STARTS_FROM_LETTER = new IElementType("LITERAL_STARTS_FROM_LETTER", null);
  IElementType LITERAL_STARTS_FROM_SYMBOL = new IElementType("LITERAL_STARTS_FROM_SYMBOL", null);
  IElementType LONG_OPTION_NAME_TOKEN = new IElementType("LONG_OPTION_NAME_TOKEN", null);
  IElementType SHORT_OPTION_NAME_TOKEN = new IElementType("SHORT_OPTION_NAME_TOKEN", null);
  IElementType SINGLE_Q_SPACED_LITERAL_STARTS_FROM_DIGIT = new IElementType("SINGLE_Q_SPACED_LITERAL_STARTS_FROM_DIGIT", null);
  IElementType SINGLE_Q_SPACED_LITERAL_STARTS_FROM_LETTER = new IElementType("SINGLE_Q_SPACED_LITERAL_STARTS_FROM_LETTER", null);
  IElementType SINGLE_Q_SPACED_LITERAL_STARTS_FROM_SYMBOL = new IElementType("SINGLE_Q_SPACED_LITERAL_STARTS_FROM_SYMBOL", null);
  IElementType SPACED_LITERAL_STARTS_FROM_DIGIT = new IElementType("SPACED_LITERAL_STARTS_FROM_DIGIT", null);
  IElementType SPACED_LITERAL_STARTS_FROM_LETTER = new IElementType("SPACED_LITERAL_STARTS_FROM_LETTER", null);
  IElementType SPACED_LITERAL_STARTS_FROM_SYMBOL = new IElementType("SPACED_LITERAL_STARTS_FROM_SYMBOL", null);

  class Factory {
    public static PsiElement createElement(ASTNode node) {
      IElementType type = node.getElementType();
      if (type == ARGUMENT) {
        return new CommandLineArgumentImpl(node);
      }
      else if (type == COMMAND) {
        return new CommandLineCommandImpl(node);
      }
      else if (type == OPTION) {
        return new CommandLineOptionImpl(node);
      }
      throw new AssertionError("Unknown element type: " + type);
    }
  }
}
