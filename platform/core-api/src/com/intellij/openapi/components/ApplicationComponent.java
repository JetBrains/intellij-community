/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

import org.jetbrains.annotations.NotNull;

/**
 * Application-level component's implementation class may implement the <code>ApplicationComponent</code> interface.<br>
 * It may have constructor with parameters that are also application components.
 * <p>
 * <strong>Note that if you register a class as an application component it will be loaded, its instance will be created and
 * {@link #initComponent()} methods will be called each time IDE is started even if user doesn't use any feature of your
 * plugin. So consider using specific extensions instead to ensure that the plugin will not impact IDE performance until user calls its
 * actions explicitly.</strong>
 */
public interface ApplicationComponent extends BaseComponent {
  class Adapter implements ApplicationComponent {
    @NotNull
    @Override
    public String getComponentName() {
      return getClass().getSimpleName();
    }

    @Override
    public void disposeComponent() { }

    @Override
    public void initComponent() { }
  }
}
