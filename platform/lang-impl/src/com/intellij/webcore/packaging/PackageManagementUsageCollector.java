// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.webcore.packaging;

import com.intellij.internal.statistic.eventLog.EventLogGroup;
import com.intellij.internal.statistic.eventLog.events.EventFields;
import com.intellij.internal.statistic.eventLog.events.EventId1;
import com.intellij.internal.statistic.eventLog.events.StringEventField;
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector;
import com.intellij.internal.statistic.utils.PluginInfo;
import com.intellij.internal.statistic.utils.PluginInfoDetectorKt;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;

@ApiStatus.Internal
public final class PackageManagementUsageCollector extends CounterUsagesCollector {
  private static final EventLogGroup GROUP = new EventLogGroup("package.management.ui", 2);
  private static final StringEventField SERVICE = EventFields.String("service", Arrays.asList("Node.js", "Python", "Bower"));

  private static final EventId1<String> BROWSE_AVAILABLE_PACKAGES = GROUP.registerEvent("browseAvailablePackages", SERVICE);
  private static final EventId1<String> INSTALL = GROUP.registerEvent("install", SERVICE);
  private static final EventId1<String> UPGRADE = GROUP.registerEvent("upgrade", SERVICE);
  private static final EventId1<String> UNINSTALL = GROUP.registerEvent("uninstall", SERVICE);

  private PackageManagementUsageCollector() {}

  @Override
  public EventLogGroup getGroup() {
    return GROUP;
  }

  public static void triggerBrowseAvailablePackagesPerformed(@NotNull Project project, @Nullable PackageManagementService service) {
    trigger(project, service, BROWSE_AVAILABLE_PACKAGES);
  }

  public static void triggerInstallPerformed(@NotNull Project project, @Nullable PackageManagementService service) {
    trigger(project, service, INSTALL);
  }

  public static void triggerUpgradePerformed(@NotNull Project project, @Nullable PackageManagementService service) {
    trigger(project, service, UPGRADE);
  }

  public static void triggerUninstallPerformed(@NotNull Project project, @Nullable PackageManagementService service) {
    trigger(project, service, UNINSTALL);
  }

  private static void trigger(@NotNull Project project, @Nullable PackageManagementService service, EventId1<String> event) {
    String serviceName = toKnownServiceName(service);
    if (serviceName != null) {
      event.log(project, serviceName);
    }
  }

  private static @Nullable String toKnownServiceName(@Nullable PackageManagementService service) {
    if (service == null) return null;
    PluginInfo info = PluginInfoDetectorKt.getPluginInfo(service.getClass());
    return info.isSafeToReport() ? service.getID() : null;
  }
}
