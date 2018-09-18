// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.presentation;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.IconUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * @author yole
 */
public class VirtualFilePresentation {
  public static Icon getIcon(@NotNull VirtualFile vFile) {
    return IconUtil.getIcon(vFile, 0, null);
  }
}