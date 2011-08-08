/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.ide.macro;

import com.intellij.ide.IdeBundle;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.fileChooser.FileChooserDialog;
import com.intellij.openapi.fileChooser.FileChooserFactory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;

/**
 * @author yole
 */
public class FilePromptMacro extends Macro implements SecondQueueExpandMacro {
  @Override
  public String getName() {
    return "FilePrompt";
  }

  @Override
  public String getDescription() {
    return "Shows a file chooser dialog";
  }

  @Override
  public String expand(DataContext dataContext) throws ExecutionCancelledException {
    Project project = PlatformDataKeys.PROJECT.getData(dataContext);
    final FileChooserDescriptor descriptor = FileChooserDescriptorFactory.createSingleLocalFileDescriptor();
    final FileChooserDialog fileChooser = FileChooserFactory.getInstance().createFileChooser(descriptor, project);
    final VirtualFile[] result = fileChooser.choose(null, project);
    if (result.length != 1) {
      throw new ExecutionCancelledException();
    }
    return FileUtil.toSystemDependentName(result [0].getPath());
  }

  @Override
  public void cachePreview(DataContext dataContext) {
    myCachedPreview = IdeBundle.message("macro.fileprompt.preview");
  }
}
