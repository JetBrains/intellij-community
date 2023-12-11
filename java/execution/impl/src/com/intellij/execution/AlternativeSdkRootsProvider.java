// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution;

import com.intellij.execution.configurations.ConfigurationWithAlternativeJre;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.RootsChangeRescanningInfo;
import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.AdditionalLibraryRootsProvider;
import com.intellij.openapi.roots.JavaSyntheticLibrary;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.SyntheticLibrary;
import com.intellij.openapi.roots.ex.ProjectRootManagerEx;
import com.intellij.openapi.util.EmptyRunnable;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.ui.AppUIUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public final class AlternativeSdkRootsProvider extends AdditionalLibraryRootsProvider {
  private static final Key<Collection<SyntheticLibrary>> ALTERNATIVE_SDK_LIBS_KEY = Key.create("ALTERNATIVE_SDK_LIBS_KEY");

  @Override
  public @NotNull Collection<SyntheticLibrary> getAdditionalProjectLibraries(@NotNull Project project) {
    return ContainerUtil.map(getAdditionalProjectJdksToIndex(project), AlternativeSdkRootsProvider::createSdkLibrary);
  }

  private static @NotNull JavaSyntheticLibrary createSdkLibrary(@NotNull Sdk sdk) {
    return new JavaSyntheticLibrary(sdk.getName(), Arrays.asList(sdk.getRootProvider().getFiles(OrderRootType.SOURCES)),
                                    Arrays.asList(sdk.getRootProvider().getFiles(OrderRootType.CLASSES)), Collections.emptySet());
  }

  public static boolean shouldIndexAlternativeJre() {
    return Registry.is("index.run.configuration.jre");
  }

  public static boolean hasEnabledAlternativeJre(@NotNull RunnerAndConfigurationSettings settings) {
    return settings.getConfiguration() instanceof ConfigurationWithAlternativeJre jreConf &&
           jreConf.isAlternativeJrePathEnabled();
  }

  public static @NotNull List<Sdk> getAdditionalProjectJdksToIndex(@NotNull Project project) {
    if (shouldIndexAlternativeJre()) {
      return getAdditionalProjectJdks(project);
    }
    return Collections.emptyList();
  }

  public static @NotNull List<Sdk> getAdditionalProjectJdks(@NotNull Project project) {
    return RunManager.getInstance(project).getAllConfigurationsList().stream()
      .map(conf -> conf instanceof ConfigurationWithAlternativeJre jreConf && jreConf.isAlternativeJrePathEnabled() ?
        jreConf.getAlternativeJrePath() : null)
      .filter(Objects::nonNull)
      .map(ProjectJdkTable.getInstance()::findJdk)
      .filter(Objects::nonNull)
      .distinct()
      .toList();
  }

  public static void reindexIfNeeded(@NotNull Project project) {
    if (!Registry.is("index.run.configuration.jre")) return;
    AlternativeSdkRootsProvider provider = Objects.requireNonNull(EP_NAME.findExtension(AlternativeSdkRootsProvider.class));
    Collection<SyntheticLibrary> additionalProjectLibraries = provider.getAdditionalProjectLibraries(project);
    boolean update;
    synchronized (ALTERNATIVE_SDK_LIBS_KEY) {
      boolean res = additionalProjectLibraries != project.getUserData(ALTERNATIVE_SDK_LIBS_KEY);
      if (res) {
        project.putUserData(ALTERNATIVE_SDK_LIBS_KEY, additionalProjectLibraries);
      }
      update = res;
    }
    if (update) {
      AppUIUtil.invokeOnEdt(() -> WriteAction.run(
        () -> ProjectRootManagerEx.getInstanceEx(project).makeRootsChange(
          EmptyRunnable.getInstance(), RootsChangeRescanningInfo.RESCAN_DEPENDENCIES_IF_NEEDED)));
    }
  }
}

