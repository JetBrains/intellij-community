package com.intellij.ide.wizard;

import javax.swing.*;

/**
 * @author nick
 * Date: 14.03.2003
 * Time: 19:07:06
 */
public interface Step {
  /**
   * {@link AbstractWizard} calls this method every time step becomes visible.
   */
  void _init();
  /**
   * {@link AbstractWizard} calls this method every time when step should "commit"
   * all its internal data, i.e. when user presses "Next", "Previous" or "Finish" button.
   *
   * @exception CommitStepException if current data is not accepted by the step. {@link AbstractWizard}
   * will show exception's message to the user, so message should be descriptive.
   * @param finishChosen - true if the user chose "Finish" button. It's expected that a step will
   * commit all necessary data in this case since an enclosing wizard will inevitably disappear
   */
  void _commit(boolean finishChosen) throws CommitStepException;
  /**
   * @return step's icon. This method can return <code>null</code>.
   */
  Icon getIcon();
  /**
   * @return {@link JComponent} that represents step's UI in the wizard. This
   * method should not return <code>null</code>.
   */
  JComponent getComponent();
}
