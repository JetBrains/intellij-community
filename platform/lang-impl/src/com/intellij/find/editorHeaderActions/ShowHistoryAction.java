package com.intellij.find.editorHeaderActions;

import com.intellij.find.EditorSearchComponent;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.util.IconLoader;
import com.intellij.util.containers.ContainerUtil;

import javax.swing.*;
import java.awt.event.*;
import java.util.ArrayList;

/**
* Created by IntelliJ IDEA.
* User: zajac
* Date: 05.03.11
* Time: 10:44
* To change this template use File | Settings | File Templates.
*/
public class ShowHistoryAction extends EditorHeaderAction implements DumbAware {
  private JComponent myTextField;

  public ShowHistoryAction(final JComponent textField, EditorSearchComponent editorSearchComponent) {
    super(editorSearchComponent);
    myTextField = textField;
    getTemplatePresentation().setIcon(IconLoader.getIcon("/actions/search.png"));
    getTemplatePresentation().setDescription("Search history");
    getTemplatePresentation().setText("Search History");

    ArrayList<Shortcut> shortcuts = new ArrayList<Shortcut>();
    if (myTextField == getEditorSearchComponent().getSearchField()) {
      //ContainerUtil.addAll(shortcuts, ActionManager.getInstance().getAction(IdeActions.ACTION_FIND).getShortcutSet().getShortcuts());
      ContainerUtil.addAll(shortcuts, ActionManager.getInstance().getAction("IncrementalSearch").getShortcutSet().getShortcuts());
    }
    shortcuts.add(new KeyboardShortcut(KeyStroke.getKeyStroke(KeyEvent.VK_H, InputEvent.CTRL_DOWN_MASK), null));

    registerCustomShortcutSet(new CustomShortcutSet(shortcuts.toArray(new Shortcut[shortcuts.size()])), myTextField);
    textField.registerKeyboardAction(new ActionListener() {
                                       @Override
                                       public void actionPerformed(ActionEvent actionEvent) {
                                         if (getEditorSearchComponent().getTextInField().isEmpty()) {
                                           getEditorSearchComponent().showHistory(false, myTextField);
                                         }
                                       }
                                     }, KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, 0), JComponent.WHEN_FOCUSED);
  }

  public void actionPerformed(final AnActionEvent e) {
    getEditorSearchComponent().showHistory(e.getInputEvent() instanceof MouseEvent, myTextField);
  }
}
