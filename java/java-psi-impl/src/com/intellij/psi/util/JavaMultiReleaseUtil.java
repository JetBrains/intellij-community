// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.util;

import com.intellij.ide.highlighter.ArchiveFileType;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.PsiFile;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.regex.Pattern;

/**
 * Utility methods to support Multi-Release JARs (MR-JARs, <a href="https://openjdk.org/jeps/238">JEP 238</a>)
 */
public final class JavaMultiReleaseUtil {

  /**
   * Maximal JDK version which does not support multi-release Jars
   */
  public static final LanguageLevel MAX_NON_MULTI_RELEASE_VERSION = LanguageLevel.JDK_1_8;
  /**
   * Minimal JDK version which supports multi-release Jars
   */
  public static final LanguageLevel MIN_MULTI_RELEASE_VERSION = LanguageLevel.JDK_1_9;
  private static final String MAIN = "main";
  private static final Pattern javaVersionPattern = Pattern.compile("java\\d+");

  /**
   * @param mainModule main module candidate (where common code for different releases resides)
   * @param additionalModule additional module candidate (where release-specific code resides)
   * @return true if the supplied modules are indeed main module and additional module
   */
  @Contract("null, _ -> false; !null, null -> false")
  @ApiStatus.Internal
  public static boolean areMainAndAdditionalMultiReleaseModules(@Nullable Module mainModule, @Nullable Module additionalModule) {
    if (mainModule == null || additionalModule == null) return false;
    if (getMainMultiReleaseModule(additionalModule) == mainModule) {
      return true;
    }

    // Fallback: Gradle and JPS
    String mainModuleName = mainModule.getName();
    if (mainModuleName.endsWith("." + MAIN)) {
      String baseModuleName = StringUtil.substringBeforeLast(mainModuleName, MAIN);
      String moduleName = additionalModule.getName();
      return javaVersionPattern.matcher(ObjectUtils.coalesce(StringUtil.substringAfter(moduleName, baseModuleName), moduleName)).matches();
    }
    return false;
  }

  /**
   * @param additionalModule additional module (where release-specific code resides)
   * @return main module (where common code for different releases resides); null if the supplied module is not recognized as
   * an additional module
   */
  @ApiStatus.Internal
  public static @Nullable Module getMainMultiReleaseModule(@NotNull Module additionalModule) {
    for (JavaMultiReleaseModuleSupport support : JavaMultiReleaseModuleSupport.EP_NAME.getExtensionList()) {
      Module result = support.getMainMultiReleaseModule(additionalModule);
      if (result != null) {
        return result;
      }
    }
    return null;
  }

  private static class VersionRootInfo {
    final @NotNull LanguageLevel level;
    final @NotNull VirtualFile versionRoot;
    final @NotNull VirtualFile classRoot;

    private VersionRootInfo(@NotNull LanguageLevel level, @NotNull VirtualFile versionRoot, @NotNull VirtualFile classRoot) {
      this.level = level;
      this.versionRoot = versionRoot;
      this.classRoot = classRoot;
    }
  }

  private static @Nullable VersionRootInfo getVersionRootInfo(@NotNull VirtualFile file) {
    VirtualFile root = VfsUtilCore.getRootFile(file);
    if (!(root.getFileType() instanceof ArchiveFileType)) return null;

    VirtualFile parent = file.getParent();
    while (parent != null && !parent.equals(root)) {
      LanguageLevel level = getVersionForVersionRoot(root, parent);
      if (level != null) {
        return new VersionRootInfo(level, parent, root);
      }
      parent = parent.getParent();
    }
    return null;
  }
  
  /**
   * Return {@link LanguageLevel} that corresponds to the specified version root directory, relative to the specified root.
   * Version root directory is something like {@code META_INF/versions/9}
   * @param root root directory (the root of the JAR file)
   * @param directory version root directory
   * @return the {@link LanguageLevel} that corresponds to the specified version root directory. E.g., {@link LanguageLevel#JDK_1_9}
   * for {@code META_INF/versions/9}. Returns null if either root or directory is null, or the specified directory is not a
   * version root.
   */
  @Contract("null, _ -> null; !null, null -> null")
  public static @Nullable LanguageLevel getVersionForVersionRoot(@Nullable VirtualFile root, @Nullable VirtualFile directory) {
    if (root == null || directory == null) return null;
    VirtualFile parent = directory.getParent();
    if (parent == null) return null;
    if (parent.getName().equals("versions")) {
      VirtualFile grandParent = parent.getParent();
      if (grandParent != null && grandParent.getName().equals("META-INF") && root.equals(grandParent.getParent())) {
        try {
          LanguageLevel level = LanguageLevel.forFeature(Integer.parseInt(directory.getName()));
          return level != null && level.isAtLeast(MIN_MULTI_RELEASE_VERSION) ? level : null;
        }
        catch (NumberFormatException ignored) {
        }
      }
    }
    return null;
  }

  /**
   * @param file virtual file that represents a version-specific file from an MR-JAR
   * @return a file representing the same class or resource from the JAR root; null if there's no corresponding resource in the JAR root,
   * or if the supplied file is not a version-specific file from an MR-JAR
   */
  public static @Nullable VirtualFile findBaseFile(@NotNull VirtualFile file) {
    VersionRootInfo info = getVersionRootInfo(file);
    if (info == null) return null;
    VirtualFile versionRoot = info.versionRoot;
    VirtualFile classRoot = info.classRoot;
    String relativePath = VfsUtilCore.getRelativePath(file, versionRoot);
    if (relativePath == null) return null;
    return classRoot.findFileByRelativePath(relativePath);
  }

  /**
   * @param file file that represents a non-version-specific file from an MR-JAR
   * @param level desired language level
   * @return a version of the file, which should be loaded on the specified language level. Returns
   * the input file if there's no more-specific version of the file, or the input file is not located inside MR-JAR.
   */
  public static @NotNull VirtualFile findVersionSpecificFile(@NotNull VirtualFile file, @NotNull LanguageLevel level) {
    VirtualFile root = VfsUtilCore.getRootFile(file);
    if (!(root.getFileType() instanceof ArchiveFileType)) return file;
    String relativePath = VfsUtilCore.getRelativePath(file, root);
    if (relativePath == null) return file;
    return findFileImpl(file, level, root, relativePath);
  }

  /**
   * @param root content root
   * @param relativePath path to the file relative to content root
   * @param level desired language level
   * @return a version of the file, which should be loaded on the specified language level. Returns null if the file is not found.
   */
  public static @Nullable VirtualFile findVersionSpecificFile(@NotNull VirtualFile root,
                                                              @NotNull String relativePath,
                                                              @NotNull LanguageLevel level) {
    VirtualFile file = root.findFileByRelativePath(relativePath);
    if (!(root.getFileType() instanceof ArchiveFileType)) return file;
    return findFileImpl(file, level, root, relativePath);
  }

  @Contract("!null, _, _, _ -> !null")
  private static @Nullable VirtualFile findFileImpl(@Nullable VirtualFile defaultFile,
                                                    @NotNull LanguageLevel level,
                                                    @NotNull VirtualFile root,
                                                    @NotNull String relativePath) {
    VirtualFile metaInf = root.findChild("META-INF");
    if (metaInf == null) return defaultFile;
    VirtualFile versions = metaInf.findChild("versions");
    if (versions == null) return defaultFile;
    int feature = level.feature();
    int minFeature = MIN_MULTI_RELEASE_VERSION.feature();
    while (feature >= minFeature) {
      VirtualFile versionRoot = versions.findChild(String.valueOf(feature));
      if (versionRoot != null) {
        VirtualFile target = versionRoot.findFileByRelativePath(relativePath);
        if (target != null) {
          return target;
        }
      }
      feature--;
    }
    return defaultFile;
  }

  /**
   * @param file PsiFile that represents a version-specific file from library 
   * @return a version that corresponds for a specified file (e.g. {@link LanguageLevel#JDK_1_9} for a file from {@code META-INF/versions/9});
   * null if the given file is not a version-specific file from a library, or a corresponding version is not supported by current IDE.
   */
  public static @Nullable LanguageLevel getVersion(@NotNull PsiFile file) {
    VirtualFile virtualFile = file.getVirtualFile();
    return virtualFile == null ? null : getVersion(virtualFile);
  }

  /**
   * @param file VirtualFile that represents a version-specific file from MR-JAR 
   * @return a version that corresponds for a specified file (e.g. {@link LanguageLevel#JDK_1_9} for a file from {@code META-INF/versions/9});
   * null if the given file is not a version-specific file from a library, or a corresponding version is not supported by current IDE.
   */
  public static @Nullable LanguageLevel getVersion(@NotNull VirtualFile file) {
    VersionRootInfo version = getVersionRootInfo(file);
    return version == null ? null : version.level;
  }
}
