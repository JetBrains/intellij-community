// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.projectRoots;

import com.intellij.openapi.options.ConfigurationException;


public interface ValidatableSdkAdditionalData extends SdkAdditionalData {
  /**
   * Checks if the SDK properties are configured correctly, and throws an exception
   * if they are not.
   *
   * @param sdkModel the model containing all configured SDKs.
   * @throws ConfigurationException if the SDK is not configured correctly.
   */
  void checkValid(SdkModel sdkModel) throws ConfigurationException;
}
