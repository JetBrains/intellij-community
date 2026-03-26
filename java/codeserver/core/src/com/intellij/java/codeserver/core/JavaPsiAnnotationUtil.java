// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeserver.core;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.roots.SourceFolder;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.CommonClassNames;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiAnnotationMemberValue;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiEnumConstant;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.PsiPackage;
import com.intellij.psi.PsiPackageStatement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.impl.PsiImplUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Utility methods related to {@link PsiAnnotation}.
 */
public final class JavaPsiAnnotationUtil {
  private JavaPsiAnnotationUtil() { }

  /**
   * @param annotation annotation class
   * @return annotation retention policy; null if cannot be determined
   */
  public static @Nullable RetentionPolicy getRetentionPolicy(@NotNull PsiClass annotation) {
    PsiModifierList modifierList = annotation.getModifierList();
    if (modifierList != null) {
      PsiAnnotation retentionAnno = modifierList.findAnnotation(CommonClassNames.JAVA_LANG_ANNOTATION_RETENTION);
      if (retentionAnno == null) return RetentionPolicy.CLASS;

      PsiAnnotationMemberValue policyRef = PsiImplUtil.findAttributeValue(retentionAnno, null);
      if (policyRef instanceof PsiReference ref) {
        PsiElement field = ref.resolve();
        if (field instanceof PsiEnumConstant constant) {
          String name = constant.getName();
          try {
            return Enum.valueOf(RetentionPolicy.class, name);
          }
          catch (IllegalArgumentException ignored) {
          }
        }
      }
    }

    return null;
  }

  /**
   * Process package annotations related to the specified {@link PsiFile}. 
   * For source files, annotations declared in package-info files from all source roots of the containing
   * module (including generated and test roots) will be processed. For class files (libraries), only the
   * same class root is searched.
   * 
   * @param file context {@link PsiFile} to find and process annotations related to 
   * @param processor a processor function
   * @param processSuperPackages whether to process annotations from super-packages. 
   *                             A super-package is a package whose qualified name is a prefix of a current package name
   *                             (e.g., package "com.example" is a super-package of "com.example.foo")
   */
  public static void processPackageAnnotations(@NotNull PsiFile file,
                                               @NotNull PackageAnnotationProcessor processor,
                                               boolean processSuperPackages) {
    boolean superPackage = false;
    ProjectFileIndex index = ProjectRootManager.getInstance(file.getProject()).getFileIndex();
    VirtualFile vFile = file.getVirtualFile();
    if (vFile == null) return;
    VirtualFile root = index.getSourceRootForFile(vFile);
    boolean compiled = false;
    if (root == null) {
      root = index.getClassRootForFile(vFile);
      if (root == null) return;
      compiled = true;
    }
    // Single-file source root -- no package-info processing for now
    if (root.equals(vFile)) return;

    // For source files, collect additional source roots from the same module with their package prefixes
    String mainPackagePrefix = "";
    List<VirtualFile> additionalRoots = Collections.emptyList();
    List<String> additionalPrefixes = Collections.emptyList();
    if (!compiled) {
      Module module = index.getModuleForFile(vFile);
      if (module != null) {
        additionalRoots = new ArrayList<>();
        additionalPrefixes = new ArrayList<>();
        for (ContentEntry entry : ModuleRootManager.getInstance(module).getContentEntries()) {
          for (SourceFolder folder : entry.getSourceFolders()) {
            VirtualFile folderFile = folder.getFile();
            if (folderFile == null) continue;
            String prefix = folder.getPackagePrefix();
            if (folderFile.equals(root)) {
              mainPackagePrefix = prefix;
            }
            else {
              additionalRoots.add(folderFile);
              additionalPrefixes.add(prefix);
            }
          }
        }
      }
    }
    PsiManager psiManager = file.getManager();

    PsiDirectory directory = file.getContainingDirectory();
    while (directory != null) {
      processDirectoryPackageAnnotations(directory, compiled, processor, superPackage);

      if (!additionalRoots.isEmpty()) {
        String relPath = VfsUtilCore.getRelativePath(directory.getVirtualFile(), root);
        String relPackage = relPath == null || relPath.isEmpty() ? "" : relPath.replace('/', '.');
        String currentPackage = mainPackagePrefix.isEmpty() ? relPackage
            : relPackage.isEmpty() ? mainPackagePrefix
            : mainPackagePrefix + "." + relPackage;
        for (int i = 0; i < additionalRoots.size(); i++) {
          String additionalRelPath = packageToRelativePath(currentPackage, additionalPrefixes.get(i));
          if (additionalRelPath != null) {
            VirtualFile correspondingVDir = additionalRelPath.isEmpty()
                ? additionalRoots.get(i)
                : additionalRoots.get(i).findFileByRelativePath(additionalRelPath);
            if (correspondingVDir != null) {
              PsiDirectory correspondingDir = psiManager.findDirectory(correspondingVDir);
              if (correspondingDir != null) {
                processDirectoryPackageAnnotations(correspondingDir, false, processor, superPackage);
              }
            }
          }
        }
      }

      if (!processSuperPackages || root.equals(directory.getVirtualFile())) break;
      directory = directory.getParentDirectory();
      superPackage = true;
    }
  }

  /**
   * Returns the relative file path (using '/' separator) for {@code packageName} in a source root
   * whose packagePrefix is {@code rootPackagePrefix}, or {@code null} if the package is not under this root.
   * Returns an empty string when the package exactly matches the root's package prefix.
   */
  private static @Nullable String packageToRelativePath(@NotNull String packageName, @NotNull String rootPackagePrefix) {
    if (rootPackagePrefix.isEmpty()) {
      return packageName.replace('.', '/');
    }
    if (packageName.equals(rootPackagePrefix)) {
      return "";
    }
    if (packageName.startsWith(rootPackagePrefix + ".")) {
      return packageName.substring(rootPackagePrefix.length() + 1).replace('.', '/');
    }
    return null;
  }

  private static void processDirectoryPackageAnnotations(@NotNull PsiDirectory directory,
                                                          boolean compiled,
                                                          @NotNull PackageAnnotationProcessor processor,
                                                          boolean superPackage) {
    PsiFile packageFile = directory.findFile(compiled ? PsiPackage.PACKAGE_INFO_CLS_FILE : PsiPackage.PACKAGE_INFO_FILE);
    if (packageFile instanceof PsiJavaFile javaFile) {
      PsiPackageStatement stmt = javaFile.getPackageStatement();
      if (stmt != null) {
        PsiModifierList modifierList = stmt.getAnnotationList();
        if (modifierList != null) {
          processor.processAll(Arrays.asList(modifierList.getAnnotations()), superPackage);
        }
      }
    }
  }

  /**
   * An interface to be used with {@link #processPackageAnnotations(PsiFile, PackageAnnotationProcessor, boolean)}
   */
  @FunctionalInterface
  public interface PackageAnnotationProcessor {
    /**
     * @param annotations applicable package annotations
     * @param superPackage whether the annotations are from a super-package
     */
    void processAll(@NotNull List<@NotNull PsiAnnotation> annotations, boolean superPackage);
  }
}
