// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.components;

/**
 * @deprecated components are deprecated. If you register a class as a project component it will be loaded, its instance will be created and
 * {@link #initComponent()} and {@link #projectOpened()} methods will be called for each project even if user doesn't use any feature of your
 * plugin. Also plugins which declare project components don't support dynamic loading. Please see
 * <a href="http://www.jetbrains.org/intellij/sdk/docs/basics/plugin_structure/plugin_components.html">guidelines</a> on migrating to other APIs.
 *
 * See {@link com.intellij.openapi.project.ProjectManager#TOPIC}.
 */
@Deprecated
public interface ProjectComponent extends BaseComponent {
  /**
   * Invoked when the project corresponding to this component instance is opened.<p>
   * Note that components may be created for even unopened projects and this method can be never
   * invoked for a particular component instance (for example for default project).
   */
  default void projectOpened() {
  }

  /**
   * Invoked when the project corresponding to this component instance is closed.<p>
   * Note that components may be created for even unopened projects and this method can be never
   * invoked for a particular component instance (for example for default project).
   */
  default void projectClosed() {
  }
}
