// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeserver.core;

import com.intellij.ide.highlighter.ArchiveFileType;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootModificationTracker;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiJavaModuleModificationTracker;
import com.intellij.psi.impl.light.LightJavaModule;
import com.intellij.psi.search.FilenameIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.ProjectScope;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.JavaMultiReleaseUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.java.JavaResourceRootType;
import org.jetbrains.jps.model.java.JavaSourceRootType;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.jar.JarFile;

/**
 * Utilities related to JPMS modules
 */
public final class JavaPsiModuleUtil {
  /**
   * @param element PSI element that belongs to the module
   * @return JPMS module the supplied element belongs to; null if no module definition is found
   */
  @Contract("null->null")
  public static @Nullable PsiJavaModule findDescriptorByElement(@Nullable PsiElement element) {
    if (element == null) return null;
    if (element instanceof PsiJavaModule module) return module;
    if (element.getContainingFile() instanceof PsiJavaFile file) {
      PsiJavaModule module = file.getModuleDeclaration();
      if (module != null) return module;
    }

    if (element instanceof PsiFileSystemItem fsItem) {
      return findDescriptorByFile(fsItem.getVirtualFile(), fsItem.getProject());
    }

    PsiFile file = element.getContainingFile();
    if (file != null) {
      return findDescriptorByFile(file.getVirtualFile(), file.getProject());
    }

    if (element instanceof PsiPackage psiPackage) {
      PsiDirectory[] directories = psiPackage.getDirectories(ProjectScope.getLibrariesScope(psiPackage.getProject()));
      for (PsiDirectory directory : directories) {
        PsiJavaModule descriptor = findDescriptorByFile(directory.getVirtualFile(), directory.getProject());
        if (descriptor != null) return descriptor;
      }
    }
    return null;
  }

  /**
   * @param file virtual file that belongs to a module
   * @param project current project
   * @return JPMS module the supplied file belongs to; null if no module definition is found
   */
  @Contract("null, _->null")
  public static @Nullable PsiJavaModule findDescriptorByFile(@Nullable VirtualFile file, @NotNull Project project) {
    if (file == null) return null;
    ProjectFileIndex index = ProjectFileIndex.getInstance(project);
    return index.isInLibrary(file)
           ? findDescriptorInLibrary(file, project)
           : findDescriptorByModule(index.getModuleForFile(file), index.isInTestSourceContent(file));
  }

  /**
   * @param library library that declares a JPMS module
   * @param project current project
   * @return JPMS module declared by the supplied library; null if not found
   */
  @Contract("null, _ -> null")
  public static @Nullable PsiJavaModule findDescriptorByLibrary(@Nullable Library library, @NotNull Project project) {
    if (library == null) return null;
    final VirtualFile[] files = library.getFiles(OrderRootType.CLASSES);
    if (files.length == 0) return null;
    final PsiJavaModule javaModule = findDescriptorInLibrary(files[0], project);
    return javaModule != null && javaModule.isValid() ? javaModule : null;
  }

  /**
   * @param file library content root
   * @param project current project
   * @return JPMS module declared by the supplied library; null if not found
   */
  public static @Nullable PsiJavaModule findDescriptorInLibrary(@NotNull VirtualFile file, @NotNull Project project) {
    ProjectFileIndex index = ProjectFileIndex.getInstance(project);
    VirtualFile root = index.getClassRootForFile(file);
    if (root != null) {
      VirtualFile descriptorFile =
        JavaMultiReleaseUtil.findVersionSpecificFile(root, PsiJavaModule.MODULE_INFO_CLS_FILE, LanguageLevel.HIGHEST);
      if (descriptorFile != null) {
        PsiFile psiFile = PsiManager.getInstance(project).findFile(descriptorFile);
        if (psiFile instanceof PsiJavaFile) {
          return ((PsiJavaFile)psiFile).getModuleDeclaration();
        }
      }
      else if (root.getFileType() instanceof ArchiveFileType && "jar".equalsIgnoreCase(root.getExtension())) {
        PsiDirectory rootPsi = PsiManager.getInstance(project).findDirectory(root);
        assert rootPsi != null : root;
        return CachedValuesManager.getCachedValue(rootPsi, () -> {
          VirtualFile _root = rootPsi.getVirtualFile();
          LightJavaModule result = LightJavaModule.create(rootPsi.getManager(), _root, LightJavaModule.moduleName(_root));
          return CachedValueProvider.Result.create(result, _root, ProjectRootModificationTracker.getInstance(rootPsi.getProject()));
        });
      }
    }
    else {
      root = index.getSourceRootForFile(file);
      if (root != null) {
        VirtualFile moduleDescriptor = root.findChild(PsiJavaModule.MODULE_INFO_FILE);
        PsiFile psiFile = moduleDescriptor != null ? PsiManager.getInstance(project).findFile(moduleDescriptor) : null;
        if (psiFile instanceof PsiJavaFile) {
          return ((PsiJavaFile)psiFile).getModuleDeclaration();
        }
      }
    }

    return null;
  }

  /**
   * @param module IntelliJ IDEA module
   * @param inTests if true, the search will be performed in test root
   * @return JPMS module that corresponds to a given IntelliJ IDEA module; null if not found
   */
  @Contract("null,_->null")
  public static @Nullable PsiJavaModule findDescriptorByModule(@Nullable Module module, boolean inTests) {
    if (module == null) return null;
    CachedValuesManager valuesManager = CachedValuesManager.getManager(module.getProject());
    PsiJavaModule javaModule = inTests //to have different providers for production and tests
                               ? valuesManager.getCachedValue(module, () -> createModuleCacheResult(module, true))
                               : valuesManager.getCachedValue(module, () -> createModuleCacheResult(module, false));
    return javaModule != null && javaModule.isValid() ? javaModule : null;
  }

  private static @NotNull CachedValueProvider.Result<PsiJavaModule> createModuleCacheResult(@NotNull Module module,
                                                                                            boolean inTests) {
    Project project = module.getProject();
    return new CachedValueProvider.Result<>(findDescriptionByModuleInner(module, inTests),
                                            ProjectRootModificationTracker.getInstance(project),
                                            PsiJavaModuleModificationTracker.getInstance(project));
  }

  private static @Nullable PsiJavaModule findDescriptionByModuleInner(@NotNull Module module, boolean inTests) {
    Project project = module.getProject();
    GlobalSearchScope moduleScope = module.getModuleScope();
    String virtualAutoModuleName = JavaManifestUtil.getManifestAttributeValue(module, PsiJavaModule.AUTO_MODULE_NAME);
    if (!DumbService.isDumb(project) &&
        FilenameIndex.getVirtualFilesByName(PsiJavaModule.MODULE_INFO_FILE, moduleScope).isEmpty() &&
        FilenameIndex.getVirtualFilesByName("MANIFEST.MF", moduleScope).isEmpty() &&
        virtualAutoModuleName == null) {
      return null;
    }
    JavaSourceRootType rootType = inTests ? JavaSourceRootType.TEST_SOURCE : JavaSourceRootType.SOURCE;
    ModuleRootManager rootManager = ModuleRootManager.getInstance(module);
    List<VirtualFile> sourceRoots = rootManager.getSourceRoots(rootType);
    Set<VirtualFile> excludeRoots = ContainerUtil.newHashSet(ModuleRootManager.getInstance(module).getExcludeRoots());
    if (!excludeRoots.isEmpty()) sourceRoots.removeIf(root -> excludeRoots.contains(root));

    List<VirtualFile> files = ContainerUtil.mapNotNull(sourceRoots, root -> root.findChild(PsiJavaModule.MODULE_INFO_FILE));
    if (files.isEmpty()) {
      JavaResourceRootType resourceRootType = inTests ? JavaResourceRootType.TEST_RESOURCE : JavaResourceRootType.RESOURCE;
      List<VirtualFile> roots = new ArrayList<>(rootManager.getSourceRoots(resourceRootType));
      roots.addAll(sourceRoots);
      files = ContainerUtil.mapNotNull(roots, root -> root.findFileByRelativePath(JarFile.MANIFEST_NAME));
      if (files.size() == 1 || new HashSet<>(files).size() == 1) {
        VirtualFile manifest = files.get(0);
        PsiFile manifestPsi = PsiManager.getInstance(project).findFile(manifest);
        assert manifestPsi != null : manifest;
        return CachedValuesManager.getCachedValue(manifestPsi, () -> {
          String name = LightJavaModule.claimedModuleName(manifest);
          LightJavaModule result =
            name != null ? LightJavaModule.create(PsiManager.getInstance(project), manifest.getParent().getParent(), name) : null;
          return CachedValueProvider.Result.create(result, manifestPsi, ProjectRootModificationTracker.getInstance(project));
        });
      }
      List<VirtualFile> sourceSourceRoots = rootManager.getSourceRoots(JavaSourceRootType.SOURCE);
      if (virtualAutoModuleName != null && !sourceSourceRoots.isEmpty()) {
        return LightJavaModule.create(PsiManager.getInstance(project), sourceSourceRoots.get(0), virtualAutoModuleName);
      }
    }
    else {
      final VirtualFile file = files.get(0);
      if (ContainerUtil.and(files, f -> f.equals(file))) {
        PsiFile psiFile = PsiManager.getInstance(project).findFile(file);
        if (psiFile instanceof PsiJavaFile) {
          return ((PsiJavaFile)psiFile).getModuleDeclaration();
        }
      }
    }

    return null;
  }
}
