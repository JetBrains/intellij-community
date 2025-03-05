// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.packaging.impl.elements;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.compiler.JavaCompilerBundle;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.packaging.artifacts.Artifact;
import com.intellij.packaging.elements.CompositePackagingElement;
import com.intellij.packaging.elements.PackagingElementType;
import com.intellij.packaging.ui.ArtifactEditorContext;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;

public class FileCopyElementType extends PackagingElementType<FileCopyPackagingElement> {

  FileCopyElementType() {
    super("file-copy", JavaCompilerBundle.messagePointer("file.title"));
  }

  @Override
  public Icon getCreateElementIcon() {
    return AllIcons.FileTypes.Text;
  }

  @Override
  public boolean canCreate(@NotNull ArtifactEditorContext context, @NotNull Artifact artifact) {
    return true;
  }

  @Override
  public @NotNull List<? extends FileCopyPackagingElement> chooseAndCreate(@NotNull ArtifactEditorContext context, @NotNull Artifact artifact,
                                                                           @NotNull CompositePackagingElement<?> parent) {
    final FileChooserDescriptor descriptor = new FileChooserDescriptor(true, false, true, true, false, true);
    final VirtualFile[] files = FileChooser.chooseFiles(descriptor, context.getProject(), null);
    final List<FileCopyPackagingElement> list = new ArrayList<>();
    for (VirtualFile file : files) {
      list.add(new FileCopyPackagingElement(file.getPath()));
    }
    return list;
  }

  @Override
  public @NotNull FileCopyPackagingElement createEmpty(@NotNull Project project) {
    return new FileCopyPackagingElement();
  }
}
