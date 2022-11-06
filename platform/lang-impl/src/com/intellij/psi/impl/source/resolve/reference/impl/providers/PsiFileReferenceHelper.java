// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.source.resolve.reference.impl.providers;

import com.intellij.codeInsight.daemon.quickFix.FileReferenceQuickFixProvider;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.model.ModelBranch;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.roots.impl.DirectoryIndex;
import com.intellij.openapi.util.Predicates;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiFileSystemItem;
import com.intellij.psi.PsiManager;
import com.intellij.psi.impl.SyntheticFileSystemItem;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.ProjectScope;
import com.intellij.psi.search.PsiElementProcessor;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Query;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.java.JavaModuleSourceRootTypes;

import java.util.*;
import java.util.stream.Collectors;

import static com.intellij.psi.impl.source.resolve.reference.impl.providers.JpsFileTargetContextUtils.prepareTargetContexts;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;

public class PsiFileReferenceHelper extends FileReferenceHelper {
  @NotNull
  public static PsiFileReferenceHelper getInstance() {
    return Objects.requireNonNull(FileReferenceHelper.EP_NAME.findExtension(PsiFileReferenceHelper.class));
  }

  @NotNull
  @Override
  public List<? extends LocalQuickFix> registerFixes(@NotNull FileReference reference) {
    return FileReferenceQuickFixProvider.registerQuickFix(reference);
  }

  @Override
  public PsiFileSystemItem findRoot(final @NotNull Project project, @NotNull final VirtualFile file) {
    final ProjectFileIndex index = ProjectRootManager.getInstance(project).getFileIndex();
    VirtualFile contentRootForFile = index.getSourceRootForFile(file);
    if (contentRootForFile == null) contentRootForFile = index.getContentRootForFile(file);

    if (contentRootForFile != null) {
      return PsiManager.getInstance(project).findDirectory(contentRootForFile);
    }
    return null;
  }

  @Override
  @NotNull
  public Collection<PsiFileSystemItem> getRoots(@NotNull final Module module) {
    return getContextsForModule(module, "", module.getModuleWithDependenciesScope());
  }

  @NotNull
  @Override
  public Collection<FileTargetContext> getTargetContexts(@NotNull Project project, @NotNull VirtualFile file, boolean isAbsoluteReference) {
    List<PsiFileSystemItem> contexts;
    if (isAbsoluteReference) {
      ProjectFileIndex index = ProjectRootManager.getInstance(project).getFileIndex();
      Module module = index.getModuleForFile(file);
      if (module == null) return emptyList();

      contexts = getContextsForModule(module, "", module.getModuleWithDependenciesScope());
    }
    else {
      contexts = getContexts(project, file, true);
    }

    List<FileTargetContext> fileTargetContexts = new ArrayList<>();
    for (PsiFileSystemItem context : contexts) {
      if (context instanceof VirtualPsiDirectory) {
        VirtualPsiDirectory virtual = (VirtualPsiDirectory)context;
        fileTargetContexts.add(new FileTargetContext(virtual.getRoot(), virtual.getPathToCreate()));
      }
      else {
        fileTargetContexts.add(new FileTargetContext(context));
      }
    }
    return prepareTargetContexts(project, file, fileTargetContexts);
  }

  private static String @NotNull [] removeCommonStartPackages(String @NotNull [] path, @NotNull String packagePath) {
    List<String> packages = StringUtil.split(packagePath, ".");
    List<String> result = new SmartList<>();

    int i = 0;
    while (i < packages.size() && i < path.length) {
      String part = path[i];
      String existingPackage = packages.get(i);

      if (!Objects.equals(part, existingPackage)) {
        break;
      }

      i++;
    }

    while (i < path.length) {
      result.add(path[i]);
      i++;
    }

    return ArrayUtil.toStringArray(result);
  }

  private static String @NotNull [] getRelativePath(@NotNull VirtualFile file, @NotNull VirtualFile root) {
    List<String> names = new ArrayList<>();

    VirtualFile parent = file;

    while (parent != null
           && !parent.equals(root)) {
      names.add(parent.getName());
      parent = parent.getParent();
    }

    if (names.isEmpty()) return ArrayUtil.EMPTY_STRING_ARRAY;
    String[] path = new String[names.size()];
    for (int i = 0; i < names.size(); i++) {
      path[i] = names.get(names.size() - i - 1);
    }
    return path;
  }

  @NotNull
  private static List<SourceFolder> getMissingTargetFolders(@NotNull Module module, @NotNull Collection<? extends PsiFileSystemItem> contextsForModule) {
    // find additional source folders that can be used to create a file, e.g. they do not have the exact package, but it can be created
    ModuleRootModel model = ModuleRootManager.getInstance(module);

    return Arrays.stream(model.getContentEntries())
      .flatMap(contentEntry -> Arrays.stream(contentEntry.getSourceFolders()))
      .filter(sourceFolder -> {
        if (sourceFolder.getFile() == null) return false;

        for (PsiFileSystemItem contextItem : contextsForModule) {
          if (VfsUtilCore.isAncestor(sourceFolder.getFile(), contextItem.getVirtualFile(), false)) {
            return false;
          }
        }

        return true;
      })
      .collect(Collectors.toCollection(SmartList::new));
  }

  @NotNull
  private List<PsiFileSystemItem> getContexts(@NotNull Project project, @NotNull VirtualFile file, boolean includeMissingPackages) {
    PsiFileSystemItem item = getPsiFileSystemItem(project, file);
    if (item != null) {
      PsiFileSystemItem parent = item.getParent();
      if (parent != null) {
        ProjectFileIndex index = ProjectRootManager.getInstance(project).getFileIndex();
        VirtualFile parentFile = parent.getVirtualFile();
        assert parentFile != null;

        VirtualFile root = index.getSourceRootForFile(parentFile);
        if (root != null) {
          String path = VfsUtilCore.getRelativePath(parentFile, root, '.');

          if (path != null) {
            Module module = index.getModuleForFile(file);

            if (module != null) {
              OrderEntry orderEntry = ModuleRootManager.getInstance(module).getFileIndex().getOrderEntryForFile(file);

              String rootPackagePrefix = getSourceRootPackagePrefix(orderEntry, root);
              if (!rootPackagePrefix.isEmpty()) {
                path += "." + rootPackagePrefix;
              }

              List<PsiFileSystemItem> contextsForModule =
                ModelBranch.getPsiBranch(item) != null ?
                getContextsForScope(project, path, item.getResolveScope().intersectWith(ProjectScope.getContentScope(project))) :
                getContextsForModule(module, path, module.getModuleWithDependenciesScope());
              if (!includeMissingPackages) {
                return contextsForModule;
              }

              return getAdditionalContexts(parentFile, root, module, contextsForModule);
            }
          }
        }
        return singletonList(parent);
      }
    }
    return emptyList();
  }

  @NotNull
  private static List<PsiFileSystemItem> getAdditionalContexts(@NotNull VirtualFile parentFile,
                                                               @NotNull VirtualFile root,
                                                               @NotNull Module module,
                                                               @NotNull List<PsiFileSystemItem> contextsForModule) {
    // here we try to find source roots that are not present in contextsForModule, but could be used to create a package and file

    List<SourceFolder> additionalSourceFolders = getMissingTargetFolders(module, contextsForModule);
    if (additionalSourceFolders.isEmpty()) {
      return contextsForModule;
    }

    List<PsiFileSystemItem> joinedContexts = new ArrayList<>(contextsForModule.size() + additionalSourceFolders.size());
    joinedContexts.addAll(contextsForModule);

    PsiManager manager = PsiManager.getInstance(module.getProject());

    String[] relativePath = getRelativePath(parentFile, root);
    for (SourceFolder sourceFolder : additionalSourceFolders) {
      if (sourceFolder.getFile() == null) continue;

      PsiDirectory directory = manager.findDirectory(sourceFolder.getFile());
      if (directory == null) continue;

      String srcPackagePrefix = sourceFolder.getPackagePrefix();

      String[] pathToCreate;
      if (srcPackagePrefix.isEmpty()) {
        pathToCreate = relativePath;
      }
      else {
        pathToCreate = removeCommonStartPackages(relativePath, srcPackagePrefix);
      }

      joinedContexts.add(new VirtualPsiDirectory(directory, pathToCreate));
    }

    return joinedContexts;
  }

  @Override
  @NotNull
  public Collection<PsiFileSystemItem> getContexts(@NotNull Project project, @NotNull VirtualFile file) {
    return getContexts(project, file, false);
  }

  @NotNull
  private static String getSourceRootPackagePrefix(@Nullable OrderEntry orderEntry, @NotNull VirtualFile sourceRootOfFile) {
    if (orderEntry instanceof ModuleSourceOrderEntry) {
      for (ContentEntry e : ((ModuleSourceOrderEntry)orderEntry).getRootModel().getContentEntries()) {
        for (SourceFolder sf : e.getSourceFolders(JavaModuleSourceRootTypes.SOURCES)) {
          if (sourceRootOfFile.equals(sf.getFile())) {
            String s = sf.getPackagePrefix();
            if (!s.isEmpty()) {
              return s;
            }
          }
        }
      }
    }
    return "";
  }

  @Override
  public boolean isMine(final @NotNull Project project, @NotNull final VirtualFile file) {
    final ProjectFileIndex index = ProjectRootManager.getInstance(project).getFileIndex();
    return index.isInSourceContent(file);
  }

  @Override
  @NotNull
  public String trimUrl(@NotNull String url) {
    return url.trim();
  }

  static List<PsiFileSystemItem> getContextsForModule(@NotNull Module module,
                                                      @NotNull String packageName,
                                                      @Nullable GlobalSearchScope scope) {
    Query<VirtualFile> query = DirectoryIndex.getInstance(module.getProject()).getDirectoriesByPackageName(packageName, false);

    return StreamEx.of(query.findAll()).filter(scope == null ? Predicates.alwaysTrue() : file -> scope.contains(file))
      .<PsiFileSystemItem>map(PsiManager.getInstance(module.getProject())::findDirectory)
      .nonNull()
      .toList();
  }

  @NotNull
  static List<PsiFileSystemItem> getContextsForScope(@NotNull Project project,
                                                     @NotNull String packageName,
                                                     @NotNull GlobalSearchScope scope) {
    DirectoryIndex dirIndex = DirectoryIndex.getInstance(project);
    Query<VirtualFile> query = dirIndex.getDirectoriesByPackageName(packageName, scope);
    Collection<VirtualFile> files = ContainerUtil.reverse(ContainerUtil.sorted(query.findAll(), scope::compare));
    return ContainerUtil.mapNotNull(files, PsiManager.getInstance(project)::findDirectory);
  }

  private static final class VirtualPsiDirectory extends SyntheticFileSystemItem {
    private final PsiDirectory myRoot;
    private final String[] myPathToCreate;

    private VirtualPsiDirectory(@NotNull PsiDirectory root, String @NotNull [] pathToCreate) {
      super(root.getProject());
      myRoot = root;
      myPathToCreate = pathToCreate;
    }

    @Nullable
    @Override
    public PsiFileSystemItem getParent() {
      return myRoot;
    }

    @Override
    public VirtualFile getVirtualFile() {
      return null;
    }

    private PsiDirectory getRoot() {
      return myRoot;
    }

    private String @NotNull [] getPathToCreate() {
      return myPathToCreate;
    }

    @Override
    public boolean isPhysical() {
      return false;
    }

    @Override
    public boolean isWritable() {
      return false;
    }

    @NotNull
    @Override
    public String getName() {
      return myPathToCreate.length == 0 ? myRoot.getName() : myPathToCreate[myPathToCreate.length - 1];
    }

    @Override
    public boolean processChildren(@NotNull PsiElementProcessor<? super PsiFileSystemItem> processor) {
      return false;
    }
  }
}
