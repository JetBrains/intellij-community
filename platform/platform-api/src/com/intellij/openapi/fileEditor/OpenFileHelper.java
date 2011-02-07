package com.intellij.openapi.fileEditor;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.vfs.VirtualFile;

public abstract class OpenFileHelper {

  public static boolean openAsNative(VirtualFile virtualFile) {
    boolean openAsNative = false;
    for (OpenFileHelper h : Extensions.getExtensions(EP_NAME)) {
      if (h.canHelp(virtualFile)) {
        openAsNative = h.doOpenAsNative(virtualFile);
        break;
      }
    }
    return openAsNative;
  }

  public static ExtensionPointName<OpenFileHelper> EP_NAME = ExtensionPointName.create("com.intellij.openFileDescriptorHelper");

  public abstract boolean canHelp(VirtualFile file);

  public abstract boolean doOpenAsNative(VirtualFile file);
}
