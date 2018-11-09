// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.components;

/**
 * @deprecated This interface is not used anymore. Application component do no need to extend any special interface.
 *
 * Instead of {@link #initComponent()} please use {@link com.intellij.util.messages.MessageBus} and corresponding topics.
 * Instead of {@link #disposeComponent()} please use {@link com.intellij.openapi.Disposable}.
 *
 * If for some reasons replacing {@link #disposeComponent()} / {@link #initComponent()} is not a option, {@link BaseComponent} can be extended.
 */
@Deprecated
public interface ApplicationComponent extends BaseComponent {
  /**
   * @deprecated Not used anymore.
   */
  @Deprecated
  class Adapter implements ApplicationComponent {
  }
}
