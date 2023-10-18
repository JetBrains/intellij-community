// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Iconable;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.ui.CoreAwareIconManager;
import com.intellij.ui.IconManager;
import com.intellij.util.xml.ElementPresentationManager;

import javax.swing.*;

public final class IconUtilEx {
  public static Icon getIcon(Object object, @Iconable.IconFlags int flags, Project project) {
    if (object instanceof PsiElement element) {
      return element.getIcon(flags);
    }
    if (object instanceof Module module) {
      return ModuleType.get(module).getIcon();
    }
    if (object instanceof VirtualFile file) {
      IconManager iconManager = IconManager.getInstance();
      if (iconManager instanceof CoreAwareIconManager manager) {
        return manager.getIcon(file, flags, project);
      }
    }
    return ElementPresentationManager.getIcon(object);
  }
}
