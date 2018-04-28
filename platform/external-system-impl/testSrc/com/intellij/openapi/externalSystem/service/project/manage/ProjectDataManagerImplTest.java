// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.externalSystem.service.project.manage;

import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.externalSystem.model.DataNode;
import com.intellij.openapi.externalSystem.model.Key;
import com.intellij.openapi.externalSystem.model.ProjectKeys;
import com.intellij.openapi.externalSystem.model.ProjectSystemId;
import com.intellij.openapi.externalSystem.model.project.ProjectData;
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.testFramework.PlatformTestCase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class ProjectDataManagerImplTest extends PlatformTestCase {

  public static final Key<Object> TEST_KEY = Key.create(Object.class, 0);

  public void testDataServiceIsCalledIfNoNodes() throws Exception {
    final List<String> callTrace = new ArrayList<>();
    Extensions.getRootArea().getExtensionPoint(ProjectDataService.EP_NAME).registerExtension(new TestDataService(callTrace));
    ProjectDataManagerImpl.getInstance().importData(
      Collections.singletonList(
        new DataNode<>(ProjectKeys.PROJECT, new ProjectData(ProjectSystemId.IDE,
                                                                       "externalName",
                                                                       "externalPath",
                                                                       "linkedPath"), null)), myProject, true);

    assertContainsElements(callTrace, "computeOrphanData");
  }

  static class TestDataService implements ProjectDataService {

    private final List<String> myTrace;

    public TestDataService(List<String> trace) {
      myTrace = trace;
    }

    @NotNull
    @Override
    public Key getTargetDataKey() {
      return TEST_KEY;
    }

    @Override
    public void removeData(@NotNull Computable toRemove,
                           @NotNull Collection toIgnore,
                           @NotNull ProjectData projectData,
                           @NotNull Project project,
                           @NotNull IdeModifiableModelsProvider modelsProvider) {
      myTrace.add("removeData");
    }

    @NotNull
    @Override
    public Computable<Collection> computeOrphanData(@NotNull Collection toImport,
                                                    @NotNull ProjectData projectData,
                                                    @NotNull Project project,
                                                    @NotNull IdeModifiableModelsProvider modelsProvider) {
      myTrace.add("computeOrphanData");
      return () -> Collections.emptyList();
    }

    @Override
    public void importData(@NotNull Collection toImport,
                           @Nullable ProjectData projectData,
                           @NotNull Project project,
                           @NotNull IdeModifiableModelsProvider modelsProvider) {
      myTrace.add("importData");
    }
  }

}