/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.execution.ui;

import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.TextComponentAccessor;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.ui.SortedComboBoxModel;
import com.intellij.ui.components.JBTextField;
import com.intellij.util.ui.StatusText;
import org.jetbrains.annotations.NotNull;

import javax.swing.plaf.basic.BasicComboBoxEditor;

class JreComboboxEditor extends BasicComboBoxEditor {
  public static final TextComponentAccessor<ComboBox<JrePathEditor.JreComboBoxItem>> TEXT_COMPONENT_ACCESSOR = new JreComboBoxTextComponentAccessor();
  private final SortedComboBoxModel<JrePathEditor.JreComboBoxItem> myComboBoxModel;

  JreComboboxEditor(SortedComboBoxModel<JrePathEditor.JreComboBoxItem> comboBoxModel) {
    myComboBoxModel = comboBoxModel;
  }

  @Override
  public void setItem(Object anObject) {
    editor.setText(anObject == null ? "" : ((JrePathEditor.JreComboBoxItem)anObject).getPresentableText());
  }

  @Override
  public Object getItem() {
    String text = editor.getText().trim();
    for (JrePathEditor.JreComboBoxItem item : myComboBoxModel.getItems()) {
      if (item.getPresentableText().equals(text)) {
        return item;
      }
    }
    return new JrePathEditor.CustomJreItem(FileUtil.toSystemIndependentName(text));
  }

  public StatusText getEmptyText() {
    return getEditorComponent().getEmptyText();
  }

  @Override
  public JBTextField getEditorComponent() {
    return (JBTextField)super.getEditorComponent();
  }

  private static class JreComboBoxTextComponentAccessor implements TextComponentAccessor<ComboBox<JrePathEditor.JreComboBoxItem>> {
    @Override
    public String getText(ComboBox<JrePathEditor.JreComboBoxItem> component) {
      JrePathEditor.JreComboBoxItem item = component.isEditable() ? component.getItem() : (JrePathEditor.JreComboBoxItem)component.getEditor().getItem();
      return item != null ? item.getPresentableText() : "";
    }

    @Override
    public void setText(ComboBox<JrePathEditor.JreComboBoxItem> component, @NotNull String text) {
      JrePathEditor.CustomJreItem item = new JrePathEditor.CustomJreItem(FileUtil.toSystemIndependentName(text));
      if (component.isEditable()) {
        component.getEditor().setItem(item);
      }
      else {
        ((SortedComboBoxModel<JrePathEditor.JreComboBoxItem>)component.getModel()).add(item);
        component.setItem(item);
      }
    }
  }
}
