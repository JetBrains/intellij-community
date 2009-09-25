package com.intellij.openapi.fileTypes.impl;

import com.intellij.ide.FileIconProvider;
import com.intellij.openapi.fileTypes.NativeFileType;
import com.intellij.openapi.fileTypes.UnknownFileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.DeferredIconImpl;
import com.intellij.util.Function;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.File;
import java.util.*;

/**
 * @author yole
 */
public class NativeFileIconProvider implements FileIconProvider {
  private JFileChooser myFileChooser = new JFileChooser();
  private final Map<String, Icon> myIconCache = new HashMap<String, Icon>();
  // on Windows .exe and .ico files provide their own icons which can differ for each file, cache them by full file path
  private final Set<String> myCustomIconExtensions =
    SystemInfo.isWindows ? new HashSet<String>(Arrays.asList("exe", "ico")) : new HashSet<String>();
  private final Map<String, Icon> myCustomIconCache = new HashMap<String, Icon>();

  public Icon getIcon(VirtualFile file, int flags, @Nullable Project project) {
    if (!(file.getFileType() instanceof NativeFileType) && !(file.getFileType() instanceof UnknownFileType)) {
      return null;
    }
    final String ext = file.getExtension();
    final String filePath = file.getPath();

    Icon icon;
    synchronized (myIconCache) {
      if (!myCustomIconExtensions.contains(ext)) {
        icon = ext != null ? myIconCache.get(ext) : null;
      }
      else {
        icon = filePath != null ? myCustomIconCache.get(filePath) : null;
      }
    }
    if (icon != null) {
      return icon;
    }
    return new DeferredIconImpl<VirtualFile>(file.getFileType().getIcon(), file, false, new Function<VirtualFile, Icon>() {
      public Icon fun(VirtualFile virtualFile) {
        final File f = new File(filePath);
        if (!f.exists()) {
          return null;
        }
        Icon icon;
        try {
          icon = myFileChooser.getIcon(f);
        }
        catch (Exception e) {      // see http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=4854174
          return null;
        }
        if (ext != null) {
          synchronized (myIconCache) {
            if (!myCustomIconExtensions.contains(ext)) {
              myIconCache.put(ext, icon);
            }
            else if (filePath != null) {
              myCustomIconCache.put(filePath, icon);
            }
          }
        }
        return icon;
      }
    });
  }
}
