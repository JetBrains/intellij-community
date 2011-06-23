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
package com.intellij.openapi.fileTypes;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * @author yole
 */
public class NativeFileType implements INativeFileType {
  private static final Icon ICON = IconLoader.getIcon("/fileTypes/custom.png");
  private NativeFileType() { }

  public static final NativeFileType INSTANCE = new NativeFileType();

  @NotNull
  public String getName() {
    return "Native";
  }

  @NotNull
  public String getDescription() {
    return "Files opened in associated applications";
  }

  @NotNull
  public String getDefaultExtension() {
    return "";
  }

  public Icon getIcon() {
    return ICON;
  }

  public boolean isBinary() {
    return true;
  }

  public boolean isReadOnly() {
    return false;
  }

  public String getCharset(@NotNull VirtualFile file, byte[] content) {
    return null;
  }

  @Override
  public boolean openFileInAssociatedApplication(Project project, VirtualFile file) {
    return openAssociatedApplication(file);
  }

  @Override
  public boolean useNativeIcon() {
    return true;
  }

  public static boolean openAssociatedApplication(VirtualFile file) {
    List<String> commands = new ArrayList<String>();
    if (SystemInfo.isWindows) {
      commands.add("rundll32.exe");
      commands.add("url.dll,FileProtocolHandler");
    }
    else if (SystemInfo.isMac) {
      commands.add("/usr/bin/open");
    }
    else if (SystemInfo.isKDE) {
      commands.add("kfmclient");
      commands.add("exec");
    }
    else if (SystemInfo.isGnome) {
      commands.add("gnome-open");
    }
    else {
      return false;
    }
    commands.add(file.getPath());
    try {
      Runtime.getRuntime().exec(ArrayUtil.toStringArray(commands));
    }
    catch (IOException e) {
      return false;
    }
    return true;
  }
}
