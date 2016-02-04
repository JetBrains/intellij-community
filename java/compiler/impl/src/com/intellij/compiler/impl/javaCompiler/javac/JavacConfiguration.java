/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.java.compiler.JpsJavaCompilerOptions;

@State(name = "JavacSettings", storages = @Storage("compiler.xml"))
public class JavacConfiguration implements PersistentStateComponent<JpsJavaCompilerOptions> {
  private final JpsJavaCompilerOptions mySettings = new JpsJavaCompilerOptions();
  private final Project myProject;

  public JavacConfiguration(Project project) {
    myProject = project;
  }

  @Override
  @NotNull
  public JpsJavaCompilerOptions getState() {
    JpsJavaCompilerOptions state = new JpsJavaCompilerOptions();
    XmlSerializerUtil.copyBean(mySettings, state);
    state.ADDITIONAL_OPTIONS_STRING = PathMacroManager.getInstance(myProject).collapsePathsRecursively(state.ADDITIONAL_OPTIONS_STRING);
    return state;
  }

  @Override
  public void loadState(JpsJavaCompilerOptions state) {
    XmlSerializerUtil.copyBean(state, mySettings);
  }

  public static JpsJavaCompilerOptions getOptions(Project project, Class<? extends JavacConfiguration> aClass) {
    JavacConfiguration configuration = ServiceManager.getService(project, aClass);
    return configuration.mySettings;
  }
}