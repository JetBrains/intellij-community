/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.ide.scratch;

import com.intellij.lang.Language;
import com.intellij.lang.LanguageUtil;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.IOException;

/**
 * @author gregsh
 *
 * Created on 1/19/15
 */
public abstract class RootType {

  public static final ExtensionPointName<RootType> ROOT_EP = ExtensionPointName.create("com.intellij.scratch.rootType");

  @NotNull
  public static RootType[] getAllRootIds() {
    return Extensions.getExtensions(ROOT_EP);
  }

  @NotNull
  public static RootType findById(@NotNull String id) {
    for (RootType type : getAllRootIds()) {
      if (id.equals(type.getId())) return type;
    }
    throw new AssertionError(id);
  }

  @NotNull
  public static <T extends RootType> T findByClass(Class<T> aClass) {
    return Extensions.findExtension(ROOT_EP, aClass);
  }

  @Nullable
  public static RootType forFile(@Nullable VirtualFile file) {
    return ScratchFileService.getInstance().getRootType(file);
  }

  private final String myId;
  private final String myDisplayName;

  protected RootType(@NotNull String id, @Nullable String displayName) {
    myId = id;
    myDisplayName = displayName;
  }

  @NotNull
  public final String getId() {
    return myId;
  }

  @Nullable
  public final String getDisplayName() {
    return myDisplayName;
  }

  public boolean isHidden() {
    return StringUtil.isEmpty(myDisplayName);
  }

  public boolean containsFile(@Nullable VirtualFile file) {
    if (file == null) return false;
    ScratchFileService service = ScratchFileService.getInstance();
    return service != null && service.getRootType(file) == this;
  }

  @Nullable
  public Language substituteLanguage(@NotNull Project project, @NotNull VirtualFile file) {
    return null;
  }

  @Nullable
  public Icon substituteIcon(@NotNull Project project, @NotNull VirtualFile file) {
    Language language = substituteLanguage(project, file);
    FileType fileType = LanguageUtil.getLanguageFileType(language);
    if (fileType == null) {
      String extension = file.getExtension();
      fileType = extension == null ? null : FileTypeManager.getInstance().getFileTypeByFileName(file.getNameSequence());
    }
    return fileType != null ? fileType.getIcon() : null;
  }

  @Nullable
  public String substituteName(@NotNull Project project, @NotNull VirtualFile file) {
    return null;
  }

  public VirtualFile findFile(@Nullable Project project, @NotNull String pathName, ScratchFileService.Option option) throws IOException {
    return ScratchFileService.getInstance().findFile(this, pathName, option);
  }

  public void fileOpened(@NotNull VirtualFile file, @NotNull FileEditorManager source) {
  }

  public void fileClosed(@NotNull VirtualFile file, @NotNull FileEditorManager source) {
  }

  public boolean isIgnored(@NotNull Project project, @NotNull VirtualFile element) {
    return false;
  }

  public void registerTreeUpdater(@NotNull Project project, @NotNull Disposable disposable, @NotNull Runnable onUpdate) {
  }

}
