/*
 * Copyright 2000-2010 JetBrains s.r.o.
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

import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDialog;
import com.intellij.openapi.fileChooser.FileChooserFactory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.packaging.artifacts.Artifact;
import com.intellij.packaging.elements.CompositePackagingElement;
import com.intellij.packaging.elements.PackagingElementType;
import com.intellij.packaging.ui.ArtifactEditorContext;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;

/**
* @author nik
*/
public class ExtractedDirectoryElementType extends PackagingElementType<ExtractedDirectoryPackagingElement> {
  public static final Icon EXTRACTED_FOLDER_ICON = IconLoader.getIcon("/nodes/extractedFolder.png");

  ExtractedDirectoryElementType() {
    super("extracted-dir", "Extracted Directory");
  }

  @Override
  public Icon getCreateElementIcon() {
    return EXTRACTED_FOLDER_ICON;
  }

  @Override
  public boolean canCreate(@NotNull ArtifactEditorContext context, @NotNull Artifact artifact) {
    return true;
  }

  @NotNull
  public List<? extends ExtractedDirectoryPackagingElement> chooseAndCreate(@NotNull ArtifactEditorContext context, @NotNull Artifact artifact,
                                                                   @NotNull CompositePackagingElement<?> parent) {
    final FileChooserDescriptor descriptor = new FileChooserDescriptor(false, true, true, false, true, true) {
      @Override
      public boolean isFileSelectable(VirtualFile file) {
        if (file.isInLocalFileSystem() && file.isDirectory()) return false;
        return super.isFileSelectable(file);
      }
    };
    final FileChooserDialog chooser = FileChooserFactory.getInstance().createFileChooser(descriptor, context.getProject());
    final VirtualFile[] files = chooser.choose(null, context.getProject());
    final List<ExtractedDirectoryPackagingElement> list = new ArrayList<ExtractedDirectoryPackagingElement>();
    for (VirtualFile file : files) {
      final String fullPath = file.getPath();
      final int jarEnd = fullPath.indexOf(JarFileSystem.JAR_SEPARATOR);
      list.add(new ExtractedDirectoryPackagingElement(fullPath.substring(0, jarEnd), fullPath.substring(jarEnd + 1)));
    }
    return list;
  }

  @NotNull
  public ExtractedDirectoryPackagingElement createEmpty(@NotNull Project project) {
    return new ExtractedDirectoryPackagingElement();
  }
}
