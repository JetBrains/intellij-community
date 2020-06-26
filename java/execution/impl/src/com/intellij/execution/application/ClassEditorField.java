// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.application;

import com.intellij.execution.ui.ClassBrowser;
import com.intellij.icons.AllIcons;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CustomShortcutSet;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComponentWithBrowseButton;
import com.intellij.openapi.util.Computable;
import com.intellij.psi.*;
import com.intellij.ui.EditorTextField;
import com.intellij.ui.ExtendableEditorSupport;
import com.intellij.ui.components.fields.ExtendableTextComponent;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;

public final class ClassEditorField extends EditorTextField {

  public static ClassEditorField createClassField(Project project, Computable<? extends Module> moduleSelector) {
    PsiElement defaultPackage = JavaPsiFacade.getInstance(project).findPackage("");
    JavaCodeFragmentFactory factory = JavaCodeFragmentFactory.getInstance(project);
    JavaCodeFragment fragment = factory.createReferenceCodeFragment("", defaultPackage, true, true);
    fragment.setVisibilityChecker(JavaCodeFragment.VisibilityChecker.PROJECT_SCOPE_VISIBLE);
    Document document = PsiDocumentManager.getInstance(project).getDocument(fragment);

    ClassEditorField field = new ClassEditorField(document, project, JavaFileType.INSTANCE);
    ClassBrowser.AppClassBrowser<EditorTextField> browser = new ClassBrowser.AppClassBrowser<EditorTextField>(project, moduleSelector) {
      @Override
      public String getText() {
        return field.getText();
      }

      @Override
      public void actionPerformed(ActionEvent e) {
        String text = showDialog();
        if (text != null) {
          field.setText(text);
        }
      }
    };
    field.addSettingsProvider(editor -> {
      ExtendableTextComponent.Extension extension =
        ExtendableTextComponent.Extension.create(AllIcons.General.InlineVariables, AllIcons.General.InlineVariablesHover,
                                                 ComponentWithBrowseButton.getTooltip(), () -> browser.actionPerformed(null));
      ExtendableEditorSupport.setupExtension(editor, field.getBackground(), extension);
    });
    new DumbAwareAction() {
      @Override
      public void actionPerformed(@NotNull AnActionEvent e) {
        browser.actionPerformed(null);
      }
    }.registerCustomShortcutSet(new CustomShortcutSet(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, InputEvent.SHIFT_DOWN_MASK)), field);

    return field;
  }

  private ClassEditorField(Document document, Project project, FileType fileType) {
    super(document, project, fileType);
  }
}
