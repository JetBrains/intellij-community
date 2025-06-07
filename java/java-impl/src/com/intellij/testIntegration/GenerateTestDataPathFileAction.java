// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testIntegration;

import com.intellij.java.JavaBundle;
import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.psi.PsiClass;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.ui.components.JBList;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBTextField;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

final class GenerateTestDataPathFileAction extends GenerateTestDataPathCommon {
  GenerateTestDataPathFileAction() {
    super(((project, editor, file) -> {
            var currentClass = PsiTreeUtil.findChildOfType(file, PsiClass.class);
            if (currentClass == null) return;

            var annotationValue = annotationValue(currentClass, ANNOTATION_FQN);
            if (annotationValue == null) return;

            var directoryPath = LocalFileSystem.getInstance().findFileByPath(annotationValue);
            if (directoryPath == null || !directoryPath.isDirectory() || directoryPath.getChildren() == null) {
              NotificationGroupManager.getInstance().getNotificationGroup("Test integration")
                .createNotification(JavaBundle.message("generate.method.nosuites.warn", annotationValue), NotificationType.WARNING)
                .notify(project);
              return;
            }

            var suggestedFileNames = Arrays
              .stream(currentClass.getAllMethods())
              .filter(method -> method.getName().startsWith("test"))
              .map(method -> method.getName().substring(4))
              .collect(Collectors.toSet());

            if (suggestedFileNames.isEmpty()) {
              NotificationGroupManager.getInstance().getNotificationGroup("Test integration")
                .createNotification(JavaBundle.message("generate.method.nofiles.warn", currentClass.getName(), annotationValue),
                                    NotificationType.WARNING)
                .notify(project);
              return;
            }

            var dialog = new TestDataPathDialog(project, JavaBundle.message("dialog.title.testdatapath.file.generate"), false) {

              private JBList<String> suggestedFileList;
              private JCheckBox qfCheckbox;
              private JBTextField extensionField;

              @Override
              protected List<Component> getPanelContent() {
                suggestedFileList = new JBList<String>(suggestedFileNames);
                suggestedFileList.setSelectionInterval(0, suggestedFileList.getItemsCount());
                var scrollPane = new JBScrollPane(suggestedFileList);
                scrollPane.setAlignmentX(Component.LEFT_ALIGNMENT);

                qfCheckbox = new JCheckBox();
                qfCheckbox.setText(JavaBundle.message("generate.quickfix.files"));
                qfCheckbox.setAlignmentX(Component.LEFT_ALIGNMENT);
                qfCheckbox.setSelected(false);

                extensionField = new JBTextField();
                extensionField.getEmptyText().setText(JavaBundle.message("generate.file.extension.text"));
                extensionField.setAlignmentX(Component.LEFT_ALIGNMENT);

                return List.of(scrollPane, qfCheckbox, extensionField);
              }

              @Override
              protected void doOKAction() {
                getSelectedMethod().forEach(fileName -> {
                  var requester = new Object();
                  var folder = VirtualFileManager.getInstance().findFileByNioPath(Path.of(annotationValue));
                  WriteCommandAction.runWriteCommandAction(project, () -> {
                    try {
                      folder.createChildData(requester, fileName + extensionField.getText());
                      if (isQFFileNeeded()) folder.createChildData(requester, fileName + ".after" + extensionField.getText());
                    }
                    catch (IOException | NullPointerException e) {
                      LOG.warn(e);
                    }
                  });
                });
                super.doOKAction();
              }

              @Override
              protected @Nullable ValidationInfo doValidate() {
                var in = extensionField.getText().trim();
                if (in.isBlank() || !in.startsWith(".") || in.contains(" ")) {
                  return new ValidationInfo(JavaBundle.message("generate.file.extension.validation.error", in));
                }
                return null;
              }

              List<String> getSelectedMethod() { return suggestedFileList.getSelectedValuesList(); }

              boolean isQFFileNeeded() { return qfCheckbox.isSelected(); }
            };

            ApplicationManager.getApplication().invokeLater(() -> dialog.show());
          })
    );
  }
}

