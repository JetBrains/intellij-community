// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.INativeFileType;
import com.intellij.openapi.fileTypes.UnknownFileType;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.util.Iconable;
import com.intellij.openapi.util.NotNullLazyValue;
import com.intellij.openapi.util.SystemInfoRt;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.ui.DeferredIconImpl;
import com.intellij.util.ui.update.ComparableObject;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * @author yole
 */
public final class NativeIconProvider extends IconProvider implements DumbAware {
  private final Map<Ext, Icon> myIconCache = new HashMap<>();
  // on Windows .exe and .ico files provide their own icons which can differ for each file, cache them by full file path
  private final Set<Ext> myCustomIconExtensions = SystemInfoRt.isWindows ? Set.of(new Ext("exe"), new Ext("ico")) : Collections.emptySet();
  private final Map<String, Icon> myCustomIconCache = new HashMap<>();

  private static final Ext NO_EXT = new Ext(null);

  private static final Ext CLOSED_DIR = new Ext(null, 0);

  private static final NotNullLazyValue<JFileChooser> fileChooser = NotNullLazyValue.volatileLazy(() -> new JFileChooser());

  @Override
  public @Nullable Icon getIcon(@NotNull PsiElement element, @Iconable.IconFlags int flags) {
    if (element instanceof PsiFile) {
      VirtualFile file = ((PsiFile)element).getVirtualFile();
      if (file != null) {
        return doGetIcon(file, flags);
      }
    }
    return null;
  }

  private @Nullable Icon doGetIcon(@NotNull VirtualFile virtualFile, int flags) {
    if (!isNativeFileType(virtualFile)) {
      return null;
    }

    Ext ext = getExtension(virtualFile, flags);
    Path ioFile = virtualFile.toNioPath();

    synchronized (myIconCache) {
      Icon icon;
      if (myCustomIconExtensions.contains(ext)) {
        icon = myCustomIconCache.get(ioFile.toString());
      }
      else {
        icon = ext == null ? null : myIconCache.get(ext);
      }
      if (icon != null) {
        return icon;
      }
    }

    return DeferredIconImpl.withoutReadAction(AllIcons.Nodes.NodePlaceholder, ioFile, file -> {
      if (!Files.exists(file)) {
        return null;
      }

      // we should have no read access here, to avoid deadlock with EDT needed to init component
      assert !ApplicationManager.getApplication().isReadAccessAllowed();

      Icon icon;
      try {
        icon = fileChooser.getValue().getIcon(file.toFile());
      }
      catch (Exception e) {
        // see http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=4854174
        return null;
      }

      if (ext != null) {
        synchronized (myIconCache) {
          if (myCustomIconExtensions.contains(ext)) {
            myCustomIconCache.put(file.toString(), icon);
          }
          else {
            myIconCache.put(ext, icon);
          }
        }
      }
      return icon;
    });
  }

  public static @Nullable Icon getNativeIcon(@NotNull Path file) {
    return fileChooser.getValue().getIcon(file.toFile());
  }

  private static Ext getExtension(VirtualFile file, int flags) {
    if (file.isDirectory()) {
      if (file.getExtension() == null) {
        return CLOSED_DIR;
      }
      else {
        return new Ext(file.getExtension(), flags);
      }
    }
    else {
      return file.getExtension() != null ? new Ext(file.getExtension()) : NO_EXT;
    }
  }

  private static boolean isNativeFileType(VirtualFile file) {
    FileType type = file.getFileType();

    if (type instanceof INativeFileType) {
      return ((INativeFileType)type).useNativeIcon();
    }
    return type instanceof UnknownFileType && !file.isDirectory();
  }

  private static final class Ext extends ComparableObject.Impl {
    private final Object[] myText;

    private Ext(@Nullable String text) {
      myText = new Object[] {text};
    }

    private Ext(@Nullable String text, final int flags) {
      myText = new Object[] {text, flags};
    }

    @Override
    public Object @NotNull [] getEqualityObjects() {
      return myText;
    }

    @Override
    public String toString() {
      return myText[0] != null ? myText[0].toString() : null;
    }
  }
}
