package com.intellij.ide.actionMacro;

import com.intellij.ide.IdeBundle;
import com.intellij.openapi.keymap.Keymap;
import com.intellij.openapi.keymap.ex.KeymapManagerEx;
import com.intellij.openapi.ui.Messages;
import com.intellij.ui.ListScrollingUtil;
import com.intellij.ui.ListUtil;
import com.intellij.util.containers.HashSet;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Enumeration;

/**
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Jul 22, 2003
 * Time: 4:01:10 PM
 * To change this template use Options | File Templates.
 */
public class ActionMacroConfigurationPanel {
  private JPanel myPanel;
  private JButton myDeleteButton;
  private JButton myRenameButton;
  private JButton myExcludeActionButton;
  private JList myMacrosList;
  private JList myMacroActionsList;

  final DefaultListModel myMacrosModel = new DefaultListModel();
  public ActionMacroConfigurationPanel() {
    ListUtil.addRemoveListener(myDeleteButton, myMacrosList);

    myMacrosList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    myMacroActionsList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

    myRenameButton.setEnabled(false);
    myDeleteButton.setEnabled(false);
    myExcludeActionButton.setEnabled(false);
    
    myMacrosList.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
      public void valueChanged(ListSelectionEvent e) {
        final int selIndex = myMacrosList.getSelectedIndex();
        if (selIndex == -1) {
          ((DefaultListModel) myMacroActionsList.getModel()).removeAllElements();
          myExcludeActionButton.setEnabled(false);
          myRenameButton.setEnabled(false);
        } else {
          myRenameButton.setEnabled(true);
          initActionList((ActionMacro)myMacrosModel.getElementAt(selIndex));
        }
      }
    });

    myMacroActionsList.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
      public void valueChanged(ListSelectionEvent e) {
        final int selIdx = myMacroActionsList.getSelectedIndex();
        myExcludeActionButton.setEnabled(selIdx != -1);
      }
    });

    myExcludeActionButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        final int selIndex = myMacrosList.getSelectedIndex();

        if (selIndex != -1) {
          final ActionMacro macro = (ActionMacro)myMacrosModel.getElementAt(selIndex);
          macro.deleteAction(myMacroActionsList.getSelectedIndex());
        }
        ListUtil.removeSelectedItems(myMacroActionsList);
      }
    });

    myRenameButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        final int selIndex = myMacrosList.getSelectedIndex();
        if (selIndex == -1) return;
        final ActionMacro macro = (ActionMacro) myMacrosModel.getElementAt(selIndex);
        final String newName = Messages.showInputDialog(myPanel, IdeBundle.message("prompt.enter.new.name"),
                                                        IdeBundle.message("title.rename.macro"),
                                                        Messages.getQuestionIcon(), macro.getName(), null);
        if (newName != null) {
          macro.setName(newName);
          myMacrosList.repaint();
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
    ListScrollingUtil.ensureSelectionExists(myMacrosList);
  }

  public void apply() {
    final ActionMacroManager manager = ActionMacroManager.getInstance();
    ActionMacro[] macros = manager.getAllMacros();
    HashSet<String> removedIds = new HashSet<String>();
    for (ActionMacro macro1 : macros) {
      removedIds.add(macro1.getActionId());
    }

    manager.removeAllMacros();

    final Enumeration newMacros = myMacrosModel.elements();
    while (newMacros.hasMoreElements()) {
      ActionMacro macro = (ActionMacro) newMacros.nextElement();
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
      ActionMacro newMacro = (ActionMacro) myMacrosModel.get(i);
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
    ListScrollingUtil.ensureSelectionExists(myMacroActionsList);
  }

  public JPanel getPanel() {
    return myPanel;
  }

}
