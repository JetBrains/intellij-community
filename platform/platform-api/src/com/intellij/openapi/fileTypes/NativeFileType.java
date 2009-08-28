package com.intellij.openapi.fileTypes;

import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * @author yole
 */
public class NativeFileType implements FileType {
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
      Runtime.getRuntime().exec(commands.toArray(new String[commands.size()]));
    }
    catch (IOException e) {
      return false;
    }
    return true;
  }
}
