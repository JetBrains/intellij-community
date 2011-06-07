/*
 * Copyright 2000-2011 JetBrains s.r.o.
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

import com.intellij.ide.IdeBundle;
import com.intellij.ide.projectView.impl.ProjectRootsUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.JdkOrderEntry;
import com.intellij.openapi.roots.LibraryOrderEntry;
import com.intellij.openapi.roots.ModuleOrderEntry;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.Iconable;
import com.intellij.openapi.vcs.FileStatusManager;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.problems.WolfTheProblemSolver;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiDirectoryContainer;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.ui.ScreenUtil;
import com.intellij.ui.SimpleTextAttributes;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

/**
 * @author Konstantin Bulenkov
 */
public class NavBarPresentation {
  private static final SimpleTextAttributes WOLFED = new SimpleTextAttributes(null, null, Color.red, SimpleTextAttributes.STYLE_WAVED);
  private static final Icon PROJECT_ICON = IconLoader.getIcon("/nodes/project.png");

  private final Project myProject;

  public NavBarPresentation(Project project) {
    myProject = project;
  }

  @Nullable
  public static Icon getIcon(final Object object, final boolean open) {
    if (!NavBarModel.isValid(object)) return null;
    if (object instanceof Project) return PROJECT_ICON;
    if (object instanceof Module) return ((Module)object).getModuleType().getNodeIcon(false);
    try {
      if (object instanceof PsiElement) {
        return ApplicationManager.getApplication().runReadAction(new Computable<Icon>() {
          public Icon compute() {
            return ((PsiElement)object).isValid() ? ((PsiElement)object)
              .getIcon(open ? Iconable.ICON_FLAG_OPEN : Iconable.ICON_FLAG_CLOSED) : null;
          }
        });
      }
    }
    catch (IndexNotReadyException e) {
      return null;
    }
    if (object instanceof JdkOrderEntry) return ((JdkOrderEntry)object).getJdk().getSdkType().getIcon();
    if (object instanceof LibraryOrderEntry) return IconLoader.getIcon("/nodes/ppLibClosed.png");
    if (object instanceof ModuleOrderEntry) return ((ModuleOrderEntry)object).getModule().getModuleType().getNodeIcon(false);
    return null;
  }

  @NotNull
  protected static String getPresentableText(final Object object, @Nullable Window window) {
    if (!NavBarModel.isValid(object)) return IdeBundle.message("node.structureview.invalid");
    for (NavBarModelExtension modelExtension : Extensions.getExtensions(NavBarModelExtension.EP_NAME)) {
      String text = modelExtension.getPresentableText(object);
      if (text != null) {
        boolean truncated = false;
        if (window != null) {
          Rectangle wndBounds = window.getBounds();
          Rectangle rec = ScreenUtil.getScreenRectangle(wndBounds.x, wndBounds.y);

          final int windowWidth = rec.width;

          while (window.getFontMetrics(window.getFont()).stringWidth(text) + 100 > windowWidth && text.length() > 10) {
            text = text.substring(0, text.length() - 10);
            truncated = true;
          }
        }
        return text + (truncated ? "..." : "");
      }
    }
    return object.toString();
  }

  protected SimpleTextAttributes getTextAttributes(final Object object, final boolean selected) {
    if (!NavBarModel.isValid(object)) return SimpleTextAttributes.REGULAR_ATTRIBUTES;
    if (object instanceof PsiElement) {
      if (!ApplicationManager.getApplication().runReadAction(new Computable<Boolean>() {
        public Boolean compute() {
          return ((PsiElement)object).isValid();
        }
      }).booleanValue()) return SimpleTextAttributes.GRAYED_ATTRIBUTES;
      PsiFile psiFile = ((PsiElement)object).getContainingFile();
      if (psiFile != null) {
        final VirtualFile virtualFile = psiFile.getVirtualFile();
        return new SimpleTextAttributes(null, selected ? null : FileStatusManager.getInstance(myProject).getStatus(virtualFile).getColor(),
                                        Color.red, WolfTheProblemSolver.getInstance(myProject).isProblemFile(virtualFile)
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
    return WolfTheProblemSolver.getInstance(scope.getProject()).hasProblemFilesBeneath(new Condition<VirtualFile>() {
      public boolean value(final VirtualFile virtualFile) {
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
      }
    });
  }
}
