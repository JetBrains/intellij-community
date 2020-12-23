// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.fileTemplates;

import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class FileTemplateGroupDescriptor extends FileTemplateDescriptor {
  private final @Nls String myTitle;
  private final List<FileTemplateDescriptor> myTemplates = new ArrayList<>();

  public FileTemplateGroupDescriptor(@NotNull @Nls String title, @Nullable Icon icon, FileTemplateDescriptor... children) {
    this(title, icon);
    for (FileTemplateDescriptor child : children) {
      addTemplate(child);
    }
  }

  public FileTemplateGroupDescriptor(@NotNull @Nls String title, @Nullable Icon icon) {
    super("-", icon);
    myTitle = title;
  }

  public @NotNull @Nls String getTitle() {
    return myTitle;
  }

  public @NotNull List<FileTemplateDescriptor> getTemplates() {
    return Collections.unmodifiableList(myTemplates);
  }

  public void addTemplate(@NotNull FileTemplateDescriptor descriptor) {
    myTemplates.add(descriptor);
  }

  public void addTemplate(@NotNull String fileName) {
    addTemplate(new FileTemplateDescriptor(fileName));
  }

  @Override
  public @NotNull String getDisplayName() {
    return getTitle();
  }

  @Override
  public @NotNull String getFileName() {
    return getTitle();
  }
}
