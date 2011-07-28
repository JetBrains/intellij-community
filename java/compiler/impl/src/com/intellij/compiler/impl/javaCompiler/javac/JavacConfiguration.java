/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.compiler.impl.javaCompiler.javac;

import com.intellij.openapi.components.*;
import com.intellij.openapi.project.Project;
import com.intellij.util.xmlb.XmlSerializerUtil;

@State(
  name = "JavacSettings",
  storages = {
    @Storage( file = "$PROJECT_FILE$")
   ,@Storage( file = "$PROJECT_CONFIG_DIR$/compiler.xml", scheme = StorageScheme.DIRECTORY_BASED)
    }
)
public class JavacConfiguration implements PersistentStateComponent<JavacSettings> {
  private final JavacSettings mySettings = new JavacSettings();

  public JavacSettings getState() {
    return mySettings;
  }

  public void loadState(JavacSettings state) {
    XmlSerializerUtil.copyBean(state, mySettings);
  }

  public JavacSettings getSettings() {
    return mySettings;
  }

  public static JavacSettings getSettings(Project project, Class<? extends JavacConfiguration> aClass) {
    return ServiceManager.getService(project, aClass).getSettings();
  }
}