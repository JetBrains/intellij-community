// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.fileTypes.impl.associate;

import com.intellij.internal.statistic.eventLog.FeatureUsageData;
import com.intellij.internal.statistic.service.fus.collectors.FUCounterUsageLogger;
import com.intellij.internal.statistic.utils.PluginInfo;
import com.intellij.internal.statistic.utils.PluginInfoDetectorKt;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.List;

class OSFileAssociationFeatureUsagesUtil {

  private static final String GROUP_ID            = "os.file.type.association";
  private static final String ASSOCIATION_CREATED = "os.association.created";
  private static final String OTHER_FILE_TYPE     = "Other";

  private OSFileAssociationFeatureUsagesUtil() {
  }

  static void logFilesAssociated(@NotNull List<? extends FileType> fileTypes) {
    ContainerUtil.map(fileTypes, fileType -> getAllowedTypeName(fileType))
                 .forEach(
                   fileType -> FUCounterUsageLogger.getInstance().logEvent(
                     GROUP_ID, ASSOCIATION_CREATED, new FeatureUsageData().addData("file_type", fileType)));
  }

  public static String getAllowedTypeName(@NotNull FileType fileType) {
    FileType originalType = OSAssociateFileTypesUtil.getOriginalType(fileType);
    final PluginInfo info = PluginInfoDetectorKt.getPluginInfo(originalType.getClass());
    return info.isDevelopedByJetBrains() ? originalType.getName() : OTHER_FILE_TYPE;
  }
}
