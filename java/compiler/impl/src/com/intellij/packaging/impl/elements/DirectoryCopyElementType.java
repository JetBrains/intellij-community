// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.packaging.impl.elements;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.compiler.JavaCompilerBundle;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
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

public class DirectoryCopyElementType extends PackagingElementType<DirectoryCopyPackagingElement> {

  DirectoryCopyElementType() {
    super("dir-copy", JavaCompilerBundle.messagePointer("directory.copy.element.type.name"));
  }

  @Override
  public Icon getCreateElementIcon() {
    return AllIcons.Nodes.CopyOfFolder;
  }

  @Override
  public boolean canCreate(@NotNull ArtifactEditorContext context, @NotNull Artifact artifact) {
    return true;
  }

  @Override
  public @NotNull List<? extends DirectoryCopyPackagingElement> chooseAndCreate(@NotNull ArtifactEditorContext context, @NotNull Artifact artifact,
                                                                                @NotNull CompositePackagingElement<?> parent) {
    final FileChooserDescriptor descriptor = FileChooserDescriptorFactory.createMultipleFoldersDescriptor();
    final VirtualFile[] files = FileChooser.chooseFiles(descriptor, context.getProject(), null);
    final List<DirectoryCopyPackagingElement> list = new ArrayList<>();
    for (VirtualFile file : files) {
      list.add(new DirectoryCopyPackagingElement(file.getPath()));
    }
    return list;
  }

  @Override
  public @NotNull DirectoryCopyPackagingElement createEmpty(@NotNull Project project) {
    return new DirectoryCopyPackagingElement();
  }
}
