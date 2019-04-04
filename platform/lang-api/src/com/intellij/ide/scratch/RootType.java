// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.scratch;

import com.intellij.lang.Language;
import com.intellij.lang.LanguageUtil;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.extensions.ExtensionPointName;
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
import java.util.List;

/**
 * @author gregsh
 *
 * Created on 1/19/15
 */
public abstract class RootType {
  public static final ExtensionPointName<RootType> ROOT_EP = ExtensionPointName.create("com.intellij.scratch.rootType");

  @NotNull
  public static List<RootType> getAllRootTypes() {
    return ROOT_EP.getExtensionList();
  }

  @NotNull
  public static RootType findById(@NotNull String id) {
    for (RootType type : getAllRootTypes()) {
      if (id.equals(type.getId())) return type;
    }
    throw new AssertionError(id);
  }

  @NotNull
  public static <T extends RootType> T findByClass(Class<T> aClass) {
    return ROOT_EP.findExtensionOrFail(aClass);
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
    if (file.isDirectory()) return null;
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
