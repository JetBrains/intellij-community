/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
import com.intellij.openapi.util.NlsContexts;
import com.intellij.ui.JBColor;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Alexey Kudravtsev
 */
abstract class FileTemplateTab {
  protected final List<FileTemplateBase> myTemplates = new ArrayList<>();
  private final @NlsContexts.TabTitle String myTitle;
  protected static final Color MODIFIED_FOREGROUND = JBColor.BLUE;

  protected FileTemplateTab(@NlsContexts.TabTitle String title) {
    myTitle = title;
  }

  public abstract JComponent getComponent();

  @Nullable
  public abstract FileTemplate getSelectedTemplate();

  public abstract void selectTemplate(FileTemplate template);

  public abstract void removeSelected();
  public abstract void onTemplateSelected();

  public void init(FileTemplate[] templates) {
    final FileTemplate oldSelection = getSelectedTemplate();
    final String oldSelectionName = oldSelection != null? ((FileTemplateBase)oldSelection).getQualifiedName() : null;

    myTemplates.clear();
    for (FileTemplate original : templates) {
      if (FileTemplateBase.isChild(original))
        continue;
      FileTemplate copy = addCopy(original);
      copy.setChildren(ContainerUtil.map2Array(original.getChildren(), FileTemplate.class, template -> addCopy(template)));
    }
    initSelection(ContainerUtil.find(myTemplates, base -> base.getQualifiedName().equals(oldSelectionName)));
  }

  private FileTemplate addCopy(FileTemplate original) {
    final FileTemplateBase copy = (FileTemplateBase)original.clone();
    myTemplates.add(copy);
    return copy;
  }

  protected abstract void initSelection(FileTemplate selection);

  public abstract void fireDataChanged();

  public FileTemplate @NotNull [] getTemplates() {
    return myTemplates.toArray(FileTemplate.EMPTY_ARRAY);
  }

  public abstract void addTemplate(FileTemplate newTemplate);

  public abstract void insertTemplate(FileTemplate newTemplate, int index);

  public @NlsContexts.TabTitle String getTitle() {
    return myTitle;
  }
}
