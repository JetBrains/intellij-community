/*
 * Copyright 2000-2010 JetBrains s.r.o.
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

package com.intellij.facet.impl.statistics;

import com.intellij.internal.statistic.beans.UsageDescriptor;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.ProjectManagerListener;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.Function;
import com.intellij.util.containers.HashSet;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.Set;

@State(
  name = "FrameworkUsages",
  storages = {
    @Storage(
      id = "frameworks",
      file = "$APP_CONFIG$/framework.usages.xml"
    )}
)
public class FrameworkStatisticsPersistenceComponent extends FrameworkStatisticsPersistence
  implements ApplicationComponent, PersistentStateComponent<Element> {
  private static final String TOKENIZER = ",";

  @NonNls private static final String PROJECT_TAG = "project";
  @NonNls private static final String PROJECT_ID_ATTR = "id";
  @NonNls private static final String FRAMEWORKS_ATTR = "frameworks";

  public FrameworkStatisticsPersistenceComponent() {
  }

  public static FrameworkStatisticsPersistenceComponent getInstance() {
    return ApplicationManager.getApplication().getComponent(FrameworkStatisticsPersistenceComponent.class);
  }

  public void loadState(final Element element) {
    List projectsList = element.getChildren(PROJECT_TAG);
    for (Object project : projectsList) {
      Element projectElement = (Element)project;
      String projectId = projectElement.getAttributeValue(PROJECT_ID_ATTR);
      String frameworks = projectElement.getAttributeValue(FRAMEWORKS_ATTR);
      if (!StringUtil.isEmptyOrSpaces(projectId) && !StringUtil.isEmptyOrSpaces(frameworks)) {
        Set<UsageDescriptor> frameworkDescriptors = new HashSet<UsageDescriptor>();
        for (String key : StringUtil.split(frameworks, TOKENIZER)) {
          frameworkDescriptors.add(new UsageDescriptor(FrameworkUsagesCollector.getGroupDescriptor(), key, 1));
        }
        getFrameworks().put(projectId, frameworkDescriptors);
      }
    }
  }

  public Element getState() {
    Element element = new Element("state");

    for (Map.Entry<String, Set<UsageDescriptor>> frameworks : getFrameworks().entrySet()) {
      Element projectElement = new Element(PROJECT_TAG);
      projectElement.setAttribute(PROJECT_ID_ATTR, frameworks.getKey());
      projectElement.setAttribute(FRAMEWORKS_ATTR, joinUsages(frameworks.getValue()));

      element.addContent(projectElement);
    }

    return element;
  }

  private static String joinUsages(@NotNull Set<UsageDescriptor> usages) {
    return StringUtil.join(usages, new Function<UsageDescriptor, String>() {
      @Override
      public String fun(UsageDescriptor usageDescriptor) {
        return usageDescriptor.getKey();
      }
    }, TOKENIZER);
  }

  @NotNull
  @NonNls
  public File[] getExportFiles() {
    return new File[]{PathManager.getOptionsFile("framework.usages")};
  }

  @NotNull
  public String getPresentableName() {
    return "Framework Usages";
  }

  @NonNls
  @NotNull
  public String getComponentName() {
    return "FrameworkStatisticsPersistenceComponent";
  }

  public void initComponent() {
    ProjectManager.getInstance().addProjectManagerListener(new ProjectManagerListener() {
      @Override
      public void projectOpened(Project project) {
      }

      @Override
      public boolean canCloseProject(Project project) {
        return true;
      }

      @Override
      public void projectClosed(Project project) {
      }

      @Override
      public void projectClosing(Project project) {
        if (project != null) {
          FrameworkUsagesCollector.persistProjectUsages(project);
        }
      }
    });
  }

  public void disposeComponent() {
  }
}
