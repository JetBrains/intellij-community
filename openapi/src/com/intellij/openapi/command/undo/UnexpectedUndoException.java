
package com.intellij.openapi.command.undo;

public class UnexpectedUndoException extends Exception{
  public UnexpectedUndoException(String s) {
    super(s);
  }
}