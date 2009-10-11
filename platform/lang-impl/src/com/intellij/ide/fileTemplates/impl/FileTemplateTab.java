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

package com.intellij.ide.fileTemplates.impl;

import com.intellij.ide.fileTemplates.FileTemplate;
import com.intellij.ide.fileTemplates.FileTemplateUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * @author Alexey Kudravtsev
 */
abstract class FileTemplateTab {
  public Map<FileTemplate, FileTemplate> savedTemplates;
  private final String myTitle;
  protected static final Color MODIFIED_FOREGROUND = new Color(0, 0, 210);

  protected FileTemplateTab(String title) {
    myTitle = title;
  }

  public abstract JComponent getComponent();

  @Nullable
  public abstract FileTemplate getSelectedTemplate();

  public abstract void selectTemplate(FileTemplate template);

  public abstract void removeSelected();
  public abstract void onTemplateSelected();

  public void init(FileTemplate[] templates) {
    FileTemplate oldSelection = getSelectedTemplate();
    FileTemplate newSelection = null;
    Map<FileTemplate, FileTemplate> templatesToSave = new LinkedHashMap<FileTemplate, FileTemplate>();
    for (FileTemplate aTemplate : templates) {
      FileTemplate copy = FileTemplateUtil.cloneTemplate(aTemplate);
      templatesToSave.put(aTemplate, copy);
      if (savedTemplates != null && savedTemplates.get(aTemplate) == oldSelection) {
        newSelection = copy;
      }
    }
    savedTemplates = templatesToSave;
    initSelection(newSelection);
  }

  protected abstract void initSelection(FileTemplate selection);

  public abstract void fireDataChanged();

  @NotNull 
  public FileTemplate[] getTemplates() {
    return savedTemplates.values().toArray(new FileTemplate[savedTemplates.values().size()]);
  }

  public abstract void addTemplate(FileTemplate newTemplate);

  public String getTitle() {
    return myTitle;
  }

}
