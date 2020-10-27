// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.fileTypes.impl;

import com.intellij.internal.statistic.eventLog.EventLogGroup;
import com.intellij.internal.statistic.eventLog.events.EventFields;
import com.intellij.internal.statistic.eventLog.events.EventId;
import com.intellij.internal.statistic.eventLog.events.EventId1;
import com.intellij.internal.statistic.eventLog.events.StringEventField;
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector;

public class FileTypeConfigurableInteractions extends CounterUsagesCollector {
  private static final EventLogGroup group = new EventLogGroup("file.type.configurable.interactions", 1);
  private static final StringEventField fileTypeField = EventFields.StringValidatedByCustomRule("file_type", "file_type");
  public static final EventId1<String> patternAdded = group.registerEvent("pattern.added", fileTypeField);
  public static final EventId1<String> patternEdited = group.registerEvent("pattern.edited", fileTypeField);
  public static final EventId1<String> patternRemoved = group.registerEvent("pattern.removed", fileTypeField);
  public static final EventId1<String> hashbangAdded = group.registerEvent("hashbang.added", fileTypeField);
  public static final EventId1<String> hashbangEdited = group.registerEvent("hashbang.edited", fileTypeField);
  public static final EventId1<String> hashbangRemoved = group.registerEvent("hashbang.removed", fileTypeField);
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
