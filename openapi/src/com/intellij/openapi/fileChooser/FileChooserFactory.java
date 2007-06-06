/*
 * Copyright 2000-2005 JetBrains s.r.o.
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
package com.intellij.openapi.fileChooser;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;

import java.awt.*;

public abstract class FileChooserFactory {
  public static FileChooserFactory getInstance() {
    return ServiceManager.getService(FileChooserFactory.class);
  }

  public abstract FileChooserDialog createFileChooser(FileChooserDescriptor descriptor, Project project);
  public abstract FileChooserDialog createFileChooser(FileChooserDescriptor descriptor, Component parent);

  public abstract FileTextField createFileTextField(FileChooserDescriptor descriptor, boolean showHidden, Disposable parent);
  public abstract FileTextField createFileTextField(FileChooserDescriptor descriptor, Disposable parent);
}