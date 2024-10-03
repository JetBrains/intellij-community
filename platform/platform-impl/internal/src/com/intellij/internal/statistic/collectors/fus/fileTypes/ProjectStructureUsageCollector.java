// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.collectors.fus.fileTypes;

import com.intellij.execution.wsl.WslPath;
import com.intellij.internal.statistic.beans.MetricEvent;
import com.intellij.internal.statistic.eventLog.EventLogGroup;
import com.intellij.internal.statistic.eventLog.events.EventFields;
import com.intellij.internal.statistic.eventLog.events.EventId;
import com.intellij.internal.statistic.eventLog.events.EventId1;
import com.intellij.internal.statistic.eventLog.events.EventId2;
import com.intellij.internal.statistic.service.fus.collectors.ProjectUsagesCollector;
import com.intellij.internal.statistic.utils.PluginInfoDetectorKt;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.SourceFolder;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.packageDependencies.DependencyValidationManager;
import com.intellij.psi.search.scope.packageSet.NamedScope;
import com.intellij.psi.search.scope.packageSet.NamedScopeManager;
import com.intellij.util.PlatformUtils;
import com.intellij.util.containers.JBIterable;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.module.JpsModuleSourceRootType;
import org.jetbrains.jps.model.serialization.JpsElementPropertiesSerializer;
import org.jetbrains.jps.model.serialization.JpsModelSerializerExtension;

import java.util.*;

/**
 * @author gregsh
 */
final class ProjectStructureUsageCollector extends ProjectUsagesCollector {
  private final EventLogGroup GROUP = new EventLogGroup("project.structure", 6);

  private final EventId1<Integer> MODULES_TOTAL = GROUP.registerEvent("modules.total", EventFields.Count);
  private final EventId1<Integer> MODULE_GROUPS_TOTAL = GROUP.registerEvent("module.groups.total", EventFields.Count);
  private final EventId1<Integer> UNLOADED_MODULES_TOTAL = GROUP.registerEvent("unloaded.modules.total", EventFields.Count);
  private final EventId1<Integer> CONTENT_ROOTS_TOTAL = GROUP.registerEvent("content.roots.total", EventFields.Count);
  private final EventId1<Integer> SOURCE_ROOTS_TOTAL = GROUP.registerEvent("source.roots.total", EventFields.Count);
  private final EventId1<Integer> EXCLUDED_ROOTS_TOTAL = GROUP.registerEvent("excluded.roots.total", EventFields.Count);
  private final EventId2<Integer, String> SOURCE_ROOT = GROUP.registerEvent(
    "source.root", EventFields.Count,
    EventFields.String("type", Arrays.asList("cookbooks-root",
                                             "java-resource",
                                             "java-source",
                                             "java-test-resource",
                                             "java-test",
                                             "kotlin-resource",
                                             "kotlin-source",
                                             "kotlin-test-resource",
                                             "kotlin-test")));
  private final EventId1<Integer> PACKAGE_PREFIX = GROUP.registerEvent("package.prefix", EventFields.Count);
  private final EventId1<Integer> NAMED_SCOPES_TOTAL_LOCAL = GROUP.registerEvent("named.scopes.total.local", EventFields.Count);
  private final EventId1<Integer> NAMED_SCOPES_TOTAL_SHARED = GROUP.registerEvent("named.scopes.total.shared", EventFields.Count);
  private final EventId PROJECT_IN_WSL = GROUP.registerEvent("project.in.wsl");

  @Override
  public EventLogGroup getGroup() {
    return GROUP;
  }

  @Override
  public @NotNull Set<MetricEvent> getMetrics(@NotNull Project project) {
    Map<? extends JpsModuleSourceRootType<?>, String> typeNames = JBIterable.from(JpsModelSerializerExtension.getExtensions())
      .filter(o -> PluginInfoDetectorKt.getPluginInfo(o.getClass()).isDevelopedByJetBrains())
      .flatMap(JpsModelSerializerExtension::getModuleSourceRootPropertiesSerializers)
      .toMap(JpsElementPropertiesSerializer::getType, JpsElementPropertiesSerializer::getTypeId);
    int contentRoots = 0, sourceRoots = 0, excludedRoots = 0, packagePrefix = 0;
    Object2IntMap<String> types = new Object2IntOpenHashMap<>();
    ModuleManager moduleManager = ModuleManager.getInstance(project);
    Module[] modules = moduleManager.getModules();
    Set<List<String>> moduleGroups = new HashSet<>(); 
    for (Module module : modules) {
      ModuleRootManager rootManager = ModuleRootManager.getInstance(module);
      contentRoots += rootManager.getContentEntries().length;
      sourceRoots += rootManager.getSourceRoots(true).length;
      excludedRoots += rootManager.getExcludeRoots().length;
      for (ContentEntry entry : rootManager.getContentEntries()) {
        for (SourceFolder source : entry.getSourceFolders()) {
          if (StringUtil.isNotEmpty(source.getPackagePrefix())) {
            packagePrefix++;
          }
          String key = typeNames.get(source.getRootType());
          if (key == null) {
            continue;
          }
          types.mergeInt(key, 1, Math::addExact);
        }
      }
      String[] groupPath = moduleManager.getModuleGroupPath(module);
      if (groupPath != null) {
        moduleGroups.add(Arrays.asList(groupPath));
      }
    }

    final Set<MetricEvent> result = new HashSet<>();
    result.add(MODULES_TOTAL.metric(modules.length));
    result.add(MODULE_GROUPS_TOTAL.metric(moduleGroups.size()));
    result.add(UNLOADED_MODULES_TOTAL.metric(moduleManager.getUnloadedModuleDescriptions().size()));
    result.add(CONTENT_ROOTS_TOTAL.metric(contentRoots));
    result.add(SOURCE_ROOTS_TOTAL.metric(sourceRoots));
    result.add(EXCLUDED_ROOTS_TOTAL.metric(excludedRoots));
    for (Object2IntMap.Entry<String> entry : types.object2IntEntrySet()) {
      result.add(SOURCE_ROOT.metric(entry.getIntValue(), entry.getKey()));
    }
    if (PlatformUtils.isIntelliJ()) {
      result.add(PACKAGE_PREFIX.metric(packagePrefix));
    }

    NamedScope[] localScopes = NamedScopeManager.getInstance(project).getEditableScopes();
    result.add(NAMED_SCOPES_TOTAL_LOCAL.metric(localScopes.length));
    NamedScope[] sharedScopes = DependencyValidationManager.getInstance(project).getEditableScopes();
    result.add(NAMED_SCOPES_TOTAL_SHARED.metric(sharedScopes.length));

    String basePath = project.getBasePath();
    if (basePath != null && WslPath.isWslUncPath(basePath)) {
      result.add(PROJECT_IN_WSL.metric());
    }

    return result;
  }
}
