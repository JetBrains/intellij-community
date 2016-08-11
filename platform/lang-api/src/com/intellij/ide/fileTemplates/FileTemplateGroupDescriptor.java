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

import com.intellij.openapi.fileTypes.FileTypeManager;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;

public class FileTemplateGroupDescriptor extends FileTemplateDescriptor {
  private final String myTitle;
  private final List<FileTemplateDescriptor> myTemplates = new ArrayList<>();

  public FileTemplateGroupDescriptor(String title, Icon icon, FileTemplateDescriptor... children) {
    this(title, icon);
    for (final FileTemplateDescriptor child : children) {
      addTemplate(child);
    }
  }

  public FileTemplateGroupDescriptor(String title, Icon icon) {
    super(null, icon);
    myTitle = title;
  }

  public String getTitle() {
    return myTitle;
  }

  public FileTemplateDescriptor[] getTemplates() {
    return myTemplates.toArray(new FileTemplateDescriptor[myTemplates.size()]);
  }

  @Override
  public String getDisplayName() {
    return getTitle();
  }

  public void addTemplate(FileTemplateDescriptor descriptor) {
    myTemplates.add(descriptor);
  }

  public void addTemplate(@NonNls String fileName) {
    addTemplate(new FileTemplateDescriptor(fileName, FileTypeManager.getInstance().getFileTypeByFileName(fileName).getIcon()));
  }
}