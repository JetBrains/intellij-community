// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.ui;

import com.intellij.ide.util.PackageChooserDialog;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.psi.PsiPackage;
import com.intellij.ui.ReferenceEditorComboWithBrowseButton;
import org.jetbrains.annotations.NotNull;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;


public class PackageNameReferenceEditorCombo extends ReferenceEditorComboWithBrowseButton {
  public PackageNameReferenceEditorCombo(final String text, final @NotNull Project project,
                                         final String recentsKey, final @NlsContexts.DialogTitle String chooserTitle) {
    super(null, text, project, false, recentsKey);
    addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        final PackageChooserDialog chooser = new PackageChooserDialog(chooserTitle, project);
        chooser.selectPackage(getText());
        if (chooser.showAndGet()) {
          final PsiPackage aPackage = chooser.getSelectedPackage();
          if (aPackage != null) {
            setText(aPackage.getQualifiedName());
          }
        }
      }
    });
  }
}