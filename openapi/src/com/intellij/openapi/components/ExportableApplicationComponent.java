/**
 * @author cdr
 */
/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.components;

import java.io.File;

public interface ExportableApplicationComponent extends ApplicationComponent {
  File[] getExportFiles();
  String getPresentableName();
}