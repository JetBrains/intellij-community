// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.libraryJar;

import com.intellij.internal.statistic.LibraryNameValidationRule;
import com.intellij.internal.statistic.beans.MetricEvent;
import com.intellij.internal.statistic.eventLog.EventLogGroup;
import com.intellij.internal.statistic.eventLog.events.EventFields;
import com.intellij.internal.statistic.eventLog.events.EventId2;
import com.intellij.internal.statistic.service.fus.collectors.ProjectUsagesCollector;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.search.ProjectScope;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.Set;

import static com.intellij.internal.statistic.libraryJar.LibraryJarUtilKt.findJarVersion;

/**
 * @author Andrey Cheptsov
 */
public class FUSLibraryJarUsagesCollector extends ProjectUsagesCollector {
  private static final EventLogGroup GROUP = new EventLogGroup("javaLibraryJars", 4);
  private static final EventId2<String, String> USED_LIBRARY =
    GROUP.registerEvent("used.library",
                        EventFields.Version,
                        EventFields.StringValidatedByCustomRule("library", LibraryNameValidationRule.class));

  @Override
  public EventLogGroup getGroup() {
    return GROUP;
  }

  @NotNull
  @Override
  public Set<MetricEvent> getMetrics(@NotNull Project project) {
    LibraryJarDescriptor[] descriptors = LibraryJarStatisticsService.getInstance().getTechnologyDescriptors();
    Set<MetricEvent> result = new HashSet<>(descriptors.length);

    for (LibraryJarDescriptor descriptor : descriptors) {
      String className = descriptor.myClass;
      if (className == null) continue;

      MetricEvent event = ReadAction.nonBlocking(() -> {
        PsiClass[] psiClasses = DumbService.getInstance(project).computeWithAlternativeResolveEnabled(() -> 
          JavaPsiFacade.getInstance(project).findClasses(className, ProjectScope.getLibrariesScope(project))
        );
        for (PsiClass psiClass : psiClasses) {
          String version = findJarVersion(psiClass);
          if (StringUtil.isNotEmpty(version)) {
            return USED_LIBRARY.metric(version, descriptor.myName);
          }
        }
        return null;
      }).executeSynchronously();
      ContainerUtil.addIfNotNull(result, event);
    }

    return result;
  }
}