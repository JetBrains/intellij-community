// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.bootRuntime.command;

import com.intellij.bootRuntime.Controller;
import com.intellij.bootRuntime.bundles.Runtime;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;

import java.awt.event.ActionEvent;

public class Delete extends Command {
  public Delete(Project project, Controller controller, Runtime runtime) {
    super(project, controller,"Delete", runtime);
  }

  @Override
  public void actionPerformed(ActionEvent e) {
    runWithProgress("Deleting..." , indicator -> {
      //FileUtil.delete(new File(BinTrayUtil.getJdkStoragePathFile(), BinTrayUtil.archveToDirectoryName(myRuntime.getFileName())));
      FileUtil.delete(myRuntime.getDownloadPath());
      FileUtil.delete(myRuntime.getInstallationPath());
    });
  }
}
