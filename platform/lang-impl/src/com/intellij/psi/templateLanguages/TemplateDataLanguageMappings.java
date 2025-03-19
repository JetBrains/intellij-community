// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.templateLanguages;

import com.intellij.lang.DependentLanguage;
import com.intellij.lang.InjectableLanguage;
import com.intellij.lang.Language;
import com.intellij.lang.LanguagePerFileMappings;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.fileTypes.PlainTextLanguage;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.impl.FilePropertyPusher;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;


@Service(Service.Level.PROJECT)
@State(name = "TemplateDataLanguageMappings", storages = @Storage("templateLanguages.xml"))
public final class TemplateDataLanguageMappings extends LanguagePerFileMappings<Language> {
  private final FilePropertyPusher<Language> myPropertyPusher = new TemplateDataLanguagePusher();

  public static TemplateDataLanguageMappings getInstance(final Project project) {
    return project.getService(TemplateDataLanguageMappings.class);
  }

  public TemplateDataLanguageMappings(@NotNull Project project) {
    super(project);
  }

  @Override
  protected String serialize(final @NotNull Language language) {
    return language.getID();
  }

  @Override
  public @NotNull List<Language> getAvailableValues() {
    return getTemplateableLanguages();
  }

  @Override
  public @Nullable Language getMapping(@Nullable VirtualFile file) {
    Language t = getConfiguredMapping(file);
    return t == null || t == Language.ANY ? getDefaultMapping(file) : t;
  }

  @Override
  public Language getDefaultMapping(@Nullable VirtualFile file) {
    return getDefaultMappingForFile(file);
  }

  public static @Nullable Language getDefaultMappingForFile(@Nullable VirtualFile file) {
    return file == null? null : TemplateDataLanguagePatterns.getInstance().getTemplateDataLanguageByFileName(file);
  }

  private static boolean isTemplateableLanguage(final Language language) {
    if (language == Language.ANY) return false;
    if (language instanceof TemplateLanguage || language instanceof DependentLanguage || language instanceof InjectableLanguage) {
      return false;
    }
    if (language.getBaseLanguage() != null) return isTemplateableLanguage(language.getBaseLanguage());
    return true;
  }

  public static @NotNull List<Language> getTemplateableLanguages() {
    return Language.getRegisteredLanguages().stream()
      .filter(language -> isTemplateableLanguage(language))
      .toList();
  }

  @Override
  protected @NotNull FilePropertyPusher<Language> getFilePropertyPusher() {
    return myPropertyPusher;
  }

  @Override
  protected @NotNull Language handleUnknownMapping(VirtualFile file, String value) {
    Language defaultMapping = getDefaultMapping(file);
    return defaultMapping != null ? defaultMapping : PlainTextLanguage.INSTANCE;
  }
}
