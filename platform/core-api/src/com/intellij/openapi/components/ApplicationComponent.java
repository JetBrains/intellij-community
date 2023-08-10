// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.components;

/**
 * @deprecated components are deprecated. If you register a class as an application component it will be loaded, its instance will be created and
 * {@link #initComponent()} methods will be called each time IDE is started even if user doesn't use any feature of your
 * plugin. Also plugins which declare application components don't support dynamic loading. Please see
 * <a href="http://www.jetbrains.org/intellij/sdk/docs/basics/plugin_structure/plugin_components.html">guidelines</a> on migrating to other APIs.
 */
@Deprecated
public interface ApplicationComponent extends BaseComponent {
  /**
   * @deprecated Adapter not required since Java 8 is used.
   */
  @Deprecated
  class Adapter implements ApplicationComponent {
  }
}
