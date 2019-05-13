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
package com.intellij.ide.fileTemplates;

import com.intellij.openapi.fileTypes.FileTypeRegistry;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;

public class FileTemplateDescriptor {
  private final Icon myIcon;
  private final String myFileName;

  public FileTemplateDescriptor(@NonNls String fileName) {
    this(fileName, FileTypeRegistry.getInstance().getFileTypeByFileName(fileName).getIcon());
  }

  public FileTemplateDescriptor(@NonNls String fileName, Icon icon) {
    myIcon = icon;
    myFileName = fileName;
  }

  public Icon getIcon() {
    return myIcon;
  }

  public String getDisplayName() {
    return getFileName();
  }

  public String getFileName() {
    return myFileName;
  }
}