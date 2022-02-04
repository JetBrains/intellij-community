// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.templateLanguages;

import com.intellij.lang.DependentLanguage;
import com.intellij.lang.InjectableLanguage;
import com.intellij.lang.Language;
import com.intellij.lang.LanguagePerFileMappings;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.impl.FilePropertyPusher;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * @author peter
 */
@State(name = "TemplateDataLanguageMappings", storages = @Storage("templateLanguages.xml"))
public class TemplateDataLanguageMappings extends LanguagePerFileMappings<Language> {

  public static TemplateDataLanguageMappings getInstance(final Project project) {
    return project.getService(TemplateDataLanguageMappings.class);
  }

  public TemplateDataLanguageMappings(final Project project) {
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

  @Nullable
  @Override
  public Language getMapping(@Nullable VirtualFile file) {
    Language t = getConfiguredMapping(file);
    return t == null || t == Language.ANY ? getDefaultMapping(file) : t;
  }

  @Override
  public Language getDefaultMapping(@Nullable VirtualFile file) {
    return getDefaultMappingForFile(file);
  }

  @Nullable
  public static Language getDefaultMappingForFile(@Nullable VirtualFile file) {
    return file == null? null : TemplateDataLanguagePatterns.getInstance().getTemplateDataLanguageByFileName(file);
  }

  public static @NotNull List<Language> getTemplateableLanguages() {
    return ContainerUtil.findAll(Language.getRegisteredLanguages(), new Condition<>() {
      @Override
      public boolean value(final Language language) {
        if (language == Language.ANY) return false;
        if (language instanceof TemplateLanguage || language instanceof DependentLanguage || language instanceof InjectableLanguage) {
          return false;
        }
        if (language.getBaseLanguage() != null) return value(language.getBaseLanguage());
        return true;
      }
    });
  }

  private final FilePropertyPusher<Language> myPropertyPusher = new TemplateDataLanguagePusher();

  @NotNull
  @Override
  protected FilePropertyPusher<Language> getFilePropertyPusher() {
    return myPropertyPusher;
  }
}
