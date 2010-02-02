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
package com.intellij.packaging.impl.elements;

import com.intellij.openapi.compiler.CompilerBundle;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.IconLoader;
import com.intellij.packaging.elements.CompositePackagingElement;
import com.intellij.packaging.elements.CompositePackagingElementType;
import com.intellij.packaging.impl.artifacts.ArtifactUtil;
import com.intellij.packaging.impl.ui.properties.DirectoryElementPropertiesPanel;
import com.intellij.packaging.ui.ArtifactEditorContext;
import com.intellij.packaging.ui.PackagingElementPropertiesPanel;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
* @author nik
*/
class DirectoryElementType extends CompositePackagingElementType<DirectoryPackagingElement> {
  private static final Icon ICON = IconLoader.getIcon("/actions/newFolder.png");

  DirectoryElementType() {
    super("directory", CompilerBundle.message("element.type.name.directory"));
  }

  @Override
  public Icon getCreateElementIcon() {
    return ICON;
  }

  @NotNull
  public DirectoryPackagingElement createEmpty(@NotNull Project project) {
    return new DirectoryPackagingElement();
  }

  @Override
  public PackagingElementPropertiesPanel createElementPropertiesPanel(@NotNull DirectoryPackagingElement element,
                                                                                                 @NotNull ArtifactEditorContext context) {
    if (ArtifactUtil.isArchiveName(element.getDirectoryName())) {
      return new DirectoryElementPropertiesPanel(element, context);
    }
    return null;
  }

  public CompositePackagingElement<?> createComposite(CompositePackagingElement<?> parent, String baseName, @NotNull ArtifactEditorContext context) {
    final String initialValue = PackagingElementFactoryImpl.suggestFileName(parent, baseName != null ? baseName : "folder", "");
    String path = Messages.showInputDialog(context.getProject(), "Enter directory name: ", "New Directory", null, initialValue, new FilePathValidator());
    if (path == null) return null;
    return PackagingElementFactoryImpl.createDirectoryOrArchiveWithParents(path, false);
  }

}
