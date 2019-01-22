// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.bootRuntime.command;

import com.intellij.bootRuntime.bundles.Runtime;
import com.intellij.util.io.HttpRequests;

import java.awt.event.ActionEvent;
import java.io.File;
import java.io.IOException;

public class Download extends Command {

  public Download(Runtime runtime) {
    super("Download", runtime);
  }

  @Override
  public void actionPerformed(ActionEvent e) {
    File downloadDirectoryFile = runtime.getDownloadPath();
    if (!downloadDirectoryFile.exists()) {
      String link = "https://bintray.com/jetbrains/intellij-jdk/download_file?file_path=" + getRuntime().getFileName();

    runWithProgress("Downloading...", (progressIndicator) -> {
      progressIndicator.setIndeterminate(true);

      try {
        HttpRequests.request(link).saveToFile(new File(downloadDirectoryFile, getRuntime().getFileName()), progressIndicator);
      }
      catch (IOException ioe) {
        ioe.printStackTrace();
      }
    });
  }
}
}
