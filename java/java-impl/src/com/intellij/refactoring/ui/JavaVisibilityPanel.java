/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/*
 * Created by IntelliJ IDEA.
 * User: dsl
 * Date: 07.06.2002
 * Time: 18:16:19
 * To change template for new class use 
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.refactoring.ui;

import com.intellij.psi.PsiModifier;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.util.VisibilityUtil;
import com.intellij.ui.IdeBorderFactory;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.util.EventListener;

public class JavaVisibilityPanel extends VisibilityPanelBase {
  private JRadioButton myRbAsIs;
  private JRadioButton myRbEscalate;
  private final JRadioButton myRbPrivate;
  private final JRadioButton myRbProtected;
  private final JRadioButton myRbPackageLocal;
  private final JRadioButton myRbPublic;

  public JavaVisibilityPanel(boolean hasAsIs, final boolean hasEscalate) {
    setBorder(IdeBorderFactory.createTitledBorder(RefactoringBundle.message("visibility.border.title")));
    setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
    ButtonGroup bg = new ButtonGroup();

    ItemListener listener = new ItemListener() {
      public void itemStateChanged(ItemEvent e) {
        if(e.getStateChange() == ItemEvent.SELECTED) {
          myEventDispatcher.getMulticaster().stateChanged(new ChangeEvent(this));
        }
      }
    };

    if(hasEscalate) {
      myRbEscalate = new JRadioButton();
      myRbEscalate.setText(RefactoringBundle.getEscalateVisibility());
      myRbEscalate.addItemListener(listener);
      add(myRbEscalate);
      bg.add(myRbEscalate);
    }

    if(hasAsIs) {
      myRbAsIs = new JRadioButton();
      myRbAsIs.setText(RefactoringBundle.getVisibilityAsIs());
      myRbAsIs.addItemListener(listener);
      add(myRbAsIs);
      bg.add(myRbAsIs);
    }



    myRbPrivate = new JRadioButton();
    myRbPrivate.setText(RefactoringBundle.getVisibilityPrivate());
    myRbPrivate.addItemListener(listener);
    myRbPrivate.setFocusable(false);
    add(myRbPrivate);
    bg.add(myRbPrivate);

    myRbPackageLocal = new JRadioButton();
    myRbPackageLocal.setText(RefactoringBundle.getVisibilityPackageLocal());
    myRbPackageLocal.addItemListener(listener);
    myRbPackageLocal.setFocusable(false);
    add(myRbPackageLocal);
    bg.add(myRbPackageLocal);

    myRbProtected = new JRadioButton();
    myRbProtected.setText(RefactoringBundle.getVisibilityProtected());
    myRbProtected.addItemListener(listener);
    myRbProtected.setFocusable(false);
    add(myRbProtected);
    bg.add(myRbProtected);

    myRbPublic = new JRadioButton();
    myRbPublic.setText(RefactoringBundle.getVisibilityPublic());
    myRbPublic.addItemListener(listener);
    myRbPublic.setFocusable(false);
    add(myRbPublic);
    bg.add(myRbPublic);
  }


  public String getVisibility() {
    if (myRbPublic.isSelected()) {
      return PsiModifier.PUBLIC;
    }
    if (myRbPackageLocal.isSelected()) {
      return PsiModifier.PACKAGE_LOCAL;
    }
    if (myRbProtected.isSelected()) {
      return PsiModifier.PROTECTED;
    }
    if (myRbPrivate.isSelected()) {
      return PsiModifier.PRIVATE;
    }
    if (myRbEscalate != null && myRbEscalate.isSelected()) {
      return VisibilityUtil.ESCALATE_VISIBILITY;
    }

    return null;
  }

  public void setVisibility(String visibility) {
    if (PsiModifier.PUBLIC.equals(visibility)) {
      myRbPublic.setSelected(true);
    }
    else if (PsiModifier.PROTECTED.equals(visibility)) {
      myRbProtected.setSelected(true);
    }
    else if (PsiModifier.PACKAGE_LOCAL.equals(visibility)) {
      myRbPackageLocal.setSelected(true);
    }
    else if (PsiModifier.PRIVATE.equals(visibility)) {
      myRbPrivate.setSelected(true);
    }
    else if (myRbEscalate != null) {
      myRbEscalate.setSelected(true);
    }
    else if (myRbAsIs != null) {
      myRbAsIs.setSelected(true);
    }
  }

  public void disableAllButPublic() {
    myRbPrivate.setEnabled(false);
    myRbProtected.setEnabled(false);
    myRbPackageLocal.setEnabled(false);
    if (myRbEscalate != null) {
      myRbEscalate.setEnabled(false);
    }
    if (myRbAsIs != null) {
      myRbAsIs.setEnabled(false);
    }
    myRbPublic.setEnabled(true);
    myRbPublic.setSelected(true);
  }

}
