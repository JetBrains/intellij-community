// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.ui;

import com.intellij.execution.ExecutionBundle;
import com.intellij.execution.JavaRunConfigurationBase;
import com.intellij.openapi.editor.SpellCheckingEditorCustomizationProvider;
import com.intellij.openapi.fileTypes.FileTypes;
import com.intellij.openapi.util.Key;
import com.intellij.ui.*;
import com.intellij.util.execution.ParametersListUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.Objects;

import static com.intellij.execution.ui.CommandLinePanel.setMinimumWidth;

public class VmOptionsEditor extends JPanel implements FragmentWrapper, Expandable {
  public static final Key<JavaRunConfigurationBase> SETTINGS_KEY = Key.create("JavaRunConfigurationSettings");
  private final LanguageTextField myEditor;
  private LanguageTextField myPopupEditor;
  private final ExpandableEditorSupport mySupport;

  VmOptionsEditor(JavaRunConfigurationBase settings) {
    super(new BorderLayout());
    myEditor = new LanguageTextField(FileTypes.PLAIN_TEXT.getLanguage(), settings.getProject(), "", true);
    String message = ExecutionBundle.message("run.configuration.java.vm.parameters.empty.text");
    myEditor.getAccessibleContext().setAccessibleName(message);
    myEditor.setPlaceholder(message);
    setupEditor(myEditor, settings);
    myEditor.setCaretPosition(0);
    add(myEditor, BorderLayout.CENTER);
    setMinimumWidth(this, 400);
    mySupport = new ExpandableEditorSupport(myEditor, ParametersListUtil.DEFAULT_LINE_PARSER, ParametersListUtil.DEFAULT_LINE_JOINER) {
      @Override
      protected @NotNull EditorTextField createPopupEditor(@NotNull EditorTextField field, @NotNull String text) {
        LanguageTextField popupEditor = new LanguageTextField(FileTypes.PLAIN_TEXT.getLanguage(), settings.getProject(), text);
        setupEditor(popupEditor, settings);
        myPopupEditor = popupEditor;
        myPopupEditor.setCaretPosition(Math.min(text.length(), myEditor.getCaretModel().getOffset()));
        return popupEditor;
      }
    };
  }

  void setupEditor(LanguageTextField popupEditor, JavaRunConfigurationBase settings) {
    CommonParameterFragments.setMonospaced(popupEditor);
    EditorCustomization disableSpellChecking = SpellCheckingEditorCustomizationProvider.getInstance().getDisabledCustomization();
    if (disableSpellChecking != null) {
      popupEditor.addSettingsProvider(editor -> {
        disableSpellChecking.customize(editor);
      });
    }
    popupEditor.getDocument().putUserData(SETTINGS_KEY, settings);
  }

  public EditorTextField getTextField() {
    return mySupport.isExpanded() ? Objects.requireNonNull(myPopupEditor) : myEditor;
  }

  @Override
  public JComponent getComponentToRegister() {
    return myEditor;
  }

  @Override
  public void expand() {
    mySupport.expand();
  }

  @Override
  public void collapse() {
    mySupport.collapse();
  }

  @Override
  public boolean isExpanded() {
    return mySupport.isExpanded();
  }
}
