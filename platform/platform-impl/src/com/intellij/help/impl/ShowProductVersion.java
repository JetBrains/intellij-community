// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.help.impl;

import com.intellij.diagnostic.VMOptions;
import com.intellij.openapi.application.ApplicationStarter;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.application.ex.ApplicationInfoEx;
import org.jetbrains.annotations.NotNull;

import java.io.File;

/**
 * @author Konstantin Bulenkov
 */
public class ShowProductVersion implements ApplicationStarter {
  @Override
  public String getCommandName() {
    return "-version";
  }

  @SuppressWarnings("UseOfSystemOutOrSystemErr")
  @Override
  public void main(String @NotNull [] args) {
    System.out.println(ApplicationInfoEx.getInstanceEx().getFullVersion());
    if (args.length > 1 && "verbose".equals(args[1])) {
      String customOptionsPath = PathManager.getCustomOptionsDirectory();
      if (customOptionsPath != null) {
        System.out.println("Custom properties path: " + customOptionsPath + PathManager.PROPERTIES_FILE_NAME);
      }
      File customVmoptions = VMOptions.getWriteFile();
      if (customVmoptions != null) {
        System.out.println("Custom VMOptions path: " + customVmoptions.getPath());
      }
      System.out.println("System path: " + PathManager.getSystemPath());
      System.out.println("Config path: " + PathManager.getConfigPath());
      System.out.println("Log path: " + PathManager.getLogPath());
      System.out.println("Plugins path: " + PathManager.getPluginsPath());
    }
    System.exit(0);
  }
}
