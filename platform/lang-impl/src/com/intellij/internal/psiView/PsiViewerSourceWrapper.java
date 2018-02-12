/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.internal.psiView;

import com.intellij.lang.Language;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.FileTypes;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.fileTypes.impl.AbstractFileType;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

class PsiViewerSourceWrapper implements Comparable<PsiViewerSourceWrapper> {

  final FileType myFileType;
  final PsiViewerExtension myExtension;

  public PsiViewerSourceWrapper(@NotNull final FileType fileType) {
    myFileType = fileType;
    myExtension = null;
  }

  public PsiViewerSourceWrapper(final PsiViewerExtension extension) {
    myFileType = null;
    myExtension = extension;
  }

  public String getText() {
    return myFileType != null ? myFileType.getName() + " file" : myExtension.getName();
  }

  @Nullable
  public Icon getIcon() {
    return myFileType != null ? myFileType.getIcon() : myExtension.getIcon();
  }

  @Override
  public int compareTo(@NotNull final PsiViewerSourceWrapper o) {
    return getText().compareToIgnoreCase(o.getText());
  }


  @NotNull
  public static List<PsiViewerSourceWrapper> getExtensionBasedWrappers() {
    return Arrays
      .stream(Extensions.getExtensions(PsiViewerExtension.EP_NAME))
      .map(el -> new PsiViewerSourceWrapper(el))
      .collect(Collectors.toList());
  }

  @NotNull
  public static List<PsiViewerSourceWrapper> getFileTypeBasedWrappers() {
    Set<FileType> allFileTypes = ContainerUtil.newHashSet();
    List<PsiViewerSourceWrapper> sourceWrappers = ContainerUtil.newArrayList();
    Collections.addAll(allFileTypes, FileTypeManager.getInstance().getRegisteredFileTypes());
    for (Language language : Language.getRegisteredLanguages()) {
      FileType fileType = language.getAssociatedFileType();
      if (fileType != null) {
        allFileTypes.add(fileType);
      }
    }
    for (FileType fileType : allFileTypes) {
      if (isAcceptableFileType(fileType)) {
        final PsiViewerSourceWrapper wrapper = new PsiViewerSourceWrapper(fileType);
        sourceWrappers.add(wrapper);
      }
    }

    return sourceWrappers;
  }

  private static boolean isAcceptableFileType(FileType fileType) {
    return fileType != StdFileTypes.GUI_DESIGNER_FORM &&
           fileType != StdFileTypes.IDEA_MODULE &&
           fileType != StdFileTypes.IDEA_PROJECT &&
           fileType != StdFileTypes.IDEA_WORKSPACE &&
           fileType != FileTypes.ARCHIVE &&
           fileType != FileTypes.UNKNOWN &&
           fileType != FileTypes.PLAIN_TEXT &&
           !(fileType instanceof AbstractFileType) &&
           !fileType.isBinary() &&
           !fileType.isReadOnly();
  }
}
