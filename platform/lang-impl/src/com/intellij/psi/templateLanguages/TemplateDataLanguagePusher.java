// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Konstantin.Ulitin
 */
public class TemplateDataLanguagePusher extends FilePropertyPusherBase<Language> {
  private static final FileAttribute PERSISTENCE = new FileAttribute("template_language", 4, true);
  public static final FilePropertyKey<Language> KEY =
    FilePropertyKeyImpl.createPersistentStringKey("TEMPLATE_DATA_LANGUAGE", PERSISTENCE,
                                                  TemplateDataLanguagePusher::asString, TemplateDataLanguagePusher::fromString);

  @NotNull
  @Override
  public FilePropertyKey<Language> getFileDataKey() {
    return KEY;
  }

  @Override
  public boolean pushDirectoriesOnly() {
    return false;
  }

  @NotNull
  @Override
  public Language getDefaultValue() {
    return Language.ANY;
  }

  @Nullable
  @Override
  public Language getImmediateValue(@NotNull Project project, @Nullable VirtualFile file) {
    return TemplateDataLanguageMappings.getInstance(project).getImmediateMapping(file);
  }

  @Nullable
  @Override
  public Language getImmediateValue(@NotNull Module module) {
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

  @NotNull
  private static Language fromString(@NotNull String id) {
    Language lang = Language.findLanguageByID(id);
    return ObjectUtils.notNull(lang, Language.ANY);
  }

  @Override
  public void propertyChanged(@NotNull Project project, @NotNull VirtualFile fileOrDir, @NotNull Language actualProperty) {
    PushedFilePropertiesUpdater.getInstance(project).filePropertiesChanged(fileOrDir, file -> acceptsFile(file, project));
  }
}
