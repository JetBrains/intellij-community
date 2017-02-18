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

package com.intellij.ide.macro;

import com.intellij.ide.DataManager;
import com.intellij.ide.IdeBundle;
import com.intellij.openapi.actionSystem.impl.SimpleDataContext;
import com.intellij.openapi.help.HelpManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.DoubleClickListener;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.SeparatorFactory;
import com.intellij.ui.components.JBList;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

public final class MacrosDialog extends DialogWrapper {
  private final DefaultListModel myMacrosModel;
  private final JList myMacrosList;
  private final JTextArea myPreviewTextarea;

  public MacrosDialog(Project project) {
    super(project, true);
    MacroManager.getInstance().cacheMacrosPreview(SimpleDataContext.getProjectContext(project));
    setTitle(IdeBundle.message("title.macros"));
    setOKButtonText(IdeBundle.message("button.insert"));

    myMacrosModel = new DefaultListModel();
    myMacrosList = new JBList(myMacrosModel);
    myPreviewTextarea = new JTextArea();

    init();
  }

  public MacrosDialog(Component parent) {
    super(parent, true);
    MacroManager.getInstance().cacheMacrosPreview(DataManager.getInstance().getDataContext(parent));
    setTitle(IdeBundle.message("title.macros"));
    setOKButtonText(IdeBundle.message("button.insert"));

    myMacrosModel = new DefaultListModel();
    myMacrosList = new JBList(myMacrosModel);
    myPreviewTextarea = new JTextArea();

    init();
  }

  @Override
  protected void init() {
    super.init();

    java.util.List<Macro> macros = new ArrayList<>(MacroManager.getInstance().getMacros());
    Collections.sort(macros, new Comparator<Macro>() {
      @Override
      public int compare(Macro macro1, Macro macro2) {
        String name1 = macro1.getName();
        String name2 = macro2.getName();
        if (!StringUtil.startsWithChar(name1, '/')) {
          name1 = ZERO + name1;
        }
        if (!StringUtil.startsWithChar(name2, '/')) {
          name2 = ZERO + name2;
        }
        return name1.compareToIgnoreCase(name2);
      }
      private final String ZERO = new String(new char[] {0});
    });
    for (Macro macro : macros) {
      myMacrosModel.addElement(new MacroWrapper(macro));
    }

    addListeners();
    if (myMacrosModel.size() > 0){
      myMacrosList.setSelectedIndex(0);
    }
    else{
      setOKActionEnabled(false);
    }
  }

  @Override
  @NotNull
  protected Action[] createActions() {
    return new Action[]{getOKAction(),getCancelAction(),getHelpAction()};
  }

  @Override
  protected void doHelpAction() {
    HelpManager.getInstance().invokeHelp("reference.settings.ide.settings.external.tools.macros");
  }

  @Override
  protected String getDimensionServiceKey(){
    return "#com.intellij.ide.macro.MacrosDialog";
  }

  @Override
  protected JComponent createCenterPanel() {
    JPanel panel = new JPanel(new GridBagLayout());
    GridBagConstraints constr;

    // list label
    constr = new GridBagConstraints();
    constr.gridy = 0;
    constr.anchor = GridBagConstraints.WEST;
    constr.fill = GridBagConstraints.HORIZONTAL;
    panel.add(SeparatorFactory.createSeparator(IdeBundle.message("label.macros"), null), constr);

    // macros list
    constr = new GridBagConstraints();
    constr.gridy = 1;
    constr.weightx = 1;
    constr.weighty = 1;
    constr.fill = GridBagConstraints.BOTH;
    constr.anchor = GridBagConstraints.WEST;
    panel.add(ScrollPaneFactory.createScrollPane(myMacrosList), constr);
    myMacrosList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    myMacrosList.setPreferredSize(null);

    // preview label
    constr = new GridBagConstraints();
    constr.gridx = 0;
    constr.gridy = 2;
    constr.anchor = GridBagConstraints.WEST;
    constr.fill = GridBagConstraints.HORIZONTAL;
    panel.add(SeparatorFactory.createSeparator(IdeBundle.message("label.macro.preview"), null), constr);

    // preview
    constr = new GridBagConstraints();
    constr.gridx = 0;
    constr.gridy = 3;
    constr.weightx = 1;
    constr.weighty = 1;
    constr.fill = GridBagConstraints.BOTH;
    constr.anchor = GridBagConstraints.WEST;
    panel.add(ScrollPaneFactory.createScrollPane(myPreviewTextarea), constr);
    myPreviewTextarea.setEditable(false);
    myPreviewTextarea.setLineWrap(true);
    myPreviewTextarea.setPreferredSize(null);

    panel.setPreferredSize(JBUI.size(400, 500));

    return panel;
  }

  /**
   * Macro info shown in list
   */
  private static final class MacroWrapper {
    private final Macro myMacro;

    public MacroWrapper(Macro macro) {
      myMacro = macro;
    }

    public String toString() {
      return myMacro.getName() + " - " + myMacro.getDescription();
    }
  }

  private void addListeners() {
    myMacrosList.getSelectionModel().addListSelectionListener(
      new ListSelectionListener() {
        @Override
        public void valueChanged(ListSelectionEvent e) {
          Macro macro = getSelectedMacro();
          if (macro == null){
            myPreviewTextarea.setText("");
            setOKActionEnabled(false);
          }
          else{
            myPreviewTextarea.setText(macro.preview());
            setOKActionEnabled(true);
          }
        }
      }
    );

    new DoubleClickListener() {
      @Override
      protected boolean onDoubleClick(MouseEvent e) {
        if (getSelectedMacro() != null){
          close(OK_EXIT_CODE);
          return true;
        }
        return false;
      }
    }.installOn(myMacrosList);
  }

  public Macro getSelectedMacro() {
    MacroWrapper macroWrapper = (MacroWrapper)myMacrosList.getSelectedValue();
    if (macroWrapper != null){
      return macroWrapper.myMacro;
    }
    return null;
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myMacrosList;
  }
}
