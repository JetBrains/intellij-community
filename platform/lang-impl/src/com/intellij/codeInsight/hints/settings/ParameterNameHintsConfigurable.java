/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.codeInsight.hints.settings;

import com.intellij.codeInsight.hints.InlayParameterHintsProvider;
import com.intellij.codeInsight.hints.filtering.MatcherConstructor;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.editor.event.DocumentAdapter;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.fileTypes.PlainTextLanguage;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.EditorTextField;
import com.intellij.ui.EditorTextFieldProvider;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class ParameterNameHintsConfigurable extends DialogWrapper {

  private final Project myProject;
  private final InlayParameterHintsProvider myHintsProvider;
  
  public ParameterNameHintsConfigurable(@NotNull Project project, @NotNull InlayParameterHintsProvider provider) {
    super(project);
    myProject = project;
    myHintsProvider = provider;
    setTitle("Configure Parameter Name Hints Blacklist");
    init();
  }

  private void updateOkEnabled() {
    String text = myEditorTextField.getText();
    List<String> rules = StringUtil.split(text, "\n");
    boolean hasAnyInvalid = rules
      .stream()
      .filter((e) -> !e.trim().isEmpty())
      .map((s) -> MatcherConstructor.INSTANCE.createMatcher(s))
      .anyMatch((e) -> e == null);
    
    getOKAction().setEnabled(!hasAnyInvalid);
  }

  @Override
  protected void doOKAction() {
    super.doOKAction();

    Set<String> updatedBlackList = StringUtil
      .split(myEditorTextField.getText(), "\n")
      .stream()
      .filter((e) -> !e.trim().isEmpty())
      .collect(Collectors.toSet());
    
    ParameterNameHintsSettings.getInstance().setIgnorePatternSet(myHintsProvider, updatedBlackList);
  }

  @Nullable
  @Override
  protected JComponent createCenterPanel() {
    return myConfigurable;
  }

  public JPanel myConfigurable;
  private EditorTextField myEditorTextField;

  private void createUIComponents() {
    EditorTextFieldProvider service = ServiceManager.getService(myProject, EditorTextFieldProvider.class);
    myEditorTextField = service.getEditorField(PlainTextLanguage.INSTANCE, myProject, ContainerUtil.emptyIterable());
    
    Set<String> blacklist = ParameterNameHintsSettings.getInstance().getIgnorePatternSet(myHintsProvider);
    String text = StringUtil.join(blacklist, "\n");
    
    myEditorTextField.setText(text);
    myEditorTextField.addDocumentListener(new DocumentAdapter() {
      @Override
      public void documentChanged(DocumentEvent e) {
        updateOkEnabled();
      }
    });
  }
  
  
  
}
