// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection;

import com.intellij.analysis.AnalysisScope;
import com.intellij.codeInspection.options.OptPane;
import com.intellij.codeInspection.reference.RefElement;
import com.intellij.lang.LangBundle;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiManager;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.SearchScope;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.codeInspection.options.OptPane.checkbox;
import static com.intellij.codeInspection.options.OptPane.pane;

public final class EmptyDirectoryInspection extends GlobalInspectionTool {

  @SuppressWarnings("PublicField")
  public boolean onlyReportDirectoriesUnderSourceRoots = false;

  @Override
  public @NotNull OptPane getOptionsPane() {
    return pane(
      checkbox("onlyReportDirectoriesUnderSourceRoots", LangBundle.message("empty.directories.only.under.source.roots.option")));
  }

  @Override
  public boolean isGraphNeeded() {
    return false;
  }

  @Override
  public boolean isReadActionNeeded() {
    return false;
  }

  @Override
  public void runInspection(@NotNull AnalysisScope scope,
                            @NotNull InspectionManager manager,
                            @NotNull GlobalInspectionContext context,
                            @NotNull ProblemDescriptionsProcessor processor) {
    final Project project = context.getProject();
    final ProjectFileIndex index = ProjectRootManager.getInstance(project).getFileIndex();
    final SearchScope searchScope = ReadAction.compute(() -> scope.toSearchScope());
    if (!(searchScope instanceof GlobalSearchScope globalSearchScope)) {
      return;
    }
    index.iterateContent(file -> {
      if (onlyReportDirectoriesUnderSourceRoots && ReadAction.compute(() -> !index.isInSourceContent(file))) {
        return true;
      }
      if (!file.isDirectory() || file.getChildren().length != 0) {
        return true;
      }
      final PsiDirectory directory = ReadAction.compute(() -> PsiManager.getInstance(project).findDirectory(file));
      final RefElement refDirectory = context.getRefManager().getReference(directory);
      if (refDirectory == null || context.shouldCheck(refDirectory, this)) {
        return true;
      }
      final String relativePath = getPathRelativeToModule(file, project);
      if (relativePath == null) {
        return true;
      }
      processor.addProblemElement(refDirectory, manager.createProblemDescriptor(
        LangBundle.message("empty.directories.problem.descriptor", relativePath),
        new EmptyPackageFix(file.getUrl(), file.getName())));
      return true;
    }, globalSearchScope);
  }

  private static @Nullable String getPathRelativeToModule(VirtualFile file, Project project) {
    final ProjectRootManager rootManager = ProjectRootManager.getInstance(project);
    final VirtualFile[] contentRoots = rootManager.getContentRootsFromAllModules();
    for (VirtualFile otherRoot : contentRoots) {
      if (VfsUtilCore.isAncestor(otherRoot, file, false)) {
        return VfsUtilCore.getRelativePath(file, otherRoot, '/');
      }
    }
    return null;
  }

  private static final class EmptyPackageFix implements QuickFix<CommonProblemDescriptor> {

    private final String url;
    private final String name;

    EmptyPackageFix(String url, String name) {
      this.url = url;
      this.name = name;
    }

    @Override
    public @NotNull String getName() {
      return LangBundle.message(
        "empty.directories.delete.quickfix", name);
    }

    @Override
    public @NotNull String getFamilyName() {
      return LangBundle.message("empty.directories.delete.quickfix", "");
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull CommonProblemDescriptor descriptor) {
      final VirtualFile file = VirtualFileManager.getInstance().findFileByUrl(url);
      if (file == null) {
        return;
      }
      final PsiManager psiManager = PsiManager.getInstance(project);
      final PsiDirectory directory = psiManager.findDirectory(file);
      if (directory == null) {
        return;
      }
      directory.delete();
    }
  }
}
