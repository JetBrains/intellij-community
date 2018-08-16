// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.actions;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.intellij.ide.actions.GotoActionBase;
import com.intellij.ide.util.gotoByName.ChooseByNameItem;
import com.intellij.ide.util.gotoByName.ChooseByNamePopup;
import com.intellij.ide.util.gotoByName.ChooseByNamePopupComponent;
import com.intellij.ide.util.gotoByName.ListChooseByNameModel;
import com.intellij.internal.statistic.service.fus.beans.FSContent;
import com.intellij.internal.statistic.service.fus.beans.FSGroup;
import com.intellij.internal.statistic.service.fus.beans.FSSession;
import com.intellij.internal.statistic.service.fus.collectors.ApplicationUsagesCollector;
import com.intellij.internal.statistic.service.fus.collectors.FUStatisticsAggregator;
import com.intellij.internal.statistic.service.fus.collectors.FeatureUsagesCollector;
import com.intellij.internal.statistic.service.fus.collectors.ProjectUsagesCollector;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogBuilder;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import static com.intellij.util.containers.ContainerUtil.notNullize;

public class CollectFUStatisticsAction extends GotoActionBase {
  @Override
  protected void gotoActionPerformed(@NotNull AnActionEvent e) {
    final Project project = e.getProject();
    if (project == null) {
      return;
    }

    Object[] projectCollectors = Extensions.getExtensions(ProjectUsagesCollector.getExtensionPointName());
    Object[] applicationCollectors = Extensions.getExtensions(ApplicationUsagesCollector.getExtensionPointName());

    List<Item> items = new ArrayList<>();
    for (Object collector : ContainerUtil.concat(projectCollectors, applicationCollectors)) {
      if (collector instanceof FeatureUsagesCollector) {
        String groupId = ((FeatureUsagesCollector)collector).getGroupId();
        String className = StringUtil.nullize(collector.getClass().getSimpleName(), true);
        items.add(new Item(groupId, className));
      }
    }

    ContainerUtil.sort(items, Comparator.comparing(it -> it.myGroupId));
    ListChooseByNameModel<Item> model = new MyChooseByNameModel(project, items);

    ChooseByNamePopup popup = ChooseByNamePopup.createPopup(project, model, GotoActionBase.getPsiContext(e));
    popup.setShowListForEmptyPattern(true);

    popup.invoke(new ChooseByNamePopupComponent.Callback() {
      @Override
      public void onClose() {
        if (CollectFUStatisticsAction.this.getClass().equals(myInAction)) myInAction = null;
      }

      @Override
      public void elementChosen(Object element) {
        showCollectorUsages(project, ((Item)element).myGroupId);
      }
    }, ModalityState.current(), false);
  }

  private static void showCollectorUsages(@NotNull Project project, @NotNull String groupId) {
    FUStatisticsAggregator aggregator = FUStatisticsAggregator.create();
    FSContent data = aggregator.getUsageCollectorsData(Collections.singleton(groupId));
    if (data == null) {
      Messages.showErrorDialog(project, "Can't collect usages", "Error");
      return;
    }

    StringBuilder result = new StringBuilder();
    Gson gson = new GsonBuilder().setPrettyPrinting().create();

    for (FSSession session : notNullize(data.getSessions())) {
      for (FSGroup group : ContainerUtil.filter(notNullize(session.getGroups()), it -> groupId.equals(it.id))) {
        result.append(gson.toJson(group, FSGroup.class));
        result.append("\n\n");
      }
    }

    JTextArea textArea = new JTextArea(result.toString());

    DialogBuilder builder = new DialogBuilder();
    builder.setCenterPanel(ScrollPaneFactory.createScrollPane(textArea));
    builder.setPreferredFocusComponent(textArea);
    builder.setTitle(groupId);
    builder.addOkAction();
    builder.show();
  }

  private static class Item implements ChooseByNameItem {
    @NotNull private final String myGroupId;
    @Nullable private final String myClassName;

    private Item(@NotNull String groupId, @Nullable String className) {
      myGroupId = groupId;
      myClassName = className;
    }

    @Override
    public String getName() {
      return myGroupId;
    }

    @Override
    public String getDescription() {
      return myClassName;
    }
  }

  private static class MyChooseByNameModel extends ListChooseByNameModel<Item> {
    private MyChooseByNameModel(Project project, List<Item> items) {
      super(project, "Enter usage collector group id", "No collectors found", items);
    }

    @Override
    public boolean useMiddleMatching() {
      return true;
    }
  }
}