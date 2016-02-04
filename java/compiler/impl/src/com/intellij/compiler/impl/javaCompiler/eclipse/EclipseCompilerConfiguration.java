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
package com.intellij.compiler.impl.javaCompiler.eclipse;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.project.Project;
import com.intellij.util.xmlb.XmlSerializerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.java.compiler.EclipseCompilerOptions;

@State(name = "EclipseCompilerSettings", storages = @Storage("compiler.xml"))
public class EclipseCompilerConfiguration implements PersistentStateComponent<EclipseCompilerOptions> {
  private final EclipseCompilerOptions mySettings = new EclipseCompilerOptions();

  @NotNull
  public EclipseCompilerOptions getState() {
    return mySettings;
  }

  public void loadState(EclipseCompilerOptions state) {
    XmlSerializerUtil.copyBean(state, mySettings);
  }

  public static EclipseCompilerOptions getOptions(Project project, Class<? extends EclipseCompilerConfiguration> aClass) {
    return ServiceManager.getService(project, aClass).getState();
  }}