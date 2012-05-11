/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.openapi.components;

/**
 * Project-level component's implementation class should implement the <code>ProjectComponent</code> interface.
 * It should have constructor with a single parameter of {@link com.intellij.openapi.project.Project}
 * type or with no parameters.
 * <p>
 * See <a href=../../../../../plugins.html>plugins.html</a> for more information.
 * @see AbstractProjectComponent
 */
public interface ProjectComponent extends BaseComponent {
  /**
   * Invoked when the project corresponding to this component instance is opened.<p>
   * Note that components may be created for even unopened projects and this method can be never
   * invoked for a particular component instance (for example for default project).
   */
  void projectOpened();

  /**
   * Invoked when the project corresponding to this component instance is closed.<p>
   * Note that components may be created for even unopened projects and this method can be never
   * invoked for a particular component instance (for example for default project).
   */
  void projectClosed();
}
