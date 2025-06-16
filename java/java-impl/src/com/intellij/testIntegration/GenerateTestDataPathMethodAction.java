// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testIntegration;

import com.intellij.java.JavaBundle;
import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.ui.components.JBList;
import com.intellij.ui.components.JBScrollPane;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

final class GenerateTestDataPathMethodAction extends GenerateTestDataPathCommon {
  GenerateTestDataPathMethodAction() {
    super(((project, editor, file) -> {
            var currentClass = PsiTreeUtil.findChildOfType(file, PsiClass.class);
            if (currentClass == null) return;

            var existingMethodNames = Arrays
              .stream(currentClass.getAllMethods())
              .map(PsiMethod::getName)
              .filter(name -> name.startsWith("test"))
              .collect(Collectors.toCollection(HashSet<String>::new));

            var annotationValue = annotationValue(currentClass, ANNOTATION_FQN);
            if (annotationValue == null) {
              return;
            }

            var directoryPath = LocalFileSystem.getInstance().findFileByPath(annotationValue);
            if (directoryPath == null || !directoryPath.isDirectory() || directoryPath.getChildren() == null) {
              NotificationGroupManager.getInstance().getNotificationGroup("Test integration")
                .createNotification(JavaBundle.message("generate.method.nosuites.warn", annotationValue), NotificationType.WARNING)
                .notify(project);
              return;
            }

            var preparedMethodNames = Arrays.stream(directoryPath.getChildren())
              .filter(vFile -> vFile.getExtension() != null && (!vFile.getExtension().equals("java") || !vFile.getExtension().equals("kt")))
              .map(vFile -> "test" + capitalizeFirstChar(normalizeMethodName(vFile.getName())))
              .filter(methodName -> !existingMethodNames.contains(methodName))
              .collect(Collectors.toSet());

            var dialog = new TestDataPathDialog(project, JavaBundle.message("dialog.title.testdatapath.method.generate"), false) {

              private JBList<String> suggestedFileList;
              private ComboBox<String> selectModifierBox;

              @Override
              protected List<Component> getPanelContent() {
                suggestedFileList = new JBList<>(preparedMethodNames);
                suggestedFileList.setSelectionInterval(0, suggestedFileList.getItemsCount());
                var scrollPane = new JBScrollPane(suggestedFileList);
                scrollPane.setAlignmentX(Component.LEFT_ALIGNMENT);

                final var choices = new String[]{
                  JavaBundle.message("generate.select.default.modifier.text"),
                  PsiModifier.PUBLIC,
                  PsiModifier.PROTECTED,
                  PsiModifier.PRIVATE
                };
                selectModifierBox = new ComboBox<>(choices);
                selectModifierBox.setAlignmentX(Component.LEFT_ALIGNMENT);

                return List.of(scrollPane, selectModifierBox);
              }

              @Override
              protected void doOKAction() {
                suggestedFileList.getSelectedValuesList().forEach(methodName -> {
                  WriteCommandAction.runWriteCommandAction(project, (Runnable)() -> currentClass.add(
                    PsiElementFactory.getInstance(project).createMethodFromText(getSelectedModifier() + "void " + methodName + "(){ }", currentClass))
                  );
                });
                super.doOKAction();
              }

              String getSelectedModifier() {
                return selectModifierBox.getSelectedIndex() == 0 ? "" : selectModifierBox.getSelectedItem().toString() + " ";
              }
            };

            ApplicationManager.getApplication().invokeLater(() -> dialog.show());
          })
    );
  }

  private static String capitalizeFirstChar(@NotNull String inString) {
    if (Character.isUpperCase(inString.charAt(0))) return inString;
    return inString.substring(0, 1).toUpperCase(Locale.ROOT) + inString.substring(1);
  }

  private static String normalizeMethodName(@NotNull String inString) {
    return inString.substring(0, inString.indexOf("."));
  }
}
