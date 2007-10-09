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
package com.intellij.ide.util;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NonNls;

public abstract class PropertiesComponent {
  public static PropertiesComponent getInstance(Project project) {
    return project.getComponent(PropertiesComponent.class);
  }

  public static PropertiesComponent getInstance() {
    return ApplicationManager.getApplication().getComponent(PropertiesComponent.class);
  }

  public final boolean isTrueValue(@NonNls String name) {
    return Boolean.valueOf(getValue(name)).booleanValue();
  }

  public abstract boolean isValueSet(String name);

  public abstract String getValue(@NonNls String name);

  public abstract void setValue(@NonNls String name, String value);

}
