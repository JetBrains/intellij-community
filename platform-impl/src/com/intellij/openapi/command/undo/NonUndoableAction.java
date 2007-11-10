
package com.intellij.openapi.command.undo;

import com.intellij.openapi.diagnostic.Logger;

public abstract class NonUndoableAction implements UndoableAction{
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.command.undo.NonUndoableAction");

  public final void undo() throws UnexpectedUndoException {
    LOG.assertTrue(false);
  }

  public void redo() throws UnexpectedUndoException {
    LOG.assertTrue(false);
  }

}