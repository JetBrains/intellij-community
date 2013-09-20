/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
import com.intellij.platform.templates.ArchivedTemplatesFactory;
import com.intellij.platform.templates.LocalArchivedTemplate;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.io.File;
import java.net.URL;

/**
 * @author Dmitry Avdeev
 *         Date: 20.09.13
 */
public abstract class TemplateBasedProjectType extends ProjectCategory {

  private final LocalArchivedTemplate myTemplate;

  public TemplateBasedProjectType(String templatePath) {
    ClassLoader loader = getClass().getClassLoader();
    URL resource = loader.getResource(templatePath);
    assert resource != null : templatePath;
    String name = ArchivedTemplatesFactory.getTemplateName(new File(templatePath).getName());
    myTemplate = new LocalArchivedTemplate(name, resource, loader);
  }

  @NotNull
  @Override
  public ModuleBuilder createModuleBuilder() {
    return myTemplate.createModuleBuilder();
  }

  @Override
  public String getId() {
    return getDisplayName();
  }

  @Override
  public String getDisplayName() {
    return myTemplate.getName();
  }

  @Override
  public String getDescription() {
    return myTemplate.getDescription();
  }

  @Override
  public Icon getIcon() {
    return myTemplate.getIcon();
  }
}
