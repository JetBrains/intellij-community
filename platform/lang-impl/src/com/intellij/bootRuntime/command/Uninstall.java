// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.bootRuntime.command;

import com.intellij.bootRuntime.BinTrayUtil;
import com.intellij.bootRuntime.Controller;
import com.intellij.bootRuntime.bundles.Runtime;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;

import java.awt.event.ActionEvent;
import java.io.IOException;

public class Uninstall extends Command {
  public Uninstall(Project project, Controller controller, Runtime runtime) {
    super(project, controller, "Uninstall", runtime);
  }

  @Override
  public void actionPerformed(ActionEvent e) {
    runWithProgress("Installing...", indicator -> {
      BinTrayUtil.getJdkConfigFilePath().delete();
    });
  }
}
