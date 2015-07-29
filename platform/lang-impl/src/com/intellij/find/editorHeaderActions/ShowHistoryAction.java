package com.intellij.find.editorHeaderActions;

import com.intellij.find.EditorSearchComponent;
import com.intellij.ide.ui.laf.darcula.ui.DarculaTextFieldUI;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.util.IconLoader;
import com.intellij.util.containers.ContainerUtil;

import javax.swing.*;
import javax.swing.text.JTextComponent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.ArrayList;

/**
 * User: zajac
 */
public class ShowHistoryAction extends EditorHeaderAction implements DumbAware {
  private final JTextComponent myTextField;

  public ShowHistoryAction(final JTextComponent textField, EditorSearchComponent editorSearchComponent) {
    super(editorSearchComponent);
    myTextField = textField;
    getTemplatePresentation().setIcon(IconLoader.findIcon("/com/intellij/ide/ui/laf/icons/search.png", DarculaTextFieldUI.class, true));
    final String s = myTextField == myEditorSearchComponent.getSearchTextComponent() ? "Search" : "Replace";
    getTemplatePresentation().setDescription(s + " history");
    getTemplatePresentation().setText(s + " History");

    ArrayList<Shortcut> shortcuts = new ArrayList<Shortcut>();
    if (myTextField == myEditorSearchComponent.getSearchTextComponent()) {
      //ContainerUtil.addAll(shortcuts, ActionManager.getInstance().getAction(IdeActions.ACTION_FIND).getShortcutSet().getShortcuts());
      ContainerUtil.addAll(shortcuts, ActionManager.getInstance().getAction("IncrementalSearch").getShortcutSet().getShortcuts());
    }
    shortcuts.add(new KeyboardShortcut(KeyStroke.getKeyStroke(KeyEvent.VK_H, InputEvent.CTRL_DOWN_MASK), null));

    registerCustomShortcutSet(new CustomShortcutSet(shortcuts.toArray(new Shortcut[shortcuts.size()])), myTextField);
  }

  @Override
  public void update(AnActionEvent e) {
    e.getPresentation().setVisible(myEditorSearchComponent.getFindModel().isMultiline());
  }

  @Override
  public void actionPerformed(final AnActionEvent e) {
    myEditorSearchComponent.showHistory(false, myTextField);
  }
}
