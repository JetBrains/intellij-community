/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.openapi.externalSystem.service.ui;

import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.openapi.externalSystem.ExternalSystemManager;
import com.intellij.openapi.externalSystem.ExternalSystemUiAware;
import com.intellij.openapi.externalSystem.model.ProjectSystemId;
import com.intellij.openapi.externalSystem.model.serialization.ExternalProjectPojo;
import com.intellij.openapi.externalSystem.settings.AbstractExternalSystemLocalSettings;
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComponentWithBrowseButton;
import com.intellij.ui.EditorTextField;
import com.intellij.ui.TextAccessor;
import com.intellij.util.TextFieldCompletionProvider;
import com.intellij.util.TextFieldCompletionProviderDumbAware;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Collection;
import java.util.Map;

/**
 * @author Denis Zhdanov
 * @since 24.05.13 19:13
 */
public class ExternalProjectPathField extends ComponentWithBrowseButton<EditorTextField> implements TextAccessor {

  public ExternalProjectPathField(@NotNull Project project,
                                  @NotNull ProjectSystemId externalSystemId,
                                  @NotNull FileChooserDescriptor descriptor,
                                  @NotNull String fileChooserTitle)
  {
    super(createTextField(project, externalSystemId), createBrowseListener(descriptor, fileChooserTitle));
  }

  @NotNull
  private static EditorTextField createTextField(@NotNull Project project, @NotNull ProjectSystemId externalSystemId) {
    ExternalSystemManager<?,?,?,?,?> manager = ExternalSystemApiUtil.getManager(externalSystemId);
    assert manager != null;
    final AbstractExternalSystemLocalSettings settings = manager.getLocalSettingsProvider().fun(project);
    final ExternalSystemUiAware uiAware;
    if (manager instanceof ExternalSystemUiAware) {
      uiAware = (ExternalSystemUiAware)manager;
    }
    else {
      uiAware = DefaultExternalSystemUiAware.INSTANCE;
    }
    TextFieldCompletionProvider provider = new TextFieldCompletionProviderDumbAware() {
      @Override
      protected void addCompletionVariants(@NotNull String text, int offset, @NotNull String prefix, @NotNull CompletionResultSet result) {
        for (Map.Entry<ExternalProjectPojo, Collection<ExternalProjectPojo>> entry : settings.getAvailableProjects().entrySet()) {
          String rootProjectPath = entry.getKey().getPath();
          String rootProjectName = uiAware.getProjectRepresentationName(rootProjectPath, null);
          ExternalProjectPathLookupElement rootProjectElement = new ExternalProjectPathLookupElement(rootProjectName, rootProjectPath);
          result.addElement(rootProjectElement);
          for (ExternalProjectPojo subProject : entry.getValue()) {
            String p = subProject.getPath();
            if (rootProjectPath.equals(p)) {
              continue;
            }
            String subProjectName = uiAware.getProjectRepresentationName(p, rootProjectPath);
            ExternalProjectPathLookupElement subProjectElement = new ExternalProjectPathLookupElement(subProjectName, p);
            result.addElement(subProjectElement);
          }
        }
        result.stopHere();
      }
    };
    EditorTextField result = provider.createEditor(project, false);
    result.setBorder(UIUtil.getTextFieldBorder());
    result.setOneLineMode(true);
    result.setOpaque(true);
    result.setBackground(UIUtil.getTextFieldBackground());
    return result;
  }

  @NotNull
  private static ActionListener createBrowseListener(@NotNull final FileChooserDescriptor descriptor,
                                                     final @NotNull String fileChooserTitle)
  {
    return new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        descriptor.setTitle(fileChooserTitle);
        // TODO den implement
        //VirtualFile file = FileChooser.chooseFile(descriptor, get, null);
        //if (file != null) {
        //  setWorkingDirectory(file.getPresentableUrl());
        //}
      }
    };
  }

  @Override
  public void setText(String text) {
    getChildComponent().setText(text);
  }

  @Override
  public String getText() {
    return getChildComponent().getText();
  }
}
