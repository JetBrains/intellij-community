/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.platform.templates;

import com.intellij.ide.util.projectWizard.ModuleBuilder;
import com.intellij.ide.util.projectWizard.WizardContext;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.platform.ProjectTemplate;
import com.intellij.platform.ProjectTemplatesFactory;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.List;

/**
 * @author Dmitry Avdeev
 *         Date: 10/9/12
 */
public class EmptyModuleTemplatesFactory implements ProjectTemplatesFactory {

  public static final String GROUP_NAME = "Standard";

  @NotNull
  @Override
  public String[] getGroups() {
    List<ModuleBuilder> builders = ModuleBuilder.getAllBuilders();
    return ContainerUtil.map2Array(builders, String.class, new Function<ModuleBuilder, String>() {
      @Override
      public String fun(ModuleBuilder builder) {
        return getGroupName(builder);
      }
    });
  }

  @NotNull
  @Override
  public ProjectTemplate[] createTemplates(String group, WizardContext context) {
    List<ModuleBuilder> builders = ModuleBuilder.getAllBuilders();
    for (ModuleBuilder builder : builders) {
      if (getGroupName(builder).equals(group)) return new ProjectTemplate[] {new EmptyModuleTemplate(builder)};
    }
    return new ProjectTemplate[0];
  }

  private static String getGroupName(ModuleBuilder builder) {
    String name = builder.getPresentableName();
    return name.split(" ")[0];
  }

  private static class EmptyModuleTemplate implements ProjectTemplate {
    private final ModuleBuilder myBuilder;

    public EmptyModuleTemplate(ModuleBuilder builder) {
      myBuilder = builder;
    }

    @NotNull
    @Override
    public String getName() {
      return myBuilder.getPresentableName();
    }

    @Nullable
    @Override
    public String getDescription() {
      return myBuilder.getDescription();
    }

    @Nullable
    @Override
    public JComponent getSettingsPanel() {
      return null;
    }

    @NotNull
    @Override
    public ModuleBuilder createModuleBuilder() {
      return myBuilder;
    }

    @Nullable
    @Override
    public ValidationInfo validateSettings() {
      return null;
    }
  }
}
