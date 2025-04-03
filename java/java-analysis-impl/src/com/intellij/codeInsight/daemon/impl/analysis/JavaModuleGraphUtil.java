// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.analysis;

import com.intellij.ide.highlighter.JavaClassFileType;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.java.codeserver.core.JavaPsiModuleUtil;
import com.intellij.java.syntax.parser.JavaKeywords;
import com.intellij.lang.Language;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.lang.jvm.JvmLanguage;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeRegistry;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.java.JavaFeature;
import com.intellij.psi.*;
import com.intellij.psi.impl.light.LightJavaModule;
import com.intellij.psi.impl.source.resolve.JavaResolveUtil;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

import static com.intellij.openapi.roots.DependencyScope.PROVIDED;
import static com.intellij.psi.PsiJavaModule.JAVA_BASE;

public final class JavaModuleGraphUtil {
  private static final Set<String> STATIC_REQUIRES_MODULE_NAMES = Set.of("lombok");

  private JavaModuleGraphUtil() { }

  @Contract("null->null")
  public static @Nullable PsiJavaModule findDescriptorByElement(@Nullable PsiElement element) {
    return JavaPsiModuleUtil.findDescriptorByElement(element);
  }

  @Contract("null,_->null")
  public static @Nullable PsiJavaModule findDescriptorByFile(@Nullable VirtualFile file, @NotNull Project project) {
    return JavaPsiModuleUtil.findDescriptorByFile(file, project);
  }

  @Contract("null,_->null")
  public static @Nullable PsiJavaModule findDescriptorByModule(@Nullable Module module, boolean inTests) {
    return JavaPsiModuleUtil.findDescriptorByModule(module, inTests);
  }

  public static @Nullable PsiJavaModule findDescriptorByLibrary(@Nullable Library library, @NotNull Project project) {
    return JavaPsiModuleUtil.findDescriptorByLibrary(library, project);
  }

  public static @Nullable PsiJavaModule findNonAutomaticDescriptorByModule(@Nullable Module module, boolean inTests) {
    PsiJavaModule javaModule = findDescriptorByModule(module, inTests);
    return javaModule instanceof LightJavaModule ? null : javaModule;
  }

  /**
   * Determines if a specified module is readable from a given context
   *
   * @param place            current module/position
   * @param targetModuleFile file from the target module
   * @return {@code true} if the target module is readable from the place; {@code false} otherwise.
   */
  public static boolean isModuleReadable(@NotNull PsiElement place,
                                         @NotNull VirtualFile targetModuleFile) {
    PsiJavaModule targetModule = findDescriptorByFile(targetModuleFile, place.getProject());
    if (targetModule == null) return true;
    return isModuleReadable(place, targetModule);
  }

  /**
   * Determines if the specified modules are readable from a given context.
   *
   * @param place        the current position or element from where readability is being checked
   * @param targetModule the target module to check readability against
   * @return {@code true} if any of the target modules are readable from the current context; {@code false} otherwise
   */
  public static boolean isModuleReadable(@NotNull PsiElement place,
                                         @NotNull PsiJavaModule targetModule) {
    return JavaModuleGraphHelper.getInstance().isAccessible(targetModule, place);
  }

  public static boolean addDependency(@NotNull PsiJavaModule from,
                                      @NotNull String to,
                                      @Nullable DependencyScope scope,
                                      boolean isExported) {
    if (to.equals(JAVA_BASE)) return false;
    if (!PsiUtil.isAvailable(JavaFeature.MODULES, from)) return false;
    if (from instanceof LightJavaModule) return false;
    if (to.equals(from.getName())) return false;
    if (!PsiNameHelper.isValidModuleName(to, from)) return false;
    if (alreadyContainsRequires(from, to)) return false;
    PsiUtil.addModuleStatement(from, JavaKeywords.REQUIRES + " " +
                                     (isStaticModule(to, scope) ? JavaKeywords.STATIC + " " : "") +
                                     (isExported ? JavaKeywords.TRANSITIVE + " " : "") +
                                     to);
    return true;
  }

  public static boolean addDependency(@NotNull PsiElement from,
                                      @NotNull PsiClass to,
                                      @Nullable DependencyScope scope) {
    if (!PsiUtil.isAvailable(JavaFeature.MODULES, from)) return false;
    PsiJavaModule fromDescriptor = findDescriptorByElement(from);
    if (fromDescriptor == null) return false;
    PsiJavaModule toDescriptor = findDescriptorByElement(to);
    if (toDescriptor == null) return false;
    if (!JavaModuleGraphHelper.getInstance().isAccessible(to, from)) return false;
    return addDependency(fromDescriptor, toDescriptor, scope);
  }

  public static boolean addDependency(@NotNull PsiJavaModule from,
                                      @NotNull PsiJavaModule to,
                                      @Nullable DependencyScope scope) {
    if (to.getName().equals(JAVA_BASE)) return false;
    if (!PsiUtil.isAvailable(JavaFeature.MODULES, from)) return false;
    if (from instanceof LightJavaModule) return false;
    if (from == to) return false;
    if (!PsiNameHelper.isValidModuleName(to.getName(), to)) return false;
    if (contains(from.getRequires(), to.getName())) return false;
    if (JavaPsiModuleUtil.reads(from, to)) return false;
    PsiUtil.addModuleStatement(from, JavaKeywords.REQUIRES + " " +
                                     (isStaticModule(to.getName(), scope) ? JavaKeywords.STATIC + " " : "") +
                                     (isExported(from, to) ? JavaKeywords.TRANSITIVE + " " : "") +
                                     to.getName());
    return true;
  }

  private static boolean contains(@NotNull Iterable<PsiRequiresStatement> requires, @NotNull String name) {
    for (PsiRequiresStatement statement : requires) {
      if (name.equals(statement.getModuleName())) return true;
    }
    return false;
  }

  private static boolean isExported(@NotNull PsiJavaModule from, @NotNull PsiJavaModule to) {
    VirtualFile toFile = getVirtualFile(to);
    if (toFile == null) return false;

    Module fromModule = ModuleUtilCore.findModuleForPsiElement(from);
    if (fromModule == null) return false;

    Set<OrderEntry> toEntries = new HashSet<>(ProjectFileIndex.getInstance(from.getProject())
                                                .getOrderEntriesForFile(toFile));
    if (toEntries.isEmpty()) return false;

    OrderEntry[] entries = ModuleRootManager.getInstance(fromModule).getOrderEntries();
    for (OrderEntry entry : entries) {
      if (toEntries.contains(entry) && entry instanceof ExportableOrderEntry exportable) {
        return exportable.isExported();
      }
    }
    return false;
  }

  private static @Nullable VirtualFile getVirtualFile(@NotNull PsiJavaModule module) {
    if (module instanceof LightJavaModule light) {
      return light.getRootVirtualFile();
    }
    return PsiUtilCore.getVirtualFile(module);
  }

  private static boolean alreadyContainsRequires(@NotNull PsiJavaModule module, @NotNull String dependency) {
    for (PsiRequiresStatement requiresStatement : module.getRequires()) {
      if (Objects.equals(requiresStatement.getModuleName(), dependency)) {
        return true;
      }
    }
    return false;
  }

  private static boolean isStaticModule(@NotNull String moduleName, @Nullable DependencyScope scope) {
    if (STATIC_REQUIRES_MODULE_NAMES.contains(moduleName)) return true;
    return scope == PROVIDED;
  }

  public static class JavaModuleScope extends GlobalSearchScope {
    private final @NotNull MultiMap<String, PsiJavaModule> myModules;
    private final boolean myIncludeLibraries;
    private final boolean myIsInTests;

    private JavaModuleScope(@NotNull Project project, @NotNull Set<PsiJavaModule> modules) {
      super(project);
      myModules = new MultiMap<>();
      for (PsiJavaModule module : modules) {
        myModules.putValue(module.getName(), module);
      }
      ProjectFileIndex fileIndex = ProjectFileIndex.getInstance(project);
      myIncludeLibraries = ContainerUtil.or(modules, m -> {
        PsiFile containingFile = m.getContainingFile();
        if (containingFile == null) return true;
        VirtualFile moduleFile = containingFile.getVirtualFile();
        if (moduleFile == null) return true;
        return fileIndex.isInLibrary(moduleFile);
      });
      myIsInTests = !myIncludeLibraries && ContainerUtil.or(modules, m -> {
        PsiFile containingFile = m.getContainingFile();
        if (containingFile == null) return true;
        VirtualFile moduleFile = containingFile.getVirtualFile();
        if (moduleFile == null) return true;
        return fileIndex.isInTestSourceContent(moduleFile);
      });
    }

    @Override
    public boolean isSearchInModuleContent(@NotNull Module aModule) {
      return contains(findDescriptorByModule(aModule, myIsInTests));
    }

    @Override
    public boolean isSearchInLibraries() {
      return myIncludeLibraries;
    }

    @Override
    public boolean contains(@NotNull VirtualFile file) {
      Project project = getProject();
      if (project == null) return false;
      if (!isJvmLanguageFile(file)) return false;
      ProjectFileIndex index = ProjectFileIndex.getInstance(project);
      if (index.isInLibrary(file)) return myIncludeLibraries && contains(JavaPsiModuleUtil.findDescriptorInLibrary(file, project));
      Module module = index.getModuleForFile(file);
      return contains(findDescriptorByModule(module, myIsInTests));
    }

    private boolean contains(@Nullable PsiJavaModule module) {
      if (module == null || !module.isValid()) return false;
      Collection<PsiJavaModule> myCollectedModules = myModules.get(module.getName());
      return myCollectedModules.contains(module);
    }

    private static boolean isJvmLanguageFile(@NotNull VirtualFile file) {
      FileTypeRegistry fileTypeRegistry = FileTypeRegistry.getInstance();
      FileType fileType = fileTypeRegistry.getFileTypeByFileName(file.getName());
      if (fileType == JavaClassFileType.INSTANCE ||
          fileType == JavaFileType.INSTANCE) {
        return true;
      }
      LanguageFileType languageFileType = ObjectUtils.tryCast(fileType, LanguageFileType.class);
      if(languageFileType == null) return false;
      Language language = languageFileType.getLanguage();
      return language.isKindOf(JavaLanguage.INSTANCE) ||
             language instanceof JvmLanguage ||
             language.getID().equals("kotlin");
    }

    public static @Nullable JavaModuleScope moduleScope(@NotNull PsiJavaModule module) {
      PsiFile moduleFile = module.getContainingFile();
      if (moduleFile == null) return null;
      VirtualFile virtualFile = moduleFile.getVirtualFile();
      if (virtualFile == null) return null;
      return new JavaModuleScope(module.getProject(), Set.of(module));
    }

    /**
     * Creates a JavaModuleScope that includes the given module and all transitive modules.
     *
     * @param module the base PsiJavaModule for which to create the scope, must not be null
     * @return a new JavaModuleScope including all transitive modules of the given module, or null if the moduleFile is null or no transitive modules are found
     */
    public static @Nullable JavaModuleScope moduleWithTransitiveScope(@NotNull PsiJavaModule module) {
      Set<PsiJavaModule> allModules = JavaResolveUtil.getAllTransitiveModulesIncludeCurrent(module);
      if (allModules.isEmpty()) return null;
      return new JavaModuleScope(module.getProject(), allModules);
    }
  }
}
