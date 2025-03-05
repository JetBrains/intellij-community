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
import com.intellij.packaging.elements.PackagingElement;
import com.intellij.packaging.elements.PackagingElementFactory;
import com.intellij.packaging.elements.PackagingElementType;
import com.intellij.packaging.ui.ArtifactEditorContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;

public class ExtractedDirectoryElementType extends PackagingElementType<ExtractedDirectoryPackagingElement> {

  ExtractedDirectoryElementType() {
    super("extracted-dir", JavaCompilerBundle.messagePointer("extracted.directory.element.type.name"));
  }

  @Override
  public Icon getCreateElementIcon() {
    return AllIcons.Nodes.ExtractedFolder;
  }

  @Override
  public boolean canCreate(@NotNull ArtifactEditorContext context, @NotNull Artifact artifact) {
    return true;
  }

  @Override
  public @NotNull List<? extends PackagingElement<?>> chooseAndCreate(@NotNull ArtifactEditorContext context, @NotNull Artifact artifact,
                                                                      @NotNull CompositePackagingElement<?> parent) {
    final FileChooserDescriptor descriptor = new FileChooserDescriptor(false, true, true, false, true, true) {
      @Override
      public boolean isFileSelectable(@Nullable VirtualFile file) {
        if (file == null || (file.isInLocalFileSystem() && file.isDirectory())) return false;
        return super.isFileSelectable(file);
      }
    };
    final VirtualFile[] files = FileChooser.chooseFiles(descriptor, context.getProject(), null);
    final List<PackagingElement<?>> list = new ArrayList<>();
    final PackagingElementFactory factory = PackagingElementFactory.getInstance();
    for (VirtualFile file : files) {
      list.add(factory.createExtractedDirectory(file));
    }
    return list;
  }

  @Override
  public @NotNull ExtractedDirectoryPackagingElement createEmpty(@NotNull Project project) {
    return new ExtractedDirectoryPackagingElement();
  }
}
