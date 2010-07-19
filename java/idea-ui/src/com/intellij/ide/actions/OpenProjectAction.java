/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.ide.actions;

import com.intellij.ide.IdeBundle;
import com.intellij.ide.highlighter.ProjectFileType;
import com.intellij.ide.impl.ProjectUtil;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.ex.FileChooserDialogImpl;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.projectImport.ProjectOpenProcessor;
import com.intellij.projectImport.ProjectOpenProcessorBase;
import com.intellij.util.Consumer;

import java.io.File;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class OpenProjectAction extends AnAction implements DumbAware {
  public void actionPerformed(AnActionEvent e) {
    final Project project = PlatformDataKeys.PROJECT.getData(e.getDataContext());

    final FileChooserDescriptor descriptor = new OpenProjectFileChooserDescriptor(true);
    descriptor.setTitle(IdeBundle.message("title.open.project"));
    final Set<String> extensions = new HashSet<String>();
    extensions.add(ProjectFileType.DOT_DEFAULT_EXTENSION);
    final ProjectOpenProcessor[] openProcessors = Extensions.getExtensions(ProjectOpenProcessorBase.EXTENSION_POINT_NAME);
    for (ProjectOpenProcessor openProcessor : openProcessors) {
      final String[] supportedExtensions = ((ProjectOpenProcessorBase)openProcessor).getSupportedExtensions();
      if (supportedExtensions != null) {
        Collections.addAll(extensions, supportedExtensions);
      }
    }
    descriptor.setDescription(IdeBundle.message("filter.project.files", StringUtil.join(extensions, ", ")));

    VirtualFile userHomeDir = null;
    if (SystemInfo.isMac || SystemInfo.isLinux) {
      final String home = System.getProperty("user.home");
      if (home != null) {
        userHomeDir = LocalFileSystem.getInstance().findFileByIoFile(new File(home));
      }
    }

    descriptor.putUserData(FileChooserDialogImpl.PREFER_LAST_OVER_TO_SELECT, Boolean.TRUE);

    FileChooser.chooseFilesWithSlideEffect(descriptor, project, userHomeDir,new Consumer<VirtualFile[]>() {
      @Override
      public void consume(final VirtualFile[] files) {
        if (files.length == 0 || files[0] == null) return;

        ProjectUtil.openOrImport(files[0].getPath(), project, false);
      }
    });
  }
}