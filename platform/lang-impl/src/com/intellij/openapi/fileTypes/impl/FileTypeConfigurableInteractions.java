// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileTypes.impl;

import com.intellij.internal.statistic.eventLog.EventLogGroup;
import com.intellij.internal.statistic.eventLog.events.EventFields;
import com.intellij.internal.statistic.eventLog.events.EventId;
import com.intellij.internal.statistic.eventLog.events.EventId1;
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector;
import com.intellij.openapi.fileTypes.FileType;

final class FileTypeConfigurableInteractions extends CounterUsagesCollector {
  private static final EventLogGroup group = new EventLogGroup("file.type.configurable.interactions", 1);
  public static final EventId1<FileType> patternAdded = group.registerEvent("pattern.added", EventFields.FileType);
  public static final EventId1<FileType> patternEdited = group.registerEvent("pattern.edited", EventFields.FileType);
  public static final EventId1<FileType> patternRemoved = group.registerEvent("pattern.removed", EventFields.FileType);
  public static final EventId1<FileType> hashbangAdded = group.registerEvent("hashbang.added", EventFields.FileType);
  public static final EventId1<FileType> hashbangEdited = group.registerEvent("hashbang.edited", EventFields.FileType);
  public static final EventId1<FileType> hashbangRemoved = group.registerEvent("hashbang.removed", EventFields.FileType);
  public static final EventId fileTypeAdded = group.registerEvent("file.type.added");
  public static final EventId fileTypeEdited = group.registerEvent("file.type.edited");
  public static final EventId fileTypeRemoved = group.registerEvent("file.type.removed");
  public static final EventId ignorePatternAdded = group.registerEvent("ignore.pattern.added");
  public static final EventId ignorePatternEdited = group.registerEvent("ignore.pattern.edited");
  public static final EventId ignorePatternRemoved = group.registerEvent("ignore.pattern.removed");

  @Override
  public EventLogGroup getGroup() {
    return group;
  }
}
