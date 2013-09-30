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

import com.intellij.ide.projectWizard.EmptyProjectBuilder;
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

import javax.swing.*;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * @author Dmitry Avdeev
 *         Date: 10/9/12
 */
public class PlainModuleTemplatesFactory extends ProjectTemplatesFactory {

  @NotNull
  @Override
  public String[] getGroups() {
    List<ModuleBuilder> builders = ModuleBuilder.getAllBuilders();
    Set<String> groups = ContainerUtil.map2Set(builders, new Function<ModuleBuilder, String>() {
      @Override
      public String fun(ModuleBuilder builder) {
        return builder.getGroupName();
      }
    });
    HashSet<String> set = new HashSet<String>(groups);
    set.add(OTHER_GROUP);
    return ArrayUtil.toStringArray(set);
  }

  @NotNull
  @Override
  public ProjectTemplate[] createTemplates(final String group, WizardContext context) {
    if (OTHER_GROUP.equals(group)) {
      if (!context.isCreatingNewProject()) {
        return ProjectTemplate.EMPTY_ARRAY;
      }
      return new ProjectTemplate[]{new BuilderBasedTemplate(new EmptyProjectBuilder())};
    }
    List<ModuleBuilder> builders = ModuleBuilder.getAllBuilders();
    List<ProjectTemplate> templates = ContainerUtil.mapNotNull(builders, new NullableFunction<ModuleBuilder, ProjectTemplate>() {
      @Nullable
      @Override
      public ProjectTemplate fun(ModuleBuilder builder) {
        return builder.getGroupName().equals(group) ? new BuilderBasedTemplate(builder) : null;
      }
    });
    return templates.toArray(new ProjectTemplate[templates.size()]);
  }

  @Override
  public Icon getGroupIcon(String group) {
    List<ModuleBuilder> builders = ModuleBuilder.getAllBuilders();
    for (ModuleBuilder builder : builders) {
      if (group.equals(builder.getGroupName())) {
        return builder.getNodeIcon();
      }
    }
    return null;
  }

  @Override
  public int getGroupWeight(String group) {
    List<ModuleBuilder> builders = ModuleBuilder.getAllBuilders();
    for (int i = 0; i < builders.size(); i++) {
      if (group.equals(builders.get(i).getGroupName())) {
        return builders.size() - i;
      }
    }
    return 0;
  }
}
