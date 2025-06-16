// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.collectors.fus.fileTypes;

import com.intellij.internal.statistic.beans.MetricEvent;
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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static java.util.Collections.emptySet;

@ApiStatus.Internal
public final class FileTypeUsagesCollector extends ProjectUsagesCollector {
  private static final String DEFAULT_ID = "third.party";

  private static final StringEventField FILE_EXTENSION = EventFields.StringValidatedByCustomRule(
    "file_extension", FileExtensionValidationRule.class
  );

  private final RoundedIntEventField COUNT = EventFields.RoundedInt("count");

  // temporary is not collected
  public static final EventField<String> SCHEMA = EventFields.StringValidatedByCustomRule("schema", FileTypeSchemaValidator.class);
  public static final IntEventField PERCENT = EventFields.Int("percent");
  private final ObjectListEventField FILE_SCHEME_PERCENT = new ObjectListEventField("file_schema", SCHEMA, PERCENT);

  public static final EventField<FileType> TYPE_BY_EXTENSION = EventFields.FileType;
  public static final IntEventField TYPE_BY_EXTENSION_PERCENT = EventFields.Int("percent");
  private final ObjectListEventField FILE_TYPE_BY_EXTENSION_PERCENT = new ObjectListEventField("original_file_type", TYPE_BY_EXTENSION, TYPE_BY_EXTENSION_PERCENT);
  private static final IntEventField FILE_EXTENSION_BY_PERCENT = EventFields.Int("file_extension_percent");
  private final EventLogGroup GROUP = new EventLogGroup("file.types", 10);
  private final VarargEventId FILE_TYPE_IN_PROJECT = GROUP.registerVarargEvent(
    "file.type.in.project",
    EventFields.PluginInfoFromInstance,
    EventFields.FileType,
    COUNT,
    FILE_TYPE_BY_EXTENSION_PERCENT,
    FILE_SCHEME_PERCENT
  );

  private final VarargEventId FILE_EXTENSION_IN_PROJECT = GROUP.registerVarargEvent(
    "file.extension.in.project",
    FILE_EXTENSION,
    COUNT,
    FILE_EXTENSION_BY_PERCENT
  );

  @Override
  public EventLogGroup getGroup() {
    return GROUP;
  }

  @Override
  protected @NotNull Set<MetricEvent> getMetrics(@NotNull Project project) {
    if (project.isDisposed()) return emptySet();

    final ProjectFileIndex projectFileIndex = ProjectFileIndex.getInstance(project);
    final IProjectStore stateStore = ProjectKt.getStateStore(project);
    final ObjectIntMap<FileType> filesByTypeCount = new ObjectIntHashMap<>();
    final var fileTypeMappingsCounter = new OriginalFileTypeCounter();
    final var fileExtensionCounter = new FileExtensionCounter();
    projectFileIndex.iterateContent(
      file -> {
        final FileType type = file.getFileType();
        filesByTypeCount.put(type, filesByTypeCount.getOrDefault(type, 0) + 1);
        final String fileExtension = file.getExtension();

        if (fileExtension == null) {
          fileTypeMappingsCounter.recordOriginalFileType(type, null);
          return true;
        }

        final var fileTypeByExtension = FileTypeManager.getInstance().getFileTypeByExtension(fileExtension);
        fileTypeMappingsCounter.recordOriginalFileType(type, fileTypeByExtension);
        fileExtensionCounter.recordOriginalFileType(fileExtension);
        return true;
      },
      //skip files from .idea directory otherwise 99% of projects would have XML and PLAIN_TEXT file types
      file -> !file.isDirectory() && !stateStore.isProjectFile(file)
    );

    final Set<MetricEvent> events = new HashSet<>();
    for (final FileType fileType : filesByTypeCount.keySet()) {
      List<EventPair<?>> eventPairs = new ArrayList<>(4);
      eventPairs.add(EventFields.PluginInfoFromInstance.with(fileType));
      eventPairs.add(EventFields.FileType.with(fileType));
      eventPairs.add(COUNT.with(filesByTypeCount.get(fileType)));
      eventPairs.add(FILE_TYPE_BY_EXTENSION_PERCENT.with(fileTypeMappingsCounter.getFileTypeSchemaUsagePercentage(fileType)));
      events.add(FILE_TYPE_IN_PROJECT.metric(eventPairs));
    }

    for (final var extension : fileExtensionCounter.getFileExtensionsList()) {
      final var extensionCount = fileExtensionCounter.getFileExtensionCount(extension);
      final List<EventPair<?>> eventPairs = new ArrayList<>(3);
      eventPairs.add(FILE_EXTENSION.with(extension));
      eventPairs.add(COUNT.with(extensionCount));
      eventPairs.add(FILE_EXTENSION_BY_PERCENT.with(fileExtensionCounter.getFileExtensionUsagePercentage(extension)));
      events.add(FILE_EXTENSION_IN_PROJECT.metric(eventPairs));
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
