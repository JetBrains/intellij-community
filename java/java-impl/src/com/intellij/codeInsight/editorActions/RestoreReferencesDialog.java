/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.codeInsight.editorActions;

import com.intellij.CommonBundle;
import com.intellij.ide.util.FQNameCellRenderer;
import com.intellij.java.JavaBundle;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.VerticalFlowLayout;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.psi.PsiClass;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBList;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

import static com.intellij.util.ui.UIUtil.ComponentStyle.SMALL;
import static com.intellij.util.ui.UIUtil.FontColor.BRIGHTER;

class RestoreReferencesDialog extends DialogWrapper {
  private final Object[] myNamedElements;
  private JList<Object> myList;
  private Object[] mySelectedElements = PsiClass.EMPTY_ARRAY;
  private boolean myContainsClassesOnly = true;
  private JBLabel myExplanationLabel;

  RestoreReferencesDialog(final Project project, final Object[] elements) {
    this(project, elements, true);
  }

  RestoreReferencesDialog(final Project project, final Object[] elements, boolean preselect) {
    super(project, true);
    myNamedElements = elements;
    for (Object element : elements) {
      if (!(element instanceof PsiClass)) {
        myContainsClassesOnly = false;
        break;
      }
    }
    if (myContainsClassesOnly) {
      setTitle(JavaBundle.message("dialog.import.on.paste.title"));
    }
    else {
      setTitle(JavaBundle.message("dialog.import.on.paste.title2"));
    }
    init();

    if (preselect) {
      myList.setSelectionInterval(0, myNamedElements.length - 1);
    }
  }

  @Override
  protected void doOKAction() {
    Object[] values = myList.getSelectedValues();
    mySelectedElements = values.clone();
    super.doOKAction();
  }

  @Override
  protected JComponent createCenterPanel() {
    final JPanel panel = new JPanel(new BorderLayout(UIUtil.DEFAULT_HGAP, UIUtil.DEFAULT_VGAP));
    myList = new JBList<>(myNamedElements);
    myList.setCellRenderer(new FQNameCellRenderer());
    panel.add(ScrollPaneFactory.createScrollPane(myList), BorderLayout.CENTER);

    myExplanationLabel = new JBLabel(myContainsClassesOnly ?
                                     JavaBundle.message("dialog.paste.on.import.text") :
                                     JavaBundle.message("dialog.paste.on.import.text2"), SMALL, BRIGHTER);
    panel.add(myExplanationLabel, BorderLayout.NORTH);

    final JPanel buttonPanel = new JPanel(new VerticalFlowLayout());
    final JButton okButton = new JButton(CommonBundle.getOkButtonText());
    getRootPane().setDefaultButton(okButton);
    buttonPanel.add(okButton);
    final JButton cancelButton = new JButton(CommonBundle.getCancelButtonText());
    buttonPanel.add(cancelButton);

    panel.setPreferredSize(JBUI.size(500, 400));

    return panel;
  }

  public void setExplanation(@NlsContexts.Label String explanation) {
    myExplanationLabel.setText(explanation);
  }

  @Override
  protected String getDimensionServiceKey(){
    return "#com.intellij.codeInsight.editorActions.RestoreReferencesDialog";
  }

  public Object @NotNull [] getSelectedElements(){
    return mySelectedElements;
  }
}
