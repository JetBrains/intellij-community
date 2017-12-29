/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.openapi.fileTypes;

import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;

/**
 * @author yole
 */
public class NativeFileType implements INativeFileType {
  public static final NativeFileType INSTANCE = new NativeFileType();

  private NativeFileType() { }

  @NotNull
  public String getName() {
    return "Native";
  }

  @NotNull
  public String getDescription() {
    return "Files opened in associated applications";
  }

  @Override
  @NotNull
  public String getDefaultExtension() {
    return "";
  }

  public Icon getIcon() {
    return AllIcons.FileTypes.Custom;
  }

  @Override
  public boolean isBinary() {
    return true;
  }

  @Override
  public boolean isReadOnly() {
    return false;
  }

  @Override
  public String getCharset(@NotNull VirtualFile file, @NotNull byte[] content) {
    return null;
  }

  @Override
  public boolean openFileInAssociatedApplication(final Project project, @NotNull final VirtualFile file) {
    return openAssociatedApplication(file);
  }

  @Override
  public boolean useNativeIcon() {
    return true;
  }

  public static boolean openAssociatedApplication(@NotNull final VirtualFile file) {
    final List<String> commands = new ArrayList<>();
    if (SystemInfo.isWindows) {
      commands.add("rundll32.exe");
      commands.add("url.dll,FileProtocolHandler");
    }
    else if (SystemInfo.isMac) {
      commands.add("/usr/bin/open");
    }
    else if (SystemInfo.hasXdgOpen()) {
      commands.add("xdg-open");
    }
    else {
      return false;
    }
    commands.add(file.getPath());

    try {
      new GeneralCommandLine(commands).createProcess();
      return true;
    }
    catch (Exception e) {
      return false;
    }
  }
}
