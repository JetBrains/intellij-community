/*
 * Copyright 2000-2010 JetBrains s.r.o.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.roots.libraries.ui;

import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
* @author nik
*/
public abstract class AttachRootButtonDescriptorBase extends AttachRootButtonDescriptor {
  public AttachRootButtonDescriptorBase(@NotNull OrderRootType rootType, @NotNull String buttonText) {
    super(rootType, buttonText);
  }

  public FileChooserDescriptor createChooserDescriptor() {
    return new FileChooserDescriptor(false, true, true, false, true, true);
  }

  public abstract String getChooserTitle(final @Nullable String libraryName);
  public abstract String getChooserDescription();

  @Override
  public VirtualFile[] selectFiles(final @NotNull JComponent parent, @Nullable VirtualFile initialSelection,
                                   final @Nullable Module contextModule, @Nullable String libraryName) {
    final FileChooserDescriptor chooserDescriptor = createChooserDescriptor();
    chooserDescriptor.setTitle(getChooserTitle(libraryName));
    chooserDescriptor.setDescription(getChooserDescription());
    if (contextModule != null) {
      chooserDescriptor.putUserData(LangDataKeys.MODULE_CONTEXT, contextModule);
    }
    return FileChooser.chooseFiles(parent, chooserDescriptor, initialSelection);
  }
}
