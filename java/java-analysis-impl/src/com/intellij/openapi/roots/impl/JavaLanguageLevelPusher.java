// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.roots.impl;

import com.intellij.FileIntPropertyPusher;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.module.EffectiveLanguageLevelUtil;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.newvfs.FileAttribute;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;


/**
 * @author Gregory.Shrago
 */
public class JavaLanguageLevelPusher implements FileIntPropertyPusher<LanguageLevel> {

  public static void pushLanguageLevel(@NotNull final Project project) {
    PushedFilePropertiesUpdater instance = PushedFilePropertiesUpdater.getInstance(project);
    for (FilePropertyPusher pusher : EP_NAME.getExtensionList()) {
      if (pusher instanceof JavaLanguageLevelPusher) {
        instance.pushAll(pusher);
      }
    }
  }

  @Override
  @NotNull
  public Key<LanguageLevel> getFileDataKey() {
    return LanguageLevel.KEY;
  }

  @Override
  public boolean pushDirectoriesOnly() {
    return true;
  }

  @Override
  @NotNull
  public LanguageLevel getDefaultValue() {
    return LanguageLevel.HIGHEST;
  }

  @Override
  public LanguageLevel getImmediateValue(@NotNull Project project, @Nullable VirtualFile file) {
    return null;
  }

  @Override
  public LanguageLevel getImmediateValue(@NotNull Module module) {
    return EffectiveLanguageLevelUtil.getEffectiveLanguageLevel(module);
  }

  @Override
  public boolean acceptsFile(@NotNull VirtualFile file) {
    return false;
  }

  @Override
  public boolean acceptsDirectory(@NotNull VirtualFile file, @NotNull Project project) {
    return ProjectFileIndex.SERVICE.getInstance(project).isInSourceContent(file);
  }

  private static final FileAttribute PERSISTENCE = new FileAttribute("language_level_persistence", 3, true);

  @Override
  public @NotNull FileAttribute getAttribute() {
    return PERSISTENCE;
  }

  @Override
  public int toInt(@NotNull LanguageLevel languageLevel) {
    return languageLevel.ordinal();
  }

  @Override
  public @NotNull LanguageLevel fromInt(int val) {
    return LanguageLevel.values()[val];
  }

  @Override
  public void propertyChanged(@NotNull Project project,
                              @NotNull VirtualFile fileOrDir,
                              @NotNull LanguageLevel actualProperty) {
    // Todo: GwtLanguageLevelPusher changes java language level for single files without firing filePropertiesChanged
    // so code below doesn't work.
    // Uncomment it and remove older code once the problem is fixed
    //PushedFilePropertiesUpdater.getInstance(project).filePropertiesChanged(fileOrDir, f -> isJavaLike(f.getFileType()));

    for (VirtualFile child : fileOrDir.getChildren()) {
      if (!child.isDirectory() && isJavaLike(child.getFileType())) {
        PushedFilePropertiesUpdater.getInstance(project).filePropertiesChanged(child);
      }
    }
  }

  private static boolean isJavaLike(FileType type) {
    return type instanceof LanguageFileType && ((LanguageFileType)type).getLanguage().isKindOf(JavaLanguage.INSTANCE);
  }

  @Nullable
  public String getInconsistencyLanguageLevelMessage(@NotNull String message, @NotNull PsiElement element,
                                                     @NotNull LanguageLevel level, @NotNull PsiFile file) {
    return null;
  }
}
