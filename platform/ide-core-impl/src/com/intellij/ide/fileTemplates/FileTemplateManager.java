// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.fileTemplates;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.util.Key;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Properties;

public abstract class FileTemplateManager{
  public static final Key<Properties> DEFAULT_TEMPLATE_PROPERTIES = Key.create("DEFAULT_TEMPLATE_PROPERTIES");
  public static final int RECENT_TEMPLATES_SIZE = 25;

  public static final @NonNls String INTERNAL_HTML5_TEMPLATE_NAME = "HTML File";
  public static final @NonNls String FILE_HEADER_TEMPLATE_NAME = "File Header";

  public static final String DEFAULT_TEMPLATES_CATEGORY = "Default";
  public static final String INTERNAL_TEMPLATES_CATEGORY = "Internal";
  public static final String INCLUDES_TEMPLATES_CATEGORY = "Includes";
  public static final String CODE_TEMPLATES_CATEGORY = "Code";
  public static final String J2EE_TEMPLATES_CATEGORY = "J2EE";

  public static final String PROJECT_NAME_VARIABLE = "PROJECT_NAME";

  public static FileTemplateManager getInstance(@NotNull Project project){
    return project.getService(FileTemplateManager.class).checkInitialized();
  }

  protected @NotNull FileTemplateManager checkInitialized() { return this; }

  /**
   * @deprecated Use {@link #getInstance(Project)} instead
   */
  @Deprecated
  public static FileTemplateManager getInstance() {
    return getDefaultInstance();
  }

  public static FileTemplateManager getDefaultInstance(){
    return getInstance(ProjectManager.getInstance().getDefaultProject());
  }

  public abstract @NotNull FileTemplatesScheme getCurrentScheme();

  public abstract void setCurrentScheme(@NotNull FileTemplatesScheme scheme);

  /**
   * @return Project scheme, or null if manager is created for default project.
   */
  public abstract FileTemplatesScheme getProjectScheme();

  public abstract FileTemplate @NotNull [] getTemplates(@NotNull String category);

  /**
   *  Returns all templates from "Default" category.
   */
  public abstract FileTemplate @NotNull [] getAllTemplates();

  public abstract FileTemplate getTemplate(@NotNull @NonNls String templateName);

  /**
   * @return a new Properties object filled with predefined properties.
   */
  public abstract @NotNull Properties getDefaultProperties();

  /**
   * @deprecated use {@link #getDefaultProperties()} instead
   */
  @Deprecated(forRemoval = true)
  public @NotNull Properties getDefaultProperties(@NotNull Project project) {
    Properties properties = getDefaultProperties();
    properties.setProperty(PROJECT_NAME_VARIABLE, project.getName());
    return properties;
  }

  /**
   * Creates a new template with specified name, and adds it to the list of default templates.
   * @return created template
   */
  public abstract @NotNull FileTemplate addTemplate(@NotNull @NonNls String name, @NotNull @NonNls String extension);

  public abstract void removeTemplate(@NotNull FileTemplate template);

  public abstract @NotNull Collection<String> getRecentNames();

  public abstract void addRecentName(@NotNull @NonNls String name);

  public abstract @NotNull FileTemplate getInternalTemplate(@NotNull @NonNls String templateName);
  public abstract FileTemplate findInternalTemplate(@NotNull @NonNls String templateName);

  public abstract FileTemplate @NotNull [] getInternalTemplates();

  /**
   * @param templateName template name
   * @return a template by name
   * @throws IllegalStateException if template is not found
   */
  public abstract @NotNull FileTemplate getJ2eeTemplate(@NotNull @NonNls String templateName);

  /**
   * @param templateName template name
   * @return a template by name
   * @throws IllegalStateException if template is not found
   */
  public abstract @NotNull FileTemplate getCodeTemplate(@NotNull @NonNls String templateName);

  public abstract FileTemplate @NotNull [] getAllPatterns();

  public abstract FileTemplate @NotNull [] getAllCodeTemplates();

  public abstract FileTemplate @NotNull [] getAllJ2eeTemplates();

  public abstract @NotNull String internalTemplateToSubject(@NotNull @NonNls String templateName);

  public abstract FileTemplate getPattern(@NotNull @NonNls String name);

  /**
   * Returns template with default (bundled) text.
   */
  public abstract @NotNull FileTemplate getDefaultTemplate(@NotNull @NonNls String name);

  public abstract void setTemplates(@NotNull String templatesCategory, @NotNull Collection<? extends FileTemplate> templates);

  public abstract void saveAllTemplates();
}
