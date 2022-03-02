// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.navigationToolbar;

import com.intellij.icons.AllIcons;
import com.intellij.ide.projectView.impl.ProjectRootsUtil;
import com.intellij.ide.structureView.StructureViewBundle;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.editor.colors.CodeInsightColors;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SdkType;
import com.intellij.openapi.projectRoots.SdkTypeId;
import com.intellij.openapi.roots.JdkOrderEntry;
import com.intellij.openapi.roots.LibraryOrderEntry;
import com.intellij.openapi.roots.ModuleOrderEntry;
import com.intellij.openapi.vcs.FileStatusManager;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.problems.WolfTheProblemSolver;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiDirectoryContainer;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.IconUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

/**
 * @author Konstantin Bulenkov
 */
public final class NavBarPresentation {
  private final Project project;

  public NavBarPresentation(Project project) {
    this.project = project;
  }

  private static SimpleTextAttributes getErrorAttributes() {
    SimpleTextAttributes schemeAttributes = SimpleTextAttributes.fromTextAttributes(
      EditorColorsManager.getInstance().getSchemeForCurrentUITheme().getAttributes(CodeInsightColors.ERRORS_ATTRIBUTES)
    );
    return SimpleTextAttributes.merge(new SimpleTextAttributes(SimpleTextAttributes.STYLE_USE_EFFECT_COLOR, schemeAttributes.getFgColor()),
                                      schemeAttributes);
  }

  public @Nullable Icon getIcon(final Object object) {
    if (!NavBarModel.isValid(object)) {
      return null;
    }

    for (NavBarModelExtension modelExtension : NavBarModelExtension.EP_NAME.getExtensionList()) {
      Icon icon = modelExtension.getIcon(object);
      if (icon != null) {
        return icon;
      }
    }

    if (object instanceof Project) {
      return AllIcons.Nodes.Project;
    }
    if (object instanceof Module) {
      return ModuleType.get(((Module)object)).getIcon();
    }

    try {
      if (object instanceof PsiElement) {
        Icon icon = ReadAction
          .compute(() -> ((PsiElement)object).isValid() ? ((PsiElement)object).getIcon(0) : null);

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
      Sdk jdk = ((JdkOrderEntry)object).getJdk();
      if (jdk == null) return null;

      final SdkTypeId sdkType = jdk.getSdkType();
      return ((SdkType) sdkType).getIcon();
    }
    else if (object instanceof LibraryOrderEntry) {
      return AllIcons.Nodes.PpLibFolder;
    }
    else if (object instanceof ModuleOrderEntry) {
      return ModuleType.get(((ModuleOrderEntry)object).getModule()).getIcon();
    }
    else {
      return null;
    }
  }

  @NotNull
  @Nls
  String getPresentableText(Object object, boolean forPopup) {
    String text = calcPresentableText(object, forPopup);
    return text.length() > 50 ? text.substring(0, 47) + "..." : text;
  }

  public static @NotNull @Nls String calcPresentableText(Object object, boolean forPopup) {
    if (!NavBarModel.isValid(object)) {
      return StructureViewBundle.message("node.structureview.invalid");
    }
    for (NavBarModelExtension modelExtension : NavBarModelExtension.EP_NAME.getExtensionList()) {
      String text = modelExtension.getPresentableText(object, forPopup);
      if (text != null) return text;
    }
    return object.toString(); //NON-NLS
  }

  SimpleTextAttributes getTextAttributes(final Object object, final boolean selected) {
    if (!NavBarModel.isValid(object)) {
      return SimpleTextAttributes.REGULAR_ATTRIBUTES;
    }

    if (object instanceof PsiElement) {
      if (!ReadAction.compute(() -> ((PsiElement)object).isValid()).booleanValue()) {
        return SimpleTextAttributes.GRAYED_ATTRIBUTES;
      }

      PsiFile psiFile = ((PsiElement)object).getContainingFile();
      if (psiFile != null) {
        VirtualFile virtualFile = psiFile.getVirtualFile();
        int style = virtualFile != null && WolfTheProblemSolver.getInstance(project).isProblemFile(virtualFile)
                    ? getErrorAttributes().getStyle()
                    : SimpleTextAttributes.STYLE_PLAIN;
        Color color = selected ? null : FileStatusManager.getInstance(project).getStatus(virtualFile).getColor();
        return new SimpleTextAttributes(null, color, getErrorAttributes().getWaveColor(), style);
      }
      else {
        if (object instanceof PsiDirectory) {
          VirtualFile vDir = ((PsiDirectory)object).getVirtualFile();
          if (vDir.getParent() == null || ProjectRootsUtil.isModuleContentRoot(vDir, project)) {
            return SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES;
          }
        }

        if (wolfHasProblemFilesBeneath((PsiElement)object)) {
          return getErrorAttributes();
        }
      }
    }
    else if (object instanceof Module) {
      if (WolfTheProblemSolver.getInstance(project).hasProblemFilesBeneath((Module)object)) {
        return getErrorAttributes();
      }
    }
    else if (object instanceof Project) {
      Project project = (Project)object;
      Module[] modules = ReadAction.compute(() -> ModuleManager.getInstance(project).getModules());
      WolfTheProblemSolver problemSolver = WolfTheProblemSolver.getInstance(project);
      for (Module module : modules) {
        if (problemSolver.hasProblemFilesBeneath(module)) {
          return getErrorAttributes();
        }
      }
    }
    return SimpleTextAttributes.REGULAR_ATTRIBUTES;
  }

  public static boolean wolfHasProblemFilesBeneath(@NotNull PsiElement scope) {
    return WolfTheProblemSolver.getInstance(scope.getProject()).hasProblemFilesBeneath(virtualFile -> {
      if (scope instanceof PsiDirectory) {
        final PsiDirectory directory = (PsiDirectory)scope;
        if (!VfsUtilCore.isAncestor(directory.getVirtualFile(), virtualFile, false)) return false;
        return ModuleUtilCore.findModuleForFile(virtualFile, scope.getProject()) == ModuleUtilCore.findModuleForPsiElement(scope);
      }
      else if (scope instanceof PsiDirectoryContainer) { // TODO: remove. It doesn't look like we'll have packages in navbar ever again
        final PsiDirectory[] psiDirectories = ((PsiDirectoryContainer)scope).getDirectories();
        for (PsiDirectory directory : psiDirectories) {
          if (VfsUtilCore.isAncestor(directory.getVirtualFile(), virtualFile, false)) {
            return true;
          }
        }
      }
      return false;
    });
  }
}
