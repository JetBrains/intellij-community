// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.projectView;

import com.intellij.ide.projectView.ProjectView;
import com.intellij.ide.projectView.impl.AbstractProjectViewPane;
import com.intellij.ide.scopeView.ScopeViewPane;
import com.intellij.internal.statistic.beans.MetricEvent;
import com.intellij.internal.statistic.eventLog.EventLogGroup;
import com.intellij.internal.statistic.eventLog.events.ClassEventField;
import com.intellij.internal.statistic.eventLog.events.EventFields;
import com.intellij.internal.statistic.eventLog.events.EventPair;
import com.intellij.internal.statistic.eventLog.events.VarargEventId;
import com.intellij.internal.statistic.service.fus.collectors.ProjectUsagesCollector;
import com.intellij.openapi.project.Project;
import com.intellij.psi.search.scope.packageSet.NamedScope;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

final class ProjectViewCollector extends ProjectUsagesCollector {
  private static final EventLogGroup GROUP = new EventLogGroup("project.view.pane", 2);
  public static final ClassEventField CLASS_NAME = EventFields.Class("class_name");
  public static final ClassEventField SCOPE_CLASS_NAME = EventFields.Class("scope_class_name");
  private static final VarargEventId CURRENT = GROUP.registerVarargEvent("current", CLASS_NAME, SCOPE_CLASS_NAME);

  @Override
  public EventLogGroup getGroup() {
    return GROUP;
  }

  @Override
  protected @NotNull Set<MetricEvent> getMetrics(final @NotNull Project project) {
    final ProjectView projectView = project.getServiceIfCreated(ProjectView.class);
    if (projectView == null) {
      return Collections.emptySet();
    }

    final AbstractProjectViewPane currentViewPane = projectView.getCurrentProjectViewPane();
    if (currentViewPane == null) {
      return Collections.emptySet();
    }

    final List<EventPair<?>> data = new ArrayList<>();
    data.add(CLASS_NAME.with(currentViewPane.getClass()));
    final NamedScope selectedScope = currentViewPane instanceof ScopeViewPane ? ((ScopeViewPane)currentViewPane).getSelectedScope() : null;
    if (selectedScope != null) {
      data.add(SCOPE_CLASS_NAME.with(selectedScope.getClass()));
    }

    return Collections.singleton(CURRENT.metric(data));
  }
}
