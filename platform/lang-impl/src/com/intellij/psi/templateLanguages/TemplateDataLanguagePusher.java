// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.templateLanguages;

import com.intellij.FilePropertyPusherBase;
import com.intellij.lang.Language;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.fileTypes.UnknownFileType;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.impl.PushedFilePropertiesUpdater;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.newvfs.FileAttribute;
import com.intellij.psi.FilePropertyKey;
import com.intellij.psi.FilePropertyKeyImpl;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Konstantin.Ulitin
 */
@ApiStatus.Internal
public final class TemplateDataLanguagePusher extends FilePropertyPusherBase<Language> {
  private static final FileAttribute PERSISTENCE = new FileAttribute("template_language", 4, true);
  public static final FilePropertyKey<Language> KEY =
    FilePropertyKeyImpl.createPersistentStringKey("TEMPLATE_DATA_LANGUAGE", PERSISTENCE,
                                                  TemplateDataLanguagePusher::asString, TemplateDataLanguagePusher::fromString);

  @Override
  public @NotNull FilePropertyKey<Language> getFilePropertyKey() {
    return KEY;
  }

  @Override
  public boolean pushDirectoriesOnly() {
    return false;
  }

  @Override
  public @NotNull Language getDefaultValue() {
    return Language.ANY;
  }

  @Override
  public @Nullable Language getImmediateValue(@NotNull Project project, @Nullable VirtualFile file) {
    return TemplateDataLanguageMappings.getInstance(project).getImmediateMapping(file);
  }

  @Override
  public @Nullable Language getImmediateValue(@NotNull Module module) {
    return null;
  }

  @Override
  public boolean acceptsFile(@NotNull VirtualFile file, @NotNull Project project) {
    FileType type = FileTypeManager.getInstance().getFileTypeByFileName(file.getNameSequence());
    if (type != UnknownFileType.INSTANCE) {
      return type instanceof LanguageFileType && ((LanguageFileType)type).getLanguage() instanceof TemplateLanguage;
    }
    // might be cheaper than file type detection
    return TemplateDataLanguageMappings.getInstance(project).getImmediateMapping(file) != null;
  }

  @Override
  public boolean acceptsDirectory(@NotNull VirtualFile file, @NotNull Project project) {
    return true;
  }

  private static String asString(@NotNull Language property) {
    return property.getID();
  }

  private static @NotNull Language fromString(@NotNull String id) {
    Language lang = Language.findLanguageByID(id);
    return ObjectUtils.notNull(lang, Language.ANY);
  }

  @Override
  public void propertyChanged(@NotNull Project project, @NotNull VirtualFile fileOrDir, @NotNull Language actualProperty) {
    PushedFilePropertiesUpdater.getInstance(project).filePropertiesChanged(fileOrDir, file -> acceptsFile(file, project));
  }
}
