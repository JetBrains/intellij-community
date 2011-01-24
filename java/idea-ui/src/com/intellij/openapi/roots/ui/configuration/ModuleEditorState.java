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
package com.intellij.openapi.roots.ui.configuration;

import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.util.*;
import com.intellij.openapi.project.Project;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;

/**
 * @author Eugene Zhuravlev
 *         Date: Jul 22, 2004
 */
public class ModuleEditorState implements ProjectComponent, JDOMExternalizable{

  public String LAST_EDITED_MODULE_NAME;
  public String LAST_EDITED_TAB_NAME;

  public static ModuleEditorState getInstance(Project project) {
    return (ModuleEditorState)project.getComponent(ModuleEditorState.class);
  }

  public void readExternal(Element element) throws InvalidDataException {
    DefaultJDOMExternalizer.readExternal(this, element);
  }

  public void writeExternal(Element element) throws WriteExternalException {
    DefaultJDOMExternalizer.writeExternal(this, element);
  }

  public void projectOpened() {
  }

  public void projectClosed() {
  }

  @NotNull
  public String getComponentName() {
    return "ModuleEditorState";
  }

  public void initComponent() { }

  public void disposeComponent() {
  }
}
