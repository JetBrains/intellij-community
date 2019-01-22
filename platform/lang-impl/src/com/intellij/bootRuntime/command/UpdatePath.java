// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.bootRuntime.command;

import com.intellij.bootRuntime.actions.BinTrayUtil;
import com.intellij.bootRuntime.bundles.Runtime;
import com.intellij.openapi.util.io.FileUtil;

import java.awt.event.ActionEvent;
import java.io.IOException;

public class UpdatePath extends Command {
  public UpdatePath(Runtime runtime) {
    super("Set as Boot", runtime);
  }

  @Override
  public void actionPerformed(ActionEvent e) {
    try {
      FileUtil.writeToFile(BinTrayUtil.getJdkConfigFilePath(), getRuntime().getInstallationPath().getAbsolutePath());
    }
    catch (IOException ioe) {
      ioe.printStackTrace();
    }
  }
}
