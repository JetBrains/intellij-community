/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.roots;

import com.intellij.openapi.projectRoots.ProjectJdk;

public interface JdkOrderEntry extends OrderEntry {
  ProjectJdk getJdk();
  String getJdkName();
}
