/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.psi.templateLanguages;

import com.intellij.lang.LangBundle;
import com.intellij.lang.Language;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.IconLoader;
import com.intellij.util.ui.tree.LanguagePerFileConfigurable;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * @author peter
 */
public class TemplateDataLanguageConfigurable extends LanguagePerFileConfigurable<Language> {

  public static TemplateDataLanguageConfigurable getInstance(final Project project) {
    return ShowSettingsUtil.getInstance().findProjectConfigurable(project, TemplateDataLanguageConfigurable.class);
  }

  public TemplateDataLanguageConfigurable(Project project) {
    super(project, Language.class, TemplateDataLanguageMappings.getInstance(project),
          LangBundle.message("dialog.template.data.language.caption"), LangBundle.message("template.data.language.configurable.tree.table.title"),
          LangBundle.message("template.data.language.override.warning.text"),
          LangBundle.message("template.data.language.override.warning.title"));
  }

  @Nls
  public String getDisplayName() {
    return LangBundle.message("template.data.language.configurable");
  }

  @Nullable
  public Icon getIcon() {
    return IconLoader.getIcon("/general/configurableFileTypes.png");
  }

  @Nullable
  @NonNls
  public String getHelpTopic() {
    return null;
  }

  protected String visualize(@NotNull final Language language) {
    return language.getDisplayName();
  }


}
