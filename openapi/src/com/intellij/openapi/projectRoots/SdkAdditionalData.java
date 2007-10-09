/*
 * Copyright 2000-2007 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.projectRoots;

import com.intellij.openapi.options.ConfigurationException;

/**
 * Container for the SDK properties specific to a particular SDK type.
 */
public interface SdkAdditionalData {
  Object clone() throws CloneNotSupportedException;

  /**
   * Checks if the SDK properties are configured correctly, and throws an exception
   * if they are not.
   *
   * @param sdkModel the model containing all configured SDKs.
   * @throws ConfigurationException if the SDK is not configured correctly.
   * @since 5.0.1
   */
  void checkValid(SdkModel sdkModel) throws ConfigurationException;
}
