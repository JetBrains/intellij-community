/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

package com.intellij.codeInspection.uncheckedWarnings;

import com.intellij.codeInsight.daemon.impl.quickfix.GenerifyFileFix;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.openapi.util.Pass;

import javax.swing.*;
import java.awt.*;

/**
 * User: anna
 * Date: 17-Feb-2006
 */
public class UncheckedWarningLocalInspection extends UncheckedWarningLocalInspectionBase {

  @Override
  protected LocalQuickFix[] createFixes() {
    return new LocalQuickFix[]{new GenerifyFileFix()};
  }

  @Override
  public JComponent createOptionsPanel() {
    final JPanel panel = new JPanel(new GridBagLayout());
    final GridBagConstraints gc = new GridBagConstraints(0, GridBagConstraints.RELATIVE, 1, 1, 1, 0, GridBagConstraints.NORTHWEST, GridBagConstraints.HORIZONTAL, new Insets(0,0,0,0), 0,0);

    panel.add(createSetting("Ignore unchecked assignment", IGNORE_UNCHECKED_ASSIGNMENT, new Pass<JCheckBox>() {
      @Override
      public void pass(JCheckBox cb) {
        IGNORE_UNCHECKED_ASSIGNMENT = cb.isSelected();
      }
    }), gc);

    panel.add(createSetting("Ignore unchecked generics array creation for vararg parameter", IGNORE_UNCHECKED_GENERICS_ARRAY_CREATION, new Pass<JCheckBox>() {
      @Override
      public void pass(JCheckBox cb) {
          IGNORE_UNCHECKED_GENERICS_ARRAY_CREATION = cb.isSelected();
      }
    }), gc);

    panel.add(createSetting("Ignore unchecked call as member of raw type", IGNORE_UNCHECKED_CALL, new Pass<JCheckBox>() {
      @Override
      public void pass(JCheckBox cb) {
        IGNORE_UNCHECKED_CALL = cb.isSelected();
      }
    }), gc);

    panel.add(createSetting("Ignore unchecked cast", IGNORE_UNCHECKED_CAST, new Pass<JCheckBox>() {
      @Override
      public void pass(JCheckBox cb) {
        IGNORE_UNCHECKED_CAST = cb.isSelected();
      }
    }), gc);

    panel.add(createSetting("Ignore unchecked overriding", IGNORE_UNCHECKED_OVERRIDING, new Pass<JCheckBox>() {
      @Override
      public void pass(JCheckBox cb) {
        IGNORE_UNCHECKED_OVERRIDING = cb.isSelected();
      }
    }), gc);

    gc.fill = GridBagConstraints.BOTH;
    gc.weighty = 1;
    panel.add(Box.createVerticalBox(), gc);

    return panel;
  }
}
