// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.packaging.impl.elements;

import com.intellij.openapi.compiler.JavaCompilerBundle;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.packaging.elements.CompositePackagingElement;
import com.intellij.packaging.elements.CompositePackagingElementType;
import com.intellij.packaging.impl.ui.properties.ArchiveElementPropertiesPanel;
import com.intellij.packaging.ui.ArtifactEditorContext;
import com.intellij.packaging.ui.PackagingElementPropertiesPanel;
import com.intellij.util.PlatformIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.incremental.artifacts.impl.JpsArtifactUtil;

import javax.swing.*;

class ArchiveElementType extends CompositePackagingElementType<ArchivePackagingElement> {
  ArchiveElementType() {
    super("archive", JavaCompilerBundle.messagePointer("element.type.name.archive"));
  }

  @Override
  public Icon getCreateElementIcon() {
    return PlatformIcons.JAR_ICON;
  }

  @Override
  public @NotNull ArchivePackagingElement createEmpty(@NotNull Project project) {
    return new ArchivePackagingElement();
  }

  @Override
  public PackagingElementPropertiesPanel createElementPropertiesPanel(@NotNull ArchivePackagingElement element,
                                                                                               @NotNull ArtifactEditorContext context) {
    final String name = element.getArchiveFileName();
    if (JpsArtifactUtil.isArchiveName(name)) {
      return new ArchiveElementPropertiesPanel(element, context);
    }
    return null;
  }

  @Override
  public CompositePackagingElement<?> createComposite(CompositePackagingElement<?> parent, @Nullable String baseName, @NotNull ArtifactEditorContext context) {
    final String initialValue = PackagingElementFactoryImpl.suggestFileName(parent, baseName != null ? baseName : "archive", ".jar");
    final String path = Messages.showInputDialog(context.getProject(), JavaCompilerBundle.message("enter.archive.name"),
                                                 JavaCompilerBundle.message("title.new.archive"), null, initialValue, new FilePathValidator());
    if (path == null) return null;
    return PackagingElementFactoryImpl.createDirectoryOrArchiveWithParents(path, true);
  }
}
