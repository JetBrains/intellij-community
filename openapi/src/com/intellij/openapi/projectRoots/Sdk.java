/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.projectRoots;

import com.intellij.openapi.roots.RootProvider;

/**
 * @author Eugene Zhuravlev
 *         Date: Sep 23, 2004
 */
public interface Sdk {

  SdkType getSdkType();

  String getName();

  String getVersionString();

  String getHomePath();

  RootProvider getRootProvider();

  SdkAdditionalData getSdkAdditionalData();

  SdkModificator getSdkModificator();
}
