package com.intellij.ide.wizard;

import javax.swing.*;

/**
 * @author Vladimir Kondratyev
 */
public class StepAdapter implements Step{
  public void _init() {}

  public void _commit(boolean finishChosen) throws CommitStepException {}

  public JComponent getComponent() {
    throw new UnsupportedOperationException();
  }

  public Icon getIcon() {
    throw new UnsupportedOperationException();
  }
}
