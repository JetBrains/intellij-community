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
package com.intellij.ide.util;

import com.intellij.ide.IdeBundle;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.roots.impl.DirectoryIndex;
import com.intellij.openapi.roots.ui.configuration.CommonContentEntriesEditor;
import com.intellij.openapi.roots.ui.configuration.ProjectSettingsService;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.impl.source.resolve.FileContextUtil;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.GlobalSearchScopes;
import com.intellij.util.*;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.List;

// TODO this should eventually replace PackageUtil from java-impl
public class PlatformPackageUtil {

  private static final Logger LOG = Logger.getInstance("com.intellij.ide.util.PlatformPackageUtil");

  @Nullable
  private static String findLongestExistingPackage(Project project, String packageName, GlobalSearchScope scope) {
    final PsiManager manager = PsiManager.getInstance(project);
    DirectoryIndex index = DirectoryIndex.getInstance(project);

    String nameToMatch = packageName;
    while (true) {
      Query<VirtualFile> vFiles = index.getDirectoriesByPackageName(nameToMatch, false);
      PsiDirectory directory = getWritableModuleDirectory(vFiles, scope, manager);
      if (directory != null) return index.getPackageName(directory.getVirtualFile());

      int lastDotIndex = nameToMatch.lastIndexOf('.');
      if (lastDotIndex >= 0) {
        nameToMatch = nameToMatch.substring(0, lastDotIndex);
      }
      else {
        return null;
      }
    }
  }

  @Nullable
  private static PsiDirectory getWritableModuleDirectory(@NotNull Query<VirtualFile> vFiles,
                                                         GlobalSearchScope scope,
                                                         PsiManager manager) {
    for (VirtualFile vFile : vFiles) {
      if (!scope.contains(vFile)) continue;
      PsiDirectory directory = manager.findDirectory(vFile);
      if (directory != null && directory.isValid() && directory.isWritable()) {
        return directory;
      }
    }
    return null;
  }

  @Nullable
  public static PsiDirectory findOrCreateDirectoryForPackage(@NotNull final Project project,
                                                             @Nullable Module module,
                                                             GlobalSearchScope scope,
                                                             String packageName,
                                                             PsiDirectory baseDir,
                                                             boolean askUserToCreate,
                                                             ThreeState chooseFlag) throws IncorrectOperationException {
    PsiDirectory psiDirectory = null;
    if (chooseFlag == ThreeState.UNSURE && !"".equals(packageName)) {
      String rootPackage = findLongestExistingPackage(project, packageName, scope);
      if (rootPackage != null) {
        int beginIndex = rootPackage.length() + 1;
        packageName = beginIndex < packageName.length() ? packageName.substring(beginIndex) : "";
        String postfixToShow = packageName.replace('.', File.separatorChar);
        if (packageName.length() > 0) {
          postfixToShow = File.separatorChar + postfixToShow;
        }
        psiDirectory =
          DirectoryChooserUtil.selectDirectory(project, getPackageDirectories(project, rootPackage, scope), baseDir, postfixToShow);
        if (psiDirectory == null) return null;
      }
    }

    if (psiDirectory == null) {
      if (chooseFlag == ThreeState.NO && baseDir != null) {
        VirtualFile sourceRoot = ProjectRootManager.getInstance(project).getFileIndex().getSourceRootForFile(baseDir.getVirtualFile());
        psiDirectory = PsiManager.getInstance(project).findDirectory(sourceRoot);
      }
      else {
        if (module != null && !checkSourceRootsConfigured(module)) return null;
        final GlobalSearchScope scope_ = scope;
        List<PsiDirectory> dirs =
          ContainerUtil
            .mapNotNull(ProjectRootManager.getInstance(project).getContentSourceRoots(),
                        virtualFile -> scope_.contains(virtualFile) ? PsiManager.getInstance(project).findDirectory(virtualFile) : null);
        psiDirectory = DirectoryChooserUtil.selectDirectory(project, dirs.toArray(new PsiDirectory[dirs.size()]), baseDir,
                                                            File.separatorChar + packageName.replace('.', File.separatorChar));
        if (psiDirectory == null) return null;
        final VirtualFile sourceRoot = ProjectRootManager.getInstance(project).getFileIndex().getSourceRootForFile(psiDirectory.getVirtualFile());
        psiDirectory = PsiManager.getInstance(project).findDirectory(sourceRoot);
      }
    }

    String restOfName = packageName;
    boolean askedToCreate = false;
    while (restOfName.length() > 0) {
      final String name = getLeftPart(restOfName);
      PsiDirectory foundExistingDirectory = psiDirectory.findSubdirectory(name);
      if (foundExistingDirectory == null) {
        if (!askedToCreate && askUserToCreate) {
          if (!ApplicationManager.getApplication().isUnitTestMode()) {
            int toCreate = Messages.showYesNoDialog(project,
                                                    IdeBundle.message("prompt.create.non.existing.package", packageName),
                                                    IdeBundle.message("title.package.not.found"),
                                                    Messages.getQuestionIcon());
            if (toCreate != Messages.YES) {
              return null;
            }
          }
          askedToCreate = true;
        }

        final PsiDirectory psiDirectory_ = psiDirectory;
        try {
          psiDirectory = ActionRunner.runInsideWriteAction(new ActionRunner.InterruptibleRunnableWithResult<PsiDirectory>() {
            @Override
            public PsiDirectory run() throws Exception {
              return psiDirectory_.createSubdirectory(name);
            }
          });
        }
        catch (IncorrectOperationException e) {
          throw e;
        }
        catch (IOException e) {
          throw new IncorrectOperationException(e);
        }
        catch (Exception e) {
          LOG.error(e);
        }
      }
      else {
        psiDirectory = foundExistingDirectory;
      }
      restOfName = cutLeftPart(restOfName);
    }
    return psiDirectory;
  }

  public static GlobalSearchScope adjustScope(PsiDirectory baseDir,
                                              GlobalSearchScope scope,
                                              boolean skipSourceDirsForBaseTestDirectory,
                                              boolean skipTestDirsForBaseSourceDirectory) {
    if (baseDir != null) {
      final ProjectFileIndex fileIndex = ProjectRootManager.getInstance(baseDir.getProject()).getFileIndex();
      if (fileIndex.isInTestSourceContent(baseDir.getVirtualFile())) {
        if (skipSourceDirsForBaseTestDirectory) {
          return scope.intersectWith(GlobalSearchScopes.projectTestScope(baseDir.getProject()));
        }
      }
      else {
        if (skipTestDirsForBaseSourceDirectory) {
          return scope.intersectWith(GlobalSearchScopes.projectProductionScope(baseDir.getProject()));
        }
      }
    }
    return scope;
  }

  private static PsiDirectory[] getPackageDirectories(Project project, String rootPackage, final GlobalSearchScope scope) {
    final PsiManager manager = PsiManager.getInstance(project);

    Query<VirtualFile> query = DirectoryIndex.getInstance(scope.getProject()).getDirectoriesByPackageName(rootPackage, true);
    query = new FilteredQuery<VirtualFile>(query, virtualFile -> scope.contains(virtualFile));

    List<PsiDirectory> directories = ContainerUtil.mapNotNull(query.findAll(), virtualFile -> manager.findDirectory(virtualFile));
    return directories.toArray(new PsiDirectory[directories.size()]);
  }

  private static boolean checkSourceRootsConfigured(final Module module) {
    VirtualFile[] sourceRoots = ModuleRootManager.getInstance(module).getSourceRoots();
    if (sourceRoots.length == 0) {
      Messages.showErrorDialog(
        module.getProject(),
        ProjectBundle.message("module.source.roots.not.configured.error", module.getName()),
        ProjectBundle.message("module.source.roots.not.configured.title")
      );

      ProjectSettingsService
        .getInstance(module.getProject()).showModuleConfigurationDialog(module.getName(), CommonContentEntriesEditor.NAME);

      sourceRoots = ModuleRootManager.getInstance(module).getSourceRoots();
      if (sourceRoots.length == 0) {
        return false;
      }
    }
    return true;
  }

  private static String getLeftPart(String packageName) {
    int index = packageName.indexOf('.');
    return index > -1 ? packageName.substring(0, index) : packageName;
  }

  private static String cutLeftPart(String packageName) {
    int index = packageName.indexOf('.');
    return index > -1 ? packageName.substring(index + 1) : "";
  }

  @Nullable
  public static PsiDirectory findPossiblePackageDirectoryInModule(Module module, GlobalSearchScope scope, String packageName) {
    if (!"".equals(packageName)) {
      String rootPackage = findLongestExistingPackage(module.getProject(), packageName, scope);
      if (rootPackage != null) {
        final PsiDirectory[] psiDirectories = getPackageDirectories(module.getProject(), rootPackage, scope);
        if (psiDirectories.length > 0) {
          return psiDirectories[0];
        }
      }
    }

    if (!checkSourceRootsConfigured(module)) return null;

    final VirtualFile[] sourceRoots = ModuleRootManager.getInstance(module).getSourceRoots();
    for (VirtualFile sourceRoot : sourceRoots) {
      final PsiDirectory directory = PsiManager.getInstance(module.getProject()).findDirectory(sourceRoot);
      if (directory != null) {
        return directory;
      }
    }

    return null;
  }

  @Nullable
  public static PsiDirectory getDirectory(@Nullable PsiElement element) {
    if (element == null) return null;
    // handle injection and fragment editor
    PsiFile file = FileContextUtil.getContextFile(element);
    return file == null ? null : file.getContainingDirectory();
  }
}
