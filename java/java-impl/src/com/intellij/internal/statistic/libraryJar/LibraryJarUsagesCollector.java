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
package com.intellij.internal.statistic.libraryJar;

import com.intellij.internal.statistic.AbstractApplicationUsagesCollector;
import com.intellij.internal.statistic.CollectUsagesException;
import com.intellij.internal.statistic.beans.GroupDescriptor;
import com.intellij.internal.statistic.beans.UsageDescriptor;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.JdkUtil;
import com.intellij.openapi.vfs.JarFileSystem;
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
 * @author Ivan Chirkov
 */
public class LibraryJarUsagesCollector extends AbstractApplicationUsagesCollector {
  private static final GroupDescriptor GROUP = GroupDescriptor.create("Libraries by jars", GroupDescriptor.LOWER_PRIORITY);

  private static final String DIGIT_VERSION_PATTERN_PART = "(\\d+.\\d+|\\d+)";
  private static final Pattern JAR_FILE_NAME_PATTERN = Pattern.compile("[\\w|\\-|\\.]+-(" + DIGIT_VERSION_PATTERN_PART + "[\\w|\\.]*)jar");
  private static final Pattern DIGIT_VERSION_PATTERN = Pattern.compile(DIGIT_VERSION_PATTERN_PART + ".*");

  @NotNull
  @Override
  public Set<UsageDescriptor> getProjectUsages(@NotNull final Project project) throws CollectUsagesException {
    final LibraryJarDescriptor[] descriptors = LibraryJarStatisticsService.getInstance().getTechnologyDescriptors();
    final Set<UsageDescriptor> result = new HashSet<UsageDescriptor>(descriptors.length);
    ApplicationManager.getApplication().runReadAction(new Runnable() {
      @Override
      public void run() {
        for (LibraryJarDescriptor descriptor : descriptors) {
          String className = descriptor.myClass;
          if (className == null) continue;

          PsiClass[] psiClasses = JavaPsiFacade.getInstance(project).findClasses(className, ProjectScope.getLibrariesScope(project));
          for (PsiClass psiClass : psiClasses) {
            if (psiClass == null) continue;

            VirtualFile localFile = JarFileSystem.getInstance().getLocalVirtualFileFor(psiClass.getContainingFile().getVirtualFile());
            if (localFile == null) continue;

            String version = getVersionByJarManifest(localFile);
            if (version == null) version = getVersionByJarFileName(localFile.getName());
            if (version == null) continue;

            result.add(new UsageDescriptor(descriptor.myName + "_" + version, 1));
          }
        }
      }
    });
    return result;
  }

  @Nullable
  private static String getVersionByJarManifest(@NotNull VirtualFile file) {
    String version = JdkUtil.getJarMainAttribute(file, Attributes.Name.IMPLEMENTATION_VERSION);
    if (version == null) return null;

    Matcher versionMatcher = DIGIT_VERSION_PATTERN.matcher(version);
    if (!versionMatcher.matches()) return null;

    return versionMatcher.group(1);
  }

  @Nullable
  public String getVersionByJarFileName(@NotNull String fileName) {
    Matcher fileNameMatcher = JAR_FILE_NAME_PATTERN.matcher(fileName);
    if (!fileNameMatcher.matches()) return null;

    return fileNameMatcher.group(2);
  }

  @NotNull
  @Override
  public GroupDescriptor getGroupId() {
    return GROUP;
  }
}
