/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.openapi.fileEditor.impl;

import com.intellij.ide.ui.UISettings;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.UniqueVFilePathBuilder;
import com.intellij.openapi.fileEditor.ex.IdeDocumentHistory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.UniqueNameBuilder;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFilePathWrapper;
import com.intellij.psi.search.FilenameIndex;
import com.intellij.psi.search.ProjectScope;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.Collection;
import java.util.Collections;

/**
 * @author yole
 */
public class UniqueVFilePathBuilderImpl extends UniqueVFilePathBuilder {
  @NotNull
  @Override
  public String getUniqueVirtualFilePath(Project project, VirtualFile file) {
    return getUniqueVirtualFilePath(project, file, false);
  }

  @NotNull
  @Override
  public String getUniqueVirtualFilePathWithinOpenedFileEditors(Project project, VirtualFile vFile) {
    return getUniqueVirtualFilePath(project, vFile, true);
  }

  private static String getUniqueVirtualFilePath(Project project, VirtualFile file, boolean skipNonOpenedFiles) {
    String fileName = file.getName();
    Collection<VirtualFile> filesWithSameName = skipNonOpenedFiles ? Collections.<VirtualFile>emptySet() :
                                                FilenameIndex.getVirtualFilesByName(project, fileName, ProjectScope.getProjectScope(project));
    THashSet<VirtualFile> setOfFilesWithTheSameName = new THashSet<VirtualFile>(filesWithSameName);
    // add open files out of project scope
    for(VirtualFile openFile: FileEditorManager.getInstance(project).getOpenFiles()) {
      if (openFile.getName().equals(fileName)) {
        setOfFilesWithTheSameName.add(openFile);
      }
    }
    for (VirtualFile recentlyEditedFile : IdeDocumentHistory.getInstance(project).getChangedFiles()) {
      if (recentlyEditedFile.getName().equals(fileName)) {
        setOfFilesWithTheSameName.add(recentlyEditedFile);
      }
    }

    filesWithSameName = setOfFilesWithTheSameName;

    if (filesWithSameName.size() > 1 && filesWithSameName.contains(file)) {
      if (file instanceof VirtualFilePathWrapper) {
        return ((VirtualFilePathWrapper)file).getPresentablePath();
      }
      String path = project.getBasePath();
      path = path == null ? "" : FileUtil.toSystemIndependentName(path);
      UniqueNameBuilder<VirtualFile> builder = new UniqueNameBuilder<VirtualFile>(path, File.separator, 25);
      for (VirtualFile virtualFile: filesWithSameName) {
        builder.addPath(virtualFile, virtualFile.getPath());
      }
      return getEditorTabText(file, builder, UISettings.getInstance().HIDE_KNOWN_EXTENSION_IN_TABS);
    }
    return file.getPresentableName();
  }

  public static <T> String getEditorTabText(T key, UniqueNameBuilder<T> builder, boolean hideKnownExtensionInTabs) {
    String result = builder.getShortPath(key);
    if (hideKnownExtensionInTabs) {
      String withoutExtension = FileUtil.getNameWithoutExtension(result);
      if (StringUtil.isNotEmpty(withoutExtension) && !withoutExtension.endsWith(builder.getSeparator())) {
        return withoutExtension;
      }
    }
    return result;
  }
}
