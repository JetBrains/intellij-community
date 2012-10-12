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
package com.intellij.platform.templates;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.EditorTextField;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.File;
import java.io.IOException;

/**
 * @author Dmitry Avdeev
 *         Date: 10/8/12
 */
public class SaveProjectAsTemplateDialog extends DialogWrapper {

  private JPanel myPanel;
  private JTextField myName;
  private EditorTextField myDescription;

  protected SaveProjectAsTemplateDialog(@NotNull Project project, @Nullable VirtualFile descriptionFile) {
    super(project);
    setTitle("Save Project As Template");
    myDescription.setFileType(FileTypeManager.getInstance().getFileTypeByExtension(".html"));
    if (descriptionFile != null) {
      try {
        String s = VfsUtilCore.loadText(descriptionFile);
        myDescription.setText(s);
      }
      catch (IOException e) {
        LOG.error(e);
      }
    }
    init();
  }

  @Nullable
  @Override
  protected JComponent createCenterPanel() {
    return myPanel;
  }

  @Nullable
  @Override
  public JComponent getPreferredFocusedComponent() {
    return myName;
  }

  @Nullable
  @Override
  protected String getDimensionServiceKey() {
    return "save.project.as.template.dialog";
  }

  @Nullable
  @Override
  protected ValidationInfo doValidate() {
    if (StringUtil.isEmpty(myName.getText())) {
      return new ValidationInfo("Template name should not be empty");
    }
    File file = getTemplateFile();
    return file.exists() ? new ValidationInfo("Template already exists") : null;
  }

  File getTemplateFile() {
    String name = myName.getText();
    String configURL = ArchivedTemplatesFactory.getCustomTemplatesPath();
    return new File(configURL + "/" + name + ".zip");
  }

  String getDescription() {
    return myDescription.getText();
  }

  private final static Logger LOG = Logger.getInstance(SaveProjectAsTemplateDialog.class);
}
