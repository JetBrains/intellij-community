// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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

abstract class FileTemplateTab {
  protected final List<FileTemplateBase> templates = new ArrayList<>();
  private final @NlsContexts.TabTitle String myTitle;
  static final Color MODIFIED_FOREGROUND = JBColor.BLUE;

  FileTemplateTab(@NlsContexts.TabTitle String title) {
    myTitle = title;
  }

  public abstract JComponent getComponent();

  public abstract @Nullable FileTemplate getSelectedTemplate();

  public abstract void selectTemplate(FileTemplate template);

  public abstract void removeSelected();
  public abstract void onTemplateSelected();

  public void init(List<? extends FileTemplate> templates) {
    FileTemplate oldSelection = getSelectedTemplate();
    String oldSelectionName = oldSelection != null? ((FileTemplateBase)oldSelection).getQualifiedName() : null;

    this.templates.clear();
    for (FileTemplate original : templates) {
      if (FileTemplateBase.isChild(original)) {
        continue;
      }
      FileTemplate copy = addCopy(original);
      copy.setChildren(ContainerUtil.map2Array(original.getChildren(), FileTemplate.class, template -> addCopy(template)));
    }
    initSelection(ContainerUtil.find(this.templates, base -> base.getQualifiedName().equals(oldSelectionName)));
  }

  private FileTemplate addCopy(FileTemplate original) {
    final FileTemplateBase copy = (FileTemplateBase)original.clone();
    templates.add(copy);
    return copy;
  }

  protected abstract void initSelection(FileTemplate selection);

  public abstract void fireDataChanged();

  public @NotNull List<FileTemplate> getTemplates() {
    return List.copyOf(templates);
  }

  public abstract void addTemplate(FileTemplate newTemplate);

  public abstract void insertTemplate(FileTemplate newTemplate, int index);

  public @NlsContexts.TabTitle String getTitle() {
    return myTitle;
  }
}
