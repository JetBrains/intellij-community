// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.externalSystem.model;

import com.intellij.notification.NotificationGroup;
import com.intellij.openapi.actionSystem.DataKey;
import com.intellij.openapi.externalSystem.ExternalSystemUiAware;
import com.intellij.openapi.externalSystem.view.ExternalProjectsView;
import com.intellij.openapi.externalSystem.view.ExternalSystemNode;
import com.intellij.openapi.externalSystem.view.ProjectNode;
import com.intellij.openapi.util.Key;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.List;

public final class ExternalSystemDataKeys {

  public static final @NotNull DataKey<ProjectSystemId> EXTERNAL_SYSTEM_ID = DataKey.create("external.system.id");
  public static final @NotNull DataKey<NotificationGroup> NOTIFICATION_GROUP = DataKey.create("external.system.notification");
  public static final @NotNull DataKey<ExternalProjectsView> VIEW = DataKey.create("external.system.view");
  public static final @NotNull DataKey<ProjectNode> SELECTED_PROJECT_NODE = DataKey.create("external.system.selected.project.node");
  public static final @NotNull DataKey<List<ExternalSystemNode>> SELECTED_NODES = DataKey.create("external.system.selected.nodes");
  public static final @NotNull DataKey<ExternalSystemUiAware> UI_AWARE = DataKey.create("external.system.ui.aware");
  public static final @NotNull DataKey<JTree> PROJECTS_TREE = DataKey.create("external.system.tree");

  public static final @NotNull Key<Boolean> NEWLY_IMPORTED_PROJECT = new Key<>("external.system.newly.imported");
  public static final @NotNull Key<Boolean> NEWLY_CREATED_PROJECT = new Key<>("external.system.newly.created");
  public static final @NotNull Key<Boolean> NEWLY_OPENED_PROJECT_WITH_IDE_CACHES = new Key<>("external.system.newly.opened.with.ide.caches");

  private ExternalSystemDataKeys() {
  }
}
