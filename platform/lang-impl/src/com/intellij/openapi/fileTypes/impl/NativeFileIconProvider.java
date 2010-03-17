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

package com.intellij.openapi.fileTypes.impl;

import com.intellij.ide.FileIconProvider;
import com.intellij.openapi.fileTypes.NativeFileType;
import com.intellij.openapi.fileTypes.UnknownFileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.DeferredIconImpl;
import com.intellij.util.Function;
import com.intellij.util.ui.update.ComparableObject;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.File;
import java.util.*;

/**
 * @author yole
 */
public class NativeFileIconProvider implements FileIconProvider {
  private JFileChooser myFileChooser = new JFileChooser();
  private final Map<Ext, Icon> myIconCache = new HashMap<Ext, Icon>();
  // on Windows .exe and .ico files provide their own icons which can differ for each file, cache them by full file path
  private final Set<Ext> myCustomIconExtensions =
    SystemInfo.isWindows ? new HashSet<Ext>(Arrays.asList(new Ext("exe"), new Ext("ico"))) : new HashSet<Ext>();
  private final Map<String, Icon> myCustomIconCache = new HashMap<String, Icon>();

  private static final Ext NO_EXT = new Ext(null);

  public Icon getIcon(VirtualFile file, int flags, @Nullable Project project) {
    if (!isNativeFileType(file)) return null;

    final Ext ext = file.getExtension() != null ? new Ext(file.getExtension()) : NO_EXT;
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

  protected boolean isNativeFileType(VirtualFile file) {
    return file.getFileType() instanceof NativeFileType || file.getFileType() instanceof UnknownFileType;
  }

  private static class Ext extends ComparableObject.Impl {

    private final Object[] myText;

    private Ext(@Nullable String text) {
      myText = new Object[] {text};
    }

    public Object[] getEqualityObjects() {
      return myText;
    }

    @Override
    public String toString() {
      return myText[0] != null ? myText[0].toString() : null;
    }
  }
}
