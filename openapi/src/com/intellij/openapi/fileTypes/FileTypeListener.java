/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.fileTypes;

import java.util.EventListener;

public interface FileTypeListener extends EventListener {
  void beforeFileTypesChanged(FileTypeEvent event);
  void fileTypesChanged(FileTypeEvent event);
}
