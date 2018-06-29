/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

import javax.swing.*;
import javax.swing.text.JTextComponent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseListener;
import java.io.File;

public class InsertPathAction extends AnAction {
  private static final Logger LOG = Logger.getInstance("#com.intellij.ui.InsertPathAction");
  protected final JTextComponent myTextField;
  protected static final CustomShortcutSet CTRL_F = new CustomShortcutSet(KeyStroke.getKeyStroke(KeyEvent.VK_F, InputEvent.CTRL_DOWN_MASK));
  protected final FileChooserDescriptor myDescriptor;
  protected final boolean myInsertSystemDependentPaths;
  private MouseListener myPopupHandler;
  protected static final Key INSERT_PATH_ACTION= Key.create("insertPathAction");

  private InsertPathAction(JTextComponent textField, FileChooserDescriptor descriptor, boolean insertSystemDependentPaths) {
    super(UIBundle.message("insert.file.path.to.text.action.name"));
    myTextField = textField;
    myInsertSystemDependentPaths = insertSystemDependentPaths;
    registerCustomShortcutSet(CTRL_F, myTextField);
    myDescriptor = descriptor;
  }

  public void actionPerformed(AnActionEvent e) {
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
      MouseListener popupHandler = PopupHandler.installUnknownPopupHandler(textField, actionGroup, ActionManager.getInstance());
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