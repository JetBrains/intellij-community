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
package com.intellij.execution.testDiscovery;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vfs.VirtualFile;

public class ConfigureTestDiscoveryAction extends AnAction {
  @Override
  public void update(AnActionEvent e) {
    e.getPresentation().setEnabledAndVisible(Registry.is("testDiscovery.enabled") && e.getProject() != null);
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    final FileChooserDescriptor folderDescriptor = FileChooserDescriptorFactory.createSingleFolderDescriptor();
    folderDescriptor.setTitle("Choose External Discovery Index Directory");
    folderDescriptor.setDescription("Local directory with indices retrieved from CI \n" +
                                    "to be replaced within TeamCity IDEA plugin");
    final VirtualFile virtualFile = FileChooser.chooseFile(folderDescriptor, e.getProject(), null);
    if (virtualFile != null) {
      TestDiscoveryIndex.getInstance(e.getProject()).setRemoteTestRunDataPath(virtualFile.getPath());
    }
  }
}
