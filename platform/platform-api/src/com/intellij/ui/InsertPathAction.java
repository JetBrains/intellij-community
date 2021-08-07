// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.text.JTextComponent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseListener;
import java.io.File;

@SuppressWarnings("ComponentNotRegistered")
public final class InsertPathAction extends AnAction {
  private static final Logger LOG = Logger.getInstance(InsertPathAction.class);
  private final JTextComponent myTextField;
  private static final ShortcutSet CTRL_F = new CustomShortcutSet(KeyStroke.getKeyStroke(KeyEvent.VK_F, InputEvent.CTRL_DOWN_MASK));
  private final FileChooserDescriptor myDescriptor;
  private final boolean myInsertSystemDependentPaths;
  private MouseListener myPopupHandler;
  private static final Key INSERT_PATH_ACTION= Key.create("insertPathAction");

  private InsertPathAction(JTextComponent textField, FileChooserDescriptor descriptor, boolean insertSystemDependentPaths) {
    super(UIBundle.messagePointer("insert.file.path.to.text.action.name"));
    myTextField = textField;
    myInsertSystemDependentPaths = insertSystemDependentPaths;
    registerCustomShortcutSet(CTRL_F, myTextField);
    myDescriptor = descriptor;
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    String selectedText = myTextField.getSelectedText();
    VirtualFile virtualFile;
    if (selectedText != null ) {
      virtualFile = LocalFileSystem.getInstance().findFileByPath(selectedText.replace(File.separatorChar, '/'));
    }
    else {
      virtualFile = null;
    }
    //TODO use from openapi
    //FeatureUsageTracker.getInstance().triggerFeatureUsed("ui.commandLine.insertPath");
    VirtualFile[] files = FileChooser.chooseFiles(myDescriptor, myTextField, getEventProject(e), virtualFile);
    if (files.length != 0) {
      final String path = files[0].getPath();
      myTextField.replaceSelection(myInsertSystemDependentPaths? FileUtil.toSystemDependentName(path) : path);
    }
  }

  private void uninstall() {
    uninstallPopupHandler();
    unregisterCustomShortcutSet(myTextField);
    myTextField.putClientProperty(INSERT_PATH_ACTION, null);
  }

  private void savePopupHandler(MouseListener popupHandler) {
    if (myPopupHandler != null) {
      LOG.error("Installed twice");
      uninstallPopupHandler();
    }
    myPopupHandler = popupHandler;
  }

  private void uninstallPopupHandler() {
    if (myPopupHandler == null) return;
    myTextField.removeMouseListener(myPopupHandler);
    myPopupHandler = null;
  }

  public static void addTo(JTextComponent textField) {
    addTo(textField, null);
  }

  public static void addTo(JTextComponent textField, FileChooserDescriptor descriptor) {
    addTo(textField, descriptor, true);
  }

  public static void addTo(JTextComponent textField, FileChooserDescriptor descriptor, boolean insertSystemDependentPaths) {
    if (ApplicationManager.getApplication() != null) { //NPE fixed when another class loader works
      removeFrom(textField);
      if (textField.getClientProperty(INSERT_PATH_ACTION) != null) return;
      DefaultActionGroup actionGroup = new DefaultActionGroup();
      InsertPathAction action = new InsertPathAction(
        textField, descriptor != null? descriptor : FileChooserDescriptorFactory.createSingleLocalFileDescriptor(), insertSystemDependentPaths
      );
      actionGroup.add(action);
      MouseListener popupHandler = PopupHandler.installPopupMenu(textField, actionGroup, "InsertPathActionTextFieldPopup");
      action.savePopupHandler(popupHandler);
      textField.putClientProperty(INSERT_PATH_ACTION, action);
    }
  }

  public static void removeFrom(JTextComponent textComponent) {
    InsertPathAction action = getFrom(textComponent);
    if (action == null) return;
    action.uninstall();
  }

  public static void copyFromTo(JTextComponent original, JTextComponent target) {
    InsertPathAction action = getFrom(original);
    if (action == null) return;
    removeFrom(target);
    addTo(target, action.myDescriptor);
  }

  private static InsertPathAction getFrom(JTextComponent textComponent) {
    Object property = textComponent.getClientProperty(INSERT_PATH_ACTION);
    if (!(property instanceof InsertPathAction)) return null;
    return (InsertPathAction)property;
  }

}