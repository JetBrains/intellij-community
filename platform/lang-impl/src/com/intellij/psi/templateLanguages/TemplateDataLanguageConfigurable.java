/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.psi.templateLanguages;

import com.intellij.lang.LangBundle;
import com.intellij.lang.Language;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.ColoredTableCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
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
  public TemplateDataLanguageConfigurable(Project project) {
    super(project, Language.class, TemplateDataLanguageMappings.getInstance(project),
          LangBundle.message("dialog.template.data.language.caption"), LangBundle.message("template.data.language.configurable.tree.table.title"),
          LangBundle.message("template.data.language.override.warning.text"),
          LangBundle.message("template.data.language.override.warning.title"));
  }

  @Override
  protected boolean handleDefaultValue(VirtualFile file, ColoredTableCellRenderer renderer) {
    final Language language = TemplateDataLanguagePatterns.getInstance().getTemplateDataLanguageByFileName(file);
    if (language != null) {
      renderer.append(visualize(language), SimpleTextAttributes.GRAY_ITALIC_ATTRIBUTES);
      return true;
    }
    return false;
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
    return "reference.settingsdialog.project.template.languages";
  }

  protected String visualize(@NotNull final Language language) {
    return language.getDisplayName();
  }


}
