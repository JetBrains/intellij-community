package com.intellij.openapi.fileTypes.impl;

import com.intellij.ide.FileIconProvider;
import com.intellij.openapi.fileTypes.NativeFileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.DeferredIconImpl;
import com.intellij.util.Function;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.File;
import java.util.HashMap;
import java.util.Map;

/**
 * @author yole
 */
public class NativeFileIconProvider implements FileIconProvider {
  private JFileChooser myFileChooser = new JFileChooser();
  private final Map<String, Icon> myIconCache = new HashMap<String, Icon>();

  public Icon getIcon(VirtualFile file, int flags, @Nullable Project project) {
    if (!(file.getFileType() instanceof NativeFileType)) {
      return null;
    }
    final String ext = file.getExtension();
    Icon icon;
    synchronized (myIconCache) {
      icon = ext != null ? myIconCache.get(ext) : null;
    }
    if (icon != null) {
      return icon;
    }
    return new DeferredIconImpl<VirtualFile>(file.getFileType().getIcon(), file, false, new Function<VirtualFile, Icon>() {
      public Icon fun(VirtualFile virtualFile) {
        Icon icon = myFileChooser.getIcon(new File(virtualFile.getPath()));
        if (ext != null) {
          synchronized (myIconCache) {
            myIconCache.put(ext, icon);
          }
        }
        return icon;
      }
    });
  }
}
