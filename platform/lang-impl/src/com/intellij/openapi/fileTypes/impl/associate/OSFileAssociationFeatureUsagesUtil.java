// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.fileTypes.impl.associate;

import com.intellij.internal.statistic.eventLog.EventLogGroup;
import com.intellij.internal.statistic.eventLog.events.EventFields;
import com.intellij.internal.statistic.eventLog.events.EventId1;
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector;
import com.intellij.internal.statistic.utils.PluginInfo;
import com.intellij.internal.statistic.utils.PluginInfoDetectorKt;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.List;

class OSFileAssociationFeatureUsagesUtil extends CounterUsagesCollector {

  private static final EventLogGroup GROUP = new EventLogGroup("os.file.type.association", 2);
  private static final EventId1<String> ASSOCIATION_CREATED =
    GROUP.registerEvent("os.association.created", EventFields.StringValidatedByCustomRule("file_type", "file_type"));
  private static final String OTHER_FILE_TYPE = "Other";

  @Override
  public EventLogGroup getGroup() {
    return GROUP;
  }

  private OSFileAssociationFeatureUsagesUtil() {
  }

  static void logFilesAssociated(@NotNull List<? extends FileType> fileTypes) {
    ContainerUtil.map(fileTypes, fileType -> getAllowedTypeName(fileType))
      .forEach(fileType -> ASSOCIATION_CREATED.log(fileType));
  }

  public static String getAllowedTypeName(@NotNull FileType fileType) {
    FileType originalType = OSAssociateFileTypesUtil.getOriginalType(fileType);
    final PluginInfo info = PluginInfoDetectorKt.getPluginInfo(originalType.getClass());
    return info.isDevelopedByJetBrains() ? originalType.getName() : OTHER_FILE_TYPE;
  }
}
