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

public class IconUtilEx {

  public static Icon getIcon(Object object, int flags, Project project) {
    if (object instanceof PsiElement) {
      return ((PsiElement)object).getIcon(flags);
    }
    if (object instanceof Module) {
      return getIcon((Module)object, flags);
    }
    if (object instanceof VirtualFile) {
      VirtualFile file = (VirtualFile)object;
      return IconUtil.getIcon(file, flags, project);
    }
    return ElementPresentationManager.getIcon(object);
  }

  public static Icon getIcon(Module module, int flags) {
    return getModuleTypeIcon(module.getModuleType(), flags);
  }

  public static Icon getModuleTypeIcon(final ModuleType moduleType, int flags) {
    return moduleType.getNodeIcon((flags & Iconable.ICON_FLAG_OPEN) != 0);
  }

}