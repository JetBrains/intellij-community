// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.scratch;

import com.intellij.lang.Language;
import com.intellij.lang.LanguageUtil;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeRegistry;
import com.intellij.openapi.fileTypes.UnknownFileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.IOException;
import java.util.List;

public abstract class RootType {
  public static final ExtensionPointName<RootType> ROOT_EP = new ExtensionPointName<>("com.intellij.scratch.rootType");

  public static @NotNull List<RootType> getAllRootTypes() {
    return ROOT_EP.getExtensionList();
  }

  public static @NotNull RootType findById(@NotNull String id) {
    for (RootType type : getAllRootTypes()) {
      if (id.equals(type.getId())) return type;
    }
    throw new AssertionError(id);
  }

  public static @NotNull <T extends RootType> T findByClass(@NotNull Class<T> aClass) {
    return ROOT_EP.findExtensionOrFail(aClass);
  }

  public static @Nullable RootType forFile(@Nullable VirtualFile file) {
    return ScratchFileService.findRootType(file);
  }

  private final String myId;
  private final @Nls String myDisplayName;

  protected RootType(@NonNls @NotNull String id,
                     @Nullable @Nls(capitalization = Nls.Capitalization.Title) String displayName) {
    myId = id;
    myDisplayName = displayName;
  }

  public final @NotNull String getId() {
    return myId;
  }

  public final @Nullable @Nls(capitalization = Nls.Capitalization.Title) String getDisplayName() {
    return myDisplayName;
  }

  public boolean isHidden() {
    return StringUtil.isEmpty(myDisplayName);
  }

  public boolean containsFile(@Nullable VirtualFile file) {
    return ScratchFileService.findRootType(file) == this;
  }

  public @Nullable Language substituteLanguage(@NotNull Project project, @NotNull VirtualFile file) {
    return null;
  }

  public @Nullable Icon substituteIcon(@NotNull Project project, @NotNull VirtualFile file) {
    if (file.isDirectory()) return null;
    Language language = substituteLanguage(project, file);
    FileType fileType = LanguageUtil.getLanguageFileType(language);
    if (fileType == null) {
      String extension = file.getExtension();
      fileType = extension == null ? null : FileTypeRegistry.getInstance().getFileTypeByFileName(file.getNameSequence());
    }
    return fileType != null && fileType != UnknownFileType.INSTANCE ? fileType.getIcon() : null;
  }

  public @Nullable @NlsSafe String substituteName(@NotNull Project project, @NotNull VirtualFile file) {
    return null;
  }

  public VirtualFile findFile(@Nullable Project project, @NotNull String pathName, ScratchFileService.Option option) throws IOException {
    return ScratchFileService.getInstance().findFile(this, pathName, option);
  }

  public void fileOpened(@NotNull VirtualFile file, @NotNull FileEditorManager source) {
  }

  public boolean isIgnored(@NotNull Project project, @NotNull VirtualFile element) {
    return false;
  }

  public void registerTreeUpdater(@NotNull Project project, @NotNull Disposable disposable, @NotNull Runnable onUpdate) {
  }
}
