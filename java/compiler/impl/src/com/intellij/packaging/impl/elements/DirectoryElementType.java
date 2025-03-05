// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.packaging.impl.elements;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.compiler.JavaCompilerBundle;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.packaging.elements.CompositePackagingElement;
import com.intellij.packaging.elements.CompositePackagingElementType;
import com.intellij.packaging.impl.ui.properties.DirectoryElementPropertiesPanel;
import com.intellij.packaging.ui.ArtifactEditorContext;
import com.intellij.packaging.ui.PackagingElementPropertiesPanel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.incremental.artifacts.impl.JpsArtifactUtil;

import javax.swing.*;

class DirectoryElementType extends CompositePackagingElementType<DirectoryPackagingElement> {

  DirectoryElementType() {
    super("directory", JavaCompilerBundle.messagePointer("element.type.name.directory"));
  }

  @Override
  public Icon getCreateElementIcon() {
    return AllIcons.Actions.NewFolder;
  }

  @Override
  public @NotNull DirectoryPackagingElement createEmpty(@NotNull Project project) {
    return new DirectoryPackagingElement();
  }

  @Override
  public PackagingElementPropertiesPanel createElementPropertiesPanel(@NotNull DirectoryPackagingElement element,
                                                                                                 @NotNull ArtifactEditorContext context) {
    if (JpsArtifactUtil.isArchiveName(element.getDirectoryName())) {
      return new DirectoryElementPropertiesPanel(element, context);
    }
    return null;
  }

  @Override
  public CompositePackagingElement<?> createComposite(CompositePackagingElement<?> parent, String baseName, @NotNull ArtifactEditorContext context) {
    final String initialValue = PackagingElementFactoryImpl.suggestFileName(parent, baseName != null ? baseName : "folder", "");
    String path = Messages.showInputDialog(context.getProject(), JavaCompilerBundle.message("dialog.message.enter.directory.name"),
                                           JavaCompilerBundle.message("title.new.directory"), null, initialValue, new FilePathValidator());
    if (path == null) return null;
    return PackagingElementFactoryImpl.createDirectoryOrArchiveWithParents(path, false);
  }

}
