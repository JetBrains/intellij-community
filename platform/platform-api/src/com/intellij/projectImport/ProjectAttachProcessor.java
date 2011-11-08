/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.projectImport;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.project.Project;

import java.io.File;

/**
 * @author yole
 */
public class ProjectAttachProcessor {
  public static final ExtensionPointName<ProjectAttachProcessor> EP_NAME = new ExtensionPointName<ProjectAttachProcessor>("com.intellij.projectAttachProcessor");
  
  public boolean attachToProject(Project project, File projectDir, ProjectOpenedCallback callback) {
    return false;
  }
  
  public static boolean canAttachToProject() {
    return Extensions.getExtensions(EP_NAME).length > 0;
  }
}
