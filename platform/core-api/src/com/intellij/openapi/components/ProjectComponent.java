// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.components;

/**
 * @see com.intellij.openapi.project.ProjectManager#TOPIC
 * @deprecated Components are deprecated, please see <a href="https://plugins.jetbrains.com/docs/intellij/plugin-components.html">SDK Docs</a> for guidelines on migrating to other APIs.
 * <p>
 * If you register a class as a project component it will be loaded, its instance will be created and
 * {@link #initComponent()} and {@link #projectOpened()} methods will be called for each project even if user doesn't use any feature of your
 * plugin. Also, plugins which declare project components don't support dynamic loading.
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
