/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.ide.navigationToolbar;

import com.intellij.icons.AllIcons;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.projectView.impl.ProjectRootsUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.SdkType;
import com.intellij.openapi.projectRoots.SdkTypeId;
import com.intellij.openapi.roots.JdkOrderEntry;
import com.intellij.openapi.roots.LibraryOrderEntry;
import com.intellij.openapi.roots.ModuleOrderEntry;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.vcs.FileStatusManager;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.problems.WolfTheProblemSolver;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiDirectoryContainer;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.ui.JBColor;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.IconUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

/**
 * @author Konstantin Bulenkov
 */
public class NavBarPresentation {
  private static final SimpleTextAttributes WOLFED = new SimpleTextAttributes(null, null, JBColor.red, SimpleTextAttributes.STYLE_WAVED);

  private final Project myProject;

  public NavBarPresentation(Project project) {
    myProject = project;
  }

  @SuppressWarnings("MethodMayBeStatic")
  @Nullable
  public Icon getIcon(final Object object) {
    if (!NavBarModel.isValid(object)) return null;
    if (object instanceof Project) return AllIcons.Nodes.Project;
    if (object instanceof Module) return ModuleType.get(((Module)object)).getIcon();
    try {
      if (object instanceof PsiElement) {
        Icon icon = ApplicationManager.getApplication().runReadAction(new Computable<Icon>() {
          @Override
          public Icon compute() {
            return ((PsiElement)object).isValid() ? ((PsiElement)object).getIcon(0) : null;
          }
        });

        if (icon != null && (icon.getIconHeight() > 16 * 2 || icon.getIconWidth() > 16 * 2)) {
          icon = IconUtil.cropIcon(icon, 16 * 2, 16 * 2);
        }
        return icon;
      }
    }
    catch (IndexNotReadyException e) {
      return null;
    }
    if (object instanceof JdkOrderEntry) {
      final SdkTypeId sdkType = ((JdkOrderEntry)object).getJdk().getSdkType();
      return ((SdkType) sdkType).getIcon();
    }
    if (object instanceof LibraryOrderEntry) return AllIcons.Nodes.PpLibFolder;
    if (object instanceof ModuleOrderEntry) return ModuleType.get(((ModuleOrderEntry)object).getModule()).getIcon();
    return null;
  }

  @SuppressWarnings("MethodMayBeStatic")
  @NotNull
  protected String getPresentableText(final Object object) {
    if (!NavBarModel.isValid(object)) {
      return IdeBundle.message("node.structureview.invalid");
    }
    for (NavBarModelExtension modelExtension : Extensions.getExtensions(NavBarModelExtension.EP_NAME)) {
      String text = modelExtension.getPresentableText(object);
      if (text != null) {
        return text.length() > 50 ? text.substring(0, 47) + "..." : text;
      }
    }
    return object.toString();
  }

  protected SimpleTextAttributes getTextAttributes(final Object object, final boolean selected) {
    if (!NavBarModel.isValid(object)) return SimpleTextAttributes.REGULAR_ATTRIBUTES;
    if (object instanceof PsiElement) {
      if (!ApplicationManager.getApplication().runReadAction(new Computable<Boolean>() {
        @Override
        public Boolean compute() {
          return ((PsiElement)object).isValid();
        }
      }).booleanValue()) return SimpleTextAttributes.GRAYED_ATTRIBUTES;
      PsiFile psiFile = ((PsiElement)object).getContainingFile();
      if (psiFile != null) {
        final VirtualFile virtualFile = psiFile.getVirtualFile();
        return new SimpleTextAttributes(null, selected ? null : FileStatusManager.getInstance(myProject).getStatus(virtualFile).getColor(),
                                        JBColor.red, WolfTheProblemSolver.getInstance(myProject).isProblemFile(virtualFile)
                                                   ? SimpleTextAttributes.STYLE_WAVED
                                                   : SimpleTextAttributes.STYLE_PLAIN);
      }
      else {
        if (object instanceof PsiDirectory) {
          VirtualFile vDir = ((PsiDirectory)object).getVirtualFile();
          if (vDir.getParent() == null || ProjectRootsUtil.isModuleContentRoot(vDir, myProject)) {
            return SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES;
          }
        }

        if (wolfHasProblemFilesBeneath((PsiElement)object)) {
          return WOLFED;
        }
      }
    }
    else if (object instanceof Module) {
      if (WolfTheProblemSolver.getInstance(myProject).hasProblemFilesBeneath((Module)object)) {
        return WOLFED;
      }

    }
    else if (object instanceof Project) {
      final Project project = (Project)object;
      final Module[] modules = ApplicationManager.getApplication().runReadAction(
          new Computable<Module[]>() {
            @Override
            public Module[] compute() {
              return  ModuleManager.getInstance(project).getModules();
            }
          }
      );
      for (Module module : modules) {
        if (WolfTheProblemSolver.getInstance(project).hasProblemFilesBeneath(module)) {
          return WOLFED;
        }
      }
    }
    return SimpleTextAttributes.REGULAR_ATTRIBUTES;
  }

  public static boolean wolfHasProblemFilesBeneath(final PsiElement scope) {
    return WolfTheProblemSolver.getInstance(scope.getProject()).hasProblemFilesBeneath(virtualFile -> {
      if (scope instanceof PsiDirectory) {
        final PsiDirectory directory = (PsiDirectory)scope;
        if (!VfsUtil.isAncestor(directory.getVirtualFile(), virtualFile, false)) return false;
        return ModuleUtil.findModuleForFile(virtualFile, scope.getProject()) == ModuleUtil.findModuleForPsiElement(scope);
      }
      else if (scope instanceof PsiDirectoryContainer) { // TODO: remove. It doesn't look like we'll have packages in navbar ever again
        final PsiDirectory[] psiDirectories = ((PsiDirectoryContainer)scope).getDirectories();
        for (PsiDirectory directory : psiDirectories) {
          if (VfsUtil.isAncestor(directory.getVirtualFile(), virtualFile, false)) {
            return true;
          }
        }
      }
      return false;
    });
  }
}
