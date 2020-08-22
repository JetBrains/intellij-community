// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Iconable;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.util.IconUtil;
import com.intellij.util.xml.ElementPresentationManager;

import javax.swing.*;

public final class IconUtilEx {

  public static Icon getIcon(Object object, @Iconable.IconFlags int flags, Project project) {
    if (object instanceof PsiElement) {
      return ((PsiElement)object).getIcon(flags);
    }
    if (object instanceof Module) {
      return ModuleType.get((Module)object).getIcon();
    }
    if (object instanceof VirtualFile) {
      VirtualFile file = (VirtualFile)object;
      return IconUtil.getIcon(file, flags, project);
    }
    return ElementPresentationManager.getIcon(object);
  }
}
