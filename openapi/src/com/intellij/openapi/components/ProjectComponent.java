/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.components;

/**
 * Project-level component's implementation class should implement the <code>ProjectComponent</code> interface.
 * It should have constructor with a single parameter of {@link com.intellij.openapi.project.Project}
 * type or with no parameters.
 * <p>
 * See <a href=../../../../../plugins.html>plugins.html</a> for more information.
 */
public interface ProjectComponent extends BaseComponent {
  /**
   * Invoked when the project corresponding to this component instance is opened.<p>
   * Note that components may be created for even unopened projects and this method can be never
   * invoked for a particular component intance (for example for default project).
   */
  void projectOpened();

  /**
   * Invoked when the project corresponding to this component instance is closed.<p>
   * Note that components may be created for even unopened projects and this method can be never
   * invoked for a particular component intance (for example for default project).
   */
  void projectClosed();
}
