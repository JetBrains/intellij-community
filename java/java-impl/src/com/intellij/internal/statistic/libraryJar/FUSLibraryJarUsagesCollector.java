// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.libraryJar;

import com.intellij.internal.statistic.beans.UsageDescriptor;
import com.intellij.internal.statistic.service.fus.collectors.ProjectUsagesCollector;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.JarUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.search.ProjectScope;
import com.intellij.util.containers.hash.HashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;
import java.util.jar.Attributes;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author Andrey Cheptsov
 */
public class FUSLibraryJarUsagesCollector extends ProjectUsagesCollector {
  private static final String GROUP = "statistics.javaLibraries";

  private static final String DIGIT_VERSION_PATTERN_PART = "(\\d+.\\d+|\\d+)";
  private static final Pattern JAR_FILE_NAME_PATTERN = Pattern.compile("[\\w|\\-|\\.]+-(" + DIGIT_VERSION_PATTERN_PART + "[\\w|\\.]*)jar");

  @NotNull
  @Override
  public Set<UsageDescriptor> getUsages(@NotNull Project project) {
    LibraryJarDescriptor[] descriptors = LibraryJarStatisticsService.getInstance().getTechnologyDescriptors();
    Set<UsageDescriptor> result = new HashSet<>(descriptors.length);

    ApplicationManager.getApplication().runReadAction(() -> {
      for (LibraryJarDescriptor descriptor : descriptors) {
        String className = descriptor.myClass;
        if (className == null) continue;

        PsiClass[] psiClasses = JavaPsiFacade.getInstance(project).findClasses(className, ProjectScope.getLibrariesScope(project));
        for (PsiClass psiClass : psiClasses) {
          VirtualFile jarFile = JarFileSystem.getInstance().getVirtualFileForJar(psiClass.getContainingFile().getVirtualFile());
          if (jarFile != null) {
            String version = getVersionByJarManifest(jarFile);
            if (version == null) {
              version = getVersionByJarFileName(jarFile.getName());
            }
            if (version != null && StringUtil.containsChar(version, '.')) {
              result.add(new UsageDescriptor(descriptor.myName + "_" + version, 1));
            }
          }
        }
      }
    });

    return result;
  }

  @Nullable
  private static String getVersionByJarManifest(@NotNull VirtualFile file) {
    return JarUtil.getJarAttribute(VfsUtilCore.virtualToIoFile(file), Attributes.Name.IMPLEMENTATION_VERSION);
  }

  @Nullable
  private static String getVersionByJarFileName(@NotNull String fileName) {
    Matcher fileNameMatcher = JAR_FILE_NAME_PATTERN.matcher(fileName);
    if (!fileNameMatcher.matches()) return null;

    return StringUtil.trimTrailing(fileNameMatcher.group(1), '.');
  }

  @NotNull
  @Override
  public String getGroupId() {
    return GROUP;
  }
}