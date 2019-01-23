// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.bootRuntime.command;

import com.intellij.bootRuntime.BinTrayUtil;
import com.intellij.bootRuntime.Controller;
import com.intellij.bootRuntime.bundles.Runtime;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.project.Project;

import java.awt.event.ActionEvent;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

import static org.jetbrains.io.TarKt.unpackTarGz;

public class Extract extends Command {
  public Extract(Project project, Controller controller, Runtime runtime) {
    super(project, controller, "Extract", runtime);
  }

  @Override
  public void actionPerformed(ActionEvent e) {
    runWithProgress("Extracting...", indicator -> {
      String archiveFileName = getRuntime().getFileName();
      String directoryToExtractName = BinTrayUtil.archveToDirectoryName(archiveFileName);
      String archiveFilePath = PathManager.getPluginTempPath() + File.separator + archiveFileName;
      File jdkStoragePathFile = BinTrayUtil.getJdkStoragePathFile();
      if (!jdkStoragePathFile.exists()) {
        jdkStoragePathFile.mkdir();
      }

      File archiveFile = new File(archiveFilePath);
      File directoryToExtractFile = new File(jdkStoragePathFile, directoryToExtractName);
      if (directoryToExtractFile.exists()) {
        BinTrayUtil.updateJdkConfigFileAndRestart(directoryToExtractFile);
      } else {
          if (!archiveFile.exists()) {
            // this.updateCallback.run();
          } else {

            try (FileInputStream inputStream = new FileInputStream(archiveFile)) {
              unpackTarGz(inputStream, directoryToExtractFile.toPath());
            }
            catch (IOException ex) {
              ex.printStackTrace();
            }

            BinTrayUtil.updateJdkConfigFileAndRestart(directoryToExtractFile);
          }
      }
    });
  }
}
