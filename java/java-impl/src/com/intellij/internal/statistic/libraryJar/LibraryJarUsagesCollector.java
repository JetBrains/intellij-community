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
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.search.ProjectScope;
import com.intellij.util.containers.hash.HashSet;
import org.jetbrains.annotations.NotNull;

import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author Ivan Chirkov
 */
public class LibraryJarUsagesCollector extends AbstractApplicationUsagesCollector {
  public static final Pattern MULTI_DIGIT_VERSION_PATTERN = Pattern.compile("(\\d+\\.\\d+).*");
  private static final GroupDescriptor GROUP = GroupDescriptor.create("Libraries by jars", GroupDescriptor.LOWER_PRIORITY);

  private static final Pattern PATH_PATTERN = Pattern.compile(".*/[\\w|\\-|\\.]+-([\\w|\\.]+)jar!/.*");

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
          if (className != null) {
            PsiClass psiClass = JavaPsiFacade.getInstance(project).findClass(className, ProjectScope.getLibrariesScope(project));
            if (psiClass != null) {
              Matcher matcher = PATH_PATTERN.matcher(psiClass.getContainingFile().getVirtualFile().getPath());
              if (matcher.matches()) {
                String version;
                String fullVersion = matcher.group(1);
                matcher = MULTI_DIGIT_VERSION_PATTERN.matcher(fullVersion);
                if (matcher.matches()) {
                  version = matcher.group(1);
                } else {
                  version = fullVersion.substring(0, fullVersion.indexOf("."));
                }
                result.add(new UsageDescriptor(descriptor.myName + "_" + version, 1));
              }
            }
          }
        }
      }
    });
    return result;
  }

  @NotNull
  @Override
  public GroupDescriptor getGroupId() {
    return GROUP;
  }

}
