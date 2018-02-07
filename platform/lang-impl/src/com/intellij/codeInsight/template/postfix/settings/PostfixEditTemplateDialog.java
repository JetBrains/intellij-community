// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.template.postfix.settings;

import com.intellij.codeInsight.template.postfix.templates.PostfixTemplate;
import com.intellij.codeInsight.template.postfix.templates.editable.PostfixEditableTemplateProvider;
import com.intellij.codeInsight.template.postfix.templates.editable.PostfixTemplateEditor;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.components.JBTextField;
import com.intellij.util.ui.FormBuilder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

public class PostfixEditTemplateDialog extends DialogWrapper {
  private final JBTextField myKeyTextField;
  private final PostfixTemplateEditor myEditor;

  public PostfixEditTemplateDialog(@NotNull Component parentComponent,
                                   @NotNull PostfixTemplateEditor<PostfixTemplate> editor,
                                   @NotNull PostfixEditableTemplateProvider provider,
                                   @Nullable PostfixTemplate template) {
    super(null, parentComponent, false, IdeModalityType.IDE);
    myEditor = editor;
    Disposer.register(getDisposable(), editor);
    if (template != null) {
      editor.setTemplate(template);
    }
    String initialKey = template != null ? StringUtil.trimStart(template.getKey(), ".") : "";
    myKeyTextField = new JBTextField(initialKey);
    myKeyTextField.setEditable(template == null || !template.isBuiltin());
    setTitle(template != null ? "Edit '" + initialKey + "' template" : "Create new " + provider.getName() + " template");
    // todo: validate key 
    // todo: validate key duplicates 
    init();
  }

  @NotNull
  public String getTemplateKey() {
    return myKeyTextField.getText();
  }

  @Override
  protected JComponent createCenterPanel() {
    return FormBuilder.createFormBuilder()
                      .addLabeledComponent("Key:", myKeyTextField)
                      .addComponentFillVertically(myEditor.getComponent(), 0)
                      .getPanel();
  }
}
