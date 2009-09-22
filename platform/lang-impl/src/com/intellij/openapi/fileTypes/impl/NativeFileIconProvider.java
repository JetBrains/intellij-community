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
  // on Windows .exe and .ico files provide their own icons which can differ for each file, don't cache them
  private final Set<String> myNoCacheExtensions = SystemInfo.isWindows ? new HashSet<String>(Arrays.asList("exe", "ico")) : new HashSet<String>();

  public Icon getIcon(VirtualFile file, int flags, @Nullable Project project) {
    if (!(file.getFileType() instanceof NativeFileType) && !(file.getFileType() instanceof UnknownFileType)) {
      return null;
    }
    final String ext = file.getExtension();
    Icon icon;
    synchronized (myIconCache) {
      icon = ext != null && !myNoCacheExtensions.contains(ext) ? myIconCache.get(ext) : null;
    }
    if (icon != null) {
      return icon;
    }
    return new DeferredIconImpl<VirtualFile>(file.getFileType().getIcon(), file, false, new Function<VirtualFile, Icon>() {
      public Icon fun(VirtualFile virtualFile) {
        final File f = new File(virtualFile.getPath());
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
            myIconCache.put(ext, icon);
          }
        }
        return icon;
      }
    });
  }
}
