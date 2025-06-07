// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.roots.impl;

import com.intellij.FilePropertyPusherBase;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.module.LanguageLevelUtil;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.FilePropertyKey;
import com.intellij.psi.FilePropertyKeyImpl;
import com.intellij.psi.PsiFile;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;


public final class JavaLanguageLevelPusher extends FilePropertyPusherBase<LanguageLevel> {

  private static final FilePropertyKey<LanguageLevel> KEY = FilePropertyKeyImpl.createPersistentEnumKey("LANGUAGE_LEVEL",
                                                                                                        "language_level_persistence", 4,
                                                                                                        LanguageLevel.class);

  public static void pushLanguageLevel(final @NotNull Project project) {
    JavaLanguageLevelPusher pusher = EP_NAME.findExtension(JavaLanguageLevelPusher.class);
    PushedFilePropertiesUpdater.getInstance(project).pushAll(pusher);
  }

  @Override
  public @NotNull FilePropertyKey<LanguageLevel> getFilePropertyKey() {
    return KEY;
  }

  @Override
  public boolean pushDirectoriesOnly() {
    return false;
  }

  @Override
  public boolean acceptsFile(@NotNull VirtualFile file, @NotNull Project project) {
    return file.equals(ProjectFileIndex.getInstance(project).getSourceRootForFile(file));
  }

  @Override
  public @NotNull LanguageLevel getDefaultValue() {
    return LanguageLevel.HIGHEST;
  }

  @Override
  public LanguageLevel getImmediateValue(@NotNull Project project, @Nullable VirtualFile file) {
    return JavaLanguageLevelPusherCustomizer.getImmediateValueImpl(project, file);
  }

  @Override
  public LanguageLevel getImmediateValue(@NotNull Module module) {
    return LanguageLevelUtil.getEffectiveLanguageLevel(module);
  }

  @Override
  public boolean acceptsDirectory(@NotNull VirtualFile file, @NotNull Project project) {
    return ProjectFileIndex.getInstance(project).isInSourceContent(file);
  }

  @Override
  public void propertyChanged(@NotNull Project project,
                              @NotNull VirtualFile fileOrDir,
                              @NotNull LanguageLevel actualProperty) {
    // Todo: GwtLanguageLevelPusher changes java language level for single files without firing filePropertiesChanged
    // so code below doesn't work.
    // Uncomment it and remove older code once the problem is fixed
    //PushedFilePropertiesUpdater.getInstance(project).filePropertiesChanged(fileOrDir, f -> isJavaLike(f.getFileType()));

    VirtualFileJavaLanguageLevelListener publisher = project.getMessageBus().syncPublisher(VirtualFileJavaLanguageLevelListener.TOPIC);

    for (VirtualFile child : fileOrDir.getChildren()) {
      if (!child.isDirectory() && isJavaLike(child.getFileType())) {
        PushedFilePropertiesUpdater.getInstance(project).filePropertiesChanged(child);
        publisher.levelChanged(child, actualProperty);
      }
    }
  }

  private static boolean isJavaLike(FileType type) {
    return type instanceof LanguageFileType && ((LanguageFileType)type).getLanguage().isKindOf(JavaLanguage.INSTANCE);
  }

  public @Nullable @NlsContexts.DetailedDescription String getInconsistencyLanguageLevelMessage(@NotNull String message,
                                                                                                @NotNull LanguageLevel level,
                                                                                                @NotNull PsiFile file) {
    return JavaLanguageLevelPusherCustomizer.getInconsistencyLanguageLevelMessageImpl(message, level, file);
  }

  public static @Nullable LanguageLevel getPushedLanguageLevel(@NotNull VirtualFile file) {
    return ObjectUtils.coalesce(KEY.getPersistentValue(file.getParent()), KEY.getPersistentValue(file));
  }
}
