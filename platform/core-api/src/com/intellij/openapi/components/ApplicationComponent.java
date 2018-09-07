// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.components;

/**
 * @deprecated Not used anymore.
 */
public interface ApplicationComponent extends BaseComponent {
  /**
   * @deprecated Not used anymore.
   */
  @Deprecated
  class Adapter implements ApplicationComponent {
  }
}
