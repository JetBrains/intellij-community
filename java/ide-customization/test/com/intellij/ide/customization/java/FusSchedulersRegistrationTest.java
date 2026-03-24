// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.customization.java;

import com.intellij.ide.ApplicationActivity;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.extensions.impl.ExtensionPointImpl;
import com.intellij.openapi.util.Pair;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestApplication
public class FusSchedulersRegistrationTest {
  // runs with intellij.java.tests classpath

  public static final String PLUGIN_ID = "com.intellij.java.ide";

  private final ExtensionPointName<ApplicationActivity> APP_ACTIVITIES_EP =
    new ExtensionPointName<>("com.intellij.applicationActivity");
  private final ExtensionPointName<ApplicationActivity> POST_STARTUP_ACTIVITIES_EP =
    new ExtensionPointName<>("com.intellij.postStartupActivity");

  @Test
  public void testSchedulerActivitiesAvailable() {
    var applicationActivities = getExtensionClasses(APP_ACTIVITIES_EP);
    assertTrue(applicationActivities.containsKey("com.intellij.internal.statistic.updater.StatisticsJobsScheduler"),
               "StatisticsJobsScheduler is missing");
    assertTrue(applicationActivities.containsKey("com.intellij.internal.statistic.updater.StatisticsStateCollectorsScheduler"),
               "StatisticsStateCollectorsScheduler is missing");

    assertEquals(PLUGIN_ID, applicationActivities.get("com.intellij.internal.statistic.updater.StatisticsJobsScheduler"));
    assertEquals(PLUGIN_ID, applicationActivities.get("com.intellij.internal.statistic.updater.StatisticsStateCollectorsScheduler"));

    var postStartup = getExtensionClasses(POST_STARTUP_ACTIVITIES_EP);
    assertTrue(postStartup.containsKey("com.intellij.internal.statistic.updater.StatisticsStateCollectorsSchedulerProjectActivity"),
               "StatisticsStateCollectorsSchedulerProjectActivity is missing");

    assertEquals(PLUGIN_ID, applicationActivities.get("com.intellij.internal.statistic.updater.StatisticsStateCollectorsScheduler"));
  }

  // operates with EP adapters instead of instances, because those activities are not created in unit tests mode
  // they throw ExtensionNotApplicableException.create() from init
  private static @NotNull Map<@NotNull String, String> getExtensionClasses(ExtensionPointName<ApplicationActivity> epName) {
    return ContainerUtil.map2Map(((ExtensionPointImpl<?>)epName.getPoint()).getSortedAdapters(),
                                 it -> new Pair<>(it.getAssignableToClassName(), it.pluginDescriptor.getPluginId().getIdString()));
  }
}
