// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.projectWizard;

import com.intellij.ide.util.projectWizard.ModuleBuilder;
import com.intellij.platform.templates.ArchivedProjectTemplate;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * @author Dmitry Avdeev
 */
public class TemplateBasedCategory extends ProjectCategory {

  private final ArchivedProjectTemplate myTemplate;
  private final String myProjectType;

  public TemplateBasedCategory(ArchivedProjectTemplate template, String projectType) {
    myTemplate = template;
    myProjectType = projectType;
  }

  @Override
  public String getDisplayName() {
    return myProjectType;
  }

  @Override
  public Icon getIcon() {
    return myTemplate.getIcon();
  }

  @Override
  public String getDescription() {
    return myTemplate.getDescription();
  }

  @Override
  public @NotNull ModuleBuilder createModuleBuilder() {
    return myTemplate.createModuleBuilder();
  }
}
