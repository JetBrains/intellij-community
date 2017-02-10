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
package com.intellij.ide.actionMacro;

import com.intellij.ide.IdeBundle;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.Shortcut;
import com.intellij.openapi.keymap.Keymap;
import com.intellij.openapi.keymap.ex.KeymapManagerEx;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.Splitter;
import com.intellij.openapi.util.Couple;
import com.intellij.ui.*;
import com.intellij.ui.components.JBList;
import com.intellij.util.containers.HashSet;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Jul 22, 2003
 * Time: 4:01:10 PM
 * To change this template use Options | File Templates.
 */
public class ActionMacroConfigurationPanel implements Disposable {
  private static final String SPLITTER_PROPORTION = "ActionMacroConfigurationPanel.SPLITTER_PROPORTION";
  private Splitter mySplitter;
  private JList myMacrosList;
  private JList myMacroActionsList;
  final DefaultListModel myMacrosModel = new DefaultListModel();
  private List<Couple<String>> myRenamingList;


  public ActionMacroConfigurationPanel() {
    myMacrosList = new JBList();
    myMacroActionsList = new JBList();
    myMacrosList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    myMacroActionsList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

    myMacrosList.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
      public void valueChanged(ListSelectionEvent e) {
        final int selIndex = myMacrosList.getSelectedIndex();
        if (selIndex == -1) {
          ((DefaultListModel)myMacroActionsList.getModel()).removeAllElements();
        }
        else {
          initActionList((ActionMacro)myMacrosModel.getElementAt(selIndex));
        }
      }
    });
  }

  public void reset() {
    final ActionMacro[] allMacros = ActionMacroManager.getInstance().getAllMacros();
    for (ActionMacro macro : allMacros) {
      myMacrosModel.addElement(macro.clone());
    }
    myMacrosList.setModel(myMacrosModel);
    ScrollingUtil.ensureSelectionExists(myMacrosList);
  }

  public void apply() {
    if (myRenamingList != null) {
      for (Couple<String> pair : myRenamingList) {
        Keymap[] allKeymaps = KeymapManagerEx.getInstanceEx().getAllKeymaps();
        for (Keymap keymap : allKeymaps) {
          keymap.removeAllActionShortcuts(ActionMacro.MACRO_ACTION_PREFIX + pair.getSecond());
          for(Shortcut shortcut : keymap.getShortcuts(ActionMacro.MACRO_ACTION_PREFIX + pair.getFirst())) {
            keymap.addShortcut(ActionMacro.MACRO_ACTION_PREFIX + pair.getSecond(), shortcut);
          }
          keymap.removeAllActionShortcuts(ActionMacro.MACRO_ACTION_PREFIX + pair.getFirst());
        }
      }
    }

    final ActionMacroManager manager = ActionMacroManager.getInstance();
    ActionMacro[] macros = manager.getAllMacros();
    HashSet<String> removedIds = new HashSet<>();
    for (ActionMacro macro1 : macros) {
      removedIds.add(macro1.getActionId());
    }

    manager.removeAllMacros();

    final Enumeration newMacros = myMacrosModel.elements();
    while (newMacros.hasMoreElements()) {
      ActionMacro macro = (ActionMacro)newMacros.nextElement();
      manager.addMacro(macro);
      removedIds.remove(macro.getActionId());
    }
    manager.registerActions();

    for (String id : removedIds) {
      Keymap[] allKeymaps = KeymapManagerEx.getInstanceEx().getAllKeymaps();
      for (Keymap keymap : allKeymaps) {
        keymap.removeAllActionShortcuts(id);
      }
    }
  }

  public boolean isModified() {
    final ActionMacro[] allMacros = ActionMacroManager.getInstance().getAllMacros();
    if (allMacros.length != myMacrosModel.getSize()) return true;
    for (int i = 0; i < allMacros.length; i++) {
      ActionMacro macro = allMacros[i];
      ActionMacro newMacro = (ActionMacro)myMacrosModel.get(i);
      if (!macro.equals(newMacro)) return true;
    }
    return false;
  }

  private void initActionList(ActionMacro macro) {
    DefaultListModel actionModel = new DefaultListModel();
    final ActionMacro.ActionDescriptor[] actions = macro.getActions();
    for (ActionMacro.ActionDescriptor action : actions) {
      actionModel.addElement(action);
    }
    myMacroActionsList.setModel(actionModel);
    ScrollingUtil.ensureSelectionExists(myMacroActionsList);
  }

  public JPanel getPanel() {
    if (mySplitter == null) {
      mySplitter = new Splitter(false, 0.5f);
      final String value = PropertiesComponent.getInstance().getValue(SPLITTER_PROPORTION);
      if (value != null) {
        mySplitter.setProportion(Float.parseFloat(value));
      }

      mySplitter.setFirstComponent(
        ToolbarDecorator.createDecorator(myMacrosList)
          .setEditAction(new AnActionButtonRunnable() {
            @Override
            public void run(AnActionButton button) {
              final int selIndex = myMacrosList.getSelectedIndex();
              if (selIndex == -1) return;
              final ActionMacro macro = (ActionMacro)myMacrosModel.getElementAt(selIndex);
              String newName;
              do {
                newName = Messages.showInputDialog(mySplitter, IdeBundle.message("prompt.enter.new.name"),
                                                   IdeBundle.message("title.rename.macro"),
                                                   Messages.getQuestionIcon(), macro.getName(), null);
                if (newName == null || macro.getName().equals(newName)) return;
              }
              while (!canRenameMacro(newName));

              if (myRenamingList == null) myRenamingList = new ArrayList<>();
              myRenamingList.add(Couple.of(macro.getName(), newName));
              macro.setName(newName);
              myMacrosList.repaint();
            }

            private boolean canRenameMacro(final String name) {
              final Enumeration elements = myMacrosModel.elements();
              while (elements.hasMoreElements()) {
                final ActionMacro macro = (ActionMacro)elements.nextElement();
                if (macro.getName().equals(name)) {
                  if (Messages.showYesNoDialog(IdeBundle.message("message.macro.exists", name),
                                               IdeBundle.message("title.macro.name.already.used"),
                                               Messages.getWarningIcon()) != Messages.YES) {
                    return false;
                  }
                  myMacrosModel.removeElement(macro);
                  break;
                }
              }
              return true;
            }
          }).disableAddAction().disableUpDownActions().createPanel());

      mySplitter.setSecondComponent(
        ToolbarDecorator.createDecorator(myMacroActionsList)
          .setRemoveAction(new AnActionButtonRunnable() {
            @Override
            public void run(AnActionButton button) {
              final int macrosSelectedIndex = myMacrosList.getSelectedIndex();
              if (macrosSelectedIndex != -1) {
                final ActionMacro macro = (ActionMacro)myMacrosModel.getElementAt(macrosSelectedIndex);
                macro.deleteAction(myMacroActionsList.getSelectedIndex());
              }
              ListUtil.removeSelectedItems(myMacroActionsList);
            }
          }).disableAddAction().disableUpDownActions().createPanel());
    }
    return mySplitter;
  }

  @Override
  public void dispose() {
    PropertiesComponent.getInstance().setValue(SPLITTER_PROPORTION, Float.toString(mySplitter.getProportion()));
  }
}
