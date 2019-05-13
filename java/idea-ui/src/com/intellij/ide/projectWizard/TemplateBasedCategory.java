/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

  @NotNull
  @Override
  public ModuleBuilder createModuleBuilder() {
    return myTemplate.createModuleBuilder();
  }
}
