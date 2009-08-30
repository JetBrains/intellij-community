/*
 * Created by IntelliJ IDEA.
 * User: dsl
 * Date: 17.06.2002
 * Time: 20:38:33
 * To change template for new class use
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.refactoring.memberPullUp;

import com.intellij.refactoring.util.JavaDocPolicy;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.ui.IdeBorderFactory;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;

public class JavaDocPanel extends JPanel {
  private JRadioButton myRbJavaDocAsIs = null;
  private JRadioButton myRbJavaDocMove = null;
  private JRadioButton myRbJavaDocCopy = null;
  private final TitledBorder myBorder;

  public JavaDocPanel(String title) {
    setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
    myBorder = IdeBorderFactory.createTitledBorder(title);
    this.setBorder(myBorder);

    myRbJavaDocAsIs = new JRadioButton();
    myRbJavaDocAsIs.setText(RefactoringBundle.message("javadoc.as.is"));
    add(myRbJavaDocAsIs);
    myRbJavaDocAsIs.setFocusable(false);

    myRbJavaDocCopy = new JRadioButton();
    myRbJavaDocCopy.setText(RefactoringBundle.message("javadoc.copy"));
    myRbJavaDocCopy.setFocusable(false);
    add(myRbJavaDocCopy);

    myRbJavaDocMove = new JRadioButton();
    myRbJavaDocMove.setText(RefactoringBundle.message("javadoc.move"));
    myRbJavaDocMove.setFocusable(false);
    add(myRbJavaDocMove);

    ButtonGroup bg = new ButtonGroup();
    bg.add(myRbJavaDocAsIs);
    bg.add(myRbJavaDocCopy);
    bg.add(myRbJavaDocMove);
    bg.setSelected(myRbJavaDocMove.getModel(), true);
  }

  public Dimension getPreferredSize() {
    final Dimension preferredSize = super.getPreferredSize();
    final Dimension borderSize = myBorder.getMinimumSize(this);
    return new Dimension(
      Math.max(preferredSize.width, borderSize.width + 10),
      Math.max(preferredSize.height, borderSize.height)
    );
  }

  public void setPolicy(final int javaDocPolicy) {
    if (javaDocPolicy == JavaDocPolicy.COPY) {
      myRbJavaDocCopy.setSelected(true);
    }
    else if (javaDocPolicy == JavaDocPolicy.MOVE) {
      myRbJavaDocMove.setSelected(true);
    }
    else {
      myRbJavaDocAsIs.setSelected(true);
    }
  }

  public int getPolicy() {
    if (myRbJavaDocCopy != null && myRbJavaDocCopy.isSelected()) {
      return JavaDocPolicy.COPY;
    }
    if (myRbJavaDocMove != null && myRbJavaDocMove.isSelected()) {
      return JavaDocPolicy.MOVE;
    }

    return JavaDocPolicy.ASIS;
  }
}
