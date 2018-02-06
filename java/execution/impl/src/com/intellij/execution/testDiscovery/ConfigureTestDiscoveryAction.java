// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.testDiscovery;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vfs.VirtualFile;

import java.nio.file.Paths;

public class ConfigureTestDiscoveryAction extends AnAction {
  @Override
  public void update(AnActionEvent e) {
    e.getPresentation().setEnabledAndVisible(Registry.is(TestDiscoveryExtension.TEST_DISCOVERY_REGISTRY_KEY) && e.getProject() != null);
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    final FileChooserDescriptor folderDescriptor = FileChooserDescriptorFactory.createSingleFolderDescriptor();
    folderDescriptor.setTitle("Choose External Discovery Index Directory");
    folderDescriptor.setDescription("Local directory with indices retrieved from CI \n" +
                                    "to be replaced within TeamCity IDEA plugin");
    final VirtualFile virtualFile = FileChooser.chooseFile(folderDescriptor, e.getProject(), null);
    if (virtualFile != null) {
      TestDiscoveryIndex.getInstance(e.getProject()).setRemoteTestRunDataPath(Paths.get(virtualFile.getPath()));
    }
  }
}
