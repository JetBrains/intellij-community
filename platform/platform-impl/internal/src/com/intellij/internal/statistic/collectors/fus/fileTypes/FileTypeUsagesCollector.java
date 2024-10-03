// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.collectors.fus.fileTypes;

import com.intellij.internal.statistic.beans.MetricEvent;
import com.intellij.internal.statistic.collectors.fus.fileTypes.FileTypeUsageCounterCollector.FileTypeSchemaValidator;
import com.intellij.internal.statistic.eventLog.EventLogGroup;
import com.intellij.internal.statistic.eventLog.events.*;
import com.intellij.internal.statistic.eventLog.validator.ValidationResultType;
import com.intellij.internal.statistic.eventLog.validator.rules.EventContext;
import com.intellij.internal.statistic.eventLog.validator.rules.impl.CustomValidationRule;
import com.intellij.internal.statistic.service.fus.collectors.ProjectUsagesCollector;
import com.intellij.internal.statistic.utils.PluginInfo;
import com.intellij.internal.statistic.utils.PluginInfoDetectorKt;
import com.intellij.openapi.components.impl.stores.IProjectStore;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.project.ProjectKt;
import com.intellij.util.containers.ObjectIntHashMap;
import com.intellij.util.containers.ObjectIntMap;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.*;

// todo disable in guest (no file types)
@ApiStatus.Internal
public final class FileTypeUsagesCollector extends ProjectUsagesCollector {
  private static final String DEFAULT_ID = "third.party";

  private final EventLogGroup GROUP = new EventLogGroup("file.types", 7);

  private final RoundedIntEventField COUNT = EventFields.RoundedInt("count");

  // temporary not collected
  private final EventField<String> SCHEMA = EventFields.StringValidatedByCustomRule("schema", FileTypeSchemaValidator.class);
  private final IntEventField PERCENT = EventFields.Int("percent");
  private final ObjectListEventField FILE_SCHEME_PERCENT = new ObjectListEventField("file_schema", SCHEMA, PERCENT);

  private final VarargEventId FILE_IN_PROJECT = GROUP.registerVarargEvent(
    "file.in.project",
    EventFields.PluginInfoFromInstance,
    EventFields.FileType,
    COUNT,
    FILE_SCHEME_PERCENT
  );

  @Override
  public EventLogGroup getGroup() {
    return GROUP;
  }

  @Override
  protected @NotNull Set<MetricEvent> getMetrics(@NotNull Project project) {
    if (project.isDisposed()) {
      return Collections.emptySet();
    }

    ProjectFileIndex projectFileIndex = ProjectFileIndex.getInstance(project);
    IProjectStore stateStore = ProjectKt.getStateStore(project);
    final ObjectIntMap<FileType> filesByTypeCount = new ObjectIntHashMap<>();
    projectFileIndex.iterateContent(
      file -> {
        FileType type = file.getFileType();
        filesByTypeCount.put(type, filesByTypeCount.getOrDefault(type, 0) + 1);
        return true;
      },
      //skip files from .idea directory otherwise 99% of projects would have XML and PLAIN_TEXT file types
      file -> !file.isDirectory() && !stateStore.isProjectFile(file)
    );

    final Set<MetricEvent> events = new HashSet<>();
    for (final FileType fileType : filesByTypeCount.keySet()) {
      List<EventPair<?>> eventPairs = new ArrayList<>(3);
      eventPairs.add(EventFields.PluginInfoFromInstance.with(fileType));
      eventPairs.add(EventFields.FileType.with(fileType));
      eventPairs.add(COUNT.with(filesByTypeCount.get(fileType)));
      events.add(FILE_IN_PROJECT.metric(eventPairs));
    }

    return events;
  }

  public static String getSafeFileTypeName(@NotNull FileType fileType) {
    final PluginInfo info = PluginInfoDetectorKt.getPluginInfo(fileType.getClass());
    return info.isDevelopedByJetBrains() ? fileType.getName() : DEFAULT_ID;
  }

  public static final class ValidationRule extends CustomValidationRule {
    @Override
    public @NotNull String getRuleId() {
      return "file_type";
    }

    @Override
    protected @NotNull ValidationResultType doValidate(@NotNull String data, @NotNull EventContext context) {
      if (isThirdPartyValue(data)) return ValidationResultType.ACCEPTED;

      final FileType fileType = FileTypeManager.getInstance().findFileTypeByName(data);
      if (fileType == null || !StringUtil.equals(fileType.getName(), data)) {
        return ValidationResultType.REJECTED;
      }

      final boolean isDevelopedByJB = PluginInfoDetectorKt.getPluginInfo(fileType.getClass()).isDevelopedByJetBrains();
      return isDevelopedByJB ? ValidationResultType.ACCEPTED : ValidationResultType.THIRD_PARTY;
    }
  }
}
