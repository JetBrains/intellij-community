/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.projectRoots;

import java.util.EventListener;

public interface SdkModel {

  Sdk[] getSdks();

  Sdk findSdk(String sdkName);

  interface Listener extends EventListener {
    void sdkAdded(Sdk sdk);
    void beforeSdkRemove(Sdk sdk);
    void sdkChanged(Sdk sdk);
    void sdkHomeSelected(Sdk sdk, String newSdkHome);

  }

  void addListener(Listener listener);

  void removeListener(Listener listener);
}
