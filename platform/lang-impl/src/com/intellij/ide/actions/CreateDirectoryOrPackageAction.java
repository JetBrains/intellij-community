// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions;

import com.intellij.icons.AllIcons;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.IdeView;
import com.intellij.idea.ActionsBundle;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataKey;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ui.configuration.ModuleSourceRootEditHandler;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.impl.file.PsiDirectoryFactory;
import com.intellij.util.PlatformIcons;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;
import org.jetbrains.annotations.Unmodifiable;
import org.jetbrains.jps.model.module.JpsModuleSourceRootType;

import javax.swing.Icon;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

@ApiStatus.Internal
public class CreateDirectoryOrPackageAction extends AnAction implements DumbAware {
  public static final ExtensionPointName<CreateDirectoryCompletionContributor> EP = new ExtensionPointName<>("com.intellij.createDirectoryCompletionContributor");

  @TestOnly
  public static final DataKey<String> TEST_DIRECTORY_NAME_KEY = DataKey.create("CreateDirectoryOrPackageAction.testName");

  public CreateDirectoryOrPackageAction() {
  }

  @SuppressWarnings({"unused", "ActionPresentationInstantiatedInCtor"})
  protected CreateDirectoryOrPackageAction(boolean initMessages) {
    super(ActionsBundle.messagePointer("action.NewDir.GoToAction.text"));
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent event) {
    CreateDirectoryOrPackagePopup.show(this, event);
  }

  @Override
  public void update(@NotNull AnActionEvent event) {
    Presentation presentation = event.getPresentation();

    Project project = event.getData(CommonDataKeys.PROJECT);
    if (project == null) {
      presentation.setEnabledAndVisible(false);
      return;
    }

    IdeView view = event.getData(LangDataKeys.IDE_VIEW);
    if (view == null) {
      presentation.setEnabledAndVisible(false);
      return;
    }

    List<@NotNull PsiDirectory> directories = Arrays.asList(view.getDirectories());
    if (directories.isEmpty()) {
      presentation.setEnabledAndVisible(false);
      return;
    }

    presentation.setEnabledAndVisible(true);

    boolean isPackage = isPackage(project, directories);

    if (isPackage) {
      presentation.setText(IdeBundle.messagePointer("action.package"));
      presentation.setIcon(AllIcons.Nodes.Package);
    }
    else {
      presentation.setText(IdeBundle.messagePointer("action.directory"));
      presentation.setIcon(PlatformIcons.FOLDER_ICON);
    }
  }

  protected boolean isPackage(@NotNull Project project, @NotNull List<@NotNull PsiDirectory> directories) {
    final PsiDirectoryFactory factory = PsiDirectoryFactory.getInstance(project);
    for (PsiDirectory directory : directories) {
      if (factory.isPackage(directory)) {
        return true;
      }
    }
    return false;
  }

  protected @NotNull List<CompletionItem> collectSuggestedDirectories(@NotNull PsiDirectory directory) {
    List<CompletionItem> variants = new ArrayList<>();

    VirtualFile vDir = directory.getVirtualFile();
    for (CreateDirectoryCompletionContributor contributor : EP.getExtensionList()) {
      for (CreateDirectoryCompletionContributor.Variant variant : contributor.getVariants(directory)) {
        String relativePath = FileUtil.toSystemIndependentName(variant.path);
        if (FileUtil.isAbsolutePlatformIndependent(relativePath)) {
          // only suggest sub-folders
          if (!FileUtil.isAncestor(vDir.getPath(), relativePath, true)) continue;

          // convert absolute paths to the relative paths
          relativePath = FileUtil.getRelativePath(vDir.getPath(), relativePath, '/');
          if (relativePath == null) continue;
        }

        // only suggested non-existent folders
        if (vDir.findFileByRelativePath(relativePath) != null) continue;

        ModuleSourceRootEditHandler<?> handler =
          variant.rootType != null ? ModuleSourceRootEditHandler.getEditHandler(variant.rootType) : null;

        Icon icon = handler == null ? null : handler.getRootIcon();
        if (icon == null) icon = variant.icon;
        if (icon == null) icon = AllIcons.Nodes.Folder;

        CompletionItem completionItem = new CompletionItem(contributor, relativePath, icon, variant.rootType);
        if (!variants.contains(completionItem)) {
          variants.add(completionItem);
        }
      }
    }

    variants.sort((o1, o2) -> {
      int result = StringUtil.naturalCompare(o1.contributor.getDescription(), o2.contributor.getDescription());
      if (result != 0) return result;
      return StringUtil.naturalCompare(o1.relativePath, o2.relativePath);
    });

    return variants;
  }

  @TestOnly
  @ApiStatus.Internal
  public @NotNull @Unmodifiable List<String> collectSuggestedDirectoriesTestAccessor(@NotNull PsiDirectory directory) {
    return ContainerUtil.map(collectSuggestedDirectories(directory), item -> item.relativePath);
  }
  
  @ApiStatus.Internal
  protected static final class CompletionItem {
    final @NotNull CreateDirectoryCompletionContributor contributor;

    final @NotNull String relativePath;
    final @Nullable JpsModuleSourceRootType<?> rootType;

    final @NlsContexts.ListItem @NotNull String displayText;
    final @Nullable Icon icon;

    private CompletionItem(@NotNull CreateDirectoryCompletionContributor contributor,
                           @NotNull String relativePath,
                           @Nullable Icon icon,
                           @Nullable JpsModuleSourceRootType<?> rootType) {
      this.contributor = contributor;

      this.relativePath = relativePath;
      this.rootType = rootType;

      this.displayText = FileUtil.toSystemDependentName(relativePath);
      this.icon = icon;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      CompletionItem that = (CompletionItem)o;
      return contributor.equals(that.contributor) &&
             relativePath.equals(that.relativePath) &&
             Objects.equals(rootType, that.rootType) &&
             displayText.equals(that.displayText) &&
             Objects.equals(icon, that.icon);
    }

    @Override
    public int hashCode() {
      return Objects.hash(contributor, relativePath, rootType, displayText, icon);
    }
  }
}
