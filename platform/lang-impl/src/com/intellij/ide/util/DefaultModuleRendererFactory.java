// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.util;

import com.intellij.icons.AllIcons;
import com.intellij.navigation.ItemPresentation;
import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.extensions.InternalIgnoreDependencyViolation;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.util.SlowOperations;
import com.intellij.util.TextWithIcon;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.File;
import java.util.Set;

import static com.intellij.util.ObjectUtils.coalesce;

@InternalIgnoreDependencyViolation
public class DefaultModuleRendererFactory extends ModuleRendererFactory {

  @Override
  public final @Nullable TextWithIcon getModuleTextWithIcon(Object element) {
    try (AccessToken ignore = SlowOperations.knownIssue("IDEA-334335, EA-841334")) {
      if (element instanceof PsiElement && ((PsiElement)element).isValid()) {
        return elementLocation((PsiElement)element);
      }
      else {
        return null;
      }
    }
  }

  private @Nullable TextWithIcon elementLocation(@NotNull PsiElement element) {
    Project project = element.getProject();
    ProjectFileIndex fileIndex = ProjectRootManager.getInstance(project).getFileIndex();
    VirtualFile vFile = PsiUtilCore.getVirtualFile(element);
    if (vFile != null && fileIndex.isInLibrary(vFile)) {
      return libraryLocation(project, fileIndex, vFile);
    }
    else {
      Module module = ModuleUtilCore.findModuleForPsiElement(element);
      if (module != null) {
        return projectLocation(vFile, module, fileIndex);
      }
      return null;
    }
  }

  @ApiStatus.Internal
  public static @NotNull TextWithIcon projectLocation(@Nullable VirtualFile vFile,
                                                      @NotNull Module module,
                                                      @NotNull ProjectFileIndex fileIndex) {
    boolean inTestSource = vFile != null && fileIndex.isInTestSourceContent(vFile);
    String text;
    if (Registry.is("ide.show.folder.name.instead.of.module.name")) {
      String path = ModuleUtilCore.getModuleDirPath(module);
      text = StringUtil.isEmpty(path) ? module.getName() : new File(path).getName();
    }
    else {
      text = module.getName();
    }
    Icon icon;
    if (inTestSource) {
      icon = AllIcons.Nodes.TestSourceFolder;
    }
    else {
      icon = ModuleType.get(module).getIcon();
    }
    return new TextWithIcon(text, icon);
  }

  @ApiStatus.Internal
  public @NotNull TextWithIcon libraryLocation(@NotNull Project project, @NotNull ProjectFileIndex fileIndex, @NotNull VirtualFile vFile) {
    String text = orderEntryText(fileIndex, vFile);
    Icon icon = AllIcons.Nodes.PpLibFolder;

    if (StringUtil.isEmpty(text) && Registry.is("index.run.configuration.jre")) {
      for (Sdk sdk : ProjectJdkTable.getInstance().getAllJdks()) {
        Set<VirtualFile> roots = StreamEx.of(sdk.getRootProvider().getFiles(OrderRootType.CLASSES))
          .append(sdk.getRootProvider().getFiles(OrderRootType.SOURCES))
          .toSet();
        if (VfsUtilCore.isUnder(vFile, roots)) {
          text = "< " + sdk.getName() + " >";
          break;
        }
      }
    }

    if (StringUtil.isEmpty(text)) {
      SyntheticLibrary library = syntheticLibrary(project, fileIndex, vFile);
      if (library instanceof ItemPresentation presentation) {
        text = coalesce(presentation.getPresentableText(), "");
        icon = presentation.getIcon(false);
      }
    }

    text = text.substring(text.lastIndexOf(File.separatorChar) + 1);
    VirtualFile jar = JarFileSystem.getInstance().getVirtualFileForJar(vFile);
    if (jar != null && !text.equals(jar.getName())) {
      text += " (" + jar.getName() + ")";
    }
    return new TextWithIcon(text, icon);
  }

  private @Nls @NotNull String orderEntryText(@NotNull ProjectFileIndex fileIndex, @NotNull VirtualFile vFile) {
    for (OrderEntry order : fileIndex.getOrderEntriesForFile(vFile)) {
      if (order instanceof LibraryOrderEntry || order instanceof JdkOrderEntry) {
        return getPresentableName(order, vFile);
      }
    }
    return "";
  }

  protected @Nls @NotNull String getPresentableName(OrderEntry order, VirtualFile vFile) {
    return order.getPresentableName();
  }

  private static @Nullable SyntheticLibrary syntheticLibrary(@NotNull Project project,
                                                             @NotNull ProjectFileIndex fileIndex,
                                                             @NotNull VirtualFile vFile) {
    VirtualFile sourceRoot = fileIndex.getSourceRootForFile(vFile);
    if (sourceRoot == null) {
      return null;
    }

    for (AdditionalLibraryRootsProvider provider : AdditionalLibraryRootsProvider.EP_NAME.getExtensions()) {
      for (SyntheticLibrary library : provider.getAdditionalProjectLibraries(project)) {
        if (!library.getSourceRoots().contains(sourceRoot)) {
          continue;
        }

        Condition<? super VirtualFile> excludeFileCondition = library.getUnitedExcludeCondition();
        if (excludeFileCondition == null || !excludeFileCondition.value(vFile)) {
          return library;
        }
      }
    }

    return null;
  }
}
