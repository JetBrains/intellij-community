/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.vcs;

import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.vcs.checkin.DifferenceType;

import java.awt.*;

public interface FileStatusFactory {
  FileStatus createFileStatus(String id, String description, Color color);

  DifferenceType createDifferenceTypeInserted();
  DifferenceType createDifferenceTypeDeleted();
  DifferenceType createDifferenceTypeNotChanged();
  DifferenceType createDifferenceTypeModified();

  DifferenceType createDifferenceType(String id,
                                      FileStatus fileStatus,
                                      TextAttributesKey mainTextColorKey,
                                      TextAttributesKey leftTextColorKey,
                                      TextAttributesKey rightTextColorKey,
                                      Color background,
                                      Color activeBgColor);

  FileStatus[] getAllFileStatuses();
}