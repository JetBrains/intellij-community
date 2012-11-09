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

import com.intellij.ide.util.projectWizard.EmptyModuleBuilder;
import com.intellij.ide.util.projectWizard.ModuleBuilder;
import com.intellij.ide.util.projectWizard.WizardContext;
import com.intellij.platform.ProjectTemplate;
import com.intellij.platform.ProjectTemplatesFactory;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Function;
import com.intellij.util.NullableFunction;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * @author Dmitry Avdeev
 *         Date: 10/9/12
 */
public class PlainModuleTemplatesFactory implements ProjectTemplatesFactory {

  @NotNull
  @Override
  public String[] getGroups() {
    List<ModuleBuilder> builders = ModuleBuilder.getAllBuilders();
    List<String> groups = ContainerUtil.map(builders, new Function<ModuleBuilder, String>() {
      @Override
      public String fun(ModuleBuilder builder) {
        return builder.getGroupName();
      }
    });
    groups.add(OTHER_GROUP);
    return ArrayUtil.toStringArray(groups);
  }

  @NotNull
  @Override
  public ProjectTemplate[] createTemplates(final String group, WizardContext context) {
    if (OTHER_GROUP.equals(group)) {
      if (!context.isCreatingNewProject()) {
        return ProjectTemplate.EMPTY_ARRAY;
      }
      return new ProjectTemplate[]{new BuilderBasedTemplate(new EmptyModuleBuilder() {
        @Override
        public String getPresentableName() {
          return "Empty Project";
        }

        @Override
        public String getDescription() {
          return "Empty project without modules. Use it to create free-style module structure.";
        }
      })};
    }
    ModuleBuilder[] builders = context.getAllBuilders();
    return ContainerUtil.mapNotNull(builders, new NullableFunction<ModuleBuilder, ProjectTemplate>() {
      @Nullable
      @Override
      public ProjectTemplate fun(ModuleBuilder builder) {
        return builder.getGroupName().equals(group) ? new BuilderBasedTemplate(builder) : null;
      }
    }, ProjectTemplate.EMPTY_ARRAY);
  }
}
